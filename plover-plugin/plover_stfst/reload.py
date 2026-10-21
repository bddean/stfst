"""The second extension: reload an artifact that has been rebuilt.

Plover reloads a dictionary whose file is newer than the copy it holds, and it
asks that question when the configuration changes. Nothing else asks it, so a
rebuilt artifact is invisible until a restart. This watches the artifacts and
gives Plover the question; Plover's own answer decides which of them to read
again.

An empty configuration update is that question and nothing else: it changes no
option, saves no file and fires no `config_changed` hook, and it ends in the
reload every configuration change ends in.

What is watched is the artifact directory's mtime, which `write_artifact` moves
by renaming the manifest into place. So this notices a build and not a write:
`stfst compile` is what makes a dictionary live, and it is also what refuses to
write an artifact whose checks failed. An editable artifact is the exception:
its file is written without a build, so a file newer than what Plover read moves
the artifact's mtime, and Plover's answer is then the same.
"""

import os
import threading

from plover import log
from plover.resource import resource_timestamp

# The extension in `plover.cfg` this is enabled under, and the name of its
# thread.
NAME = "stfst_reload"

# How long after a build the dictionary is live. A stat per artifact, so the
# cost of asking more often is not what makes this a second rather than less.
INTERVAL = 1.0

# An artifact is a directory, and the extension is what tells Plover its format.
SUFFIX = ".stfst"


class Reload:
    def __init__(self, engine):
        self._engine = engine
        self._stop = threading.Event()

    def start(self):
        # Each watcher gets its own event, so a restart cannot revive one that
        # is still finishing.
        self._stop = threading.Event()
        threading.Thread(
            target=self._watch, args=(self._stop,), name=NAME, daemon=True
        ).start()

    def stop(self):
        # The engine holds its lock while it stops extensions, and the watcher
        # takes that lock, so joining the watcher here would deadlock.
        self._stop.set()

    def _watch(self, stop):
        while not stop.wait(INTERVAL):
            rebuilt = self._rebuilt()
            if rebuilt and not stop.is_set():
                log.info("%s: %s was rebuilt", NAME, ", ".join(rebuilt))
                # Queued onto the engine thread, which is where a configuration
                # change belongs and where the dictionaries are read.
                self._engine.config = {}

    def _rebuilt(self):
        """The artifacts on disk that are newer than the copy Plover holds.

        A build that failed to load is one of them until it is built again: the
        timestamp of a dictionary Plover could not read is the one it could not
        read, so a fixed artifact is a rebuilt one and a broken one is asked
        about once.
        """
        out = []
        for dictionary in self._engine.dictionaries.dicts:
            path = dictionary.path or ""
            if not path.endswith(SUFFIX):
                continue
            try:
                built = resource_timestamp(path)
                file = getattr(dictionary, "file", None)
                if file and resource_timestamp(file) > dictionary.timestamp:
                    os.utime(path)
                    built = resource_timestamp(path)
            except OSError:
                # Gone, or half written. The next look is a second away.
                continue
            if built > dictionary.timestamp:
                out.append(path)
        return out
