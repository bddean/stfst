# The right bank, made concrete

`phrasing-resdesign` gives the right bank's new shape. This file fills it in
with data and lists what only the author can decide. None of it is built.

The numbers come from `~/.config/plover/phrasing/rhs_mine.py`. It counts what
follows each verb, which subject heads it, and the constructions below, over the
three Leipzig corpora (web, news, wiki) and personal text. A share is the
pipeline's blend: `personal:2 web:1 news:1 wiki:1`, each corpus per unit of its
own verb mass. Personal text has a few hundred uses of a verb at most, so its
weight for a verb is scaled by n / (n + 100). "going to" and "used to" are not
counted as go and use, because they are modalities. Raw counts are web+news
unless a line says otherwise.

```
FRPBLG  the bank: 59 verbs and the hole (P, FP, RP, FRP)
T S     the verb's complement words; TS is a third
D       past
Z       the linker
*       not; on the hole's F, never
```

## The bank

A verb outside the bank costs a stroke: the hole, then the word. It also gets
the hole's complement (-T the, -S a(n), -TS to) instead of its own. So what a
verb loses outside the bank is

    freq x (1 + what its class writes - what the hole's would write)

The redesign gives up bring, stop and tell. By this measure bring is #64 and
stop #60, so both go. tell is #51 and stays. begin, cause and learn go instead:
their complements are "to", "the" and "a", which the hole writes too, so they
lose only the stroke. begin is "to" 31% of the time, and the hole's -TS is "to".
let and receive come in.

The last seats are close. Ranks 50 to 70 lose between 5.2 and 4.0 per thousand,
so a reordering among let, receive, talk (#59), stop (#60) and cause (#61) is
inside the noise.

| # | verb | was | draft chord | class | -T | -S | -TS | T% | S% | TS% | close second |
|---:|---|---:|---|---|---|---|---|---:|---:|---:|---|
| 0 | need | 0 | PB | to (inf) | to | a | to be | 39.3 | 11.0 | 8.3 |  |
| 1 | make | 1 | PL | article | the | a(n) | sure | 12.9 | 11.8 | 4.3 |  |
| 2 | get | 2 | G | article | the | a(n) | into | 8.7 | 11.5 | 1.8 |  |
| 3 | use | 4 | B | article | the | a(n) | in | 15.8 | 7.5 | 3.2 |  |
| 4 | think | 6 | F | that | that | of | that the | 8.4 | 8.2 | 0.7 | particle 17.1 vs 17.3 |
| 5 | want | 3 | L | to (inf) | to | a | to be | 58.2 | 6.8 | 4.6 |  |
| 6 | work | 7 | R | particle | in | on | in the | 11.5 | 10.8 | 4.7 |  |
| 7 | go | 5 | BG | to (motion) | to | on | to the | 14.3 | 10.0 | 3.8 |  |
| 8 | say | 9 | FR | that | that | to | that the | 7.7 | 1.3 | 1.3 | to (inf) 9.1 vs 10.4 |
| 9 | see | 8 | LG | article | the | a(n) | what | 13.4 | 9.0 | 3.6 |  |
| 10 | add | 10 | RB | article | the | a(n) | new | 6.8 | 19.5 | 1.8 |  |
| 11 | take | 11 | BLG | article | the | a(n) | place | 10.5 | 14.0 | 6.1 |  |
| 12 | know | 12 | FPB | that | that | what | that the | 9.4 | 8.6 | 0.9 | particle 18.7 vs 18.9 |
| 13 | find | 13 | FB | article | the | a(n) | out | 10.5 | 12.7 | 5.8 |  |
| 14 | run | 14 | RPB | article | the | a(n) | out | 8.8 | 3.2 | 8.7 |  |
| 15 | come | 15 | PBLG | to (motion) | to | from | to the | 25.5 | 9.1 | 3.6 | particle 36.5 vs 38.2 |
| 16 | look | 18 | BL | particle | at | for | at the | 20.1 | 16.6 | 4.7 |  |
| 17 | mean | 16 | PBL | article | the | a(n) | for | 11.1 | 4.1 | 2.4 | to (inf) 18.6 vs 17.6 |
| 18 | help | 17 | FPL | article | the | a(n) | with | 5.3 | 0.9 | 3.0 | to (inf) 10.0 vs 9.3 |
| 19 | keep | 21 | PBG | article | the | a(n) | up | 16.5 | 6.3 | 3.0 |  |
| 20 | give | 23 | FG | article | the | a(n) | up | 10.4 | 9.5 | 5.4 |  |
| 21 | seem | 24 | PLG | to (inf) | to | like | to be | 37.2 | 22.5 | 9.3 |  |
| 22 | like | 19 | FL | to (inf) | to | the | to be | 28.6 | 13.7 | 1.9 |  |
| 23 | write | 22 | RG | article | the | a(n) | about | 7.7 | 14.4 | 3.9 |  |
| 24 | read | 25 | RL | article | the | a(n) | in | 25.1 | 3.0 | 2.2 |  |
| 25 | call | 26 | RBG | article | the | a(n) | for | 11.1 | 6.7 | 6.5 |  |
| 26 | change | 27 | FPBLG | article | the | a(n) | in | 17.5 | 1.2 | 2.8 | to (inf) 20.2 vs 21.6 |
| 27 | start | 20 | PG | article | the | a(n) | with | 6.8 | 7.5 | 7.5 | particle 20.7 vs 21.9 |
| 28 | understand | 29 | FRPB | article | the | a(n) | how | 21.4 | 0.9 | 6.5 |  |
| 29 | feel | 30 | FLG | particle | like | that | like the | 18.6 | 7.7 | 1.4 | article 28.3 vs 27.6 |
| 30 | become | 31 | FBG | article | the | a(n) | more | 10.2 | 25.3 | 4.6 |  |
| 31 | create | 35 | FRBG | article | the | a(n) | backup | 8.8 | 33.5 | 3.2 |  |
| 32 | show | 33 | FRB | that | that | up | that the | 14.8 | 8.1 | 2.8 | article 23.6 vs 25.7 |
| 33 | live | 40 | FRL | particle | in | with | in the | 33.5 | 7.4 | 5.9 |  |
| 34 | leave | 39 | FBL | article | the | a(n) | off | 16.1 | 7.5 | 13.0 |  |
| 35 | continue | 28 | RPBG | to (inf) | to | the | to be | 40.5 | 3.3 | 4.7 |  |
| 36 | include | 32 | FPBL | article | the | a(n) | in | 12.6 | 13.5 | 3.5 |  |
| 37 | appear | 38 | RPL | to (inf) | to | in | to be | 21.9 | 24.7 | 10.1 | particle 53.9 vs 56.7 |
| 38 | provide | 36 | FRPL | article | the | a(n) | for | 7.7 | 18.7 | 2.5 |  |
| 39 | build | 41 | RBL | article | the | a(n) | release | 8.4 | 15.7 | 3.1 |  |
| 40 | allow | 37 | FPG | article | the | a(n) | for | 14.0 | 4.3 | 8.6 |  |
| 41 | try | 34 | FRG | to (inf) | to | the | to be | 66.7 | 1.8 | 1.7 | particle 68.3 vs 70.2 |
| 42 | set | 48 | RLG | article | the | a(n) | up | 14.0 | 9.2 | 24.6 |  |
| 43 | play | 43 | RPG | article | the | a(n) | in | 8.6 | 14.5 | 7.4 |  |
| 44 | believe | 51 | FBLG | that | that | in | that the | 28.7 | 10.6 | 4.8 | particle 41.1 vs 44.1 |
| 45 | happen | 44 | FPLG | to (inf) | to | in | to be | 9.3 | 5.2 | 4.2 | particle 16.3 vs 18.7 |
| 46 | send | 46 | RBLG | article | the | a(n) | out | 7.5 | 15.5 | 2.7 |  |
| 47 | put | 47 | FPBG | article | the | a(n) | in | 12.3 | 10.1 | 5.5 |  |
| 48 | reach | 49 | FRPG | article | the | a(n) | out | 23.1 | 9.4 | 6.4 |  |
| 49 | return | 42 | RPBL | to (motion) | to | home | to the | 40.6 | 4.1 | 10.1 |  |
| 50 | produce | 50 | FRBL | article | the | a(n) | more | 9.2 | 20.5 | 1.7 |  |
| 51 | tell | 61 | RPLG | article | the | a(n) | people | 13.7 | 3.4 | 1.0 |  |
| 52 | move | 45 | FRLG | to (motion) | to | into | to the | 17.8 | 5.2 | 3.5 | to (inf) 26.7 vs 26.5 |
| 53 | love | 52 | FRBLG | to (inf) | to | the | to be | 11.7 | 13.2 | 0.5 |  |
| 54 | let | 66 | FRPBG | article | the | a(n) | go | 12.8 | 2.4 | 4.5 |  |
| 55 | pass | 54 | FRPBL | article | the | a(n) | through | 13.2 | 6.3 | 9.8 |  |
| 56 | ask | 57 | FRPLG | article | the | a(n) | for | 4.2 | 2.8 | 11.7 |  |
| 57 | apply | 53 | RPBLG | to (motion) | to | for | to the | 26.3 | 12.0 | 2.8 | particle 40.1 vs 41.0 |
| 58 | receive | 63 | FRPBLG | article | the | a(n) | from | 13.0 | 22.5 | 2.7 |  |

"was" is the verb's place in `verb_ranking_pos.json`. A share is of the verb's
uses; -TS counts its second word, since its first is -T's. "close second" is
another class within 3 points. The table is the classing rule's output, and the
next section says where to override it.

The draft chord is `brief_assign.py`'s rule over the six FRPBLG keys, with the
hole on P. It shows the hole's cost and is not an assignment: the bank has no
T S D Z, so say, see, use, add and want have no consonant to spell, and the
mnemonic weights need a pass of their own.

## Complement classes

The redesign says -T is the verb's primary complement word, and the class
follows from it. The rule used here: the class whose three slots write the
most, unless the class the primary word names is within 1.5 points of it. 16
verbs have another class within 3 points, in the table's last column. These are
the redesign's borderline verbs:

| verb | redesign | data | share, redesign vs data |
|---|---|---|---|
| mean | that: that, to | article: the, a(n) | 18.2 vs 17.6 |
| seem | to: to, like | same | 69.0 |
| appear | particle: in, to | to (inf): to, in, to be | 53.9 vs 56.7 |
| feel | particle: like, that, like a | same, TS like the | 28.4 vs 27.6 |
| think | that: that, of/about | that: that, of | of 8.2, about 6.5 |
| use | article: the, to | article: the, a(n) | 17.3 vs 26.4 |
| like | to: to, the | same | 44.2 |
| add | that: that, to | article: the, a(n) | 10.8 vs 28.1 |
| get | article: the, to, TS a | article: the, a(n), TS into | 27.1 vs 21.9 |

- mean: a tie. The redesign's "that" is the verb's own complement, so keep it.
- appear: "to be" (10.1%) writes more than "in the" (7.3%), so -T to and -S in.
- think: of. Personal text has of 11.7, about 6.7.
- use: "used to" is RAD. Without it, "use to" is 1.6%, so -S is a(n).
- add: a(n) is 19.5%, and 26.5% in personal text. "add that" is 5.0 and "add
  to" 4.4. add is an article verb.
- get: the redesign's -TS is better. An article verb's -TS is a free word,
  and for get "to" (7.0) beats any particle (into 1.8). With "to" allowed
  there, add (4.4), help (4.7), start (12.1), write (4.5) and send (4.0)
  take it too.
- work is a three-way split: in 11.5, on 10.8, with 9.4. Personal text puts in
  first, web+news puts with first.

### -S in the that, to and particle classes

| verb | -T | -S | web+news | personal |
|---|---|---|---|---|
| need | to | a 11.0 | a | a (n=271) |
| want | to | a 6.8 | a | a (n=145) |
| go | to | on 10.0 | on | ahead (n=90) |
| think | that | of 8.2 | about | of (n=120) |
| work | in | on 10.8 | with | on (n=200) |
| say | that | to 1.3 | in | otherwise (n=195) |
| know | that | what 8.6 | what | what (n=31) |
| come | to | from 9.1 | out | from (n=29) |
| look | at | for 16.6 | for | like (n=36) |
| seem | to | like 22.5 | like | like (n=59) |
| like | to | the 13.7 | the | the (n=48) |
| continue | to | the 3.3 | the | the (n=61) |
| feel | like | that 7.7 | that | too (n=12) |
| show | that | up 8.1 | up | up (n=21) |
| try | to | the 1.8 | the | (n=28) |
| appear | to | in 24.7 | in | in (n=7) |
| live | in | with 7.4 | with | (n=26) |
| happen | to | in 5.2 | in | when (n=87) |
| believe | that | in 10.6 | in | for (n=16) |
| return | to | home 4.1 | home | (n=42) |
| move | to | into 5.2 | forward | into (n=42) |
| love | to | the 13.2 | the | (n=14) |
| apply | to | for 12.0 | for | (n=42) |

say is mostly followed by a quote or a comma: its -S writes almost nothing,
whatever it is. try has no second word worth a key.

The that class's -TS, "that the", writes under 5% for every that verb: think
0.7, know 0.9, say 1.3, show 2.8, believe 4.8. A third word writes more for
think (about 6.5) and know (how 5.7).

No class writes "it". It follows say 17.6%, think 10.3%, make 9.4% and call
8.5%, mostly as the subject of a clause ("I think it's").

## The hole

Verbs outside the bank are 31% of verb frequency, 7.3 times need's. So the
plain hole is the bank's most frequent chord. A verb is written after always or
never 1.0% of the time, after just 0.9% and after also 1.6%, so the F and R
forms matter little. Each pattern scored as the hole's own cost plus
the cost of the bank assignment it leaves:

| pattern | chords | total | hole | bank |
|---|---|---:|---:|---:|
| P | P FP RP FRP | 428.0 | 1.06 | 400.9 |
| G | G FG RG FRG | 429.7 | 1.08 | 402.1 |
| L | L FL RL FRL | 429.8 | 1.08 | 402.2 |
| B | B FB RB FRB | 434.1 | 1.06 | 407.0 |
| BG | BG FBG RBG FRBG | 444.5 | 2.06 | 391.8 |

Every one-key pattern beats every two-key one.

After a verb ranked 60 or lower, the next word is the 10.0%, to 6.9%, a 6.1%
and that 2.7%, as the redesign has it.

## RETRO

`TODO.bs` has: "I don't understand", then -D, writes "I don't understood". It
should write "I didn't understand". Today a lone -D is the folds' `{:infl:ed}`,
which inflects the last word. RETRO's plain -D past-shifts the clause instead,
and the past goes to the finite word: do becomes did, and the verb stays a base
form. That needs the host to hand -D the clause the last stroke wrote, not its
text.

## Decisions

### The linker

| | uses | after a modal | then a bank verb | then be |
|---|---:|---:|---:|---:|
| have to, web | 3,246 | 11.7% | 41.5% | 15.6% |
| have to, news | 3,185 | 13.7% | 38.7% | 19.8% |
| need to, web | 2,637 | 9.7% | 47.6% | 24.2% |
| need to, news | 2,386 | 8.6% | 41.2% | 29.7% |
| want to, web | 3,634 | 6.9% | 56.9% | 10.3% |
| want to, news | 2,791 | 4.2% | 54.2% | 12.6% |
| need to, personal | 79 | 3.8% | 30.4% | 49.4% |
| want to, personal | 47 | 4.3% | 42.6% | 12.8% |

Only Z composes with a modal, and "have to" follows one 12 to 14% of the time.
RO saves a stroke when its linker heads a clause with no modal and the next verb
is in the bank; "need to be" and "want to be" are -TS on the bank verb either
way. Per thousand verb uses, "want to" plus a bank verb with no modal is 9.1 in
web and 7.2 in news, and "need to" is 5.4 and 4.5. Personal text has need 3.7
and want 3.1.

Recommendation: "have to" on Z. RO is "want to", which wins 5.6 to 4.3 per
thousand when personal text counts double. "need to" is the choice if personal
text should decide. RO then takes almost every "want to" with no modal, so the
bank's want keeps "want a" (6.8%) and "want the" (4.3%) and becomes an article
verb. With "need to" on RO, need does the same.

### The hole's pattern

P, G and L are within 0.4% of each other, which the cost model cannot tell
apart. Recommendation: P, which leaves G to get in the draft. Under G, "just"
would be on RG, where the old hole was.

### -SD

-SD writes about 1.6% of bank-verb strokes. "made a" is 10% of those, "added
a" 12% and "got a" 8%. Putting a(n) on -T for the article class does not help:
weighted by frequency, the and a(n) follow a past form about equally often
(39.5 to 36.8), so the diagonal moves to "made the". a(n) beats the for 14 of
the article verbs, but a per-verb order is one more fact per verb.
Recommendation: keep the/a(n) class-wide and accept the diagonal.

### "I do" and "she does"

`#^T-RG` already writes "I do": `^` gives a clause with no auxiliary its `do`,
and the hole asks for nothing in a base form. With the hole on P, that is
`#^T-P`. Main-verb and pro-form "do" ("do it", "I do.") rank 16th in web
(3,318 uses), 14th in news and 6th in personal text (194; need is 271).
Recommendation: give lexical "do" a bank chord. `#T` with it is "I do", and
`#PH` with it "she does". It takes receive's seat. It needs adding to
`verbs_candidate.py` and a count of its complements, because the miner reads
only the candidate verbs.

### What @ and 🦐 do

`#@` has no entry, and no other dictionary may take it, because `#` is
phrasing's. Between a finite word and the verb, also is the most frequent
adverb: 35.0% after a modal ("can also"), 18.8% after another auxiliary, 24.2%
after a subject. Personal text after a modal: also 25, already 24, still 20,
only 12, never 11, just 6. Recommendation: @ writes "also" after the finite
word, or before the verb if there is none: `#@TA` with go's chord is "I can
also go". That is also the answer to "also, just or still after a modal".

always, never, just and also are 55.5% of these adverbs, and the hole reaches
them only for verbs outside the bank. Recommendation: 🦐 keeps the gap.

### Subjunctive "were"

| | was | were |
|---|---:|---:|
| if I/he/she/it, web | 178 | 106 |
| if I/he/she/it, news | 174 | 81 |
| wish I/he/she/it, web+news | 26 | 10 |
| if I/he/she/it, personal | 1 | 2 |

"was" leads after "if", so it stays the default. `^` on a 1sg or 3sg past copula
writes the same thing as no `^` today: `#^TK-D` and `#TK-D` are both "if I
was". Recommendation: there, `^` writes "were", so `#^TK-D` is "if I were".

### Subject and verb cells

Over web, news and personal text, the cells below are seen under 5% as often as
their row and column totals predict, with 15 or more predicted:

| cell | seen | predicted |
|---|---:|---:|
| it think | 16 | 586 |
| it love | 3 | 212 |
| I appear | 5 | 129 |
| we appear | 3 | 61 |
| we cause | 1 | 40 |

this, what and there head 0.4%, 0.3% and 0.2% of bank-verb clauses, and most
of their cells are near zero. Only these verbs take them 20 or more times:

- this: go, help, mean, happen, make, give, allow, take, come, work, seem,
  include, cause, become
- what: happen, go, come, become, work, make, cause
- there: go, seem, need, appear, come

Recommendation: special-case none of them yet. A rare cell is free only for a
phrase that wants it, and none is proposed here. If one is, the there row
outside its five verbs has the most free cells.

### Also for approval

- The bank's membership: bring, stop, begin, cause and learn out; let and
  receive in.
- The borderline classes above: appear, use and add change, get keeps its "to".
- The that class's -TS as the verb's third word: think about, know how.
