"""A compiled stfst artifact as a Plover dictionary.

`dictionary` is the plugin proper and needs Plover. `record` and `ol` are the two
halves it is made of, the record language and the automaton reader, and neither
imports Plover: the golden generators use `record` as an oracle, and an oracle
that loaded the thing it checks would not be one.
"""

__version__ = "0.1.0"
