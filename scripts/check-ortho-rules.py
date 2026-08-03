#!/usr/bin/env python3
"""Hold `morphology/ortho.xfst` to Plover's own 38 rules, candidate by candidate.

Not an agreement rate: an exact set comparison. For every pair, Plover's rules
give a set of `(rule number, expansion)` and so does the artifact, and the two
must be equal. That is a much tighter check than comparing final answers, because
a rule that never fires and a rule that fires wrongly look the same at the end.

Needs Plover's python for the rules and the hfst toolchain for the lookup:

  hfst-lookup on PATH, then
  /Applications/Plover.app/Contents/Frameworks/Python.framework/Versions/3.13/bin/python3.13 \\
      scripts/check-ortho-rules.py build/morph/ortho.hfst
"""
import subprocess
import sys

# Every suffix the 38 rules name, plus the ones real dictionaries write. A rule
# no pair reaches is a rule this script cannot vouch for, so the list is checked
# for coverage at the end.
SUFFIXES = """
ly ry ary aries cy s es ies ing ist ists en ens ial ially tory torily
ify ifying ified ifies ification ifications ical ically icality
ity tive tivity tivities ize ized izes izing izer izers ization izations
ise ised ises ising iser isers isation isations izable izability
ology ologist ologists ological ologically ish
ed er est able ible ability ibility y ion ions ous eer ee ent ance
""".split()


# Shapes a sample of common words does not reliably reach, one word each. Rule 16
# wants a word ending in `i`, rule 10 one ending in `ie`, rule 4 one ending in `m`.
# `attachés` is not for a rule but for the alphabet: an accented letter has to reach
# rule 7 rather than fall off the tape. Seeding these makes the coverage line a fact
# rather than a property of `limit`.
SEED = "semi movie affirm attachés".split()


def words(limit):
    import plover.system as system
    out = list(SEED)
    for w in system.ORTHOGRAPHY_WORDS:
        if w.isalpha() and w.islower() and len(w) > 2:
            out.append(w)
            if len(out) >= limit:
                break
    return out


def theirs(pairs):
    import plover.system as system
    rules = system.ORTHOGRAPHY_RULES
    out = {}
    for word, suffix in pairs:
        subject = word + " ^ " + suffix
        found = set()
        for n, (pattern, replacement) in enumerate(rules, 1):
            m = pattern.match(subject)
            if m:
                found.add((n, m.expand(replacement)))
        out[(word, suffix)] = found
    return out


def ours(net, pairs):
    lines = "\n".join("%s^%s" % p for p in pairs) + "\n"
    run = subprocess.run(["hfst-lookup", "-q", "-p", net],
                         input=lines, capture_output=True, text=True)
    if run.returncode != 0:
        print(run.stderr.strip(), file=sys.stderr)
        sys.exit(1)
    out = {p: set() for p in pairs}
    for line in run.stdout.splitlines():
        if not line.strip():
            continue
        field = line.split("\t")
        if len(field) < 2 or field[1].endswith("+?"):
            continue
        word, _, suffix = field[0].partition("^")
        tag, _, expansion = field[1].partition("]")
        out[(word, suffix)].add((int(tag.removeprefix("[R=")), expansion))
    return out


def main(net, limit=4000):
    from plover.registry import registry
    import plover.system as system
    registry.update()
    system.setup("English Stenotype")

    pairs = [(w, s) for w in words(limit) for s in SUFFIXES]
    want, got = theirs(pairs), ours(net, pairs)

    bad = [p for p in pairs if want[p] != got[p]]
    print("%d pairs, %d candidate sets differ" % (len(pairs), len(bad)))
    for p in bad[:20]:
        print("  %s ^ %s: plover %s, ours %s"
              % (p[0], p[1], sorted(want[p]), sorted(got[p])))

    # A rule the sample never reaches is unchecked, and silence about that would
    # be the same mistake as a coverage claim with no number behind it.
    fired = {n for p in pairs for n, _ in want[p]}
    missed = sorted(set(range(1, 39)) - fired)
    if missed:
        print("rules no pair reached: %s" % missed)
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 4000))
