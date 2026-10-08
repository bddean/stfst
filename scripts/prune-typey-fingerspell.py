"""Remove typey-type entries whose outline is all fingerspelling chords.

Such entries hijack a fingerspelled letter run, replacing the glued letters
with an unglued plain word, so the next fingerspelled letter gets a space.
Deletes lines in place to keep the diff to just the removed entries.
"""
import json, os, re, sys

path = os.path.join(os.path.dirname(__file__), "..", "dictionaries", "typey-type-upstream.json")
d = json.load(open(path))

# Fingerspelling chords: single-stroke outlines producing a glued letter.
chords = {k for k, v in d.items()
          if "/" not in k and re.fullmatch(r"(\{>\})?\{&[a-zA-Z]\}", v)}

def landmine(k, v):
    strokes = k.split("/")
    return len(strokes) > 1 and all(s in chords for s in strokes) and "{" not in v

doomed = {k for k, v in d.items() if landmine(k, v)}
for k in sorted(doomed):
    print(f"  {k}: {d[k]!r}")
print(f"removing {len(doomed)} of {len(d)}", file=sys.stderr)

lines = open(path).read().splitlines(keepends=True)
key_re = re.compile(r'^\s*"((?:[^"\\]|\\.)*)":')
out = []
for line in lines:
    m = key_re.match(line)
    if m and json.loads(f'"{m.group(1)}"') in doomed:
        continue
    out.append(line)

new_text = "".join(out)
reloaded = json.loads(new_text)  # abort before writing if we broke the JSON
assert set(d) - set(reloaded) == doomed and len(reloaded) == len(d) - len(doomed)
open(path, "w").write(new_text)
print("done", file=sys.stderr)
