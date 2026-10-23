"""The `stfst` extension: what the artifacts need the translator to do that a
dictionary lookup cannot ask for. A held stroke waits for the next unit
(`plover_stfst.host`), a fold reads a stroke nothing matched as another
(`plover_stfst.fold`), and a join that makes no word completes to one
(`plover_stfst.morph`)."""

from . import fold, morph
from .host import Ahead


class Extension:
    def __init__(self, engine):
        self._translator = engine._translator
        self._ahead = Ahead(engine)
        self._unfold = None
        self._unjoin = None

    def start(self):
        self._ahead.start()
        self._unfold = fold.install(self._translator)
        self._unjoin = morph.install()

    def stop(self):
        if self._unjoin is not None:
            self._unjoin()
            self._unjoin = None
        if self._unfold is not None:
            self._unfold()
            self._unfold = None
        self._ahead.stop()
