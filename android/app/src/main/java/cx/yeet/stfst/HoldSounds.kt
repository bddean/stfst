package cx.yeet.stfst

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** The hold's sounds, as the machine rendered them, played on cue. */
class HoldSounds private constructor(private val arm: AudioTrack, private val disarm: AudioTrack, private val send: AudioTrack) {

    fun play(cue: NativeMachine.Cue) {
        halt(arm)
        when (cue) {
            NativeMachine.Cue.ARM -> restart(arm)
            NativeMachine.Cue.CANCEL -> {}
            NativeMachine.Cue.DISARM -> restart(disarm)
            NativeMachine.Cue.SEND -> restart(send)
        }
    }

    fun release() {
        arm.release()
        disarm.release()
        send.release()
    }

    private fun halt(track: AudioTrack) {
        track.pause()
        track.playbackHeadPosition = 0
    }

    private fun restart(track: AudioTrack) {
        halt(track)
        track.play()
    }

    companion object {
        /** The machine's sounds, or null if it does not hold. */
        fun of(machine: Long): HoldSounds? {
            val pcm = (0..2).map { NativeMachine.sound(machine, it) ?: return null }
            val (arm, disarm, send) = pcm.map(::track)
            return HoldSounds(arm, disarm, send)
        }

        private fun track(pcm: ShortArray): AudioTrack {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            track.write(pcm, 0, pcm.size)
            return track
        }
    }
}
