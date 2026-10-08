#!/usr/bin/env python3
"""Build repeat-idioms.json: the non-literal overrides above repeat-word.py.

The generic {:repeat_word:<conn>} meta already yields every literal X-conn-X
(back to back, side by side, more and more), so this dictionary holds only the
trigrams whose third word is NOT the first (in and out, this or that). Two
sources, merged and deduped by first word (seed wins):

  SEED  -- timeless fixed expressions the corpus underranks against grammar
           (point of view, break the ice, this or that).
  MINED -- high-frequency trigrams from scripts/mine-repeat-trigrams.py
           (men and women, whether or not, keep in mind, work from home).
           Preposition scaffolding ("prior to the") is dropped by rejecting a
           determiner/pronoun as the third word.

A first word that forms a common literal reduplication is left to the repeat,
per the "prefer the repeat" rule, so nothing here shadows it. Each entry is
emitted under every stem of its connective (STEMS), so either brief triggers it.
Run with Plover's bundled python.
"""

import json
import os
import re
from collections import defaultdict

DICTS = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "dictionaries")
MINED = os.path.join(DICTS, "repeat-trigrams.json")

# stems per connective (must match repeat-word.py STEMS).
STEMS = {
    "and": ["^SKP"], "or": ["^OR"], "to": ["^O", "^TO"], "the": ["^-T"],
    "of": ["^-F", "^*F"], "by": ["^PWEU"], "for": ["^TP-R", "^TPOR"],
    "in": ["^TPH", "^EUPB"], "on": ["^OB", "^OPB"], "over": ["^OEFR"],
}

# First words absent from both stock dicts, outlined here by default-theory
# convention (spick~pick PEUBG, kith~myth PH*EUT, warts~wart WART).
MANUAL_OUTLINE = {"cradle": "KRAEUL", "spick": "SPEUBG", "kith": "K*EUT",
                  "warts": "WARTS"}

# A mined trigram is grammar, not an idiom, when either flanking word is a
# determiner, pronoun, auxiliary, preposition, conjunction, relativizer, or a
# bare statistic unit. Quantifiers (two, more, nothing, ...) are kept: they head
# real idioms (two or three, all or nothing). Applied to both flanking words.
STOP = {
    # determiners / pronouns
    "the", "a", "an", "her", "his", "its", "their", "your", "my", "our",
    "this", "that", "these", "those", "them", "him", "us", "it", "he", "she",
    "they", "we", "you", "i",
    # auxiliaries / light verbs
    "have", "has", "had", "be", "been", "being", "is", "are", "was", "were",
    "do", "does", "did", "will", "would", "shall", "should", "can", "could",
    "may", "might", "must", "get", "got", "not",
    # prepositions / conjunctions / relativizers
    "and", "or", "but", "nor", "so", "yet", "than", "then", "of", "to", "in",
    "on", "for", "by", "with", "from", "at", "as", "into", "onto", "upon",
    "over", "under", "off", "up", "out", "about", "around", "through", "below",
    "above", "which", "who", "whom", "whose", "how", "when", "where", "why",
    "what", "course", "per", "until", "unless", "while", "although",
    "because", "another",
    # bare statistic units (corpus artifacts: "cap of billion")
    "billion", "million", "trillion", "percent", "yards",
    # superlative / hedge fragments, not idiom content ("arguably the most")
    "most", "least", "arguably", "perhaps", "largest", "biggest", "preceded",
    # corpus-specific proper nouns ("duchess of sussex", "bitcoin or ethereum")
    "bitcoin", "ethereum", "duke", "duchess", "sussex",
}
MAX_MINED_PER_CONN = 40

# Mining is trusted only for connectives that join content words, where a fixed
# trigram is idiomatic. The prepositions to/by/for/on/over yield mostly syntax
# ("check to see", "requests for comment"); their value is the generic
# reduplication, so they draw idioms from the seed alone.
MINE_CONN = {"and", "or", "in", "of", "the"}

# SEED: curated fixed expressions, connective -> [full phrase]. First word is
# the trigger; keep the single dominant completion per first word. These are
# brainstormed, not corpus-mined: the corpus underranks timeless idioms against
# frequent-but-generic grammar, and a language model already knows the fixed
# binomials, so this list is where the coverage lives. A first word whose own
# X-conn-X reduplication is the common expression (more and more, side by side,
# year over year) is deliberately absent, so the generic repeat still wins it.
SEED = {
    "and": [
        "back and forth", "in and out", "out and about", "give and take",
        "pros and cons", "odds and ends", "salt and pepper", "cause and effect",
        "trial and error", "supply and demand", "wear and tear",
        "law and order", "peace and quiet", "safe and sound", "sick and tired",
        "nuts and bolts", "bells and whistles", "hit and run", "cat and mouse",
        "far and wide", "first and foremost", "loud and clear", "here and there",
        "left and right",
        "now and then", "ups and downs", "part and parcel", "touch and go",
        "hide and seek", "bait and switch", "tooth and nail", "hem and haw",
        "wine and dine", "cloak and dagger", "flesh and blood", "pride and joy",
        "meat and potatoes", "hustle and bustle", "null and void",
        "pins and needles", "to and fro", "by and large", "rise and shine",
        "dead and gone", "black and white", "bread and butter", "rock and roll",
        "rhythm and blues", "prim and proper", "spick and span",
        "short and sweet", "slow and steady", "tried and true", "thick and thin",
        "ebb and flow", "wax and wane", "kith and kin", "rant and rave",
        "born and raised", "aches and pains", "arts and crafts",
        "bits and pieces", "body and soul", "bricks and mortar",
        "chalk and cheese", "cops and robbers", "fair and square",
        "fame and fortune", "fast and furious", "fire and brimstone",
        "fish and chips", "fits and starts", "flora and fauna",
        "forgive and forget", "fun and games", "hammer and tongs",
        "heart and soul", "heaven and earth", "high and dry", "hopes and dreams",
        "house and home", "huff and puff", "kiss and tell", "knife and fork",
        "leaps and bounds", "lock and key", "lost and found",
        "movers and shakers", "needle and thread", "nickel and dime",
        "pen and paper", "ready and willing", "search and rescue",
        "shock and awe", "show and tell", "signed and sealed", "skin and bones",
        "smoke and mirrors", "sound and fury", "sticks and stones",
        "sugar and spice", "sweet and sour", "terms and conditions",
        "this and that", "thunder and lightning", "time and again",
        "toil and trouble", "track and field", "trials and tribulations",
        "twist and shout", "war and peace", "warts and all", "ways and means",
        "wheel and deal", "yin and yang", "bow and arrow", "cup and saucer",
        "day and night", "hot and cold", "life and limb", "live and learn",
        "song and dance", "open and shut", "cease and desist",
        "divide and conquer", "each and every", "few and far between",
        "grin and bear it", "pick and choose", "tar and feather",
        "wash and wear", "chapter and verse", "pomp and circumstance",
        "rack and ruin", "hue and cry", "assault and battery", "down and out",
        "name and shame",
    ],
    "or": [
        "this or that", "now or never", "all or nothing", "sink or swim",
        "make or break", "hit or miss", "do or die", "feast or famine",
        "fight or flight", "sooner or later", "win or lose", "life or death",
        "rain or shine", "yes or no", "friend or foe", "boom or bust",
        "more or less", "take it or leave it", "trick or treat",
        "right or wrong", "true or false", "dead or alive", "once or twice",
        "truth or dare", "cash or credit", "paper or plastic", "love or hate",
        "kill or cure", "either or", "put up or shut up", "use it or lose it",
        "hell or high water", "heads or tails", "live or die", "give or take",
        "stand or fall", "fish or cut bait",
    ],
    "to": [
        "next to nothing", "down to earth", "soup to nuts", "cradle to grave",
        "start to finish", "top to bottom", "dawn to dusk", "farm to table",
        "floor to ceiling", "zero to hero", "rags to riches", "womb to tomb",
        "stem to stern", "nose to tail", "head to toe",
    ],
    "of": [
        "matter of fact", "point of view", "state of mind", "line of sight",
        "train of thought", "peace of mind", "piece of cake",
        "change of heart", "leap of faith", "frame of mind", "word of mouth",
        "act of god", "sense of humor", "rule of thumb", "labor of love",
        "work of art", "chain of command", "stroke of luck",
        "figure of speech", "pack of lies", "court of law", "king of kings",
        "day of days", "lord of lords", "song of songs", "heart of hearts",
        "holy of holies", "tip of the iceberg", "bundle of joy",
        "ray of sunshine", "pillar of strength", "man of the hour",
        "fountain of youth", "benefit of the doubt", "force of habit",
        "freedom of speech", "crack of dawn", "den of thieves",
        "house of cards", "kiss of death", "neck of the woods", "nick of time",
        "reign of terror", "seal of approval", "shadow of a doubt",
        "sigh of relief", "slip of the tongue", "speed of light",
        "twist of fate", "walk of life", "web of lies", "grain of salt",
        "breath of fresh air", "pinch of salt", "master of none",
    ],
    "the": [
        "break the ice", "bite the bullet", "spill the beans", "hit the road",
        "raise the bar", "call the shots", "face the music", "read the room",
        "cross the line", "seize the day", "take the cake", "rock the boat",
        "cut the cord", "beat the odds", "over the top", "under the weather",
        "for the win", "off the record", "hold the phone", "kick the bucket",
        "pass the buck", "miss the boat", "steal the show", "set the stage",
        "tip the scales", "turn the tide", "weather the storm", "jump the gun",
        "learn the ropes", "pop the question", "roll the dice", "save the day",
        "shoot the breeze", "sound the alarm", "play the field",
        "paint the town", "mind the gap", "grasp the nettle", "test the waters",
        "toe the line", "buck the trend",
    ],
    "for": [
        "eye for an eye", "tit for tat", "taken for granted", "know for sure",
        "up for grabs", "room for improvement", "recipe for disaster",
        "fit for a king", "reach for the stars", "wait for it",
        "thanks for nothing", "gone for good", "bad for business",
        "fend for yourself", "run for it",
    ],
    "by": [],
    "in": [
        "ace in the hole", "bird in the hand", "hole in one", "pain in the neck",
        "partner in crime", "pie in the sky", "thorn in the side",
        "wolf in sheep's clothing", "needle in a haystack",
        "elephant in the room", "fly in the ointment", "stick in the mud",
        "diamond in the rough", "once in a while", "pig in a poke",
        "knight in shining armor", "method in the madness",
    ],
    "on": [
        "icing on the cake", "cards on the table", "chip on the shoulder",
        "eyes on the prize", "boots on the ground", "all hands on deck",
        "money on the table", "feet on the ground", "cash on delivery",
    ],
    "over": [
        "mind over matter", "head over heels", "cry over spilled milk",
        "bridge over troubled water",
    ],
}


def load_reverse():
    rev = defaultdict(list)
    for name, rank in (("two-syllable.json", -1), ("typey-type-full.json", 0),
                       ("typey-composed.json", 0), ("main.json", 1)):
        path = os.path.join(DICTS, name)
        if not os.path.exists(path):
            continue
        for stroke, word in json.load(open(path)).items():
            rev[word].append((rank, stroke))
    return rev


_BAD = re.compile(r"[#@^+0-9]")


def best_outline(rev, word):
    # A two-syllable stroke's `^` is the rule's marker, not another dictionary's.
    cands = [(rank, s) for rank, s in rev.get(word, [])
             if not _BAD.search(s[1:] if rank < 0 else s)]
    if not cands:
        return None
    # Fewest strokes first (single beats spelled-out), then two-syllable over
    # Typey Type over main, then shorter, then alpha. So multi-stroke only fills
    # words that had no single outline.
    return min(cands, key=lambda rs: (rs[1].count("/"), rs[0], len(rs[1]), rs[1]))[1]


def mined_phrases():
    """connective -> [phrase], from the corpus, grammar filtered."""
    if not os.path.exists(MINED):
        print(f"note: {MINED} absent; run mine-repeat-trigrams.py for data")
        return {}
    data = json.load(open(MINED))["conn"]
    out = {}
    for conn, info in data.items():
        if conn not in MINE_CONN:
            continue
        phrases = []
        for w1, w2, freq, share, ncorp in info["idioms"]:
            if w1 in STOP or w2 in STOP:
                continue
            phrases.append(f"{w1} {conn} {w2}")
            if len(phrases) >= MAX_MINED_PER_CONN:
                break
        out[conn] = phrases
    return out


def main():
    rev = load_reverse()
    mined = mined_phrases()
    out, seen, missing, collided = {}, {}, [], []

    # seed first (wins), then mined fills gaps
    for conn in STEMS:
        for source, phrases in (("seed", SEED.get(conn, [])),
                                ("mined", mined.get(conn, []))):
            for phrase in phrases:
                parts = phrase.split()
                first = parts[0]
                if len(parts) == 3 and parts[0] == parts[2]:
                    continue  # exact W-conn-W -> the generic repeat handles it
                key = (conn, first)
                if key in seen:
                    if source == "seed":
                        collided.append((phrase, seen[key]))
                    continue
                outline = MANUAL_OUTLINE.get(first) or best_outline(rev, first)
                if outline is None:
                    missing.append((phrase, first))
                    continue
                seen[key] = phrase
                for stem in STEMS[conn]:
                    out[f"{outline}/{stem}"] = phrase

    path = os.path.join(DICTS, "repeat-idioms.json")
    with open(path, "w") as f:
        json.dump(dict(sorted(out.items())), f, indent=0, ensure_ascii=False)
        f.write("\n")

    phrases = len(set(out.values()))
    print(f"wrote {len(out)} entries ({phrases} phrases) -> {path}")
    if collided:
        print(f"\n{len(collided)} seed first-word collisions dropped:")
        for dropped, kept in collided[:20]:
            print(f"  {dropped!r}  (kept {kept!r})")
    if missing:
        print(f"\n{len(missing)} phrases with no clean outline (skipped):")
        for phrase, first in missing[:20]:
            print(f"  {phrase!r}  (word {first!r})")


if __name__ == "__main__":
    main()
