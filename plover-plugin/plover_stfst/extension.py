"""The `stfst` extension: what the artifacts need the translator to do that a
dictionary lookup cannot ask for. A held stroke waits for the next unit
(`plover_stfst.host`), and a fold reads a stroke nothing matched as another
(`plover_stfst.fold`)."""

from . import fold
from .host import Ahead


class Extension:
    def __init__(self, engine):
        self._translator = engine._translator
        self._ahead = Ahead(engine)
        self._unfold = None

    def start(self):
        self._ahead.start()
        self._unfold = fold.install(self._translator)

    def stop(self):
        if self._unfold is not None:
            self._unfold()
            self._unfold = None
        self._ahead.stop()
