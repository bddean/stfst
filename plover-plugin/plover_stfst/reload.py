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
write an artifact whose checks failed.
"""

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
        self._thread = None

    def start(self):
        self._stop.clear()
        self._thread = threading.Thread(target=self._watch, name=NAME, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()
        self._thread.join()
        self._thread = None

    def _watch(self):
        while not self._stop.wait(INTERVAL):
            rebuilt = self._rebuilt()
            if rebuilt:
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
            except OSError:
                # Gone, or half written. The next look is a second away.
                continue
            if built > dictionary.timestamp:
                out.append(path)
        return out
