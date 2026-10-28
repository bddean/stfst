package cx.yeet.stfst

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Steno machines on USB: found, asked for, opened, and read, one thread each.
 *
 * A Plover HID machine is told from the rest of a composite device by its
 * report descriptor. A serial machine is any device `usb-serial-for-android`
 * has a driver for, and speaks Gemini PR or Stentura as [serialProtocol] says.
 * A device with both is read as Plover HID, which says more.
 *
 * Every event does the same thing, [scan], which is idempotent: open what is
 * attached, usable and permitted, and close what is gone. Everything here but
 * the reader threads runs on the main thread.
 *
 * It lives exactly as long as the keyboard is the selected input method, which
 * is what keeps its prompts, and [UsbActivity]'s, away from every other time.
 */
class Machines(
    private val context: Context,
    /** The text of `plover.cfg`. */
    private val cfg: String,
    /** The system's key names in steno order, space-separated. */
    private val names: String,
    private val serialProtocol: () -> Int,
    private val stroke: (Long) -> Unit,
    /** Whether any machine is connected changed, or a machine was lost and why. */
    private val changed: (String) -> Unit,
) {
    private val usb = context.getSystemService(UsbManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val links = mutableMapOf<String, Link>()
    /** Devices asked for permission, until unplugged, so a refusal is not asked again. */
    private val asked = mutableSetOf<String>()
    /** Devices that are no machine, or lost the protocol, until unplugged. */
    private val unusable = mutableSetOf<String>()

    val connected: Boolean get() = links.isNotEmpty()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = scan()
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            addAction(ACTION_SCAN)
        }
        // Exported, because attach and detach come from the system. Anything
        // else sending ACTION_SCAN only makes a scan happen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        UsbActivity.offer(context, true)
        scan()
    }

    fun stop() {
        UsbActivity.offer(context, false)
        context.unregisterReceiver(receiver)
        links.values.forEach { it.close() }
        links.clear()
    }

    fun scan() {
        val devices = usb.deviceList.values
        val present = devices.map { it.deviceName }.toSet()
        asked.retainAll(present)
        unusable.retainAll(present)
        val was = connected
        for (name in links.keys - present) links.remove(name)?.close()
        for (device in devices) {
            val name = device.deviceName
            if (name in links || name in unusable) continue
            val serial = UsbSerialProber.getDefaultProber().probeDevice(device)
            if (serial == null && candidates(device).isEmpty()) continue
            if (!usb.hasPermission(device)) {
                if (asked.add(name)) usb.requestPermission(device, permission())
                continue
            }
            val link = try {
                open(device, serial)
            } catch (e: Exception) {
                Log.e(StenoService.TAG, "opening ${device.productName}: $e")
                null
            }
            if (link == null) {
                unusable += name
                continue
            }
            links[name] = link
            link.start()
        }
        if (connected != was) changed(if (connected) "machine: ${labels()}" else "")
    }

    private fun labels() = links.values.joinToString(", ") { it.label }

    private fun permission(): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(ACTION_SCAN).setPackage(context.packageName), PendingIntent.FLAG_IMMUTABLE,
    )

    /** The interfaces that could be Plover HID: HID, not a boot keyboard or mouse, with an interrupt IN endpoint. */
    private fun candidates(device: UsbDevice): List<UsbInterface> =
        (0 until device.interfaceCount).map(device::getInterface).filter { intf ->
            intf.interfaceClass == UsbConstants.USB_CLASS_HID && intf.interfaceProtocol == 0 &&
                input(intf) != null
        }

    private fun input(intf: UsbInterface): UsbEndpoint? =
        (0 until intf.endpointCount).map(intf::getEndpoint).firstOrNull {
            it.direction == UsbConstants.USB_DIR_IN && it.type == UsbConstants.USB_ENDPOINT_XFER_INT
        }

    private fun open(device: UsbDevice, serial: UsbSerialDriver?): Link? {
        val connection = usb.openDevice(device) ?: return null
        return try {
            open(device, serial, connection).also { if (it == null) connection.close() }
        } catch (e: Exception) {
            connection.close()
            throw e
        }
    }

    private fun open(device: UsbDevice, serial: UsbSerialDriver?, connection: UsbDeviceConnection): Link? {
        val label = device.productName ?: device.deviceName
        for (intf in candidates(device)) {
            if (!connection.claimInterface(intf, true)) continue
            val descriptor = ByteArray(4096)
            val n = connection.controlTransfer(
                0x81, GET_DESCRIPTOR, REPORT_DESCRIPTOR shl 8, intf.id, descriptor, descriptor.size, 1000,
            )
            if (n > 0 && NativeMachine.declares(descriptor.copyOf(n))) {
                val transport = HidTransport(connection, intf, input(intf)!!)
                return Link(device.deviceName, "$label (Plover HID)", NativeMachine.HID, transport)
            }
            connection.releaseInterface(intf)
        }
        if (serial == null) return null
        val (protocol, baud, bits, parity, stops) = NativeMachine.serial(cfg, serialProtocol()).toList()
        val port = serial.ports.first()
        port.open(connection)
        port.setParameters(
            baud, bits,
            STOP_BITS[stops] ?: UsbSerialPort.STOPBITS_1,
            PARITY[parity.toChar()] ?: UsbSerialPort.PARITY_NONE,
        )
        // A virtual serial port on a hobbyist board may send nothing until the
        // host says it is there.
        try {
            port.dtr = true
        } catch (e: Exception) {
        }
        try {
            port.purgeHwBuffers(true, true)
        } catch (e: Exception) {
        }
        val name = if (protocol == NativeMachine.STENTURA) "Stentura" else "Gemini PR"
        return Link(device.deviceName, "$label ($name)", protocol, SerialTransport(port))
    }

    private fun lost(link: Link, why: String) {
        if (links[link.device] !== link) return
        links.remove(link.device)
        unusable += link.device
        Log.e(StenoService.TAG, "${link.label}: $why")
        changed("${link.label}: $why")
    }

    /** One machine's reader: the loop every protocol shares. */
    private inner class Link(
        /** The device's name, which keys [links]. */
        val device: String,
        val label: String,
        protocol: Int,
        private val transport: Transport,
    ) : Thread("machine $device") {
        private val machine = NativeMachine.open(protocol, cfg, names)
        private val sounds = HoldSounds.of(machine)
        @Volatile private var closed = false

        init {
            NativeMachine.warnings(machine).lines().filter { it.isNotEmpty() }
                .forEach { Log.w(StenoService.TAG, "plover.cfg: $it") }
        }

        fun close() {
            closed = true
            transport.close()
        }

        override fun run() {
            val buffer = ByteArray(transport.size)
            var why = "unplugged"
            try {
                while (!closed) {
                    NativeMachine.outgoing(machine, clock())?.let(transport::write)
                    val wake = NativeMachine.wake(machine, clock())
                    val n = transport.read(buffer, if (wake < 0) IDLE_MS else maxOf(1L, wake))
                    if (closed || n < 0) break
                    val out = if (n > 0) NativeMachine.feed(machine, clock(), buffer, n)
                    else NativeMachine.idle(machine, clock())
                    if (out == null) {
                        why = NativeMachine.error(machine)
                        break
                    }
                    if (out[0] >= 0) sounds?.play(NativeMachine.Cue.entries[out[0].toInt()])
                    if (out.size > 1) main.post { for (i in 1 until out.size) stroke(out[i]) }
                }
            } catch (e: IOException) {
                why = e.message ?: e.toString()
            } finally {
                transport.close()
                sounds?.release()
                NativeMachine.close(machine)
                if (!closed) main.post { lost(this, why) }
            }
        }
    }

    private interface Transport {
        /** The most one read can return. */
        val size: Int

        /** Bytes read into `into`, 0 if none came in time, or -1 if the device is gone. */
        fun read(into: ByteArray, timeoutMs: Long): Int
        fun write(bytes: ByteArray)

        /** Safe to call twice, and from another thread than a read's. */
        fun close()
    }

    /** One report per read, off the interrupt endpoint. */
    private class HidTransport(
        private val connection: UsbDeviceConnection,
        private val intf: UsbInterface,
        endpoint: UsbEndpoint,
    ) : Transport {
        override val size = endpoint.maxPacketSize
        private val request = UsbRequest().apply { initialize(connection, endpoint) }
        private val buffer = ByteBuffer.allocate(size)
        private var queued = false
        private val open = AtomicBoolean(true)

        override fun read(into: ByteArray, timeoutMs: Long): Int {
            if (!queued) {
                buffer.clear()
                if (!request.queue(buffer)) return -1
                queued = true
            }
            val done = try {
                connection.requestWait(timeoutMs)
            } catch (e: TimeoutException) {
                return 0
            } ?: return -1
            queued = false
            if (done !== request) return 0
            val n = buffer.position()
            buffer.flip()
            buffer.get(into, 0, n)
            return n
        }

        override fun write(bytes: ByteArray) {}

        override fun close() {
            if (!open.getAndSet(false)) return
            request.cancel()
            connection.releaseInterface(intf)
            connection.close()
        }
    }

    private class SerialTransport(private val port: UsbSerialPort) : Transport {
        override val size = maxOf(64, port.readEndpoint.maxPacketSize)
        private val open = AtomicBoolean(true)

        override fun read(into: ByteArray, timeoutMs: Long): Int = port.read(into, timeoutMs.toInt())

        override fun write(bytes: ByteArray) = port.write(bytes, 1000)

        override fun close() {
            if (!open.getAndSet(false)) return
            try {
                port.close()
            } catch (e: IOException) {
            }
        }
    }

    companion object {
        /** Scan again: a permission answered, or a device handed to [UsbActivity]. */
        const val ACTION_SCAN = "cx.yeet.stfst.SCAN"

        /** How long a read waits when the machine has nothing timed. */
        private const val IDLE_MS = 1000L
        private const val GET_DESCRIPTOR = 6
        private const val REPORT_DESCRIPTOR = 0x22

        /** pyserial's stop bits, times two. */
        private val STOP_BITS = mapOf(
            2 to UsbSerialPort.STOPBITS_1, 3 to UsbSerialPort.STOPBITS_1_5, 4 to UsbSerialPort.STOPBITS_2,
        )
        private val PARITY = mapOf(
            'N' to UsbSerialPort.PARITY_NONE, 'O' to UsbSerialPort.PARITY_ODD, 'E' to UsbSerialPort.PARITY_EVEN,
            'M' to UsbSerialPort.PARITY_MARK, 'S' to UsbSerialPort.PARITY_SPACE,
        )

        private fun clock() = SystemClock.uptimeMillis()
    }
}
