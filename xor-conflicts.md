# Two-syllable strokes without `^`: what they collide with

**Words.** 3,804 words of two syllables at zipf ≥ 3, less regular -s, -ed and -ing forms (folds write those), each written by the §8 rule: the stressed vowel's chord XOR the other vowel's letter, consonants placed by stress. Strokes are rebuilt from CMU phonemes with a plain phoneme-to-key map.

**Rivals.** One-syllable words from `typey-type-full.json`, only at their rule stroke: a stroke is kept when its consonant keys are the word's own phonemes (plus Plover's habits `*S` st, `*LG` lk, `-FBG` sk, `-GT` ght). That drops 900 Typey Type strokes, briefs and misstrokes such as `URB` you're and `ARPBLG` arch. A rival may also be a fold: a one-syllable rule stroke plus `-S`, `-D` or `-G`, written as the inflected word with no entry (*seeds* is `SAOED` + `-S`).

**Not counted.** A two-syllable word whose stroke is its own stem's, or a fold of it (*nearer* on `TPHAER`, which is *near*), is not a conflict: the suffix adds no key, so the word is written stem then suffix. The test is spelling only, so it also catches coincidences (corner, corn).

**Losers.** On a shared stroke the most frequent word wins. Two-syllable words losing to each other happens under `^` as well.

## Schemes

A word whose stroke has a bank no one-syllable word has ("free") is never marked. "Short" is three consonants or fewer.

| placement | scheme | not counted (stem) | two-syllable losers | of zipf > 4 | one-syllable losers |
|---|---|---|---|---|---|
| by stress (current) | no marker | 43 | 422 | 75 | 127 |
| by stress (current) | ^ on every word (Extended now) | 0 | 227 | 37 | 0 |
| by stress (current) | ^ on short words only | 26 | 220 | 33 | 28 |
| by stress (current) | * on every word | 5 | 308 | 48 | 58 |
| by stress (current) | * on every word, # too when short | 4 | 232 | 36 | 10 |
| by stress (current) | # on short words where the bar fits | 27 | 251 | 37 | 40 |
| by stress, -er R on the left | no marker | 120 | 448 | 73 | 167 |
| by stress, -er R on the left | ^ on every word (Extended now) | 0 | 256 | 34 | 0 |
| by stress, -er R on the left | ^ on short words only | 70 | 237 | 29 | 40 |
| by stress, -er R on the left | * on every word | 18 | 336 | 46 | 65 |
| by stress, -er R on the left | * on every word, # too when short | 11 | 251 | 33 | 10 |
| by stress, -er R on the left | # on short words where the bar fits | 72 | 267 | 33 | 55 |

## Conflicts: by stress (current), no marker

One row per two-syllable word that shares its stroke. **rival** is the most frequent other claimant.


### -er (111)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| appear | 4.8 | `PER` | per | 5.5 | word | appear |
| matter | 5.4 | `PHART` | mart | 3.7 | word | mart |
| either | 5.3 | `A*ERT` | earth | 5.1 | word | earth |
| major | 5.3 | `PHAEURPBLG` | marriage | 4.9 | two-syllable | marriage |
| temper | 3.8 | `TERPL` | term | 5.2 | word | temper |
| center | 5.2 | `SERPBT` | centre | 5.0 | two-syllable | centre |
| packer | 3.3 | `PARBG` | park | 5.2 | word | packer |
| pastor | 4.1 | `PARTS` | parts | 5.1 | word | pastor |
| changer | 3.5 | `KHARPBLG` | charge | 5.0 | word | changer |
| author | 5.0 | `A*URT` | earthy | 3.0 | two-syllable | earthy |
| master | 4.9 | `PHARTS` | marts | 2.3 | fold of mart | marts |
| collar | 4.0 | `KORL` | color | 4.9 | two-syllable | collar |
| color | 4.9 | `KORL` | colour | 4.5 | two-syllable | colour |
| colour | 4.5 | `KORL` | color | 4.9 | two-syllable | colour |
| labor | 4.8 | `HRAEURB` | labour | 4.7 | two-syllable | labour |
| labour | 4.7 | `HRAEURB` | labor | 4.8 | two-syllable | labour |
| armour | 3.8 | `ARPL` | arm | 4.7 | word | armour |
| hinder | 3.4 | `HEURPBD` | hundred | 4.7 | two-syllable | hinder |
| honor | 4.7 | `ORPB` | ocean | 4.7 | two-syllable | honor |
| honour | 4.4 | `ORPB` | ocean | 4.7 | two-syllable | honour |
| sector | 4.7 | `SERBGT` | circuit | 4.5 | two-syllable | circuit |
| faster | 4.6 | `TPARTS` | farts | 3.2 | fold of fart | farts |
| bidder | 3.5 | `PWEURD` | bird | 4.6 | word | bidder |
| miner | 3.6 | `PHAOEURPB` | minor | 4.6 | two-syllable | miner |
| minor | 4.6 | `PHAOEURPB` | miner | 3.6 | two-syllable | miner |
| favor | 4.6 | `TPAEUFR` | favour | 4.3 | two-syllable | favour |
| favour | 4.3 | `TPAEUFR` | favor | 4.6 | two-syllable | favour |
| prefer | 4.6 | `TPR-R` | refer | 4.5 | two-syllable | refer |
| refer | 4.5 | `TPR-R` | prefer | 4.6 | two-syllable | refer |
| premier | 4.6 | `PHRAOR` | premiere | 4.2 | two-syllable | premiere |
| ticker | 3.0 | `TEURBG` | turkey | 4.6 | two-syllable | ticker |
| manner | 4.5 | `PHARPB` | manor | 3.8 | two-syllable | manor |
| manor | 3.8 | `PHARPB` | manner | 4.5 | two-syllable | manor |
| motor | 4.5 | `PHOERT` | metro | 4.2 | two-syllable | metro |
| bunker | 3.7 | `PW*URPBG` | burning | 4.5 | fold of burne | bunker |
| fodder | 3.4 | `TPORD` | ford | 4.5 | word | fodder |
| rubber | 4.2 | `RURB` | rush | 4.5 | word | rubber |
| chatter | 3.4 | `KHART` | chart | 4.5 | word | chatter |
| hammer | 4.2 | `HARPL` | harm | 4.5 | word | hammer |
| hamper | 3.3 | `HARPL` | harm | 4.5 | word | hamper |
| mirror | 4.5 | `PHEUR` | myrrh | 2.6 | word | myrrh |
| server | 4.5 | `SEFR` | sever | 3.1 | two-syllable | sever |
| sever | 3.1 | `SEFR` | server | 4.5 | two-syllable | sever |
| voter | 4.1 | `SROERT` | resort | 4.4 | two-syllable | voter |
| reader | 4.3 | `RAERD` | reared | 3.1 | fold of rear | reared |
| foster | 4.3 | `TPORTS` | forts | 3.2 | fold of fort | forts |
| powder | 4.2 | `POURD` | poured | 3.8 | word | poured |
| altar | 3.8 | `AURLT` | ultra | 4.2 | two-syllable | altar |
| alter | 4.0 | `AURLT` | ultra | 4.2 | two-syllable | alter |
| maker | 4.2 | `PHAEURBG` | micro | 4.1 | two-syllable | micro |
| humor | 4.2 | `KWHRAOURPL` | humour | 3.8 | two-syllable | humour |
| humour | 3.8 | `KWHRAOURPL` | humor | 4.2 | two-syllable | humour |
| poster | 4.2 | `PORTS` | ports | 4.1 | fold of port | ports |
| wander | 3.7 | `WARPBD` | warned | 4.2 | word | wander |
| arbor | 3.5 | `ARB` | ash | 4.2 | word | arbor |
| azure | 3.4 | `ARB` | ash | 4.2 | word | azure |
| harbor | 4.2 | `HARB` | harbour | 4.0 | two-syllable | harbour |
| harbour | 4.0 | `HARB` | harbor | 4.2 | two-syllable | harbour |
| banner | 4.1 | `PWARPB` | barn | 4.0 | word | barn |
| neighbor | 4.1 | `TPHA*ERB` | neighbour | 3.7 | two-syllable | neighbour |
| neighbour | 3.7 | `TPHA*ERB` | neighbor | 4.1 | two-syllable | neighbour |
| safer | 4.1 | `SAEUFR` | saver | 3.3 | two-syllable | saver |
| saver | 3.3 | `SAEUFR` | safer | 4.1 | two-syllable | saver |
| bugger | 3.0 | `PWURG` | burger | 4.1 | two-syllable | bugger |
| burger | 4.1 | `PWURG` | bugger | 3.0 | two-syllable | bugger |
| fiber | 4.1 | `TPAOEURB` | fibre | 3.7 | two-syllable | fibre |
| ladder | 4.0 | `HRARD` | lard | 3.0 | word | lard |
| cooler | 4.0 | `KAORL` | carol | 4.0 | two-syllable | cooler |
| greener | 3.2 | `TKPWRAOERPB` | fusion | 4.0 | two-syllable | greener |
| cellar | 3.6 | `SERL` | seller | 4.0 | two-syllable | cellar |
| seller | 4.0 | `SERL` | cellar | 3.6 | two-syllable | cellar |
| unsure | 3.8 | `STPHAOR` | inspire | 4.0 | two-syllable | unsure |
| wicker | 3.0 | `WEURBG` | wishing | 3.9 | fold of wish | wicker |
| censor | 3.4 | `SERPBS` | sensor | 3.9 | two-syllable | censor |
| sensor | 3.9 | `SERPBS` | censor | 3.4 | two-syllable | censor |
| quicker | 3.9 | `KWEURBG` | quirk | 3.1 | word | quirk |
| header | 3.9 | `HAERD` | heared | 2.0 | fold of hear | heared |
| heather | 3.9 | `HA*ERT` | hearth | 3.1 | word | hearth |
| muster | 3.4 | `PHURTS` | mistress | 3.9 | two-syllable | muster |
| cracker | 3.5 | `KRARBG` | crashing | 3.8 | fold of crash | cracker |
| barber | 3.8 | `PWARB` | bash | 3.8 | word | bash |
| lunar | 3.8 | `HRAOURPB` | lesion | 3.1 | two-syllable | lesion |
| simmer | 3.3 | `SEURPL` | serum | 3.7 | two-syllable | simmer |
| rotor | 3.4 | `ROERT` | retro | 3.7 | two-syllable | rotor |
| rumor | 3.7 | `RAOURPL` | rumour | 3.4 | two-syllable | rumour |
| rumour | 3.4 | `RAOURPL` | rumor | 3.7 | two-syllable | rumour |
| cutter | 3.7 | `KURT` | curt | 3.2 | word | curt |
| bummer | 3.2 | `PWURPL` | bumper | 3.7 | two-syllable | bummer |
| bumper | 3.7 | `PWURPL` | bummer | 3.2 | two-syllable | bummer |
| boner | 3.2 | `PWOERPB` | borne | 3.7 | word | boner |
| hacker | 3.7 | `HARBG` | hark | 2.8 | word | hark |
| decor | 3.6 | `TKOER` | dowry | 3.1 | two-syllable | dowry |
| tenor | 3.4 | `TERPB` | turbine | 3.6 | two-syllable | tenor |
| tracker | 3.6 | `TRARBG` | trashing | 2.9 | fold of trash | trashing |
| fatter | 3.1 | `TPART` | fart | 3.6 | word | fatter |
| barter | 3.0 | `PWART` | batter | 3.6 | two-syllable | barter |
| batter | 3.6 | `PWART` | barter | 3.0 | two-syllable | barter |
| heater | 3.5 | `HAERT` | hearth | 3.1 | word | hearth |
| badger | 3.5 | `PWARPBLG` | barge | 3.5 | word | badger |
| caster | 3.1 | `KARTS` | carts | 3.5 | fold of cart | caster |
| castor | 3.2 | `KARTS` | carts | 3.5 | fold of cart | castor |
| racer | 3.5 | `RAEURS` | rares | 2.3 | fold of rare | rares |
| helper | 3.4 | `HERPL` | herm | 2.3 | word | herm |
| insure | 3.4 | `STPHAOER` | sneer | 2.7 | word | sneer |
| cougar | 3.4 | `KAOURG` | curing | 3.3 | fold of cure | curing |
| parlor | 3.3 | `PARL` | parlour | 3.1 | two-syllable | parlour |
| parlour | 3.1 | `PARL` | parlor | 3.3 | two-syllable | parlour |
| hangar | 3.3 | `HARPBG` | hanger | 3.2 | two-syllable | hanger |
| hanger | 3.2 | `HARPBG` | hangar | 3.3 | two-syllable | hanger |
| specter | 3.1 | `SPERBGT` | spectre | 3.2 | two-syllable | specter |
| passer | 3.2 | `PARS` | parse | 3.0 | word | parse |

### -ly (23)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| filly | 3.1 | `TP-L` | follow | 5.1 | two-syllable | filly |
| rely | 4.3 | `HRAOU` | allow | 5.0 | two-syllable | rely |
| sully | 3.1 | `SEL` | sell | 5.0 | word | sully |
| fully | 4.9 | `TPEL` | fell | 4.8 | word | fell |
| holy | 4.9 | `HOUL` | howl | 3.2 | word | howl |
| apply | 4.8 | `PHROEU` | ploy | 3.2 | word | ploy |
| bully | 3.9 | `PWEL` | bell | 4.6 | word | bully |
| newly | 4.4 | `TPHAOEL` | kneel | 3.2 | word | kneel |
| belly | 4.1 | `PWUL` | bull | 4.3 | word | belly |
| poorly | 4.1 | `PAOEURL` | payroll | 3.8 | two-syllable | payroll |
| gently | 4.1 | `SKWRUPBLT` | gentile | 3.0 | two-syllable | gentile |
| firmly | 4.0 | `TP-RPL` | pharma | 3.4 | two-syllable | pharma |
| hilly | 3.2 | `H-L` | hollow | 4.0 | two-syllable | hilly |
| holly | 3.9 | `HOEUL` | wholly | 3.8 | two-syllable | wholly |
| wholly | 3.8 | `HOEUL` | holly | 3.9 | two-syllable | wholly |
| promptly | 3.8 | `PROEUPLT` | primate | 3.1 | two-syllable | primate |
| folly | 3.3 | `TPOEUL` | foil | 3.8 | word | folly |
| chilly | 3.5 | `KH-L` | chili | 3.7 | two-syllable | chilly |
| nelly | 3.2 | `TPHUL` | null | 3.7 | word | nelly |
| boldly | 3.3 | `PWOEULD` | boiled | 3.7 | word | boldly |
| calmly | 3.6 | `KAEPL` | camel | 3.6 | two-syllable | calmly |
| oddly | 3.6 | `OEULD` | oiled | 3.1 | word | oiled |
| woolly | 3.0 | `WAOEUL` | wile | 2.6 | word | wile |

### -y (115)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| hazy | 3.1 | `HAZ` | has | 6.4 | word | hazy |
| hurry | 4.1 | `HER` | her | 6.3 | word | hurry |
| annie | 3.9 | `AEUPB` | any | 6.1 | two-syllable | annie |
| any | 6.1 | `AEUPB` | annie | 3.9 | two-syllable | annie |
| midday | 3.4 | `PHAEUD` | made | 5.9 | word | midday |
| tacky | 3.2 | `TAEUBG` | take | 5.9 | word | tacky |
| petty | 4.1 | `PUT` | put | 5.7 | word | petty |
| money | 5.6 | `PHOEPB` | amin | 3.2 | two-syllable | amin |
| carry | 4.9 | `KAEUR` | care | 5.6 | word | carry |
| today | 5.5 | `TKAOEU` | die | 5.1 | word | die |
| sassy | 3.2 | `SAEUS` | says | 5.5 | fold of say | sassy |
| envoy | 3.5 | `OEFPB` | often | 5.4 | two-syllable | envoy |
| body | 5.4 | `PWOEUD` | abide | 3.6 | two-syllable | abide |
| essay | 4.2 | `SAU` | saw | 5.3 | word | essay |
| obey | 3.8 | `PWAO*EU` | buy | 5.3 | word | obey |
| ruddy | 3.0 | `RED` | red | 5.3 | word | ruddy |
| county | 5.2 | `KOEPBT` | content | 5.0 | two-syllable | content |
| sorry | 5.2 | `SOEUR` | soir | 2.4 | word | soir |
| relay | 3.9 | `ROEL` | role | 5.2 | word | relay |
| pony | 3.8 | `POUPB` | upon | 5.1 | two-syllable | pony |
| lacy | 3.2 | `HRAS` | release | 5.1 | two-syllable | lacy |
| buddy | 4.4 | `PWED` | bed | 5.1 | word | buddy |
| savvy | 3.6 | `SAEUF` | safe | 5.1 | word | savvy |
| funny | 5.0 | `TPEPB` | fen | 2.8 | word | fen |
| foxy | 3.0 | `TPOEUBGS` | focus | 5.0 | two-syllable | foxy |
| woody | 3.8 | `WAOEUD` | wide | 5.0 | word | woody |
| earthy | 3.0 | `A*URT` | author | 5.0 | two-syllable | earthy |
| laundry | 4.1 | `HRAERPBD` | learned | 4.8 | fold of learn | laundry |
| edgy | 3.4 | `UPBLG` | engine | 4.8 | two-syllable | edgy |
| lucky | 4.8 | `HREBG` | loco | 3.2 | two-syllable | loco |
| wacky | 3.3 | `WAEUBG` | wake | 4.8 | word | wacky |
| goody | 3.0 | `TKPWAOEUD` | guide | 4.8 | word | goody |
| valley | 4.7 | `SRAEL` | surreal | 3.6 | two-syllable | surreal |
| array | 4.1 | `RAEU` | ray | 4.7 | word | array |
| jockey | 3.6 | `SKWROEBG` | joke | 4.7 | word | jockey |
| leafy | 3.3 | `HRAUF` | laugh | 4.7 | word | leafy |
| tiny | 4.6 | `TAOPB` | eighteen | 3.8 | two-syllable | eighteen |
| candy | 4.3 | `KAEUPBD` | kinda | 4.6 | two-syllable | candy |
| grassy | 3.2 | `TKPWRAEUS` | grace | 4.6 | word | grassy |
| turkey | 4.6 | `TEURBG` | ticker | 3.0 | two-syllable | ticker |
| rusty | 3.6 | `RETS` | arrest | 4.5 | two-syllable | rusty |
| decay | 3.8 | `TKAU` | da | 4.5 | word | decay |
| sexy | 4.4 | `SUBGS` | sucks | 4.4 | fold of suck | sucks |
| rugby | 4.4 | `REBG` | rec | 3.6 | word | rec |
| gritty | 3.4 | `TKPWR-T` | regret | 4.4 | two-syllable | gritty |
| fatty | 3.8 | `TPAEUT` | fate | 4.4 | word | fatty |
| bouncy | 3.2 | `PWOEPBS` | bones | 4.3 | word | bouncy |
| booty | 3.6 | `PWAOEUT` | bite | 4.3 | word | booty |
| slimy | 3.1 | `SHRAOPL` | regime | 4.3 | two-syllable | slimy |
| coney | 3.1 | `KOPB` | con | 4.3 | word | coney |
| teeny | 3.0 | `TAOUPB` | tune | 4.3 | word | teeny |
| sweetie | 3.6 | `SWAOUT` | suite | 4.2 | word | sweetie |
| barley | 3.5 | `PWAERL` | barrel | 4.2 | two-syllable | barley |
| hasty | 3.3 | `HAEUTS` | hates | 4.2 | fold of hate | hasty |
| savoy | 3.1 | `SRAOEU` | vi | 4.1 | word | savoy |
| monkey | 4.1 | `PHO*EPBG` | moaning | 3.4 | fold of moan | moaning |
| penny | 4.1 | `PUPB` | pun | 3.5 | word | pun |
| sunny | 4.1 | `SEPB` | sen | 4.0 | word | sen |
| buggy | 3.3 | `PWEG` | beg | 4.0 | word | buggy |
| gravy | 3.5 | `TKPWRAF` | graph | 4.0 | word | gravy |
| creepy | 4.0 | `KRAOUP` | croup | 2.2 | word | croup |
| ferry | 4.0 | `TPUR` | fur | 4.0 | word | ferry |
| needy | 3.6 | `TPHAOUD` | nude | 4.0 | word | needy |
| puppy | 4.0 | `PEP` | pep | 3.5 | word | pep |
| dusty | 3.7 | `TKETS` | debts | 4.0 | fold of debt | dusty |
| naughty | 3.8 | `TPHAET` | neat | 4.0 | word | naughty |
| berry | 4.0 | `PWUR` | burr | 3.4 | word | burr |
| hearsay | 3.0 | `HERS` | hers | 4.0 | fold of her | hearsay |
| curry | 4.0 | `KER` | ker | 2.8 | word | ker |
| spooky | 3.5 | `SPAOEUBG` | spike | 4.0 | word | spooky |
| sixty | 3.9 | `S-BGTS` | cystic | 3.1 | two-syllable | cystic |
| rowdy | 3.2 | `ROED` | rode | 3.9 | word | rowdy |
| starry | 3.1 | `STAEUR` | stare | 3.9 | word | starry |
| portray | 3.6 | `TPRAOEU` | fry | 3.9 | word | portray |
| muddy | 3.6 | `PHED` | med | 3.9 | word | muddy |
| shiny | 3.9 | `SHAOPB` | shannon | 3.8 | two-syllable | shannon |
| messy | 3.9 | `PHUS` | mus | 3.0 | fold of mu | mus |
| hardy | 3.9 | `HAEURD` | hydro | 3.6 | two-syllable | hydro |
| busty | 3.1 | `PWETS` | bets | 3.9 | fold of bet | busty |
| cowboy | 3.9 | `KUB` | cub | 3.5 | word | cub |
| levy | 3.9 | `HRUF` | lava | 3.7 | two-syllable | lava |
| merry | 3.8 | `PHUR` | emir | 3.0 | two-syllable | emir |
| pinky | 3.4 | `P*PBG` | picnic | 3.8 | two-syllable | pinky |
| dreary | 3.1 | `TKRAUR` | drawer | 3.8 | word | dreary |
| eighty | 3.8 | `A*UT` | auth | 3.0 | word | auth |
| bounty | 3.7 | `PWOEPBT` | bonnet | 3.4 | two-syllable | bonnet |
| pointy | 3.1 | `POPBT` | potent | 3.7 | two-syllable | pointy |
| classy | 3.7 | `KHRAEUS` | clays | 2.8 | fold of clay | clays |
| lefty | 3.2 | `HRUFT` | lust | 3.7 | word | lefty |
| poultry | 3.5 | `POERLT` | petrol | 3.7 | two-syllable | poultry |
| juicy | 3.7 | `SKWRAOES` | gees | 2.7 | fold of gee | gees |
| parsley | 3.3 | `PAERLS` | parcel | 3.7 | two-syllable | parsley |
| dowry | 3.1 | `TKOER` | decor | 3.6 | two-syllable | dowry |
| fuzzy | 3.6 | `TPEZ` | fez | 3.0 | word | fez |
| speedy | 3.6 | `SPAOUD` | spewed | 2.7 | fold of spew | spewed |
| cosy | 3.3 | `KOUZ` | cozy | 3.6 | two-syllable | cosy |
| cozy | 3.6 | `KOUZ` | cosy | 3.3 | two-syllable | cosy |
| paddy | 3.6 | `PAEUD` | payed | 3.0 | fold of pay | payed |
| alloy | 3.6 | `AOL` | aloe | 3.1 | two-syllable | aloe |
| shaky | 3.5 | `SHABG` | shack | 3.4 | word | shack |
| sturdy | 3.4 | `STERD` | steward | 3.5 | two-syllable | sturdy |
| parry | 3.4 | `PAEUR` | pare | 2.8 | word | pare |
| hubby | 3.4 | `HEB` | hobo | 3.1 | two-syllable | hobo |
| volley | 3.2 | `SROEL` | vowel | 3.3 | two-syllable | volley |
| husky | 3.3 | `HEBGS` | hex | 3.3 | word | husky |
| rosy | 3.3 | `ROUZ` | rouse | 3.1 | word | rouse |
| smokey | 3.3 | `SPHOBG` | smock | 2.6 | word | smock |
| breezy | 3.1 | `PWRAOUZ` | bruise | 3.2 | word | breezy |
| baggy | 3.2 | `PWAEUG` | baying | 2.3 | fold of bay | baying |
| murky | 3.1 | `PHERBG` | meshing | 2.4 | fold of mesh | meshing |
| pesky | 3.1 | `PUBGS` | pucks | 2.5 | fold of puck | pucks |
| feisty | 3.1 | `TPAOTS` | foots | 2.2 | fold of foot | foots |
| squeaky | 3.1 | `SKWAUBG` | squawk | 2.4 | word | squawk |
| fussy | 3.0 | `TPES` | fes | 2.7 | fold of fe | fes |
| jetty | 3.0 | `SKWRUT` | jut | 2.5 | word | jut |

### rest (551)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| tattoo | 4.1 | `TO` | to | 7.4 | word | tattoo |
| open | 5.5 | `OPB` | on | 6.9 | word | open |
| canine | 3.3 | `KAPB` | can | 6.5 | word | canine |
| cocoon | 3.1 | `KAPB` | can | 6.5 | word | cocoon |
| outta | 4.1 | `OUT` | out | 6.4 | word | outta |
| thorough | 4.0 | `TH-R` | there | 6.3 | word | thorough |
| anew | 3.2 | `TPHOU` | now | 6.2 | word | anew |
| uno | 3.3 | `TPHOU` | now | 6.2 | word | uno |
| amid | 4.0 | `PHAEUD` | made | 5.9 | word | amid |
| captain | 4.9 | `K-PBT` | can't | 5.8 | word | captain |
| cotton | 4.3 | `K-PBT` | can't | 5.8 | word | cotton |
| although | 5.3 | `THRAOE` | three | 5.8 | word | although |
| around | 5.8 | `ROUPBD` | round | 5.1 | word | round |
| baton | 3.6 | `TPWAOPB` | between | 5.8 | two-syllable | baton |
| between | 5.8 | `TPWAOPB` | baton | 3.6 | two-syllable | baton |
| census | 4.2 | `SEUPBS` | since | 5.8 | word | census |
| jose | 4.2 | `SHOE` | show | 5.7 | word | jose |
| petit | 3.3 | `PUT` | put | 5.7 | word | petit |
| police | 5.3 | `PHRAES` | please | 5.7 | word | police |
| amin | 3.2 | `PHOEPB` | money | 5.6 | two-syllable | amin |
| until | 5.6 | `TPHEL` | nel | 3.1 | word | nel |
| awoke | 3.1 | `WAOEBG` | week | 5.6 | word | awoke |
| simplest | 3.6 | `SUPLTS` | system | 5.6 | two-syllable | simplest |
| system | 5.6 | `SUPLTS` | simplest | 3.6 | two-syllable | simplest |
| pinto | 3.1 | `POEUPBT` | point | 5.5 | word | pinto |
| amen | 3.9 | `PHAEPB` | mean | 5.5 | word | amen |
| belief | 4.5 | `PWHRAOF` | believe | 5.5 | two-syllable | belief |
| believe | 5.5 | `PWHRAOF` | belief | 4.5 | two-syllable | belief |
| mono | 3.5 | `PHEPB` | men | 5.5 | word | mono |
| chorus | 3.9 | `KOURS` | course | 5.4 | word | chorus |
| finesse | 3.2 | `TPHUS` | news | 5.4 | fold of new | finesse |
| menace | 3.5 | `PHAEPBS` | means | 5.4 | fold of mean | menace |
| ago | 5.4 | `TKPWAO` | goo | 3.4 | word | goo |
| often | 5.4 | `OEFPB` | oven | 4.0 | two-syllable | oven |
| oven | 4.0 | `OEFPB` | often | 5.4 | two-syllable | oven |
| abide | 3.6 | `PWOEUD` | body | 5.4 | two-syllable | abide |
| martyr | 3.5 | `PHART` | matter | 5.4 | two-syllable | martyr |
| data | 5.3 | `TKEUT` | duet | 3.4 | two-syllable | duet |
| duet | 3.4 | `TKEUT` | data | 5.3 | two-syllable | duet |
| agile | 3.4 | `AEUPBLG` | age | 5.3 | word | agile |
| image | 4.9 | `AEUPBLG` | age | 5.3 | word | image |
| among | 5.3 | `PHAOPBG` | mango | 3.6 | two-syllable | mango |
| mango | 3.6 | `PHAOPBG` | among | 5.3 | two-syllable | mango |
| himself | 5.2 | `SPHUFL` | myself | 5.3 | two-syllable | himself |
| myself | 5.3 | `SPHUFL` | himself | 5.2 | two-syllable | himself |
| fauna | 3.5 | `TPUPB` | fun | 5.3 | word | fauna |
| margin | 4.3 | `PHAEURPBLG` | major | 5.3 | two-syllable | margin |
| marriage | 4.9 | `PHAEURPBLG` | major | 5.3 | two-syllable | marriage |
| gonna | 5.3 | `TKPWAOPB` | goon | 3.1 | word | goon |
| program | 5.3 | `PRAORPLG` | programme | 4.6 | two-syllable | programme |
| programme | 4.6 | `PRAORPLG` | program | 5.3 | two-syllable | programme |
| process | 5.3 | `PROES` | pros | 3.9 | fold of pro | pros |
| prowess | 3.4 | `PROES` | process | 5.3 | two-syllable | prowess |
| across | 5.2 | `KRAOS` | crisis | 4.6 | two-syllable | crisis |
| crisis | 4.6 | `KRAOS` | across | 5.2 | two-syllable | crisis |
| tuna | 3.6 | `TOUPB` | town | 5.2 | word | tuna |
| issue | 5.2 | `ERB` | herb | 3.8 | word | herb |
| piston | 3.5 | `POEUPBTS` | points | 5.2 | fold of point | piston |
| typhoon | 3.6 | `TPAOEUPB` | fine | 5.2 | word | typhoon |
| cuddle | 3.4 | `KEULD` | killed | 5.2 | fold of kill | cuddle |
| centre | 5.0 | `SERPBT` | center | 5.2 | two-syllable | centre |
| content | 5.0 | `KOEPBT` | county | 5.2 | two-syllable | content |
| private | 5.2 | `PROEUFT` | profit | 4.7 | two-syllable | profit |
| profit | 4.7 | `PROEUFT` | private | 5.2 | two-syllable | profit |
| event | 5.2 | `SR-PBT` | resent | 3.4 | two-syllable | resent |
| resent | 3.4 | `SR-PBT` | event | 5.2 | two-syllable | resent |
| middle | 5.2 | `PHULD` | mulled | 2.5 | fold of mull | mulled |
| taxi | 4.2 | `TAEUBGS` | takes | 5.2 | fold of take | taxi |
| bio | 4.1 | `PWOEU` | boy | 5.2 | word | bio |
| alone | 5.2 | `HRAOEPB` | lien | 3.2 | word | lien |
| provide | 5.2 | `SPRAEUD` | sprayed | 3.5 | fold of spray | sprayed |
| follow | 5.1 | `TP-L` | filly | 3.1 | two-syllable | filly |
| amount | 5.1 | `PHAOUPBT` | mountain | 4.7 | two-syllable | mountain |
| mountain | 4.7 | `PHAOUPBT` | amount | 5.1 | two-syllable | mountain |
| derive | 3.5 | `TKRAOEUF` | drive | 5.1 | word | derive |
| upon | 5.1 | `POUPB` | pony | 3.8 | two-syllable | pony |
| harass | 3.3 | `HRAS` | release | 5.1 | two-syllable | harass |
| release | 5.1 | `HRAS` | las | 4.3 | fold of la | las |
| semi | 4.5 | `SUPL` | simple | 5.1 | two-syllable | semi |
| simple | 5.1 | `SUPL` | semi | 4.5 | two-syllable | semi |
| soma | 3.2 | `SAOEPL` | seem | 5.1 | word | soma |
| bullet | 4.2 | `PWEULT` | built | 5.1 | word | bullet |
| practice | 5.1 | `PRAEUBGTS` | practise | 3.5 | two-syllable | practise |
| practise | 3.5 | `PRAEUBGTS` | practice | 5.1 | two-syllable | practise |
| abyss | 3.4 | `PWAEUS` | base | 5.0 | word | abyss |
| album | 5.0 | `AUPBL` | annum | 3.2 | two-syllable | annum |
| annum | 3.2 | `AUPBL` | album | 5.0 | two-syllable | annum |
| austere | 3.0 | `STOER` | store | 5.0 | word | austere |
| create | 5.0 | `KRAUT` | kraut | 2.4 | word | kraut |
| ahead | 5.0 | `HED` | hed | 2.6 | fold of he | hed |
| allow | 5.0 | `HRAOU` | rely | 4.3 | two-syllable | rely |
| beta | 4.2 | `PWAET` | beat | 5.0 | word | beta |
| focus | 5.0 | `TPOEUBGS` | foxy | 3.0 | two-syllable | foxy |
| silo | 3.0 | `SAEUL` | sale | 5.0 | word | silo |
| solo | 4.6 | `SEL` | sell | 5.0 | word | solo |
| justice | 5.0 | `SKWRETS` | jets | 4.1 | fold of jet | jets |
| photo | 5.0 | `TPET` | fete | 2.7 | word | fete |
| sauna | 3.2 | `SUPB` | sun | 5.0 | word | sauna |
| begin | 4.8 | `TKPWUPB` | gun | 5.0 | word | begin |
| terrain | 3.9 | `TRA*EUPB` | train | 5.0 | word | terrain |
| jingle | 3.2 | `SKWRUPBLG` | judge | 5.0 | word | jingle |
| unit | 5.0 | `KWRAOEPBT` | unite | 3.9 | two-syllable | unite |
| unite | 3.9 | `KWRAOEPBT` | unit | 5.0 | two-syllable | unite |
| debris | 3.9 | `TKPWRAO` | degree | 4.9 | two-syllable | debris |
| degree | 4.9 | `TKPWRAO` | debris | 3.9 | two-syllable | debris |
| flora | 3.7 | `TPHRAOR` | floor | 4.9 | word | flora |
| stupid | 4.9 | `STAOEPD` | steeped | 3.1 | fold of steep | steeped |
| defence | 4.5 | `TKP-PBS` | defense | 4.9 | two-syllable | defence |
| defense | 4.9 | `TKP-PBS` | defence | 4.5 | two-syllable | defence |
| finish | 4.9 | `TP-RPB` | fission | 3.1 | two-syllable | fission |
| fission | 3.1 | `TP-RPB` | finish | 4.9 | two-syllable | fission |
| drunken | 3.7 | `TKR*EUPBG` | drink | 4.9 | word | drunken |
| basic | 4.9 | `PWABGS` | backs | 4.1 | word | backs |
| award | 4.9 | `W-RD` | where'd | 3.1 | word | where'd |
| hula | 3.0 | `HOUL` | holy | 4.9 | two-syllable | hula |
| lowest | 4.4 | `HROTS` | lots | 4.9 | word | lowest |
| avoid | 4.9 | `SRAOEUD` | vied | 2.5 | fold of vi | vied |
| mission | 4.9 | `PH-RPB` | moron | 3.8 | two-syllable | moron |
| moron | 3.8 | `PH-RPB` | mission | 4.9 | two-syllable | moron |
| petite | 3.4 | `TPAOT` | foot | 4.9 | word | petite |
| attempt | 4.8 | `TAEPLT` | template | 3.7 | two-syllable | template |
| template | 3.7 | `TAEPLT` | attempt | 4.8 | two-syllable | template |
| placid | 3.0 | `PHRAEUSD` | placed | 4.8 | fold of place | placid |
| engine | 4.8 | `UPBLG` | edgy | 3.4 | two-syllable | edgy |
| mantle | 3.6 | `PHAEPBLT` | mental | 4.8 | two-syllable | mantle |
| mental | 4.8 | `PHAEPBLT` | mantle | 3.6 | two-syllable | mantle |
| arose | 3.7 | `ROEZ` | rose | 4.8 | word | arose |
| mallet | 3.0 | `PHAELT` | metal | 4.8 | two-syllable | mallet |
| metal | 4.8 | `PHAELT` | mallet | 3.0 | two-syllable | mallet |
| zeta | 3.1 | `SAET` | seat | 4.8 | word | zeta |
| arise | 3.9 | `RAOEUZ` | rise | 4.8 | word | arise |
| basis | 4.8 | `PWAS` | bass | 4.3 | word | bass |
| proceed | 4.2 | `SPRAED` | spread | 4.8 | word | proceed |
| ascent | 3.4 | `SAEPBT` | senate | 4.8 | two-syllable | ascent |
| assent | 3.0 | `SAEPBT` | senate | 4.8 | two-syllable | assent |
| senate | 4.8 | `SAEPBT` | ascent | 3.4 | two-syllable | ascent |
| concept | 4.8 | `KOEPBTS` | contest | 4.5 | two-syllable | contest |
| contest | 4.5 | `KOEPBTS` | concept | 4.8 | two-syllable | contest |
| loco | 3.2 | `HREBG` | lucky | 4.8 | two-syllable | loco |
| patient | 4.8 | `PARPBT` | pattern | 4.6 | two-syllable | pattern |
| pattern | 4.6 | `PARPBT` | patient | 4.8 | two-syllable | pattern |
| ample | 3.7 | `AEPL` | apple | 4.8 | two-syllable | ample |
| apple | 4.8 | `AEPL` | ample | 3.7 | two-syllable | ample |
| emo | 3.2 | `AEPL` | apple | 4.8 | two-syllable | emo |
| request | 4.8 | `KWR-TS` | requests | 4.3 | two-syllable | requests |
| requests | 4.3 | `KWR-TS` | request | 4.8 | two-syllable | requests |
| unfit | 3.5 | `TPHET` | net | 4.8 | word | unfit |
| renew | 3.7 | `TPHRAOEU` | fly | 4.8 | word | renew |
| honest | 4.7 | `OEPBTS` | onset | 3.7 | two-syllable | onset |
| onset | 3.7 | `OEPBTS` | honest | 4.7 | two-syllable | onset |
| reduce | 4.7 | `TKRAOEUS` | dries | 3.2 | word | dries |
| solid | 4.7 | `SOEULD` | soiled | 3.0 | word | soiled |
| inflate | 3.0 | `TPHRAT` | flat | 4.7 | word | inflate |
| surreal | 3.6 | `SRAEL` | valley | 4.7 | two-syllable | surreal |
| hundred | 4.7 | `HEURPBD` | hinder | 3.4 | two-syllable | hinder |
| approve | 4.1 | `PRAOF` | proof | 4.7 | word | approve |
| forest | 4.7 | `TPOERTS` | fortress | 3.8 | two-syllable | fortress |
| fortress | 3.8 | `TPOERTS` | forest | 4.7 | two-syllable | fortress |
| ocean | 4.7 | `ORPB` | honor | 4.7 | two-syllable | honor |
| native | 4.7 | `TPHAFT` | nast | 2.8 | word | nast |
| presume | 3.6 | `SPRAOEUPL` | supreme | 4.7 | two-syllable | presume |
| supreme | 4.7 | `SPRAOEUPL` | presume | 3.6 | two-syllable | presume |
| email | 4.7 | `PHA*UL` | maul | 2.9 | word | maul |
| lament | 3.2 | `PHRAEPBT` | planet | 4.7 | two-syllable | lament |
| planet | 4.7 | `PHRAEPBT` | lament | 3.2 | two-syllable | lament |
| appeal | 4.7 | `PEL` | polo | 3.8 | two-syllable | polo |
| polo | 3.8 | `PEL` | appeal | 4.7 | two-syllable | polo |
| drama | 4.7 | `TKRUPL` | drum | 4.1 | word | drum |
| mistake | 4.7 | `STPHABG` | snack | 3.9 | word | snack |
| rewrite | 3.5 | `RAOUT` | route | 4.7 | word | rewrite |
| circuit | 4.5 | `SERBGT` | sector | 4.7 | two-syllable | circuit |
| guinness | 3.5 | `TKPWUPBS` | guns | 4.7 | fold of gun | guinness |
| howell | 3.5 | `HOEL` | hole | 4.7 | word | howell |
| logo | 4.3 | `HREG` | leg | 4.7 | word | logo |
| auburn | 3.7 | `AURPB` | urban | 4.7 | two-syllable | auburn |
| urban | 4.7 | `AURPB` | auburn | 3.7 | two-syllable | auburn |
| zero | 4.6 | `SOER` | sore | 4.0 | word | sore |
| eighteen | 3.8 | `TAOPB` | tiny | 4.6 | two-syllable | eighteen |
| upset | 4.6 | `SPEUT` | spit | 3.9 | word | spit |
| candid | 3.5 | `KAEUPBD` | kinda | 4.6 | two-syllable | candid |
| kinda | 4.6 | `KAEUPBD` | candy | 4.3 | two-syllable | candy |
| motion | 4.6 | `PHOURPB` | mourn | 3.4 | word | mourn |
| remote | 4.4 | `PHROT` | plot | 4.6 | word | remote |
| sinus | 3.3 | `SAOEPBS` | scenes | 4.6 | word | sinus |
| premiere | 4.2 | `PHRAOR` | premier | 4.6 | two-syllable | premiere |
| revere | 3.1 | `SRAOR` | severe | 4.6 | two-syllable | revere |
| severe | 4.6 | `SRAOR` | revere | 3.1 | two-syllable | revere |
| vantage | 3.3 | `SR-PBLGT` | victim | 4.6 | two-syllable | vantage |
| victim | 4.6 | `SR-PBLGT` | vantage | 3.3 | two-syllable | vantage |
| arrest | 4.5 | `RETS` | rusty | 3.6 | two-syllable | rusty |
| errand | 3.0 | `AERPBD` | earned | 4.5 | fold of earn | errand |
| polite | 3.9 | `PHRAEUT` | plate | 4.5 | word | polite |
| relief | 4.5 | `HRAOF` | relieve | 3.8 | two-syllable | relieve |
| relieve | 3.8 | `HRAOF` | relief | 4.5 | two-syllable | relieve |
| demi | 3.4 | `TKUPL` | dumb | 4.5 | word | demi |
| manage | 4.5 | `PH-PBLG` | mimic | 3.5 | two-syllable | mimic |
| mimic | 3.5 | `PH-PBLG` | manage | 4.5 | two-syllable | mimic |
| multi | 4.5 | `PHELT` | melt | 4.0 | word | melt |
| bourbon | 3.6 | `PWURPB` | burn | 4.5 | word | bourbon |
| metro | 4.2 | `PHOERT` | motor | 4.5 | two-syllable | metro |
| pasta | 3.9 | `PUTS` | puts | 4.5 | fold of put | pasta |
| sample | 4.5 | `SAEPL` | seam | 3.4 | word | seam |
| basal | 3.4 | `PWEULS` | bills | 4.5 | fold of bill | basal |
| licence | 4.0 | `HRAOUPBS` | license | 4.5 | two-syllable | licence |
| license | 4.5 | `HRAOUPBS` | licence | 4.0 | two-syllable | licence |
| python | 3.8 | `PA*EUPBT` | paint | 4.5 | word | python |
| staple | 3.7 | `STAEPL` | steam | 4.5 | word | staple |
| campus | 4.5 | `KAUPLS` | capsule | 3.7 | two-syllable | capsule |
| capsule | 3.7 | `KAUPLS` | campus | 4.5 | two-syllable | capsule |
| contain | 4.5 | `TKPHAO*EUPB` | domain | 4.3 | two-syllable | domain |
| domain | 4.3 | `TKPHAO*EUPB` | contain | 4.5 | two-syllable | domain |
| defend | 4.5 | `TKP-PBD` | depend | 4.2 | two-syllable | depend |
| depend | 4.2 | `TKP-PBD` | defend | 4.5 | two-syllable | depend |
| mutton | 3.0 | `PHOUPBT` | mount | 4.5 | word | mutton |
| terrace | 3.9 | `TAERS` | tears | 4.5 | word | terrace |
| hustle | 3.7 | `HEULS` | hills | 4.5 | fold of hill | hustle |
| acid | 4.5 | `AEUSD` | aids | 4.2 | fold of aid | aids |
| angel | 4.5 | `AEPBLG` | angle | 4.4 | two-syllable | angle |
| angle | 4.4 | `AEPBLG` | angel | 4.5 | two-syllable | angle |
| offense | 4.3 | `TPOEPBS` | phones | 4.5 | word | offense |
| spoken | 4.5 | `SPOPBG` | sponge | 3.6 | word | sponge |
| chassis | 3.5 | `KHAEUS` | chase | 4.4 | word | chassis |
| muscle | 4.4 | `PHEULS` | mills | 4.2 | fold of mill | mills |
| resale | 3.3 | `ROELS` | roles | 4.4 | word | resale |
| shadow | 4.4 | `SHAOD` | shooed | 2.0 | fold of shoo | shooed |
| output | 4.4 | `OPT` | opt | 3.9 | word | opt |
| reveal | 4.4 | `SRAL` | val | 3.7 | word | val |
| alight | 3.0 | `HRO*EUT` | alright | 4.4 | two-syllable | alight |
| alright | 4.4 | `HRO*EUT` | alight | 3.0 | two-syllable | alight |
| assist | 4.4 | `SAEUTS` | sates | 2.1 | fold of sate | sates |
| racist | 4.4 | `RATS` | rats | 4.0 | fold of rat | rats |
| soulful | 3.1 | `SOFL` | solve | 4.4 | word | soulful |
| stomach | 4.4 | `STAOPBLG` | stoppage | 3.2 | two-syllable | stoppage |
| stoppage | 3.2 | `STAOPBLG` | stomach | 4.4 | two-syllable | stoppage |
| virus | 4.4 | `SRAOERS` | veers | 2.5 | fold of veer | veers |
| mama | 4.2 | `PHUPL` | mum | 4.4 | word | mama |
| mamma | 3.3 | `PHUPL` | mum | 4.4 | word | mamma |
| elite | 4.4 | `HRAOT` | loot | 3.5 | word | loot |
| lotta | 3.2 | `HRAOT` | elite | 4.4 | two-syllable | lotta |
| insane | 4.4 | `STPHAPB` | monsoon | 3.3 | two-syllable | monsoon |
| monsoon | 3.3 | `STPHAPB` | insane | 4.4 | two-syllable | monsoon |
| sudden | 4.4 | `SEUPBD` | sinned | 3.0 | fold of sin | sinned |
| fallen | 4.4 | `TPAEUPBL` | famine | 3.6 | two-syllable | famine |
| famine | 3.6 | `TPAEUPBL` | fallen | 4.4 | two-syllable | famine |
| inflict | 3.3 | `TPHR-BGT` | reflect | 4.4 | two-syllable | inflict |
| reflect | 4.4 | `TPHR-BGT` | inflict | 3.3 | two-syllable | inflict |
| resource | 4.4 | `RAERS` | rears | 2.6 | fold of rear | rears |
| arrive | 4.4 | `RAOEUF` | rife | 3.1 | word | rife |
| rapid | 4.4 | `RAEUPD` | raped | 4.1 | fold of rape | raped |
| regret | 4.4 | `TKPWR-T` | gritty | 3.4 | two-syllable | gritty |
| overt | 3.3 | `SROERT` | resort | 4.4 | two-syllable | overt |
| resort | 4.4 | `SROERT` | voter | 4.1 | two-syllable | voter |
| soda | 4.0 | `SAOED` | seed | 4.4 | word | soda |
| asleep | 4.4 | `SHROEP` | slope | 3.9 | word | slope |
| gorgeous | 4.3 | `TKPWOERPBLGS` | gorges | 2.8 | fold of gorge | gorges |
| ribbon | 3.8 | `ROEUPB` | robin | 4.3 | two-syllable | ribbon |
| robin | 4.3 | `ROEUPB` | ribbon | 3.8 | two-syllable | ribbon |
| forgave | 3.0 | `TKPWRAEUF` | grave | 4.3 | word | forgave |
| mandate | 4.0 | `PH-PBTD` | midnight | 4.3 | two-syllable | mandate |
| midnight | 4.3 | `PH-PBTD` | mandate | 4.0 | two-syllable | mandate |
| reside | 3.7 | `SRAOUD` | viewed | 4.3 | word | reside |
| retake | 3.3 | `ROEBGT` | rocket | 4.3 | two-syllable | retake |
| rocket | 4.3 | `ROEBGT` | retake | 3.3 | two-syllable | retake |
| trauma | 4.1 | `TRUPL` | triple | 4.3 | two-syllable | trauma |
| triple | 4.3 | `TRUPL` | trauma | 4.1 | two-syllable | trauma |
| regime | 4.3 | `SHRAOPL` | slimy | 3.1 | two-syllable | slimy |
| shalom | 3.0 | `SHRAOPL` | regime | 4.3 | two-syllable | shalom |
| relax | 4.3 | `HRAEBGS` | leaks | 3.9 | word | leaks |
| revenge | 4.3 | `SR-PBLG` | vigil | 3.2 | two-syllable | vigil |
| vigil | 3.2 | `SR-PBLG` | revenge | 4.3 | two-syllable | vigil |
| facet | 3.2 | `TPAETS` | fastest | 4.2 | two-syllable | facet |
| fastest | 4.2 | `TPAETS` | feats | 3.2 | fold of feat | feats |
| widen | 3.2 | `WAOUPBD` | wound | 4.2 | word | widen |
| ultra | 4.2 | `AURLT` | alter | 4.0 | two-syllable | alter |
| asset | 4.2 | `AETS` | eats | 4.0 | fold of eat | eats |
| micro | 4.1 | `PHAEURBG` | maker | 4.2 | two-syllable | micro |
| parrot | 3.5 | `PAORT` | portrait | 4.2 | two-syllable | parrot |
| portrait | 4.2 | `PAORT` | parrot | 3.5 | two-syllable | parrot |
| acre | 3.9 | `ARBG` | arc | 4.2 | word | acre |
| closest | 4.2 | `KHROTS` | clots | 3.0 | fold of clot | clots |
| malice | 3.3 | `PHAEULS` | males | 4.2 | word | malice |
| sunken | 3.3 | `S*EUPBG` | sink | 4.2 | word | sunken |
| delta | 4.1 | `TKAELT` | dealt | 4.2 | word | delta |
| villa | 4.2 | `SRAEUL` | vale | 3.6 | word | vale |
| mason | 4.1 | `PHAOEUPBS` | mines | 4.2 | word | mason |
| refill | 3.2 | `RAOUFL` | rifle | 4.2 | two-syllable | refill |
| rifle | 4.2 | `RAOUFL` | refill | 3.2 | two-syllable | refill |
| barrel | 4.2 | `PWAERL` | barley | 3.5 | two-syllable | barley |
| evolve | 3.8 | `SROEFL` | resolve | 4.2 | two-syllable | evolve |
| resolve | 4.2 | `SROEFL` | evolve | 3.8 | two-syllable | evolve |
| revolve | 3.2 | `SROEFL` | resolve | 4.2 | two-syllable | revolve |
| parade | 4.2 | `PRAEUD` | prayed | 3.7 | fold of pray | prayed |
| racket | 3.3 | `RAEBGT` | react | 4.2 | two-syllable | racket |
| react | 4.2 | `RAEBGT` | racket | 3.3 | two-syllable | racket |
| retain | 4.2 | `TRA*UPB` | trainee | 3.4 | two-syllable | trainee |
| trainee | 3.4 | `TRA*UPB` | retain | 4.2 | two-syllable | trainee |
| teaspoon | 3.3 | `TAOEPBS` | teens | 4.2 | word | teaspoon |
| greatness | 3.7 | `TKPWRAPBTS` | grants | 4.2 | fold of grant | greatness |
| erode | 3.0 | `ROD` | rod | 4.1 | word | erode |
| akin | 3.7 | `KAEUPB` | cabin | 4.1 | two-syllable | akin |
| cabin | 4.1 | `KAEUPB` | cane | 3.8 | word | cane |
| arrow | 4.1 | `AOR` | oar | 2.9 | word | oar |
| precise | 4.1 | `SPRAOUS` | spruce | 3.4 | word | spruce |
| adore | 3.7 | `TKAOER` | deer | 4.1 | word | adore |
| saddle | 3.7 | `SAELD` | sealed | 4.1 | word | saddle |
| anon | 3.3 | `TPHAOPB` | noon | 4.1 | word | anon |
| arrange | 4.0 | `RAEUPBLG` | rage | 4.1 | word | arrange |
| cannon | 4.0 | `KAOPB` | canon | 4.1 | two-syllable | cannon |
| canon | 4.1 | `KAOPB` | cannon | 4.0 | two-syllable | cannon |
| apiece | 3.2 | `POES` | pose | 4.1 | word | apiece |
| peso | 3.0 | `POES` | pose | 4.1 | word | peso |
| payroll | 3.8 | `PAOEURL` | poorly | 4.1 | two-syllable | payroll |
| lawsuit | 4.1 | `HRATS` | lats | 2.3 | fold of lat | lats |
| scala | 3.0 | `SKUL` | skull | 4.1 | word | scala |
| basin | 4.1 | `PWAPBS` | bans | 3.7 | fold of ban | bans |
| gentile | 3.0 | `SKWRUPBLT` | gently | 4.1 | two-syllable | gentile |
| subtle | 4.1 | `SEULT` | silt | 3.0 | word | silt |
| boycott | 3.8 | `PWEUBGT` | bucket | 4.1 | two-syllable | boycott |
| bucket | 4.1 | `PWEUBGT` | boycott | 3.8 | two-syllable | boycott |
| ballot | 4.1 | `PWAOLT` | beetle | 3.5 | two-syllable | beetle |
| beetle | 3.5 | `PWAOLT` | ballot | 4.1 | two-syllable | beetle |
| demo | 4.1 | `TKOEPL` | dome | 3.9 | word | dome |
| fibre | 3.7 | `TPAOEURB` | fiber | 4.1 | two-syllable | fibre |
| northeast | 4.1 | `TPHRAOETS` | fleets | 3.3 | fold of fleet | fleets |
| salvage | 3.6 | `S-FPBLG` | savage | 4.0 | two-syllable | salvage |
| savage | 4.0 | `S-FPBLG` | salvage | 3.6 | two-syllable | salvage |
| elect | 4.0 | `HR-BGT` | lockout | 3.1 | two-syllable | lockout |
| lockout | 3.1 | `HR-BGT` | elect | 4.0 | two-syllable | lockout |
| passive | 4.0 | `PAEUFS` | paves | 2.5 | fold of pave | paves |
| axis | 4.0 | `AEUBGS` | aches | 3.3 | word | aches |
| carol | 4.0 | `KAORL` | cooler | 4.0 | two-syllable | cooler |
| choral | 3.2 | `KAORL` | carol | 4.0 | two-syllable | choral |
| coral | 3.9 | `KAORL` | carol | 4.0 | two-syllable | coral |
| fusion | 4.0 | `TKPWRAOERPB` | greener | 3.2 | two-syllable | greener |
| ashore | 3.5 | `SHAOER` | sheer | 4.0 | word | ashore |
| salmon | 4.0 | `SAOPBL` | semen | 3.4 | two-syllable | semen |
| semen | 3.4 | `SAOPBL` | salmon | 4.0 | two-syllable | semen |
| borough | 3.8 | `PW-R` | borrow | 4.0 | two-syllable | borough |
| borrow | 4.0 | `PW-R` | borough | 3.8 | two-syllable | borough |
| pharma | 3.4 | `TP-RPL` | firmly | 4.0 | two-syllable | pharma |
| inspire | 4.0 | `STPHAOR` | unsure | 3.8 | two-syllable | unsure |
| fossil | 4.0 | `TPOEULS` | foils | 2.8 | fold of foil | foils |
| coward | 3.7 | `KORD` | cord | 4.0 | word | coward |
| reddish | 3.4 | `RURBD` | rushed | 4.0 | fold of rush | reddish |
| magna | 3.3 | `PH-PBG` | manga | 4.0 | two-syllable | magna |
| manga | 4.0 | `PH-PBG` | magna | 3.3 | two-syllable | magna |
| hollow | 4.0 | `H-L` | hilly | 3.2 | two-syllable | hilly |
| weaken | 3.5 | `WAPBG` | wang | 4.0 | word | weaken |
| appease | 3.2 | `PEZ` | oppose | 3.9 | two-syllable | appease |
| oppose | 3.9 | `PEZ` | appease | 3.2 | two-syllable | appease |
| deli | 3.4 | `TKUL` | dull | 3.9 | word | deli |
| idol | 3.9 | `AEULD` | aled | 2.3 | fold of ale | aled |
| towel | 3.9 | `TOEL` | tole | 2.0 | word | tole |
| cystic | 3.1 | `S-BGTS` | sixty | 3.9 | two-syllable | cystic |
| applause | 3.8 | `PHRUZ` | plaza | 3.9 | two-syllable | applause |
| plaza | 3.9 | `PHRUZ` | applause | 3.8 | two-syllable | applause |
| brigade | 3.9 | `TKPWRAD` | grad | 3.6 | word | grad |
| hostess | 3.4 | `HOETS` | hottest | 3.9 | two-syllable | hostess |
| hottest | 3.9 | `HOETS` | hostess | 3.4 | two-syllable | hostess |
| awhile | 3.9 | `WOEUL` | willow | 3.7 | two-syllable | willow |
| willow | 3.7 | `WOEUL` | awhile | 3.9 | two-syllable | willow |
| fortnight | 3.4 | `TPOEURPBT` | fourteen | 3.9 | two-syllable | fortnight |
| fourteen | 3.9 | `TPOEURPBT` | fortnight | 3.4 | two-syllable | fortnight |
| shannon | 3.8 | `SHAOPB` | shiny | 3.9 | two-syllable | shannon |
| kitten | 3.6 | `KUPBT` | cunt | 3.9 | word | kitten |
| fulfil | 3.5 | `TPHREL` | fulfill | 3.9 | two-syllable | fulfil |
| fulfill | 3.9 | `TPHREL` | fulfil | 3.5 | two-syllable | fulfil |
| hydro | 3.6 | `HAEURD` | hardy | 3.9 | two-syllable | hydro |
| mistress | 3.9 | `PHURTS` | muster | 3.4 | two-syllable | muster |
| otis | 3.4 | `OUTS` | outs | 3.9 | word | otis |
| pickup | 3.9 | `PEPBG` | penning | 2.7 | fold of pen | penning |
| resin | 3.6 | `RUPBZ` | risen | 3.9 | two-syllable | resin |
| risen | 3.9 | `RUPBZ` | resin | 3.6 | two-syllable | resin |
| lava | 3.7 | `HRUF` | levy | 3.9 | two-syllable | lava |
| relive | 3.2 | `HRUF` | levy | 3.9 | two-syllable | relive |
| epoch | 3.2 | `OEPBG` | owning | 3.8 | fold of own | epoch |
| hola | 3.1 | `HAOEL` | heel | 3.8 | word | hola |
| primate | 3.1 | `PROEUPLT` | promptly | 3.8 | two-syllable | primate |
| teammate | 3.8 | `TEPLT` | tempt | 3.2 | word | tempt |
| emir | 3.0 | `PHUR` | merry | 3.8 | two-syllable | emir |
| slowest | 3.1 | `SHROTS` | slots | 3.8 | fold of slot | slowest |
| rascal | 3.1 | `R-BLGS` | reckless | 3.8 | two-syllable | rascal |
| reckless | 3.8 | `R-BLGS` | rascal | 3.1 | two-syllable | rascal |
| token | 3.8 | `TOPBG` | tong | 3.2 | word | tong |
| pancake | 3.4 | `P*PBG` | picnic | 3.8 | two-syllable | pancake |
| picnic | 3.8 | `P*PBG` | pinky | 3.4 | two-syllable | pinky |
| neuron | 3.2 | `TPHAURPB` | newborn | 3.8 | two-syllable | neuron |
| newborn | 3.8 | `TPHAURPB` | neuron | 3.2 | two-syllable | neuron |
| parole | 3.8 | `PROEL` | propel | 3.2 | two-syllable | propel |
| propel | 3.2 | `PROEL` | parole | 3.8 | two-syllable | propel |
| lesion | 3.1 | `HRAOURPB` | lunar | 3.8 | two-syllable | lesion |
| slightest | 3.8 | `SHRAO*UTS` | sleuths | 2.2 | fold of sleuth | sleuths |
| canoe | 3.5 | `TKPHOU` | dunno | 3.8 | two-syllable | canoe |
| dunno | 3.8 | `TKPHOU` | undo | 3.6 | two-syllable | undo |
| undo | 3.6 | `TKPHOU` | dunno | 3.8 | two-syllable | undo |
| duchess | 3.8 | `TKEUFPS` | ditches | 3.1 | fold of ditch | ditches |
| pagan | 3.6 | `PEUPBG` | ping | 3.8 | word | pagan |
| recap | 3.5 | `ROEPBG` | reckon | 3.8 | two-syllable | recap |
| reckon | 3.8 | `ROEPBG` | recap | 3.5 | two-syllable | recap |
| bonnet | 3.4 | `PWOEPBT` | bounty | 3.7 | two-syllable | bonnet |
| cola | 3.7 | `KAOEL` | keel | 3.2 | word | keel |
| palette | 3.7 | `PAELT` | pallet | 3.1 | two-syllable | pallet |
| pallet | 3.1 | `PAELT` | palette | 3.7 | two-syllable | pallet |
| petal | 3.0 | `PAELT` | palette | 3.7 | two-syllable | petal |
| potent | 3.7 | `POPBT` | pointy | 3.1 | two-syllable | pointy |
| serum | 3.7 | `SEURPL` | simmer | 3.3 | two-syllable | simmer |
| tempo | 3.7 | `TOEPL` | tome | 3.2 | word | tome |
| chili | 3.7 | `KH-L` | chilly | 3.5 | two-syllable | chilly |
| kilo | 3.1 | `KOEUL` | coil | 3.7 | word | kilo |
| retro | 3.7 | `ROERT` | rotor | 3.4 | two-syllable | rotor |
| averse | 3.1 | `SRAERS` | versa | 3.7 | two-syllable | averse |
| versa | 3.7 | `SRAERS` | averse | 3.1 | two-syllable | averse |
| petrol | 3.7 | `POERLT` | poultry | 3.5 | two-syllable | poultry |
| appoint | 3.7 | `PAOEUPBT` | pint | 3.7 | word | pint |
| peacock | 3.4 | `PAOEBG` | peek | 3.7 | word | peacock |
| tidal | 3.7 | `TOEULD` | toiled | 2.6 | fold of toil | toiled |
| helpless | 3.7 | `H-PLS` | hummus | 3.0 | two-syllable | hummus |
| hummus | 3.0 | `H-PLS` | helpless | 3.7 | two-syllable | hummus |
| taco | 3.7 | `TAOUBG` | tubing | 3.3 | fold of tube | tubing |
| opaque | 3.4 | `PAOEUBG` | pike | 3.7 | word | opaque |
| mellow | 3.3 | `PHOEL` | mole | 3.7 | word | mellow |
| parcel | 3.7 | `PAERLS` | pearls | 3.5 | word | pearls |
| erase | 3.7 | `RAUS` | raws | 2.0 | fold of raw | raws |
| reddit | 3.7 | `RUTD` | rutted | 2.0 | fold of rut | rutted |
| lupus | 3.2 | `HRAOPS` | loops | 3.6 | word | lupus |
| nexus | 3.6 | `TPHEUBGS` | nicks | 3.1 | fold of nick | nicks |
| camel | 3.6 | `KAEPL` | calmly | 3.6 | two-syllable | calmly |
| chemo | 3.2 | `KAEPL` | camel | 3.6 | two-syllable | chemo |
| coco | 3.5 | `KEBG` | cocoa | 3.6 | two-syllable | coco |
| cocoa | 3.6 | `KEBG` | coco | 3.5 | two-syllable | coco |
| paddle | 3.5 | `PAELD` | pedal | 3.6 | two-syllable | paddle |
| pedal | 3.6 | `PAELD` | paddle | 3.5 | two-syllable | paddle |
| align | 3.6 | `HROEUPB` | loin | 2.8 | word | loin |
| leftist | 3.6 | `HRUFTS` | lusts | 2.5 | fold of lust | lusts |
| ripple | 3.4 | `RUPL` | rum | 3.6 | word | ripple |
| turbine | 3.6 | `TERPB` | tenor | 3.4 | two-syllable | tenor |
| ado | 3.2 | `TKAO` | doo | 3.6 | word | ado |
| brittle | 3.3 | `PWRULT` | rebuilt | 3.6 | two-syllable | brittle |
| rebuilt | 3.6 | `PWRULT` | brittle | 3.3 | two-syllable | brittle |
| aloe | 3.1 | `AOL` | alloy | 3.6 | two-syllable | aloe |
| motel | 3.6 | `TPHOEL` | knoll | 2.9 | word | knoll |
| pseudo | 3.6 | `SAUD` | sawed | 2.8 | word | sawed |
| unrest | 3.6 | `TPHREUTS` | flits | 2.0 | fold of flit | flits |
| mundo | 3.0 | `PHOUPBD` | mound | 3.6 | word | mundo |
| upheld | 3.6 | `PHEULD` | milled | 2.9 | fold of mill | milled |
| citrus | 3.6 | `SERTS` | certs | 2.2 | fold of cert | certs |
| cushion | 3.6 | `KERPB` | kern | 3.2 | word | kern |
| dictate | 3.6 | `TKAT` | dat | 3.5 | word | dat |
| lima | 3.6 | `HROEUPL` | limo | 3.3 | two-syllable | limo |
| limo | 3.3 | `HROEUPL` | lima | 3.6 | two-syllable | limo |
| seaside | 3.6 | `SAUSD` | sauced | 2.0 | fold of sauce | sauced |
| crescent | 3.6 | `KR-PBTS` | kristin | 3.3 | two-syllable | kristin |
| kristin | 3.3 | `KR-PBTS` | crescent | 3.6 | two-syllable | kristin |
| hassle | 3.6 | `HAELS` | heals | 3.3 | word | heals |
| offend | 3.6 | `TPOEPBD` | phoned | 3.3 | word | phoned |
| lettuce | 3.6 | `HREUTS` | lites | 2.2 | fold of lit | lites |
| sumo | 3.1 | `SAUPL` | psalm | 3.6 | word | sumo |
| amend | 3.5 | `PHAEPBD` | madden | 3.5 | two-syllable | madden |
| madden | 3.5 | `PHAEPBD` | amend | 3.5 | two-syllable | madden |
| bowel | 3.5 | `PWOEL` | bole | 2.4 | word | bole |
| ogre | 3.1 | `ORG` | org | 3.5 | word | ogre |
| casa | 3.5 | `KUS` | cuss | 2.8 | word | cuss |
| junta | 3.3 | `HAUPBT` | haunt | 3.5 | word | junta |
| marrow | 3.5 | `PHAOR` | moor | 3.4 | word | moor |
| repo | 3.1 | `RAEP` | reap | 3.5 | word | repo |
| suspense | 3.5 | `SPEUPBS` | spins | 3.5 | word | spins |
| nicest | 3.5 | `TPHAOUTS` | newts | 2.2 | fold of newt | newts |
| stricken | 3.5 | `STRUPBG` | strung | 3.3 | word | strung |
| uphold | 3.5 | `PHOULD` | mould | 3.5 | word | mould |
| aloud | 3.5 | `HRAOUD` | lewd | 3.2 | word | lewd |
| bridal | 3.5 | `PWROEULD` | broiled | 2.4 | fold of broil | broiled |
| faggot | 3.5 | `TPAOGT` | footing | 3.5 | fold of foot | faggot |
| nuisance | 3.5 | `TPHOUPBS` | nouns | 3.3 | word | nouns |
| axel | 3.3 | `AEBLGS` | axle | 3.5 | two-syllable | axel |
| axle | 3.5 | `AEBLGS` | axel | 3.3 | two-syllable | axel |
| bitten | 3.5 | `PWUPBT` | bunt | 2.8 | word | bunt |
| lingo | 3.0 | `HROEUPBG` | login | 3.5 | two-syllable | lingo |
| login | 3.5 | `HROEUPBG` | lingo | 3.0 | two-syllable | lingo |
| techno | 3.5 | `TOEPBG` | toning | 2.8 | fold of tone | toning |
| tycoon | 3.2 | `TKAOEUPB` | dine | 3.5 | word | tycoon |
| tendon | 3.3 | `TOEPBD` | toned | 3.5 | word | tendon |
| cascade | 3.5 | `SKEUD` | skid | 3.3 | word | skid |
| paso | 3.5 | `PAOS` | poos | 2.1 | fold of poo | poos |
| penal | 3.5 | `POEPBL` | pollen | 3.5 | two-syllable | penal |
| pollen | 3.5 | `POEPBL` | penal | 3.5 | two-syllable | penal |
| ketchup | 3.5 | `KEUFP` | kitsch | 2.7 | word | kitsch |
| astute | 3.1 | `STOUT` | stout | 3.5 | word | astute |
| marquee | 3.2 | `KPHROE` | marquis | 3.5 | two-syllable | marquee |
| marquis | 3.5 | `KPHROE` | marquee | 3.2 | two-syllable | marquee |
| nugget | 3.2 | `TPHEUGT` | knitting | 3.5 | fold of knit | nugget |
| steward | 3.5 | `STERD` | sturdy | 3.4 | two-syllable | sturdy |
| cupboard | 3.4 | `KURBD` | curbed | 2.6 | word | curbed |
| oblique | 3.2 | `PWHRAEBG` | bleak | 3.4 | word | oblique |
| sulfur | 3.4 | `SUFRL` | sulphur | 3.2 | two-syllable | sulphur |
| sulphur | 3.2 | `SUFRL` | sulfur | 3.4 | two-syllable | sulphur |
| dearest | 3.4 | `TKARTS` | darts | 3.4 | fold of dart | darts |
| medic | 3.4 | `PHUBGD` | mucked | 2.1 | fold of muck | mucked |
| noodle | 3.4 | `TPHAOELD` | kneeled | 2.3 | fold of kneel | kneeled |
| hobo | 3.1 | `HEB` | hubby | 3.4 | two-syllable | hobo |
| leakage | 3.2 | `HREPBLG` | ledge | 3.4 | word | leakage |
| moustache | 3.2 | `PHAURBTS` | mustache | 3.4 | two-syllable | moustache |
| mustache | 3.4 | `PHAURBTS` | moustache | 3.2 | two-syllable | moustache |
| payload | 3.4 | `PAOEULD` | piled | 3.4 | word | payload |
| roulette | 3.4 | `HROET` | loath | 2.7 | word | loath |
| vanish | 3.4 | `SRAEURPB` | varnish | 3.0 | two-syllable | varnish |
| varnish | 3.0 | `SRAEURPB` | vanish | 3.4 | two-syllable | varnish |
| barrow | 3.4 | `PWAOR` | boor | 2.1 | word | boor |
| loosen | 3.4 | `HRAOEPBS` | liens | 2.7 | fold of lien | liens |
| feudal | 3.4 | `TKPWROULD` | growled | 2.6 | word | growled |
| lattice | 3.4 | `HRAEUTS` | lates | 2.2 | fold of late | lates |
| relapse | 3.3 | `HRAEPS` | leaps | 3.4 | fold of leap | relapse |
| topless | 3.4 | `TOEPLS` | tomes | 2.6 | fold of tome | tomes |
| palate | 3.4 | `P-LT` | pellet | 3.1 | two-syllable | pellet |
| pellet | 3.1 | `P-LT` | palate | 3.4 | two-syllable | pellet |
| poodle | 3.0 | `PAOELD` | peeled | 3.3 | word | poodle |
| vowel | 3.3 | `SROEL` | volley | 3.2 | two-syllable | volley |
| ethos | 3.3 | `A*ETS` | eighths | 2.5 | fold of eighth | eighths |
| carton | 3.2 | `KAORPBT` | contra | 3.3 | two-syllable | carton |
| contra | 3.3 | `KAORPBT` | carton | 3.2 | two-syllable | carton |
| donut | 3.3 | `TKOEUPBT` | doughnut | 3.2 | two-syllable | doughnut |
| doughnut | 3.2 | `TKOEUPBT` | donut | 3.3 | two-syllable | doughnut |
| dugout | 3.0 | `TKOUGT` | doubting | 3.3 | fold of doubt | dugout |
| horrid | 3.3 | `HOEURD` | hydra | 3.3 | two-syllable | horrid |
| hydra | 3.3 | `HOEURD` | horrid | 3.3 | two-syllable | horrid |
| prepaid | 3.3 | `PRA*UD` | repaid | 3.3 | two-syllable | repaid |
| repaid | 3.3 | `PRA*UD` | prepaid | 3.3 | two-syllable | repaid |
| lipid | 3.3 | `HRAOPD` | looped | 3.0 | fold of loop | looped |
| evoke | 3.3 | `SROBG` | revoke | 3.3 | two-syllable | revoke |
| revoke | 3.3 | `SROBG` | evoke | 3.3 | two-syllable | revoke |
| furnish | 3.1 | `TPERPB` | fern | 3.3 | word | furnish |
| bison | 3.3 | `PWAEUPBS` | banes | 2.1 | fold of bane | banes |
| attest | 3.2 | `TAETS` | teats | 2.3 | fold of teat | teats |
| emit | 3.2 | `PHUT` | mutt | 2.9 | word | mutt |
| judo | 3.2 | `SKWRAUD` | jawed | 2.5 | fold of jaw | jawed |
| nitrate | 3.2 | `TPHOEURT` | nitro | 3.2 | two-syllable | nitro |
| nitro | 3.2 | `TPHOEURT` | nitrate | 3.2 | two-syllable | nitro |
| taurus | 3.2 | `TARS` | tars | 2.5 | fold of tar | tars |
| spectre | 3.2 | `SPERBGT` | specter | 3.1 | two-syllable | specter |
| sabre | 3.0 | `SARB` | sash | 3.2 | word | sabre |
| cripple | 3.2 | `KRUPL` | crumb | 3.0 | word | crumb |
| propane | 3.2 | `PRAOEPB` | preen | 2.2 | word | preen |
| bagel | 3.2 | `PWAULG` | balling | 2.8 | fold of ball | balling |
| faucet | 3.1 | `TPAEUTS` | fates | 3.2 | fold of fate | faucet |
| tulip | 3.2 | `TAOEPL` | teem | 2.1 | word | teem |
| starboard | 3.2 | `STARBD` | stashed | 3.0 | fold of stash | stashed |
| abode | 3.1 | `PWAOED` | bede | 2.7 | word | bede |
| dada | 3.1 | `TKUD` | dud | 3.0 | word | dud |
| rupee | 3.0 | `PRAOEU` | pry | 3.1 | word | rupee |
| callous | 3.1 | `KAOLS` | cools | 3.0 | word | cools |
| regroup | 3.1 | `TKPWRAOEUP` | gripe | 2.8 | word | gripe |
| sepsis | 3.0 | `SUPS` | supes | 2.0 | fold of sup | supes |

### Not counted: stroke is the stem's (43)

ally (ale), armor (arm), barker (bark), bearer (bear), burner (burn), charger (charge), charter (chart), clearer (clear), corner (corn), cursor (curse), darker (dark), fairer (fair), farmer (farm), former (form), fresher (fresh), harder (hard), harsher (harsh), honey (hone), lady (lad), larger (large), learner (learn), marker (mark), merger (merge), nearer (near), poorer (poor), porter (port), quarter (quart), sally (sale), scary (scare), shady (shad), sharper (sharp), shorter (short), smarter (smart), starter (start), storey (store), surfer (surf), tally (tale), terror (ter), warmer (warm), wary (ware), washer (wash), wearer (wear), worker (work)

## Conflicts: by stress, -er R on the left, no marker

One row per two-syllable word that shares its stroke. **rival** is the most frequent other claimant.


### -er (183)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| after | 6.1 | `RAFT` | raft | 3.5 | word | raft |
| over | 6.1 | `ROEF` | rove | 2.9 | word | rove |
| never | 5.9 | `TPHREF` | fluffy | 3.6 | two-syllable | fluffy |
| hover | 3.3 | `HROF` | love | 5.8 | word | hover |
| hooker | 3.7 | `HRAOBG` | look | 5.8 | word | hooker |
| under | 5.7 | `RUPBD` | ridden | 3.6 | two-syllable | ridden |
| ever | 5.7 | `REF` | rev | 4.1 | word | rev |
| hotter | 3.6 | `HROT` | lot | 5.6 | word | hotter |
| tiger | 4.3 | `TRAOEUG` | trying | 5.5 | fold of try | tiger |
| appear | 4.8 | `PER` | per | 5.5 | word | appear |
| matter | 5.4 | `PHRAT` | platter | 3.2 | two-syllable | platter |
| platter | 3.2 | `PHRAT` | matter | 5.4 | two-syllable | platter |
| fender | 3.4 | `TPREPBD` | friend | 5.4 | word | fender |
| hater | 3.1 | `HRAEUT` | late | 5.3 | word | hater |
| manner | 4.5 | `PHRAPB` | plan | 5.3 | word | manner |
| manor | 3.8 | `PHRAPB` | plan | 5.3 | word | manor |
| former | 5.3 | `TPRORPL` | perform | 4.7 | two-syllable | perform |
| header | 3.9 | `HRAED` | lead | 5.2 | word | header |
| center | 5.2 | `SREPBT` | centre | 5.0 | two-syllable | centre |
| answer | 5.2 | `RAPBS` | rans | 2.1 | fold of ran | rans |
| diver | 3.4 | `TKRAOEUF` | drive | 5.1 | word | diver |
| river | 5.0 | `REUF` | riff | 3.4 | word | riff |
| master | 4.9 | `PHRATS` | plaster | 3.5 | two-syllable | plaster |
| plaster | 3.5 | `PHRATS` | master | 4.9 | two-syllable | plaster |
| hacker | 3.7 | `HRABG` | lack | 4.9 | word | hacker |
| older | 4.9 | `ROLD` | rolled | 4.2 | word | rolled |
| collar | 4.0 | `KROL` | color | 4.9 | two-syllable | collar |
| color | 4.9 | `KROL` | colour | 4.5 | two-syllable | colour |
| colour | 4.5 | `KROL` | color | 4.9 | two-syllable | colour |
| cider | 3.5 | `SRAOEUD` | avoid | 4.9 | two-syllable | cider |
| owner | 4.8 | `RO*EPB` | roan | 2.7 | word | roan |
| powder | 4.2 | `PROUD` | proud | 4.8 | word | powder |
| labor | 4.8 | `HRAEUB` | labour | 4.7 | two-syllable | labour |
| labour | 4.7 | `HRAEUB` | labor | 4.8 | two-syllable | labour |
| bigger | 4.7 | `PWREUG` | brig | 3.3 | word | brig |
| planar | 3.1 | `PHRAEUPB` | plane | 4.7 | word | planar |
| honor | 4.7 | `ROPB` | honour | 4.4 | two-syllable | honour |
| honour | 4.4 | `ROPB` | honor | 4.7 | two-syllable | honour |
| writer | 4.7 | `RAOEUT` | rite | 3.6 | word | rite |
| sugar | 4.7 | `SHRUG` | slug | 3.3 | word | slug |
| sector | 4.7 | `SREBGT` | vector | 4.0 | two-syllable | vector |
| vector | 4.0 | `SREBGT` | sector | 4.7 | two-syllable | vector |
| measure | 4.7 | `PHRAERB` | pleasure | 4.6 | two-syllable | pleasure |
| pleasure | 4.6 | `PHRAERB` | measure | 4.7 | two-syllable | pleasure |
| miner | 3.6 | `PHRAOEUPB` | minor | 4.6 | two-syllable | miner |
| minor | 4.6 | `PHRAOEUPB` | miner | 3.6 | two-syllable | miner |
| steamer | 3.4 | `STRAEPL` | stream | 4.6 | word | steamer |
| dollar | 4.6 | `TKROL` | droll | 2.6 | word | droll |
| favor | 4.6 | `TPRAEUF` | favour | 4.3 | two-syllable | favour |
| favour | 4.3 | `TPRAEUF` | favor | 4.6 | two-syllable | favour |
| harder | 4.6 | `HRARD` | lard | 3.0 | word | lard |
| prefer | 4.6 | `TPR-R` | refer | 4.5 | two-syllable | refer |
| refer | 4.5 | `TPR-R` | prefer | 4.6 | two-syllable | refer |
| premier | 4.6 | `PHRAOR` | premiere | 4.2 | two-syllable | premiere |
| ester | 3.1 | `RETS` | arrest | 4.5 | two-syllable | ester |
| solar | 4.5 | `SROEL` | vowel | 3.3 | two-syllable | vowel |
| cipher | 3.1 | `SRAOEUF` | survive | 4.5 | two-syllable | cipher |
| tender | 4.1 | `TREPBD` | trend | 4.5 | word | tender |
| soccer | 4.5 | `SROBG` | evoke | 3.3 | two-syllable | evoke |
| finger | 4.5 | `TPREUPBG` | fringe | 3.9 | word | fringe |
| ticker | 3.0 | `TREUBG` | trick | 4.5 | word | ticker |
| usher | 3.5 | `RURB` | rush | 4.5 | word | usher |
| killer | 4.5 | `KREUL` | krill | 2.6 | word | krill |
| founder | 4.5 | `TPROUPBD` | frowned | 3.2 | word | frowned |
| poster | 4.2 | `PROTS` | protest | 4.5 | two-syllable | poster |
| tailor | 3.6 | `TRA*EUL` | trail | 4.5 | word | tailor |
| anchor | 4.0 | `RA*PBG` | rank | 4.4 | word | anchor |
| anger | 4.4 | `RAPBG` | rang | 3.7 | word | rang |
| bother | 4.4 | `PWRO*T` | broth | 3.4 | word | broth |
| heather | 3.9 | `HRA*ET` | leather | 4.3 | two-syllable | heather |
| leather | 4.3 | `HRA*ET` | heather | 3.9 | two-syllable | heather |
| rooster | 3.3 | `RAOTS` | roots | 4.3 | fold of root | rooster |
| fighter | 4.3 | `TPRAO*EUT` | fright | 3.3 | word | fright |
| dagger | 3.5 | `TKRAG` | drag | 4.3 | word | dagger |
| shipper | 3.0 | `SHREUP` | slip | 4.3 | word | shipper |
| copper | 4.3 | `KROP` | crop | 4.2 | word | crop |
| giver | 3.2 | `TKPWREUF` | forgive | 4.2 | two-syllable | giver |
| outer | 4.2 | `ROUT` | rout | 3.1 | word | rout |
| baker | 4.2 | `PWRAEUBG` | brake | 4.0 | word | brake |
| clever | 4.2 | `KHREF` | clef | 2.5 | word | clef |
| laser | 4.2 | `HRAEUZ` | lays | 3.7 | word | lays |
| dealer | 4.2 | `TKRAEL` | cartel | 3.6 | two-syllable | cartel |
| hitter | 3.5 | `HREUT` | lit | 4.2 | word | hitter |
| humor | 4.2 | `KWHRAOUPL` | humour | 3.8 | two-syllable | humour |
| humour | 3.8 | `KWHRAOUPL` | humor | 4.2 | two-syllable | humour |
| victor | 4.2 | `SREUBGT` | vacate | 3.1 | two-syllable | vacate |
| grammar | 4.2 | `TKPWRAPL` | gram | 3.8 | word | gram |
| hammer | 4.2 | `HRAPL` | lamb | 4.0 | word | lamb |
| hamper | 3.3 | `HRAPL` | hammer | 4.2 | two-syllable | hamper |
| harbor | 4.2 | `HRARB` | harbour | 4.0 | two-syllable | harbour |
| harbour | 4.0 | `HRARB` | harbor | 4.2 | two-syllable | harbour |
| pepper | 4.1 | `PREP` | prep | 3.9 | word | prep |
| banner | 4.1 | `PWRAPB` | bran | 3.3 | word | bran |
| ginger | 3.9 | `SKWREUPBLG` | jungle | 4.1 | two-syllable | ginger |
| injure | 3.3 | `REUPBLG` | ridge | 4.1 | word | injure |
| wrapper | 3.2 | `RAP` | rap | 4.1 | word | wrapper |
| neighbor | 4.1 | `TPHRA*EB` | neighbour | 3.7 | two-syllable | neighbour |
| neighbour | 3.7 | `TPHRA*EB` | neighbor | 4.1 | two-syllable | neighbour |
| hunger | 4.1 | `HRUPBG` | lung | 4.1 | word | lung |
| nicer | 3.6 | `TPHRAOEUS` | flies | 4.1 | word | nicer |
| roster | 4.1 | `ROTS` | rots | 2.7 | fold of rot | rots |
| safer | 4.1 | `SRAEUF` | saver | 3.3 | two-syllable | saver |
| saver | 3.3 | `SRAEUF` | safer | 4.1 | two-syllable | saver |
| fiber | 4.1 | `TPRAOEUB` | fibre | 3.7 | two-syllable | fibre |
| altar | 3.8 | `RAULT` | alter | 4.0 | two-syllable | altar |
| alter | 4.0 | `RAULT` | altar | 3.8 | two-syllable | altar |
| lunar | 3.8 | `HRAOUPB` | lineup | 4.0 | two-syllable | lunar |
| eager | 4.0 | `RAEG` | reggae | 3.5 | two-syllable | reggae |
| razor | 3.7 | `RAEUZ` | rays | 4.0 | word | razor |
| keeper | 4.0 | `KRAOEP` | creep | 3.8 | word | creep |
| cellar | 3.6 | `SREL` | seller | 4.0 | two-syllable | cellar |
| seller | 4.0 | `SREL` | cellar | 3.6 | two-syllable | cellar |
| liquor | 4.0 | `HREUBG` | lick | 3.8 | word | lick |
| donor | 3.9 | `TKROEPB` | drone | 4.0 | word | donor |
| unsure | 3.8 | `STPHAOR` | inspire | 4.0 | two-syllable | unsure |
| cluster | 4.0 | `KHRUTS` | klutz | 2.1 | word | klutz |
| feather | 3.7 | `TPRA*ET` | freight | 4.0 | word | feather |
| armor | 4.0 | `RARPL` | armour | 3.8 | two-syllable | armour |
| armour | 3.8 | `RARPL` | armor | 4.0 | two-syllable | armour |
| frontier | 4.0 | `TPHRAER` | nearer | 3.4 | two-syllable | nearer |
| nearer | 3.4 | `TPHRAER` | frontier | 4.0 | two-syllable | nearer |
| poker | 4.0 | `PROEBG` | probing | 3.2 | fold of probe | probing |
| polar | 4.0 | `PROEL` | parole | 3.8 | two-syllable | parole |
| scholar | 4.0 | `SKROL` | scroll | 3.9 | word | scroll |
| censor | 3.4 | `SREPBS` | sensor | 3.9 | two-syllable | censor |
| sensor | 3.9 | `SREPBS` | censor | 3.4 | two-syllable | censor |
| odor | 3.5 | `ROED` | rode | 3.9 | word | odor |
| cruiser | 3.5 | `KRAOUZ` | crews | 3.9 | word | cruiser |
| bouncer | 3.1 | `PWROUPBS` | browns | 3.9 | fold of brown | bouncer |
| utter | 3.9 | `RUT` | rut | 3.1 | word | rut |
| barber | 3.8 | `PWRARB` | brash | 3.0 | word | brash |
| temper | 3.8 | `TREPL` | tremor | 3.0 | two-syllable | tremor |
| tremor | 3.0 | `TREPL` | temper | 3.8 | two-syllable | tremor |
| sender | 3.2 | `SREPBD` | vendor | 3.8 | two-syllable | sender |
| vendor | 3.8 | `SREPBD` | sender | 3.2 | two-syllable | sender |
| caller | 3.6 | `KRAUL` | crawl | 3.8 | word | caller |
| shutter | 3.5 | `SHRUT` | slut | 3.8 | word | shutter |
| rumor | 3.7 | `RAOUPL` | rumour | 3.4 | two-syllable | rumour |
| rumour | 3.4 | `RAOUPL` | rumor | 3.7 | two-syllable | rumour |
| chaser | 3.1 | `KHRAEUS` | classy | 3.7 | two-syllable | chaser |
| prosper | 3.5 | `PROPS` | props | 3.7 | fold of prop | prosper |
| boiler | 3.7 | `PWROEUL` | broil | 2.4 | word | broil |
| otter | 3.4 | `ROT` | rot | 3.7 | word | otter |
| bummer | 3.2 | `PWRUPL` | bumper | 3.7 | two-syllable | bummer |
| bumper | 3.7 | `PWRUPL` | bummer | 3.2 | two-syllable | bummer |
| downer | 3.1 | `TKROUPB` | drown | 3.7 | word | downer |
| arbor | 3.5 | `RARB` | rash | 3.7 | word | arbor |
| azure | 3.4 | `RARB` | rash | 3.7 | word | azure |
| decor | 3.6 | `TKOER` | dowry | 3.1 | two-syllable | dowry |
| poorer | 3.6 | `PRAOR` | rapport | 3.2 | two-syllable | rapport |
| blister | 3.0 | `PWHREUTS` | blitz | 3.6 | word | blister |
| batter | 3.6 | `PWRAT` | brat | 3.5 | word | brat |
| tanner | 3.6 | `TRAPB` | tran | 3.1 | word | tran |
| cater | 3.5 | `KRAEUT` | crate | 3.5 | word | crate |
| skipper | 3.5 | `SKREUP` | scrip | 2.6 | word | scrip |
| glamour | 3.5 | `TKPWHRAPL` | glam | 3.2 | word | glam |
| hopper | 3.5 | `HROP` | lop | 2.7 | word | lop |
| liter | 3.5 | `HRAOET` | leet | 2.2 | word | leet |
| calmer | 3.0 | `KRAUPL` | creamy | 3.4 | two-syllable | calmer |
| filler | 3.4 | `TPREUL` | frill | 2.3 | word | frill |
| cooker | 3.3 | `KRAOBG` | crook | 3.4 | word | cooker |
| kicker | 3.4 | `KREUBG` | crick | 2.8 | word | crick |
| insure | 3.4 | `STPHAOER` | sneer | 2.7 | word | sneer |
| punter | 3.0 | `PRUPBT` | reprint | 3.4 | two-syllable | punter |
| biker | 3.4 | `PWRAOEUBG` | bribing | 2.9 | fold of bribe | bribing |
| parlor | 3.3 | `PRARL` | parlour | 3.1 | two-syllable | parlour |
| parlour | 3.1 | `PRARL` | parlor | 3.3 | two-syllable | parlour |
| hangar | 3.3 | `HRAPBG` | hanger | 3.2 | two-syllable | hanger |
| hanger | 3.2 | `HRAPBG` | hangar | 3.3 | two-syllable | hanger |
| simmer | 3.3 | `SREUPL` | vim | 2.7 | word | vim |
| camper | 3.3 | `KRAPL` | cram | 3.1 | word | cram |
| specter | 3.1 | `SPREBGT` | spectre | 3.2 | two-syllable | specter |
| taser | 3.1 | `TRAEUZ` | trays | 3.2 | word | taser |
| tester | 3.2 | `TRETS` | trusty | 3.0 | two-syllable | trusty |
| cheater | 3.2 | `KHRAET` | cleat | 2.4 | word | cleat |
| shatter | 3.2 | `SHRAT` | slat | 2.4 | word | slat |
| boner | 3.2 | `PWROEPB` | brownie | 3.2 | two-syllable | brownie |
| gator | 3.2 | `TKPWRAEUT` | grate | 3.2 | word | grate |
| shopper | 3.2 | `SHROP` | slop | 2.7 | word | slop |
| caster | 3.1 | `KRATS` | castor | 3.2 | two-syllable | caster |
| castor | 3.2 | `KRATS` | caster | 3.1 | two-syllable | caster |
| shocker | 3.1 | `SHROBG` | schlock | 2.1 | word | schlock |
| banger | 3.0 | `PWRAPBG` | branning | 2.2 | fold of bran | branning |

### -ly (23)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| filly | 3.1 | `TP-L` | follow | 5.1 | two-syllable | filly |
| rely | 4.3 | `HRAOU` | allow | 5.0 | two-syllable | rely |
| sully | 3.1 | `SEL` | sell | 5.0 | word | sully |
| fully | 4.9 | `TPEL` | fell | 4.8 | word | fell |
| holy | 4.9 | `HOUL` | howl | 3.2 | word | howl |
| apply | 4.8 | `PHROEU` | ploy | 3.2 | word | ploy |
| bully | 3.9 | `PWEL` | bell | 4.6 | word | bully |
| newly | 4.4 | `TPHAOEL` | kneel | 3.2 | word | kneel |
| belly | 4.1 | `PWUL` | bull | 4.3 | word | belly |
| poorly | 4.1 | `PAOEURL` | payroll | 3.8 | two-syllable | payroll |
| gently | 4.1 | `SKWRUPBLT` | gentile | 3.0 | two-syllable | gentile |
| firmly | 4.0 | `TP-RPL` | pharma | 3.4 | two-syllable | pharma |
| hilly | 3.2 | `H-L` | hollow | 4.0 | two-syllable | hilly |
| holly | 3.9 | `HOEUL` | wholly | 3.8 | two-syllable | wholly |
| wholly | 3.8 | `HOEUL` | holly | 3.9 | two-syllable | wholly |
| promptly | 3.8 | `PROEUPLT` | primate | 3.1 | two-syllable | primate |
| folly | 3.3 | `TPOEUL` | foil | 3.8 | word | folly |
| chilly | 3.5 | `KH-L` | chili | 3.7 | two-syllable | chilly |
| nelly | 3.2 | `TPHUL` | null | 3.7 | word | nelly |
| boldly | 3.3 | `PWOEULD` | boiled | 3.7 | word | boldly |
| calmly | 3.6 | `KAEPL` | camel | 3.6 | two-syllable | calmly |
| oddly | 3.6 | `OEULD` | oiled | 3.1 | word | oiled |
| woolly | 3.0 | `WAOEUL` | wile | 2.6 | word | wile |

### -y (116)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| hazy | 3.1 | `HAZ` | has | 6.4 | word | hazy |
| hurry | 4.1 | `HER` | her | 6.3 | word | hurry |
| annie | 3.9 | `AEUPB` | any | 6.1 | two-syllable | annie |
| any | 6.1 | `AEUPB` | annie | 3.9 | two-syllable | annie |
| midday | 3.4 | `PHAEUD` | made | 5.9 | word | midday |
| tacky | 3.2 | `TAEUBG` | take | 5.9 | word | tacky |
| fluffy | 3.6 | `TPHREF` | never | 5.9 | two-syllable | fluffy |
| petty | 4.1 | `PUT` | put | 5.7 | word | petty |
| money | 5.6 | `PHOEPB` | amin | 3.2 | two-syllable | amin |
| carry | 4.9 | `KAEUR` | care | 5.6 | word | carry |
| today | 5.5 | `TKAOEU` | die | 5.1 | word | die |
| sassy | 3.2 | `SAEUS` | says | 5.5 | fold of say | sassy |
| envoy | 3.5 | `OEFPB` | often | 5.4 | two-syllable | envoy |
| body | 5.4 | `PWOEUD` | abide | 3.6 | two-syllable | abide |
| essay | 4.2 | `SAU` | saw | 5.3 | word | essay |
| obey | 3.8 | `PWAO*EU` | buy | 5.3 | word | obey |
| ruddy | 3.0 | `RED` | red | 5.3 | word | ruddy |
| county | 5.2 | `KOEPBT` | content | 5.0 | two-syllable | content |
| sorry | 5.2 | `SOEUR` | soir | 2.4 | word | soir |
| relay | 3.9 | `ROEL` | role | 5.2 | word | relay |
| pony | 3.8 | `POUPB` | upon | 5.1 | two-syllable | pony |
| lacy | 3.2 | `HRAS` | release | 5.1 | two-syllable | lacy |
| buddy | 4.4 | `PWED` | bed | 5.1 | word | buddy |
| savvy | 3.6 | `SAEUF` | safe | 5.1 | word | savvy |
| funny | 5.0 | `TPEPB` | fen | 2.8 | word | fen |
| foxy | 3.0 | `TPOEUBGS` | focus | 5.0 | two-syllable | foxy |
| woody | 3.8 | `WAOEUD` | wide | 5.0 | word | woody |
| laundry | 4.1 | `HRAERPBD` | learned | 4.8 | fold of learn | laundry |
| edgy | 3.4 | `UPBLG` | engine | 4.8 | two-syllable | edgy |
| lucky | 4.8 | `HREBG` | loco | 3.2 | two-syllable | loco |
| wacky | 3.3 | `WAEUBG` | wake | 4.8 | word | wacky |
| goody | 3.0 | `TKPWAOEUD` | guide | 4.8 | word | goody |
| valley | 4.7 | `SRAEL` | surreal | 3.6 | two-syllable | surreal |
| array | 4.1 | `RAEU` | ray | 4.7 | word | array |
| jockey | 3.6 | `SKWROEBG` | joke | 4.7 | word | jockey |
| leafy | 3.3 | `HRAUF` | laugh | 4.7 | word | leafy |
| tiny | 4.6 | `TAOPB` | eighteen | 3.8 | two-syllable | eighteen |
| candy | 4.3 | `KAEUPBD` | kinda | 4.6 | two-syllable | candy |
| grassy | 3.2 | `TKPWRAEUS` | grace | 4.6 | word | grassy |
| rusty | 3.6 | `RETS` | arrest | 4.5 | two-syllable | rusty |
| decay | 3.8 | `TKAU` | da | 4.5 | word | decay |
| volley | 3.2 | `SROEL` | solar | 4.5 | two-syllable | volley |
| sexy | 4.4 | `SUBGS` | sucks | 4.4 | fold of suck | sucks |
| rugby | 4.4 | `REBG` | rec | 3.6 | word | rec |
| gritty | 3.4 | `TKPWR-T` | regret | 4.4 | two-syllable | gritty |
| fatty | 3.8 | `TPAEUT` | fate | 4.4 | word | fatty |
| bouncy | 3.2 | `PWOEPBS` | bones | 4.3 | word | bouncy |
| booty | 3.6 | `PWAOEUT` | bite | 4.3 | word | booty |
| slimy | 3.1 | `SHRAOPL` | regime | 4.3 | two-syllable | slimy |
| coney | 3.1 | `KOPB` | con | 4.3 | word | coney |
| teeny | 3.0 | `TAOUPB` | tune | 4.3 | word | teeny |
| sweetie | 3.6 | `SWAOUT` | suite | 4.2 | word | sweetie |
| barley | 3.5 | `PWAERL` | barrel | 4.2 | two-syllable | barley |
| hasty | 3.3 | `HAEUTS` | hates | 4.2 | fold of hate | hasty |
| savoy | 3.1 | `SRAOEU` | vi | 4.1 | word | savoy |
| monkey | 4.1 | `PHO*EPBG` | moaning | 3.4 | fold of moan | moaning |
| penny | 4.1 | `PUPB` | pun | 3.5 | word | pun |
| sunny | 4.1 | `SEPB` | sen | 4.0 | word | sen |
| buggy | 3.3 | `PWEG` | beg | 4.0 | word | buggy |
| gravy | 3.5 | `TKPWRAF` | graph | 4.0 | word | gravy |
| creepy | 4.0 | `KRAOUP` | croup | 2.2 | word | croup |
| ferry | 4.0 | `TPUR` | fur | 4.0 | word | ferry |
| needy | 3.6 | `TPHAOUD` | nude | 4.0 | word | needy |
| puppy | 4.0 | `PEP` | pep | 3.5 | word | pep |
| dusty | 3.7 | `TKETS` | debts | 4.0 | fold of debt | dusty |
| naughty | 3.8 | `TPHAET` | neat | 4.0 | word | naughty |
| berry | 4.0 | `PWUR` | burr | 3.4 | word | burr |
| hearsay | 3.0 | `HERS` | hers | 4.0 | fold of her | hearsay |
| curry | 4.0 | `KER` | ker | 2.8 | word | ker |
| spooky | 3.5 | `SPAOEUBG` | spike | 4.0 | word | spooky |
| sixty | 3.9 | `S-BGTS` | cystic | 3.1 | two-syllable | cystic |
| rowdy | 3.2 | `ROED` | rode | 3.9 | word | rowdy |
| starry | 3.1 | `STAEUR` | stare | 3.9 | word | starry |
| portray | 3.6 | `TPRAOEU` | fry | 3.9 | word | portray |
| muddy | 3.6 | `PHED` | med | 3.9 | word | muddy |
| shiny | 3.9 | `SHAOPB` | shannon | 3.8 | two-syllable | shannon |
| messy | 3.9 | `PHUS` | mus | 3.0 | fold of mu | mus |
| hardy | 3.9 | `HAEURD` | hydro | 3.6 | two-syllable | hydro |
| busty | 3.1 | `PWETS` | bets | 3.9 | fold of bet | busty |
| cowboy | 3.9 | `KUB` | cub | 3.5 | word | cub |
| levy | 3.9 | `HRUF` | lava | 3.7 | two-syllable | lava |
| merry | 3.8 | `PHUR` | emir | 3.0 | two-syllable | emir |
| pinky | 3.4 | `P*PBG` | picnic | 3.8 | two-syllable | pinky |
| dreary | 3.1 | `TKRAUR` | drawer | 3.8 | word | dreary |
| eighty | 3.8 | `A*UT` | auth | 3.0 | word | auth |
| bounty | 3.7 | `PWOEPBT` | bonnet | 3.4 | two-syllable | bonnet |
| pointy | 3.1 | `POPBT` | potent | 3.7 | two-syllable | pointy |
| classy | 3.7 | `KHRAEUS` | chaser | 3.1 | two-syllable | chaser |
| lefty | 3.2 | `HRUFT` | lust | 3.7 | word | lefty |
| poultry | 3.5 | `POERLT` | petrol | 3.7 | two-syllable | poultry |
| juicy | 3.7 | `SKWRAOES` | gees | 2.7 | fold of gee | gees |
| parsley | 3.3 | `PAERLS` | parcel | 3.7 | two-syllable | parsley |
| dowry | 3.1 | `TKOER` | decor | 3.6 | two-syllable | dowry |
| fuzzy | 3.6 | `TPEZ` | fez | 3.0 | word | fez |
| speedy | 3.6 | `SPAOUD` | spewed | 2.7 | fold of spew | spewed |
| cosy | 3.3 | `KOUZ` | cozy | 3.6 | two-syllable | cosy |
| cozy | 3.6 | `KOUZ` | cosy | 3.3 | two-syllable | cosy |
| paddy | 3.6 | `PAEUD` | payed | 3.0 | fold of pay | payed |
| alloy | 3.6 | `AOL` | aloe | 3.1 | two-syllable | aloe |
| shaky | 3.5 | `SHABG` | shack | 3.4 | word | shack |
| parry | 3.4 | `PAEUR` | pare | 2.8 | word | pare |
| creamy | 3.4 | `KRAUPL` | calmer | 3.0 | two-syllable | calmer |
| hubby | 3.4 | `HEB` | hobo | 3.1 | two-syllable | hobo |
| husky | 3.3 | `HEBGS` | hex | 3.3 | word | husky |
| rosy | 3.3 | `ROUZ` | rouse | 3.1 | word | rouse |
| smokey | 3.3 | `SPHOBG` | smock | 2.6 | word | smock |
| trusty | 3.0 | `TRETS` | tester | 3.2 | two-syllable | trusty |
| brownie | 3.2 | `PWROEPB` | boner | 3.2 | two-syllable | boner |
| breezy | 3.1 | `PWRAOUZ` | bruise | 3.2 | word | breezy |
| baggy | 3.2 | `PWAEUG` | baying | 2.3 | fold of bay | baying |
| murky | 3.1 | `PHERBG` | meshing | 2.4 | fold of mesh | meshing |
| pesky | 3.1 | `PUBGS` | pucks | 2.5 | fold of puck | pucks |
| feisty | 3.1 | `TPAOTS` | foots | 2.2 | fold of foot | foots |
| squeaky | 3.1 | `SKWAUBG` | squawk | 2.4 | word | squawk |
| fussy | 3.0 | `TPES` | fes | 2.7 | fold of fe | fes |
| jetty | 3.0 | `SKWRUT` | jut | 2.5 | word | jut |

### rest (546)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| tattoo | 4.1 | `TO` | to | 7.4 | word | tattoo |
| open | 5.5 | `OPB` | on | 6.9 | word | open |
| canine | 3.3 | `KAPB` | can | 6.5 | word | canine |
| cocoon | 3.1 | `KAPB` | can | 6.5 | word | cocoon |
| outta | 4.1 | `OUT` | out | 6.4 | word | outta |
| thorough | 4.0 | `TH-R` | there | 6.3 | word | thorough |
| anew | 3.2 | `TPHOU` | now | 6.2 | word | anew |
| uno | 3.3 | `TPHOU` | now | 6.2 | word | uno |
| amid | 4.0 | `PHAEUD` | made | 5.9 | word | amid |
| captain | 4.9 | `K-PBT` | can't | 5.8 | word | captain |
| cotton | 4.3 | `K-PBT` | can't | 5.8 | word | cotton |
| although | 5.3 | `THRAOE` | three | 5.8 | word | although |
| around | 5.8 | `ROUPBD` | round | 5.1 | word | round |
| baton | 3.6 | `TPWAOPB` | between | 5.8 | two-syllable | baton |
| between | 5.8 | `TPWAOPB` | baton | 3.6 | two-syllable | baton |
| census | 4.2 | `SEUPBS` | since | 5.8 | word | census |
| ridden | 3.6 | `RUPBD` | under | 5.7 | two-syllable | ridden |
| jose | 4.2 | `SHOE` | show | 5.7 | word | jose |
| petit | 3.3 | `PUT` | put | 5.7 | word | petit |
| police | 5.3 | `PHRAES` | please | 5.7 | word | police |
| amin | 3.2 | `PHOEPB` | money | 5.6 | two-syllable | amin |
| until | 5.6 | `TPHEL` | nel | 3.1 | word | nel |
| awoke | 3.1 | `WAOEBG` | week | 5.6 | word | awoke |
| simplest | 3.6 | `SUPLTS` | system | 5.6 | two-syllable | simplest |
| system | 5.6 | `SUPLTS` | simplest | 3.6 | two-syllable | simplest |
| pinto | 3.1 | `POEUPBT` | point | 5.5 | word | pinto |
| amen | 3.9 | `PHAEPB` | mean | 5.5 | word | amen |
| belief | 4.5 | `PWHRAOF` | believe | 5.5 | two-syllable | belief |
| believe | 5.5 | `PWHRAOF` | belief | 4.5 | two-syllable | belief |
| mono | 3.5 | `PHEPB` | men | 5.5 | word | mono |
| chorus | 3.9 | `KOURS` | course | 5.4 | word | chorus |
| finesse | 3.2 | `TPHUS` | news | 5.4 | fold of new | finesse |
| menace | 3.5 | `PHAEPBS` | means | 5.4 | fold of mean | menace |
| ago | 5.4 | `TKPWAO` | goo | 3.4 | word | goo |
| often | 5.4 | `OEFPB` | oven | 4.0 | two-syllable | oven |
| oven | 4.0 | `OEFPB` | often | 5.4 | two-syllable | oven |
| abide | 3.6 | `PWOEUD` | body | 5.4 | two-syllable | abide |
| data | 5.3 | `TKEUT` | duet | 3.4 | two-syllable | duet |
| duet | 3.4 | `TKEUT` | data | 5.3 | two-syllable | duet |
| agile | 3.4 | `AEUPBLG` | age | 5.3 | word | agile |
| image | 4.9 | `AEUPBLG` | age | 5.3 | word | image |
| among | 5.3 | `PHAOPBG` | mango | 3.6 | two-syllable | mango |
| mango | 3.6 | `PHAOPBG` | among | 5.3 | two-syllable | mango |
| himself | 5.2 | `SPHUFL` | myself | 5.3 | two-syllable | himself |
| myself | 5.3 | `SPHUFL` | himself | 5.2 | two-syllable | himself |
| fauna | 3.5 | `TPUPB` | fun | 5.3 | word | fauna |
| gonna | 5.3 | `TKPWAOPB` | goon | 3.1 | word | goon |
| perform | 4.7 | `TPRORPL` | former | 5.3 | two-syllable | perform |
| program | 5.3 | `PRAORPLG` | programme | 4.6 | two-syllable | programme |
| programme | 4.6 | `PRAORPLG` | program | 5.3 | two-syllable | programme |
| process | 5.3 | `PROES` | pros | 3.9 | fold of pro | pros |
| prowess | 3.4 | `PROES` | process | 5.3 | two-syllable | prowess |
| across | 5.2 | `KRAOS` | crisis | 4.6 | two-syllable | crisis |
| crisis | 4.6 | `KRAOS` | across | 5.2 | two-syllable | crisis |
| tuna | 3.6 | `TOUPB` | town | 5.2 | word | tuna |
| effort | 4.9 | `REFT` | rest | 5.2 | word | effort |
| issue | 5.2 | `ERB` | herb | 3.8 | word | herb |
| piston | 3.5 | `POEUPBTS` | points | 5.2 | fold of point | piston |
| typhoon | 3.6 | `TPAOEUPB` | fine | 5.2 | word | typhoon |
| cuddle | 3.4 | `KEULD` | killed | 5.2 | fold of kill | cuddle |
| centre | 5.0 | `SREPBT` | center | 5.2 | two-syllable | centre |
| content | 5.0 | `KOEPBT` | county | 5.2 | two-syllable | content |
| private | 5.2 | `PROEUFT` | profit | 4.7 | two-syllable | profit |
| profit | 4.7 | `PROEUFT` | private | 5.2 | two-syllable | profit |
| event | 5.2 | `SR-PBT` | resent | 3.4 | two-syllable | resent |
| resent | 3.4 | `SR-PBT` | event | 5.2 | two-syllable | resent |
| middle | 5.2 | `PHULD` | mulled | 2.5 | fold of mull | mulled |
| taxi | 4.2 | `TAEUBGS` | takes | 5.2 | fold of take | taxi |
| bio | 4.1 | `PWOEU` | boy | 5.2 | word | bio |
| alone | 5.2 | `HRAOEPB` | lien | 3.2 | word | lien |
| provide | 5.2 | `SPRAEUD` | sprayed | 3.5 | fold of spray | sprayed |
| follow | 5.1 | `TP-L` | filly | 3.1 | two-syllable | filly |
| amount | 5.1 | `PHAOUPBT` | mountain | 4.7 | two-syllable | mountain |
| mountain | 4.7 | `PHAOUPBT` | amount | 5.1 | two-syllable | mountain |
| derive | 3.5 | `TKRAOEUF` | drive | 5.1 | word | derive |
| upon | 5.1 | `POUPB` | pony | 3.8 | two-syllable | pony |
| harass | 3.3 | `HRAS` | release | 5.1 | two-syllable | harass |
| release | 5.1 | `HRAS` | las | 4.3 | fold of la | las |
| semi | 4.5 | `SUPL` | simple | 5.1 | two-syllable | semi |
| simple | 5.1 | `SUPL` | semi | 4.5 | two-syllable | semi |
| soma | 3.2 | `SAOEPL` | seem | 5.1 | word | soma |
| bullet | 4.2 | `PWEULT` | built | 5.1 | word | bullet |
| practice | 5.1 | `PRAEUBGTS` | practise | 3.5 | two-syllable | practise |
| practise | 3.5 | `PRAEUBGTS` | practice | 5.1 | two-syllable | practise |
| abyss | 3.4 | `PWAEUS` | base | 5.0 | word | abyss |
| standard | 5.0 | `STRAPBD` | strand | 3.7 | word | strand |
| album | 5.0 | `AUPBL` | annum | 3.2 | two-syllable | annum |
| annum | 3.2 | `AUPBL` | album | 5.0 | two-syllable | annum |
| austere | 3.0 | `STOER` | store | 5.0 | word | austere |
| create | 5.0 | `KRAUT` | kraut | 2.4 | word | kraut |
| ahead | 5.0 | `HED` | hed | 2.6 | fold of he | hed |
| allow | 5.0 | `HRAOU` | rely | 4.3 | two-syllable | rely |
| beta | 4.2 | `PWAET` | beat | 5.0 | word | beta |
| focus | 5.0 | `TPOEUBGS` | foxy | 3.0 | two-syllable | foxy |
| silo | 3.0 | `SAEUL` | sale | 5.0 | word | silo |
| solo | 4.6 | `SEL` | sell | 5.0 | word | solo |
| justice | 5.0 | `SKWRETS` | jets | 4.1 | fold of jet | jets |
| photo | 5.0 | `TPET` | fete | 2.7 | word | fete |
| sauna | 3.2 | `SUPB` | sun | 5.0 | word | sauna |
| begin | 4.8 | `TKPWUPB` | gun | 5.0 | word | begin |
| terrain | 3.9 | `TRA*EUPB` | train | 5.0 | word | terrain |
| jingle | 3.2 | `SKWRUPBLG` | judge | 5.0 | word | jingle |
| unit | 5.0 | `KWRAOEPBT` | unite | 3.9 | two-syllable | unite |
| unite | 3.9 | `KWRAOEPBT` | unit | 5.0 | two-syllable | unite |
| debris | 3.9 | `TKPWRAO` | degree | 4.9 | two-syllable | debris |
| degree | 4.9 | `TKPWRAO` | debris | 3.9 | two-syllable | debris |
| flora | 3.7 | `TPHRAOR` | floor | 4.9 | word | flora |
| stupid | 4.9 | `STAOEPD` | steeped | 3.1 | fold of steep | steeped |
| defence | 4.5 | `TKP-PBS` | defense | 4.9 | two-syllable | defence |
| defense | 4.9 | `TKP-PBS` | defence | 4.5 | two-syllable | defence |
| finish | 4.9 | `TP-RPB` | fission | 3.1 | two-syllable | fission |
| fission | 3.1 | `TP-RPB` | finish | 4.9 | two-syllable | fission |
| margin | 4.3 | `PHAEURPBLG` | marriage | 4.9 | two-syllable | margin |
| marriage | 4.9 | `PHAEURPBLG` | margin | 4.3 | two-syllable | margin |
| drunken | 3.7 | `TKR*EUPBG` | drink | 4.9 | word | drunken |
| basic | 4.9 | `PWABGS` | backs | 4.1 | word | backs |
| award | 4.9 | `W-RD` | where'd | 3.1 | word | where'd |
| hula | 3.0 | `HOUL` | holy | 4.9 | two-syllable | hula |
| lowest | 4.4 | `HROTS` | lots | 4.9 | word | lowest |
| avoid | 4.9 | `SRAOEUD` | cider | 3.5 | two-syllable | cider |
| mission | 4.9 | `PH-RPB` | moron | 3.8 | two-syllable | moron |
| moron | 3.8 | `PH-RPB` | mission | 4.9 | two-syllable | moron |
| petite | 3.4 | `TPAOT` | foot | 4.9 | word | petite |
| attempt | 4.8 | `TAEPLT` | template | 3.7 | two-syllable | template |
| template | 3.7 | `TAEPLT` | attempt | 4.8 | two-syllable | template |
| placid | 3.0 | `PHRAEUSD` | placed | 4.8 | fold of place | placid |
| engine | 4.8 | `UPBLG` | edgy | 3.4 | two-syllable | edgy |
| mantle | 3.6 | `PHAEPBLT` | mental | 4.8 | two-syllable | mantle |
| mental | 4.8 | `PHAEPBLT` | mantle | 3.6 | two-syllable | mantle |
| arose | 3.7 | `ROEZ` | rose | 4.8 | word | arose |
| mallet | 3.0 | `PHAELT` | metal | 4.8 | two-syllable | mallet |
| metal | 4.8 | `PHAELT` | mallet | 3.0 | two-syllable | mallet |
| zeta | 3.1 | `SAET` | seat | 4.8 | word | zeta |
| arise | 3.9 | `RAOEUZ` | rise | 4.8 | word | arise |
| basis | 4.8 | `PWAS` | bass | 4.3 | word | bass |
| proceed | 4.2 | `SPRAED` | spread | 4.8 | word | proceed |
| ascent | 3.4 | `SAEPBT` | senate | 4.8 | two-syllable | ascent |
| assent | 3.0 | `SAEPBT` | senate | 4.8 | two-syllable | assent |
| senate | 4.8 | `SAEPBT` | ascent | 3.4 | two-syllable | ascent |
| concept | 4.8 | `KOEPBTS` | contest | 4.5 | two-syllable | contest |
| contest | 4.5 | `KOEPBTS` | concept | 4.8 | two-syllable | contest |
| loco | 3.2 | `HREBG` | lucky | 4.8 | two-syllable | loco |
| ample | 3.7 | `AEPL` | apple | 4.8 | two-syllable | ample |
| apple | 4.8 | `AEPL` | ample | 3.7 | two-syllable | ample |
| emo | 3.2 | `AEPL` | apple | 4.8 | two-syllable | emo |
| request | 4.8 | `KWR-TS` | requests | 4.3 | two-syllable | requests |
| requests | 4.3 | `KWR-TS` | request | 4.8 | two-syllable | requests |
| unfit | 3.5 | `TPHET` | net | 4.8 | word | unfit |
| renew | 3.7 | `TPHRAOEU` | fly | 4.8 | word | renew |
| honest | 4.7 | `OEPBTS` | onset | 3.7 | two-syllable | onset |
| onset | 3.7 | `OEPBTS` | honest | 4.7 | two-syllable | onset |
| reduce | 4.7 | `TKRAOEUS` | dries | 3.2 | word | dries |
| solid | 4.7 | `SOEULD` | soiled | 3.0 | word | soiled |
| inflate | 3.0 | `TPHRAT` | flat | 4.7 | word | inflate |
| surreal | 3.6 | `SRAEL` | valley | 4.7 | two-syllable | surreal |
| approve | 4.1 | `PRAOF` | proof | 4.7 | word | approve |
| forest | 4.7 | `TPOERTS` | fortress | 3.8 | two-syllable | fortress |
| fortress | 3.8 | `TPOERTS` | forest | 4.7 | two-syllable | fortress |
| native | 4.7 | `TPHAFT` | nast | 2.8 | word | nast |
| presume | 3.6 | `SPRAOEUPL` | supreme | 4.7 | two-syllable | presume |
| supreme | 4.7 | `SPRAOEUPL` | presume | 3.6 | two-syllable | presume |
| email | 4.7 | `PHA*UL` | maul | 2.9 | word | maul |
| lament | 3.2 | `PHRAEPBT` | planet | 4.7 | two-syllable | lament |
| planet | 4.7 | `PHRAEPBT` | lament | 3.2 | two-syllable | lament |
| appeal | 4.7 | `PEL` | polo | 3.8 | two-syllable | polo |
| polo | 3.8 | `PEL` | appeal | 4.7 | two-syllable | polo |
| drama | 4.7 | `TKRUPL` | drum | 4.1 | word | drum |
| mistake | 4.7 | `STPHABG` | snack | 3.9 | word | snack |
| rewrite | 3.5 | `RAOUT` | route | 4.7 | word | rewrite |
| guinness | 3.5 | `TKPWUPBS` | guns | 4.7 | fold of gun | guinness |
| howell | 3.5 | `HOEL` | hole | 4.7 | word | howell |
| logo | 4.3 | `HREG` | leg | 4.7 | word | logo |
| zero | 4.6 | `SOER` | sore | 4.0 | word | sore |
| eighteen | 3.8 | `TAOPB` | tiny | 4.6 | two-syllable | eighteen |
| upset | 4.6 | `SPEUT` | spit | 3.9 | word | spit |
| candid | 3.5 | `KAEUPBD` | kinda | 4.6 | two-syllable | candid |
| kinda | 4.6 | `KAEUPBD` | candy | 4.3 | two-syllable | candy |
| motion | 4.6 | `PHOURPB` | mourn | 3.4 | word | mourn |
| remote | 4.4 | `PHROT` | plot | 4.6 | word | remote |
| sinus | 3.3 | `SAOEPBS` | scenes | 4.6 | word | sinus |
| metre | 3.8 | `PHRET` | promote | 4.6 | two-syllable | metre |
| promote | 4.6 | `PHRET` | metre | 3.8 | two-syllable | metre |
| premiere | 4.2 | `PHRAOR` | premier | 4.6 | two-syllable | premiere |
| revere | 3.1 | `SRAOR` | severe | 4.6 | two-syllable | revere |
| severe | 4.6 | `SRAOR` | revere | 3.1 | two-syllable | revere |
| vantage | 3.3 | `SR-PBLGT` | victim | 4.6 | two-syllable | vantage |
| victim | 4.6 | `SR-PBLGT` | vantage | 3.3 | two-syllable | vantage |
| arrest | 4.5 | `RETS` | rusty | 3.6 | two-syllable | rusty |
| errand | 3.0 | `AERPBD` | earned | 4.5 | fold of earn | errand |
| polite | 3.9 | `PHRAEUT` | plate | 4.5 | word | polite |
| relief | 4.5 | `HRAOF` | relieve | 3.8 | two-syllable | relieve |
| relieve | 3.8 | `HRAOF` | relief | 4.5 | two-syllable | relieve |
| demi | 3.4 | `TKUPL` | dumb | 4.5 | word | demi |
| manage | 4.5 | `PH-PBLG` | mimic | 3.5 | two-syllable | mimic |
| mimic | 3.5 | `PH-PBLG` | manage | 4.5 | two-syllable | mimic |
| multi | 4.5 | `PHELT` | melt | 4.0 | word | melt |
| bourbon | 3.6 | `PWURPB` | burn | 4.5 | word | bourbon |
| pasta | 3.9 | `PUTS` | puts | 4.5 | fold of put | pasta |
| sample | 4.5 | `SAEPL` | seam | 3.4 | word | seam |
| vowel | 3.3 | `SROEL` | solar | 4.5 | two-syllable | vowel |
| basal | 3.4 | `PWEULS` | bills | 4.5 | fold of bill | basal |
| survive | 4.5 | `SRAOEUF` | cipher | 3.1 | two-syllable | cipher |
| licence | 4.0 | `HRAOUPBS` | license | 4.5 | two-syllable | licence |
| license | 4.5 | `HRAOUPBS` | licence | 4.0 | two-syllable | licence |
| python | 3.8 | `PA*EUPBT` | paint | 4.5 | word | python |
| staple | 3.7 | `STAEPL` | steam | 4.5 | word | staple |
| campus | 4.5 | `KAUPLS` | capsule | 3.7 | two-syllable | capsule |
| capsule | 3.7 | `KAUPLS` | campus | 4.5 | two-syllable | capsule |
| contain | 4.5 | `TKPHAO*EUPB` | domain | 4.3 | two-syllable | domain |
| domain | 4.3 | `TKPHAO*EUPB` | contain | 4.5 | two-syllable | domain |
| evoke | 3.3 | `SROBG` | soccer | 4.5 | two-syllable | evoke |
| revoke | 3.3 | `SROBG` | soccer | 4.5 | two-syllable | revoke |
| defend | 4.5 | `TKP-PBD` | depend | 4.2 | two-syllable | depend |
| depend | 4.2 | `TKP-PBD` | defend | 4.5 | two-syllable | depend |
| mutton | 3.0 | `PHOUPBT` | mount | 4.5 | word | mutton |
| terrace | 3.9 | `TAERS` | tears | 4.5 | word | terrace |
| hustle | 3.7 | `HEULS` | hills | 4.5 | fold of hill | hustle |
| acid | 4.5 | `AEUSD` | aids | 4.2 | fold of aid | aids |
| angel | 4.5 | `AEPBLG` | angle | 4.4 | two-syllable | angle |
| angle | 4.4 | `AEPBLG` | angel | 4.5 | two-syllable | angle |
| offense | 4.3 | `TPOEPBS` | phones | 4.5 | word | offense |
| protest | 4.5 | `PROTS` | poster | 4.2 | two-syllable | poster |
| spoken | 4.5 | `SPOPBG` | sponge | 3.6 | word | sponge |
| chassis | 3.5 | `KHAEUS` | chase | 4.4 | word | chassis |
| muscle | 4.4 | `PHEULS` | mills | 4.2 | fold of mill | mills |
| resale | 3.3 | `ROELS` | roles | 4.4 | word | resale |
| shadow | 4.4 | `SHAOD` | shooed | 2.0 | fold of shoo | shooed |
| output | 4.4 | `OPT` | opt | 3.9 | word | opt |
| reveal | 4.4 | `SRAL` | val | 3.7 | word | val |
| alight | 3.0 | `HRO*EUT` | alright | 4.4 | two-syllable | alight |
| alright | 4.4 | `HRO*EUT` | alight | 3.0 | two-syllable | alight |
| assist | 4.4 | `SAEUTS` | sates | 2.1 | fold of sate | sates |
| racist | 4.4 | `RATS` | rats | 4.0 | fold of rat | rats |
| soulful | 3.1 | `SOFL` | solve | 4.4 | word | soulful |
| stomach | 4.4 | `STAOPBLG` | stoppage | 3.2 | two-syllable | stoppage |
| stoppage | 3.2 | `STAOPBLG` | stomach | 4.4 | two-syllable | stoppage |
| virus | 4.4 | `SRAOERS` | veers | 2.5 | fold of veer | veers |
| mama | 4.2 | `PHUPL` | mum | 4.4 | word | mama |
| mamma | 3.3 | `PHUPL` | mum | 4.4 | word | mamma |
| elite | 4.4 | `HRAOT` | loot | 3.5 | word | loot |
| lotta | 3.2 | `HRAOT` | elite | 4.4 | two-syllable | lotta |
| insane | 4.4 | `STPHAPB` | monsoon | 3.3 | two-syllable | monsoon |
| monsoon | 3.3 | `STPHAPB` | insane | 4.4 | two-syllable | monsoon |
| sudden | 4.4 | `SEUPBD` | sinned | 3.0 | fold of sin | sinned |
| fallen | 4.4 | `TPAEUPBL` | famine | 3.6 | two-syllable | famine |
| famine | 3.6 | `TPAEUPBL` | fallen | 4.4 | two-syllable | famine |
| inflict | 3.3 | `TPHR-BGT` | reflect | 4.4 | two-syllable | inflict |
| reflect | 4.4 | `TPHR-BGT` | inflict | 3.3 | two-syllable | inflict |
| resource | 4.4 | `RAERS` | rears | 2.6 | fold of rear | rears |
| arrive | 4.4 | `RAOEUF` | rife | 3.1 | word | rife |
| rapid | 4.4 | `RAEUPD` | raped | 4.1 | fold of rape | raped |
| regret | 4.4 | `TKPWR-T` | gritty | 3.4 | two-syllable | gritty |
| overt | 3.3 | `SROERT` | resort | 4.4 | two-syllable | overt |
| resort | 4.4 | `SROERT` | overt | 3.3 | two-syllable | overt |
| soda | 4.0 | `SAOED` | seed | 4.4 | word | soda |
| asleep | 4.4 | `SHROEP` | slope | 3.9 | word | slope |
| gorgeous | 4.3 | `TKPWOERPBLGS` | gorges | 2.8 | fold of gorge | gorges |
| ribbon | 3.8 | `ROEUPB` | robin | 4.3 | two-syllable | ribbon |
| robin | 4.3 | `ROEUPB` | ribbon | 3.8 | two-syllable | ribbon |
| forgave | 3.0 | `TKPWRAEUF` | grave | 4.3 | word | forgave |
| mandate | 4.0 | `PH-PBTD` | midnight | 4.3 | two-syllable | mandate |
| midnight | 4.3 | `PH-PBTD` | mandate | 4.0 | two-syllable | mandate |
| reside | 3.7 | `SRAOUD` | viewed | 4.3 | word | reside |
| retake | 3.3 | `ROEBGT` | rocket | 4.3 | two-syllable | retake |
| rocket | 4.3 | `ROEBGT` | retake | 3.3 | two-syllable | retake |
| trauma | 4.1 | `TRUPL` | triple | 4.3 | two-syllable | trauma |
| triple | 4.3 | `TRUPL` | trauma | 4.1 | two-syllable | trauma |
| regime | 4.3 | `SHRAOPL` | slimy | 3.1 | two-syllable | slimy |
| shalom | 3.0 | `SHRAOPL` | regime | 4.3 | two-syllable | shalom |
| relax | 4.3 | `HRAEBGS` | leaks | 3.9 | word | leaks |
| revenge | 4.3 | `SR-PBLG` | vigil | 3.2 | two-syllable | vigil |
| vigil | 3.2 | `SR-PBLG` | revenge | 4.3 | two-syllable | vigil |
| forgive | 4.2 | `TKPWREUF` | giver | 3.2 | two-syllable | giver |
| facet | 3.2 | `TPAETS` | fastest | 4.2 | two-syllable | facet |
| fastest | 4.2 | `TPAETS` | feats | 3.2 | fold of feat | feats |
| widen | 3.2 | `WAOUPBD` | wound | 4.2 | word | widen |
| asset | 4.2 | `AETS` | eats | 4.0 | fold of eat | eats |
| parrot | 3.5 | `PAORT` | portrait | 4.2 | two-syllable | parrot |
| portrait | 4.2 | `PAORT` | parrot | 3.5 | two-syllable | parrot |
| closest | 4.2 | `KHROTS` | clots | 3.0 | fold of clot | clots |
| malice | 3.3 | `PHAEULS` | males | 4.2 | word | malice |
| sunken | 3.3 | `S*EUPBG` | sink | 4.2 | word | sunken |
| delta | 4.1 | `TKAELT` | dealt | 4.2 | word | delta |
| villa | 4.2 | `SRAEUL` | vale | 3.6 | word | vale |
| cartel | 3.6 | `TKRAEL` | dealer | 4.2 | two-syllable | cartel |
| mason | 4.1 | `PHAOEUPBS` | mines | 4.2 | word | mason |
| refill | 3.2 | `RAOUFL` | rifle | 4.2 | two-syllable | refill |
| rifle | 4.2 | `RAOUFL` | refill | 3.2 | two-syllable | refill |
| vacate | 3.1 | `SREUBGT` | victor | 4.2 | two-syllable | vacate |
| barrel | 4.2 | `PWAERL` | barley | 3.5 | two-syllable | barley |
| evolve | 3.8 | `SROEFL` | resolve | 4.2 | two-syllable | evolve |
| resolve | 4.2 | `SROEFL` | evolve | 3.8 | two-syllable | evolve |
| revolve | 3.2 | `SROEFL` | resolve | 4.2 | two-syllable | revolve |
| parade | 4.2 | `PRAEUD` | prayed | 3.7 | fold of pray | prayed |
| racket | 3.3 | `RAEBGT` | react | 4.2 | two-syllable | racket |
| react | 4.2 | `RAEBGT` | racket | 3.3 | two-syllable | racket |
| retain | 4.2 | `TRA*UPB` | trainee | 3.4 | two-syllable | trainee |
| trainee | 3.4 | `TRA*UPB` | retain | 4.2 | two-syllable | trainee |
| teaspoon | 3.3 | `TAOEPBS` | teens | 4.2 | word | teaspoon |
| greatness | 3.7 | `TKPWRAPBTS` | grants | 4.2 | fold of grant | greatness |
| erode | 3.0 | `ROD` | rod | 4.1 | word | erode |
| akin | 3.7 | `KAEUPB` | cabin | 4.1 | two-syllable | akin |
| cabin | 4.1 | `KAEUPB` | cane | 3.8 | word | cane |
| arrow | 4.1 | `AOR` | oar | 2.9 | word | oar |
| precise | 4.1 | `SPRAOUS` | spruce | 3.4 | word | spruce |
| adore | 3.7 | `TKAOER` | deer | 4.1 | word | adore |
| jungle | 4.1 | `SKWREUPBLG` | ginger | 3.9 | two-syllable | ginger |
| saddle | 3.7 | `SAELD` | sealed | 4.1 | word | saddle |
| anon | 3.3 | `TPHAOPB` | noon | 4.1 | word | anon |
| arrange | 4.0 | `RAEUPBLG` | rage | 4.1 | word | arrange |
| cannon | 4.0 | `KAOPB` | canon | 4.1 | two-syllable | cannon |
| canon | 4.1 | `KAOPB` | cannon | 4.0 | two-syllable | cannon |
| apiece | 3.2 | `POES` | pose | 4.1 | word | apiece |
| peso | 3.0 | `POES` | pose | 4.1 | word | peso |
| payroll | 3.8 | `PAOEURL` | poorly | 4.1 | two-syllable | payroll |
| lawsuit | 4.1 | `HRATS` | lats | 2.3 | fold of lat | lats |
| scala | 3.0 | `SKUL` | skull | 4.1 | word | scala |
| basin | 4.1 | `PWAPBS` | bans | 3.7 | fold of ban | bans |
| gentile | 3.0 | `SKWRUPBLT` | gently | 4.1 | two-syllable | gentile |
| subtle | 4.1 | `SEULT` | silt | 3.0 | word | silt |
| boycott | 3.8 | `PWEUBGT` | bucket | 4.1 | two-syllable | boycott |
| bucket | 4.1 | `PWEUBGT` | boycott | 3.8 | two-syllable | boycott |
| ballot | 4.1 | `PWAOLT` | beetle | 3.5 | two-syllable | beetle |
| beetle | 3.5 | `PWAOLT` | ballot | 4.1 | two-syllable | beetle |
| demo | 4.1 | `TKOEPL` | dome | 3.9 | word | dome |
| fibre | 3.7 | `TPRAOEUB` | fiber | 4.1 | two-syllable | fibre |
| northeast | 4.1 | `TPHRAOETS` | fleets | 3.3 | fold of fleet | fleets |
| salvage | 3.6 | `S-FPBLG` | savage | 4.0 | two-syllable | salvage |
| savage | 4.0 | `S-FPBLG` | salvage | 3.6 | two-syllable | salvage |
| lineup | 4.0 | `HRAOUPB` | lunar | 3.8 | two-syllable | lunar |
| reggae | 3.5 | `RAEG` | eager | 4.0 | two-syllable | reggae |
| elect | 4.0 | `HR-BGT` | lockout | 3.1 | two-syllable | lockout |
| lockout | 3.1 | `HR-BGT` | elect | 4.0 | two-syllable | lockout |
| passive | 4.0 | `PAEUFS` | paves | 2.5 | fold of pave | paves |
| axis | 4.0 | `AEUBGS` | aches | 3.3 | word | aches |
| carol | 4.0 | `KAORL` | coral | 3.9 | two-syllable | coral |
| choral | 3.2 | `KAORL` | carol | 4.0 | two-syllable | choral |
| coral | 3.9 | `KAORL` | carol | 4.0 | two-syllable | coral |
| ashore | 3.5 | `SHAOER` | sheer | 4.0 | word | ashore |
| salmon | 4.0 | `SAOPBL` | semen | 3.4 | two-syllable | semen |
| semen | 3.4 | `SAOPBL` | salmon | 4.0 | two-syllable | semen |
| borough | 3.8 | `PW-R` | borrow | 4.0 | two-syllable | borough |
| borrow | 4.0 | `PW-R` | borough | 3.8 | two-syllable | borough |
| pharma | 3.4 | `TP-RPL` | firmly | 4.0 | two-syllable | pharma |
| inspire | 4.0 | `STPHAOR` | unsure | 3.8 | two-syllable | unsure |
| fossil | 4.0 | `TPOEULS` | foils | 2.8 | fold of foil | foils |
| hazard | 4.0 | `HRADZ` | lads | 3.9 | word | lads |
| reddish | 3.4 | `RURBD` | rushed | 4.0 | fold of rush | reddish |
| magna | 3.3 | `PH-PBG` | manga | 4.0 | two-syllable | magna |
| manga | 4.0 | `PH-PBG` | magna | 3.3 | two-syllable | magna |
| parole | 3.8 | `PROEL` | polar | 4.0 | two-syllable | parole |
| propel | 3.2 | `PROEL` | polar | 4.0 | two-syllable | propel |
| hollow | 4.0 | `H-L` | hilly | 3.2 | two-syllable | hilly |
| weaken | 3.5 | `WAPBG` | wang | 4.0 | word | weaken |
| appease | 3.2 | `PEZ` | oppose | 3.9 | two-syllable | appease |
| oppose | 3.9 | `PEZ` | appease | 3.2 | two-syllable | appease |
| deli | 3.4 | `TKUL` | dull | 3.9 | word | deli |
| idol | 3.9 | `AEULD` | aled | 2.3 | fold of ale | aled |
| towel | 3.9 | `TOEL` | tole | 2.0 | word | tole |
| acre | 3.9 | `RABG` | rack | 3.9 | word | rack |
| cystic | 3.1 | `S-BGTS` | sixty | 3.9 | two-syllable | cystic |
| applause | 3.8 | `PHRUZ` | plaza | 3.9 | two-syllable | applause |
| plaza | 3.9 | `PHRUZ` | applause | 3.8 | two-syllable | applause |
| brigade | 3.9 | `TKPWRAD` | grad | 3.6 | word | grad |
| hostess | 3.4 | `HOETS` | hottest | 3.9 | two-syllable | hostess |
| hottest | 3.9 | `HOETS` | hostess | 3.4 | two-syllable | hostess |
| awhile | 3.9 | `WOEUL` | willow | 3.7 | two-syllable | willow |
| willow | 3.7 | `WOEUL` | awhile | 3.9 | two-syllable | willow |
| fortnight | 3.4 | `TPOEURPBT` | fourteen | 3.9 | two-syllable | fortnight |
| fourteen | 3.9 | `TPOEURPBT` | fortnight | 3.4 | two-syllable | fortnight |
| shannon | 3.8 | `SHAOPB` | shiny | 3.9 | two-syllable | shannon |
| kitten | 3.6 | `KUPBT` | cunt | 3.9 | word | kitten |
| fulfil | 3.5 | `TPHREL` | fulfill | 3.9 | two-syllable | fulfil |
| fulfill | 3.9 | `TPHREL` | fulfil | 3.5 | two-syllable | fulfil |
| hydro | 3.6 | `HAEURD` | hardy | 3.9 | two-syllable | hydro |
| otis | 3.4 | `OUTS` | outs | 3.9 | word | otis |
| pickup | 3.9 | `PEPBG` | penning | 2.7 | fold of pen | penning |
| resin | 3.6 | `RUPBZ` | risen | 3.9 | two-syllable | resin |
| risen | 3.9 | `RUPBZ` | resin | 3.6 | two-syllable | resin |
| lava | 3.7 | `HRUF` | levy | 3.9 | two-syllable | lava |
| relive | 3.2 | `HRUF` | levy | 3.9 | two-syllable | relive |
| epoch | 3.2 | `OEPBG` | owning | 3.8 | fold of own | epoch |
| hola | 3.1 | `HAOEL` | heel | 3.8 | word | hola |
| primate | 3.1 | `PROEUPLT` | promptly | 3.8 | two-syllable | primate |
| teammate | 3.8 | `TEPLT` | tempt | 3.2 | word | tempt |
| emir | 3.0 | `PHUR` | merry | 3.8 | two-syllable | emir |
| slowest | 3.1 | `SHROTS` | slots | 3.8 | fold of slot | slowest |
| rascal | 3.1 | `R-BLGS` | reckless | 3.8 | two-syllable | rascal |
| reckless | 3.8 | `R-BLGS` | rascal | 3.1 | two-syllable | rascal |
| token | 3.8 | `TOPBG` | tong | 3.2 | word | tong |
| pancake | 3.4 | `P*PBG` | picnic | 3.8 | two-syllable | pancake |
| picnic | 3.8 | `P*PBG` | pinky | 3.4 | two-syllable | pinky |
| neuron | 3.2 | `TPHAURPB` | newborn | 3.8 | two-syllable | neuron |
| newborn | 3.8 | `TPHAURPB` | neuron | 3.2 | two-syllable | neuron |
| slightest | 3.8 | `SHRAO*UTS` | sleuths | 2.2 | fold of sleuth | sleuths |
| canoe | 3.5 | `TKPHOU` | dunno | 3.8 | two-syllable | canoe |
| dunno | 3.8 | `TKPHOU` | undo | 3.6 | two-syllable | undo |
| undo | 3.6 | `TKPHOU` | dunno | 3.8 | two-syllable | undo |
| duchess | 3.8 | `TKEUFPS` | ditches | 3.1 | fold of ditch | ditches |
| pagan | 3.6 | `PEUPBG` | ping | 3.8 | word | pagan |
| recap | 3.5 | `ROEPBG` | reckon | 3.8 | two-syllable | recap |
| reckon | 3.8 | `ROEPBG` | recap | 3.5 | two-syllable | recap |
| bonnet | 3.4 | `PWOEPBT` | bounty | 3.7 | two-syllable | bonnet |
| cola | 3.7 | `KAOEL` | keel | 3.2 | word | keel |
| palette | 3.7 | `PAELT` | pallet | 3.1 | two-syllable | pallet |
| pallet | 3.1 | `PAELT` | palette | 3.7 | two-syllable | pallet |
| petal | 3.0 | `PAELT` | palette | 3.7 | two-syllable | petal |
| potent | 3.7 | `POPBT` | pointy | 3.1 | two-syllable | pointy |
| tempo | 3.7 | `TOEPL` | tome | 3.2 | word | tome |
| chili | 3.7 | `KH-L` | chilly | 3.5 | two-syllable | chilly |
| kilo | 3.1 | `KOEUL` | coil | 3.7 | word | kilo |
| averse | 3.1 | `SRAERS` | versa | 3.7 | two-syllable | averse |
| versa | 3.7 | `SRAERS` | averse | 3.1 | two-syllable | averse |
| petrol | 3.7 | `POERLT` | poultry | 3.5 | two-syllable | poultry |
| appoint | 3.7 | `PAOEUPBT` | pint | 3.7 | word | pint |
| peacock | 3.4 | `PAOEBG` | peek | 3.7 | word | peacock |
| tidal | 3.7 | `TOEULD` | toiled | 2.6 | fold of toil | toiled |
| helpless | 3.7 | `H-PLS` | hummus | 3.0 | two-syllable | hummus |
| hummus | 3.0 | `H-PLS` | helpless | 3.7 | two-syllable | hummus |
| taco | 3.7 | `TAOUBG` | tubing | 3.3 | fold of tube | tubing |
| opaque | 3.4 | `PAOEUBG` | pike | 3.7 | word | opaque |
| mellow | 3.3 | `PHOEL` | mole | 3.7 | word | mellow |
| parcel | 3.7 | `PAERLS` | pearls | 3.5 | word | pearls |
| erase | 3.7 | `RAUS` | raws | 2.0 | fold of raw | raws |
| reddit | 3.7 | `RUTD` | rutted | 2.0 | fold of rut | rutted |
| lupus | 3.2 | `HRAOPS` | loops | 3.6 | word | lupus |
| nexus | 3.6 | `TPHEUBGS` | nicks | 3.1 | fold of nick | nicks |
| camel | 3.6 | `KAEPL` | calmly | 3.6 | two-syllable | calmly |
| chemo | 3.2 | `KAEPL` | camel | 3.6 | two-syllable | chemo |
| coco | 3.5 | `KEBG` | cocoa | 3.6 | two-syllable | coco |
| cocoa | 3.6 | `KEBG` | coco | 3.5 | two-syllable | coco |
| paddle | 3.5 | `PAELD` | pedal | 3.6 | two-syllable | paddle |
| pedal | 3.6 | `PAELD` | paddle | 3.5 | two-syllable | paddle |
| align | 3.6 | `HROEUPB` | loin | 2.8 | word | loin |
| leftist | 3.6 | `HRUFTS` | lusts | 2.5 | fold of lust | lusts |
| lizard | 3.6 | `HREUDZ` | lids | 3.1 | word | lids |
| ripple | 3.4 | `RUPL` | rum | 3.6 | word | ripple |
| ado | 3.2 | `TKAO` | doo | 3.6 | word | ado |
| brittle | 3.3 | `PWRULT` | rebuilt | 3.6 | two-syllable | brittle |
| rebuilt | 3.6 | `PWRULT` | brittle | 3.3 | two-syllable | brittle |
| aloe | 3.1 | `AOL` | alloy | 3.6 | two-syllable | aloe |
| motel | 3.6 | `TPHOEL` | knoll | 2.9 | word | knoll |
| pseudo | 3.6 | `SAUD` | sawed | 2.8 | word | sawed |
| unrest | 3.6 | `TPHREUTS` | flits | 2.0 | fold of flit | flits |
| mundo | 3.0 | `PHOUPBD` | mound | 3.6 | word | mundo |
| rapport | 3.2 | `PRAOR` | poorer | 3.6 | two-syllable | rapport |
| upheld | 3.6 | `PHEULD` | milled | 2.9 | fold of mill | milled |
| citrus | 3.6 | `SERTS` | certs | 2.2 | fold of cert | certs |
| cushion | 3.6 | `KERPB` | kern | 3.2 | word | kern |
| dictate | 3.6 | `TKAT` | dat | 3.5 | word | dat |
| lima | 3.6 | `HROEUPL` | limo | 3.3 | two-syllable | limo |
| limo | 3.3 | `HROEUPL` | lima | 3.6 | two-syllable | limo |
| seaside | 3.6 | `SAUSD` | sauced | 2.0 | fold of sauce | sauced |
| crescent | 3.6 | `KR-PBTS` | kristin | 3.3 | two-syllable | kristin |
| kristin | 3.3 | `KR-PBTS` | crescent | 3.6 | two-syllable | kristin |
| hassle | 3.6 | `HAELS` | heals | 3.3 | word | heals |
| offend | 3.6 | `TPOEPBD` | phoned | 3.3 | word | phoned |
| lettuce | 3.6 | `HREUTS` | lites | 2.2 | fold of lit | lites |
| sumo | 3.1 | `SAUPL` | psalm | 3.6 | word | sumo |
| amend | 3.5 | `PHAEPBD` | madden | 3.5 | two-syllable | madden |
| madden | 3.5 | `PHAEPBD` | amend | 3.5 | two-syllable | madden |
| bowel | 3.5 | `PWOEL` | bole | 2.4 | word | bole |
| casa | 3.5 | `KUS` | cuss | 2.8 | word | cuss |
| junta | 3.3 | `HAUPBT` | haunt | 3.5 | word | junta |
| marrow | 3.5 | `PHAOR` | moor | 3.4 | word | moor |
| repo | 3.1 | `RAEP` | reap | 3.5 | word | repo |
| suspense | 3.5 | `SPEUPBS` | spins | 3.5 | word | spins |
| nicest | 3.5 | `TPHAOUTS` | newts | 2.2 | fold of newt | newts |
| stricken | 3.5 | `STRUPBG` | strung | 3.3 | word | strung |
| uphold | 3.5 | `PHOULD` | mould | 3.5 | word | mould |
| aloud | 3.5 | `HRAOUD` | lewd | 3.2 | word | lewd |
| bridal | 3.5 | `PWROEULD` | broiled | 2.4 | fold of broil | broiled |
| faggot | 3.5 | `TPAOGT` | footing | 3.5 | fold of foot | faggot |
| nuisance | 3.5 | `TPHOUPBS` | nouns | 3.3 | word | nouns |
| axel | 3.3 | `AEBLGS` | axle | 3.5 | two-syllable | axel |
| axle | 3.5 | `AEBLGS` | axel | 3.3 | two-syllable | axel |
| bitten | 3.5 | `PWUPBT` | bunt | 2.8 | word | bunt |
| lingo | 3.0 | `HROEUPBG` | login | 3.5 | two-syllable | lingo |
| login | 3.5 | `HROEUPBG` | lingo | 3.0 | two-syllable | lingo |
| techno | 3.5 | `TOEPBG` | toning | 2.8 | fold of tone | toning |
| tycoon | 3.2 | `TKAOEUPB` | dine | 3.5 | word | tycoon |
| tendon | 3.3 | `TOEPBD` | toned | 3.5 | word | tendon |
| cascade | 3.5 | `SKEUD` | skid | 3.3 | word | skid |
| paso | 3.5 | `PAOS` | poos | 2.1 | fold of poo | poos |
| penal | 3.5 | `POEPBL` | pollen | 3.5 | two-syllable | penal |
| pollen | 3.5 | `POEPBL` | penal | 3.5 | two-syllable | penal |
| ketchup | 3.5 | `KEUFP` | kitsch | 2.7 | word | kitsch |
| astute | 3.1 | `STOUT` | stout | 3.5 | word | astute |
| marquee | 3.2 | `KPHROE` | marquis | 3.5 | two-syllable | marquee |
| marquis | 3.5 | `KPHROE` | marquee | 3.2 | two-syllable | marquee |
| nugget | 3.2 | `TPHEUGT` | knitting | 3.5 | fold of knit | nugget |
| oblique | 3.2 | `PWHRAEBG` | bleak | 3.4 | word | oblique |
| sulfur | 3.4 | `SRUFL` | sulphur | 3.2 | two-syllable | sulphur |
| sulphur | 3.2 | `SRUFL` | sulfur | 3.4 | two-syllable | sulphur |
| dearest | 3.4 | `TKARTS` | darts | 3.4 | fold of dart | darts |
| medic | 3.4 | `PHUBGD` | mucked | 2.1 | fold of muck | mucked |
| noodle | 3.4 | `TPHAOELD` | kneeled | 2.3 | fold of kneel | kneeled |
| hobo | 3.1 | `HEB` | hubby | 3.4 | two-syllable | hobo |
| leakage | 3.2 | `HREPBLG` | ledge | 3.4 | word | leakage |
| moustache | 3.2 | `PHAURBTS` | mustache | 3.4 | two-syllable | moustache |
| mustache | 3.4 | `PHAURBTS` | moustache | 3.2 | two-syllable | moustache |
| payload | 3.4 | `PAOEULD` | piled | 3.4 | word | payload |
| reprint | 3.4 | `PRUPBT` | punter | 3.0 | two-syllable | punter |
| roulette | 3.4 | `HROET` | loath | 2.7 | word | loath |
| vanish | 3.4 | `SRAEURPB` | varnish | 3.0 | two-syllable | varnish |
| varnish | 3.0 | `SRAEURPB` | vanish | 3.4 | two-syllable | varnish |
| barrow | 3.4 | `PWAOR` | boor | 2.1 | word | boor |
| loosen | 3.4 | `HRAOEPBS` | liens | 2.7 | fold of lien | liens |
| feudal | 3.4 | `TKPWROULD` | growled | 2.6 | word | growled |
| lattice | 3.4 | `HRAEUTS` | lates | 2.2 | fold of late | lates |
| relapse | 3.3 | `HRAEPS` | leaps | 3.4 | fold of leap | relapse |
| topless | 3.4 | `TOEPLS` | tomes | 2.6 | fold of tome | tomes |
| palate | 3.4 | `P-LT` | pellet | 3.1 | two-syllable | pellet |
| pellet | 3.1 | `P-LT` | palate | 3.4 | two-syllable | pellet |
| poodle | 3.0 | `PAOELD` | peeled | 3.3 | word | poodle |
| ethos | 3.3 | `A*ETS` | eighths | 2.5 | fold of eighth | eighths |
| carton | 3.2 | `KAORPBT` | contra | 3.3 | two-syllable | carton |
| contra | 3.3 | `KAORPBT` | carton | 3.2 | two-syllable | carton |
| donut | 3.3 | `TKOEUPBT` | doughnut | 3.2 | two-syllable | doughnut |
| doughnut | 3.2 | `TKOEUPBT` | donut | 3.3 | two-syllable | doughnut |
| dugout | 3.0 | `TKOUGT` | doubting | 3.3 | fold of doubt | dugout |
| horrid | 3.3 | `HOEURD` | hydra | 3.3 | two-syllable | horrid |
| hydra | 3.3 | `HOEURD` | horrid | 3.3 | two-syllable | horrid |
| prepaid | 3.3 | `PRA*UD` | repaid | 3.3 | two-syllable | repaid |
| repaid | 3.3 | `PRA*UD` | prepaid | 3.3 | two-syllable | repaid |
| lipid | 3.3 | `HRAOPD` | looped | 3.0 | fold of loop | looped |
| furnish | 3.1 | `TPERPB` | fern | 3.3 | word | furnish |
| bison | 3.3 | `PWAEUPBS` | banes | 2.1 | fold of bane | banes |
| attest | 3.2 | `TAETS` | teats | 2.3 | fold of teat | teats |
| emit | 3.2 | `PHUT` | mutt | 2.9 | word | mutt |
| judo | 3.2 | `SKWRAUD` | jawed | 2.5 | fold of jaw | jawed |
| nitrate | 3.2 | `TPHOEURT` | nitro | 3.2 | two-syllable | nitro |
| nitro | 3.2 | `TPHOEURT` | nitrate | 3.2 | two-syllable | nitro |
| taurus | 3.2 | `TARS` | tars | 2.5 | fold of tar | tars |
| spectre | 3.2 | `SPREBGT` | specter | 3.1 | two-syllable | specter |
| cripple | 3.2 | `KRUPL` | crumb | 3.0 | word | crumb |
| propane | 3.2 | `PRAOEPB` | preen | 2.2 | word | preen |
| bagel | 3.2 | `PWAULG` | balling | 2.8 | fold of ball | balling |
| faucet | 3.1 | `TPAEUTS` | fates | 3.2 | fold of fate | faucet |
| tulip | 3.2 | `TAOEPL` | teem | 2.1 | word | teem |
| abode | 3.1 | `PWAOED` | bede | 2.7 | word | bede |
| dada | 3.1 | `TKUD` | dud | 3.0 | word | dud |
| rupee | 3.0 | `PRAOEU` | pry | 3.1 | word | rupee |
| callous | 3.1 | `KAOLS` | cools | 3.0 | word | cools |
| regroup | 3.1 | `TKPWRAOEUP` | gripe | 2.8 | word | gripe |
| sepsis | 3.0 | `SUPS` | supes | 2.0 | fold of sup | supes |

## Conflicts: by stress, -er R on the left, * on every word, # too when short

One row per two-syllable word that shares its stroke. **rival** is the most frequent other claimant.


### -er (115)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| other | 6.2 | `#RO*T` | otter | 3.4 | two-syllable | otter |
| otter | 3.4 | `#RO*T` | other | 6.2 | two-syllable | otter |
| over | 6.1 | `#RO*EF` | rover | 3.8 | two-syllable | rover |
| rover | 3.8 | `#RO*EF` | over | 6.1 | two-syllable | rover |
| never | 5.9 | `#TPHR*EF` | fluffy | 3.6 | two-syllable | fluffy |
| under | 5.7 | `#R*UPBD` | ridden | 3.6 | two-syllable | ridden |
| hater | 3.1 | `#HRA*EUT` | later | 5.5 | two-syllable | hater |
| later | 5.5 | `#HRA*EUT` | hater | 3.1 | two-syllable | hater |
| former | 5.3 | `TPRO*RPL` | perform | 4.7 | two-syllable | perform |
| mother | 5.2 | `#PHRO*T` | remote | 4.4 | two-syllable | remote |
| center | 5.2 | `SR*EPBT` | centre | 5.0 | two-syllable | centre |
| rounder | 3.3 | `RO*UPBD` | 'round | 5.1 | word | rounder |
| linger | 3.3 | `HR*EUPBG` | link | 5.0 | word | linger |
| header | 3.9 | `#HRA*ED` | leader | 5.0 | two-syllable | header |
| leader | 5.0 | `#HRA*ED` | header | 3.9 | two-syllable | header |
| ladder | 4.0 | `#HRA*D` | lady | 5.0 | two-syllable | ladder |
| master | 4.9 | `PHRA*TS` | plaster | 3.5 | two-syllable | plaster |
| plaster | 3.5 | `PHRA*TS` | master | 4.9 | two-syllable | plaster |
| collar | 4.0 | `#KRO*L` | color | 4.9 | two-syllable | collar |
| color | 4.9 | `#KRO*L` | colour | 4.5 | two-syllable | colour |
| colour | 4.5 | `#KRO*L` | color | 4.9 | two-syllable | colour |
| peter | 4.9 | `#PRAO*ET` | upright | 3.7 | two-syllable | upright |
| cider | 3.5 | `#SRAO*EUD` | avoid | 4.9 | two-syllable | cider |
| labor | 4.8 | `#HRA*EUB` | labour | 4.7 | two-syllable | labour |
| labour | 4.7 | `#HRA*EUB` | labor | 4.8 | two-syllable | labour |
| honor | 4.7 | `#RO*PB` | honour | 4.4 | two-syllable | honour |
| honour | 4.4 | `#RO*PB` | honor | 4.7 | two-syllable | honour |
| sector | 4.7 | `SR*EBGT` | vector | 4.0 | two-syllable | vector |
| vector | 4.0 | `SR*EBGT` | sector | 4.7 | two-syllable | vector |
| miner | 3.6 | `#PHRAO*EUPB` | minor | 4.6 | two-syllable | miner |
| minor | 4.6 | `#PHRAO*EUPB` | miner | 3.6 | two-syllable | miner |
| favor | 4.6 | `#TPRA*EUF` | favour | 4.3 | two-syllable | favour |
| favour | 4.3 | `#TPRA*EUF` | favor | 4.6 | two-syllable | favour |
| panther | 3.6 | `PRA*PBT` | pattern | 4.6 | two-syllable | panther |
| premier | 4.6 | `PHRAO*R` | premiere | 4.2 | two-syllable | premiere |
| ester | 3.1 | `#R*ETS` | arrest | 4.5 | two-syllable | ester |
| manner | 4.5 | `#PHRA*PB` | manor | 3.8 | two-syllable | manor |
| manor | 3.8 | `#PHRA*PB` | manner | 4.5 | two-syllable | manor |
| solar | 4.5 | `#SRO*EL` | vowel | 3.3 | two-syllable | vowel |
| soccer | 4.5 | `#SRO*BG` | evoke | 3.3 | two-syllable | evoke |
| poster | 4.2 | `PRO*TS` | protest | 4.5 | two-syllable | poster |
| planar | 3.1 | `PHRA*EUPB` | plain | 4.4 | word | planar |
| valor | 3.2 | `#SRA*L` | reveal | 4.4 | two-syllable | valor |
| anchor | 4.0 | `#RA*PBG` | anger | 4.4 | two-syllable | anchor |
| anger | 4.4 | `#RA*PBG` | anchor | 4.0 | two-syllable | anchor |
| heater | 3.5 | `#HRA*ET` | leather | 4.3 | two-syllable | heater |
| heather | 3.9 | `#HRA*ET` | leather | 4.3 | two-syllable | heather |
| leather | 4.3 | `#HRA*ET` | heather | 3.9 | two-syllable | heather |
| hover | 3.3 | `#HRO*F` | lover | 4.3 | two-syllable | hover |
| lover | 4.3 | `#HRO*F` | hover | 3.3 | two-syllable | hover |
| sailor | 3.8 | `#SRA*EUL` | villa | 4.2 | two-syllable | sailor |
| humor | 4.2 | `KWHRAOUPL` | humour | 3.8 | two-syllable | humour |
| humour | 3.8 | `KWHRAOUPL` | humor | 4.2 | two-syllable | humour |
| grocer | 3.0 | `TKPWRO*ES` | grows | 4.2 | fold of grow | grocer |
| harbor | 4.2 | `HRA*RB` | harbour | 4.0 | two-syllable | harbour |
| harbour | 4.0 | `HRA*RB` | harbor | 4.2 | two-syllable | harbour |
| harsher | 3.1 | `HRA*RB` | harbor | 4.2 | two-syllable | harsher |
| ginger | 3.9 | `SKWR*EUPBLG` | jungle | 4.1 | two-syllable | ginger |
| neighbor | 4.1 | `#TPHRA*EB` | neighbour | 3.7 | two-syllable | neighbour |
| neighbour | 3.7 | `#TPHRA*EB` | neighbor | 4.1 | two-syllable | neighbour |
| safer | 4.1 | `#SRA*EUF` | saver | 3.3 | two-syllable | saver |
| saver | 3.3 | `#SRA*EUF` | safer | 4.1 | two-syllable | saver |
| fiber | 4.1 | `#TPRAO*EUB` | fibre | 3.7 | two-syllable | fibre |
| altar | 3.8 | `#RA*ULT` | alter | 4.0 | two-syllable | altar |
| alter | 4.0 | `#RA*ULT` | altar | 3.8 | two-syllable | altar |
| hamper | 3.3 | `HRA*PL` | lamp | 4.0 | word | hamper |
| lunar | 3.8 | `#HRAO*UPB` | lineup | 4.0 | two-syllable | lunar |
| eager | 4.0 | `#RA*EG` | reggae | 3.5 | two-syllable | reggae |
| cellar | 3.6 | `#SR*EL` | seller | 4.0 | two-syllable | cellar |
| seller | 4.0 | `#SR*EL` | cellar | 3.6 | two-syllable | cellar |
| armor | 4.0 | `#RA*RPL` | armour | 3.8 | two-syllable | armour |
| armour | 3.8 | `#RA*RPL` | armor | 4.0 | two-syllable | armour |
| polar | 4.0 | `#PRO*EL` | parole | 3.8 | two-syllable | parole |
| censor | 3.4 | `SR*EPBS` | sensor | 3.9 | two-syllable | censor |
| sensor | 3.9 | `SR*EPBS` | censor | 3.4 | two-syllable | censor |
| boulder | 3.7 | `PWRO*ULD` | broadly | 3.8 | two-syllable | boulder |
| temper | 3.8 | `TR*EPL` | tremor | 3.0 | two-syllable | tremor |
| tremor | 3.0 | `TR*EPL` | temper | 3.8 | two-syllable | tremor |
| sender | 3.2 | `SR*EPBD` | vendor | 3.8 | two-syllable | sender |
| vendor | 3.8 | `SR*EPBD` | sender | 3.2 | two-syllable | sender |
| banger | 3.0 | `PWRA*PBG` | banker | 3.8 | two-syllable | banger |
| banker | 3.8 | `PWRA*PBG` | banger | 3.0 | two-syllable | banger |
| joker | 3.7 | `SKWRO*EBG` | jockey | 3.6 | two-syllable | jockey |
| rumor | 3.7 | `#RAO*UPL` | rumour | 3.4 | two-syllable | rumour |
| rumour | 3.4 | `#RAO*UPL` | rumor | 3.7 | two-syllable | rumour |
| chaser | 3.1 | `KHRA*EUS` | classy | 3.7 | two-syllable | chaser |
| hitter | 3.5 | `#HR*EUT` | litter | 3.7 | two-syllable | hitter |
| litter | 3.7 | `#HR*EUT` | hitter | 3.5 | two-syllable | hitter |
| freezer | 3.7 | `TPRAO*EZ` | frieze | 2.8 | word | frieze |
| decor | 3.6 | `#TKO*ER` | dowry | 3.1 | two-syllable | dowry |
| blocker | 3.2 | `PWHRO*BG` | bloc | 3.6 | word | blocker |
| poorer | 3.6 | `#PRAO*R` | rapport | 3.2 | two-syllable | rapport |
| ether | 3.4 | `#RAO*ET` | rotate | 3.6 | two-syllable | ether |
| plumber | 3.4 | `PHR*UPL` | plum | 3.5 | word | plumber |
| diver | 3.4 | `#TKRAO*EUF` | derive | 3.5 | two-syllable | diver |
| router | 3.5 | `#RAO*UT` | rewrite | 3.5 | two-syllable | rewrite |
| arbor | 3.5 | `#RA*RB` | azure | 3.4 | two-syllable | azure |
| azure | 3.4 | `#RA*RB` | arbor | 3.5 | two-syllable | azure |
| ledger | 3.5 | `#HR*EPBLG` | leakage | 3.2 | two-syllable | leakage |
| odor | 3.5 | `#RO*ED` | rowdy | 3.2 | two-syllable | rowdy |
| calmer | 3.0 | `#KRA*UPL` | creamy | 3.4 | two-syllable | calmer |
| punter | 3.0 | `PR*UPBT` | reprint | 3.4 | two-syllable | punter |
| steamer | 3.4 | `STRA*EPL` | streamer | 3.0 | two-syllable | streamer |
| streamer | 3.0 | `STRA*EPL` | steamer | 3.4 | two-syllable | streamer |
| parlor | 3.3 | `PRA*RL` | parlour | 3.1 | two-syllable | parlour |
| parlour | 3.1 | `PRA*RL` | parlor | 3.3 | two-syllable | parlour |
| hangar | 3.3 | `#HRA*PBG` | hanger | 3.2 | two-syllable | hanger |
| hanger | 3.2 | `#HRA*PBG` | hangar | 3.3 | two-syllable | hanger |
| flatter | 3.3 | `TPHRA*T` | inflate | 3.0 | two-syllable | inflate |
| camper | 3.3 | `KRA*PL` | cramp | 3.0 | word | cramp |
| specter | 3.1 | `SPR*EBGT` | spectre | 3.2 | two-syllable | specter |
| tester | 3.2 | `TR*ETS` | trusty | 3.0 | two-syllable | trusty |
| boner | 3.2 | `PWRO*EPB` | brownie | 3.2 | two-syllable | brownie |
| caster | 3.1 | `KRA*TS` | castor | 3.2 | two-syllable | caster |
| castor | 3.2 | `KRA*TS` | caster | 3.1 | two-syllable | caster |

### -ly (15)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| filly | 3.1 | `#TP*L` | follow | 5.1 | two-syllable | filly |
| rely | 4.3 | `#HRAO*U` | allow | 5.0 | two-syllable | rely |
| holy | 4.9 | `#HO*UL` | hula | 3.0 | two-syllable | hula |
| sully | 3.1 | `#S*EL` | solo | 4.6 | two-syllable | sully |
| poorly | 4.1 | `#PAO*EURL` | payroll | 3.8 | two-syllable | payroll |
| gently | 4.1 | `SKWRUPBLT` | gentile | 3.0 | two-syllable | gentile |
| frankly | 4.0 | `TPRA*EUPBLG` | fragile | 3.9 | two-syllable | fragile |
| sally | 4.0 | `#SA*EUL` | silo | 3.0 | two-syllable | silo |
| hilly | 3.2 | `#H*L` | hollow | 4.0 | two-syllable | hilly |
| holly | 3.9 | `#HO*EUL` | wholly | 3.8 | two-syllable | wholly |
| wholly | 3.8 | `#HO*EUL` | holly | 3.9 | two-syllable | wholly |
| promptly | 3.8 | `PRO*EUPLT` | primate | 3.1 | two-syllable | primate |
| broadly | 3.8 | `PWRO*ULD` | boulder | 3.7 | two-syllable | boulder |
| chilly | 3.5 | `#KH*L` | chili | 3.7 | two-syllable | chilly |
| calmly | 3.6 | `#KA*EPL` | camel | 3.6 | two-syllable | calmly |

### -y (47)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| annie | 3.9 | `#A*EUPB` | any | 6.1 | two-syllable | annie |
| any | 6.1 | `#A*EUPB` | annie | 3.9 | two-syllable | annie |
| fluffy | 3.6 | `#TPHR*EF` | never | 5.9 | two-syllable | fluffy |
| money | 5.6 | `#PHO*EPB` | amin | 3.2 | two-syllable | amin |
| envoy | 3.5 | `OEFPB` | often | 5.4 | two-syllable | envoy |
| body | 5.4 | `#PWO*EUD` | abide | 3.6 | two-syllable | abide |
| county | 5.2 | `KO*EPBT` | content | 5.0 | two-syllable | content |
| pony | 3.8 | `#PO*UPB` | upon | 5.1 | two-syllable | pony |
| lacy | 3.2 | `#HRA*S` | release | 5.1 | two-syllable | lacy |
| foxy | 3.0 | `#TPO*EUBGS` | focus | 5.0 | two-syllable | foxy |
| lady | 5.0 | `#HRA*D` | ladder | 4.0 | two-syllable | ladder |
| edgy | 3.4 | `#*UPBLG` | engine | 4.8 | two-syllable | edgy |
| lucky | 4.8 | `#HR*EBG` | loco | 3.2 | two-syllable | loco |
| boogie | 3.5 | `PWAO*EUG` | buying | 4.8 | fold of buy | boogie |
| valley | 4.7 | `#SRA*EL` | surreal | 3.6 | two-syllable | surreal |
| tiny | 4.6 | `#TAO*PB` | eighteen | 3.8 | two-syllable | eighteen |
| candy | 4.3 | `KA*EUPBD` | kinda | 4.6 | two-syllable | candy |
| rusty | 3.6 | `#R*ETS` | arrest | 4.5 | two-syllable | rusty |
| volley | 3.2 | `#SRO*EL` | solar | 4.5 | two-syllable | volley |
| slimy | 3.1 | `#SHRAO*PL` | regime | 4.3 | two-syllable | slimy |
| granny | 3.6 | `TKPWRA*EUPB` | grain | 4.2 | word | granny |
| barley | 3.5 | `#PWA*ERL` | barrel | 4.2 | two-syllable | barley |
| petty | 4.1 | `#P*UT` | petit | 3.3 | two-syllable | petit |
| midday | 3.4 | `#PHA*EUD` | amid | 4.0 | two-syllable | midday |
| sixty | 3.9 | `S*BGTS` | cystic | 3.1 | two-syllable | cystic |
| shiny | 3.9 | `#SHAO*PB` | shannon | 3.8 | two-syllable | shannon |
| hardy | 3.9 | `#HA*EURD` | hydro | 3.6 | two-syllable | hydro |
| levy | 3.9 | `#HR*UF` | lava | 3.7 | two-syllable | lava |
| merry | 3.8 | `#PH*UR` | emir | 3.0 | two-syllable | emir |
| standby | 3.4 | `STA*EUPBD` | stained | 3.8 | fold of stain | standby |
| bounty | 3.7 | `PWO*EPBT` | bonnet | 3.4 | two-syllable | bonnet |
| jockey | 3.6 | `SKWRO*EBG` | joker | 3.7 | two-syllable | jockey |
| classy | 3.7 | `KHRA*EUS` | chaser | 3.1 | two-syllable | chaser |
| poultry | 3.5 | `POERLT` | petrol | 3.7 | two-syllable | poultry |
| parsley | 3.3 | `PA*ERLS` | parcel | 3.7 | two-syllable | parsley |
| dowry | 3.1 | `#TKO*ER` | decor | 3.6 | two-syllable | dowry |
| cosy | 3.3 | `KO*UZ` | cozy | 3.6 | two-syllable | cosy |
| cozy | 3.6 | `KO*UZ` | cosy | 3.3 | two-syllable | cosy |
| alloy | 3.6 | `#AO*L` | aloe | 3.1 | two-syllable | aloe |
| storey | 3.6 | `#STO*ER` | austere | 3.0 | two-syllable | austere |
| brandy | 3.5 | `PWRA*EUPBD` | brained | 2.7 | fold of brain | brained |
| rowdy | 3.2 | `#RO*ED` | odor | 3.5 | two-syllable | rowdy |
| creamy | 3.4 | `#KRA*UPL` | calmer | 3.0 | two-syllable | calmer |
| hubby | 3.4 | `#H*EB` | hobo | 3.1 | two-syllable | hobo |
| grassy | 3.2 | `TKPWRA*EUS` | greys | 2.9 | fold of grey | greys |
| trusty | 3.0 | `TR*ETS` | tester | 3.2 | two-syllable | trusty |
| brownie | 3.2 | `PWRO*EPB` | boner | 3.2 | two-syllable | boner |

### rest (308)

| word | zipf | stroke | rival | zipf | rival is | loses |
|---|---|---|---|---|---|---|
| baton | 3.6 | `TPWAOPB` | between | 5.8 | two-syllable | baton |
| between | 5.8 | `TPWAOPB` | baton | 3.6 | two-syllable | baton |
| ridden | 3.6 | `#R*UPBD` | under | 5.7 | two-syllable | ridden |
| again | 5.7 | `TKPW*EUPB` | begun | 4.4 | two-syllable | begun |
| begun | 4.4 | `TKPW*EUPB` | again | 5.7 | two-syllable | begun |
| amin | 3.2 | `#PHO*EPB` | money | 5.6 | two-syllable | amin |
| simplest | 3.6 | `S*UPLTS` | system | 5.6 | two-syllable | simplest |
| system | 5.6 | `S*UPLTS` | simplest | 3.6 | two-syllable | simplest |
| belief | 4.5 | `#PWHRAO*F` | believe | 5.5 | two-syllable | belief |
| believe | 5.5 | `#PWHRAO*F` | belief | 4.5 | two-syllable | belief |
| service | 5.5 | `S*UFRS` | surfs | 2.2 | fold of surf | surfs |
| often | 5.4 | `OEFPB` | oven | 4.0 | two-syllable | oven |
| oven | 4.0 | `OEFPB` | often | 5.4 | two-syllable | oven |
| abide | 3.6 | `#PWO*EUD` | body | 5.4 | two-syllable | abide |
| data | 5.3 | `#TK*EUT` | duet | 3.4 | two-syllable | duet |
| duet | 3.4 | `#TK*EUT` | data | 5.3 | two-syllable | duet |
| among | 5.3 | `#PHAO*PBG` | mango | 3.6 | two-syllable | mango |
| mango | 3.6 | `#PHAO*PBG` | among | 5.3 | two-syllable | mango |
| himself | 5.2 | `SPH*UFL` | myself | 5.3 | two-syllable | himself |
| myself | 5.3 | `SPH*UFL` | himself | 5.2 | two-syllable | himself |
| perform | 4.7 | `TPRO*RPL` | former | 5.3 | two-syllable | perform |
| program | 5.3 | `PRAO*RPLG` | programme | 4.6 | two-syllable | programme |
| programme | 4.6 | `PRAO*RPLG` | program | 5.3 | two-syllable | programme |
| across | 5.2 | `KRAO*S` | crisis | 4.6 | two-syllable | crisis |
| crisis | 4.6 | `KRAO*S` | across | 5.2 | two-syllable | crisis |
| remote | 4.4 | `#PHRO*T` | mother | 5.2 | two-syllable | remote |
| centre | 5.0 | `SR*EPBT` | center | 5.2 | two-syllable | centre |
| content | 5.0 | `KO*EPBT` | county | 5.2 | two-syllable | content |
| private | 5.2 | `PRO*EUFT` | profit | 4.7 | two-syllable | profit |
| profit | 4.7 | `PRO*EUFT` | private | 5.2 | two-syllable | profit |
| follow | 5.1 | `#TP*L` | filly | 3.1 | two-syllable | filly |
| upon | 5.1 | `#PO*UPB` | pony | 3.8 | two-syllable | pony |
| harass | 3.3 | `#HRA*S` | release | 5.1 | two-syllable | harass |
| release | 5.1 | `#HRA*S` | harass | 3.3 | two-syllable | harass |
| practice | 5.1 | `PRA*EUBGTS` | practise | 3.5 | two-syllable | practise |
| practise | 3.5 | `PRA*EUBGTS` | practice | 5.1 | two-syllable | practise |
| album | 5.0 | `AUPBL` | annum | 3.2 | two-syllable | annum |
| annum | 3.2 | `AUPBL` | album | 5.0 | two-syllable | annum |
| allow | 5.0 | `#HRAO*U` | rely | 4.3 | two-syllable | rely |
| focus | 5.0 | `#TPO*EUBGS` | foxy | 3.0 | two-syllable | foxy |
| unit | 5.0 | `KWRAO*EPBT` | unite | 3.9 | two-syllable | unite |
| unite | 3.9 | `KWRAO*EPBT` | unit | 5.0 | two-syllable | unite |
| agile | 3.4 | `#A*EUPBLG` | image | 4.9 | two-syllable | agile |
| image | 4.9 | `#A*EUPBLG` | agile | 3.4 | two-syllable | agile |
| debris | 3.9 | `#TKPWRAO*` | degree | 4.9 | two-syllable | debris |
| degree | 4.9 | `#TKPWRAO*` | debris | 3.9 | two-syllable | debris |
| defence | 4.5 | `TKP-PBS` | defense | 4.9 | two-syllable | defence |
| defense | 4.9 | `TKP-PBS` | defence | 4.5 | two-syllable | defence |
| triple | 4.3 | `TR*UPL` | trump | 4.9 | word | triple |
| finish | 4.9 | `#TP*RPB` | fission | 3.1 | two-syllable | fission |
| fission | 3.1 | `#TP*RPB` | finish | 4.9 | two-syllable | fission |
| drunken | 3.7 | `TKR*EUPBG` | drink | 4.9 | word | drunken |
| captain | 4.9 | `K*PBT` | cotton | 4.3 | two-syllable | cotton |
| cotton | 4.3 | `K*PBT` | captain | 4.9 | two-syllable | cotton |
| hula | 3.0 | `#HO*UL` | holy | 4.9 | two-syllable | hula |
| upright | 3.7 | `#PRAO*ET` | peter | 4.9 | two-syllable | upright |
| avoid | 4.9 | `#SRAO*EUD` | cider | 3.5 | two-syllable | cider |
| mission | 4.9 | `#PH*RPB` | moron | 3.8 | two-syllable | moron |
| moron | 3.8 | `#PH*RPB` | mission | 4.9 | two-syllable | moron |
| attempt | 4.8 | `TA*EPLT` | template | 3.7 | two-syllable | template |
| template | 3.7 | `TA*EPLT` | attempt | 4.8 | two-syllable | template |
| engine | 4.8 | `#*UPBLG` | edgy | 3.4 | two-syllable | edgy |
| mantle | 3.6 | `PHAEPBLT` | mental | 4.8 | two-syllable | mantle |
| mental | 4.8 | `PHAEPBLT` | mantle | 3.6 | two-syllable | mantle |
| mallet | 3.0 | `#PHA*ELT` | metal | 4.8 | two-syllable | mallet |
| metal | 4.8 | `#PHA*ELT` | mallet | 3.0 | two-syllable | mallet |
| ascent | 3.4 | `#SA*EPBT` | senate | 4.8 | two-syllable | ascent |
| assent | 3.0 | `#SA*EPBT` | senate | 4.8 | two-syllable | assent |
| senate | 4.8 | `#SA*EPBT` | ascent | 3.4 | two-syllable | ascent |
| concept | 4.8 | `KO*EPBTS` | contest | 4.5 | two-syllable | contest |
| contest | 4.5 | `KO*EPBTS` | concept | 4.8 | two-syllable | contest |
| loco | 3.2 | `#HR*EBG` | lucky | 4.8 | two-syllable | loco |
| ample | 3.7 | `#A*EPL` | apple | 4.8 | two-syllable | ample |
| apple | 4.8 | `#A*EPL` | ample | 3.7 | two-syllable | ample |
| emo | 3.2 | `#A*EPL` | apple | 4.8 | two-syllable | emo |
| request | 4.8 | `KWR*TS` | requests | 4.3 | two-syllable | requests |
| requests | 4.3 | `KWR*TS` | request | 4.8 | two-syllable | requests |
| iron | 4.7 | `#RAO*EUPB` | rainbow | 4.1 | two-syllable | rainbow |
| rainbow | 4.1 | `#RAO*EUPB` | iron | 4.7 | two-syllable | rainbow |
| honest | 4.7 | `#O*EPBTS` | onset | 3.7 | two-syllable | onset |
| onset | 3.7 | `#O*EPBTS` | honest | 4.7 | two-syllable | onset |
| surreal | 3.6 | `#SRA*EL` | valley | 4.7 | two-syllable | surreal |
| forest | 4.7 | `TPO*ERTS` | fortress | 3.8 | two-syllable | fortress |
| fortress | 3.8 | `TPO*ERTS` | forest | 4.7 | two-syllable | fortress |
| presume | 3.6 | `SPRAO*EUPL` | supreme | 4.7 | two-syllable | presume |
| supreme | 4.7 | `SPRAO*EUPL` | presume | 3.6 | two-syllable | presume |
| lament | 3.2 | `PHRA*EPBT` | planet | 4.7 | two-syllable | lament |
| planet | 4.7 | `PHRA*EPBT` | lament | 3.2 | two-syllable | lament |
| appeal | 4.7 | `#P*EL` | polo | 3.8 | two-syllable | polo |
| polo | 3.8 | `#P*EL` | appeal | 4.7 | two-syllable | polo |
| chicken | 4.6 | `KH*UPBG` | chunk | 3.6 | word | chunk |
| eighteen | 3.8 | `#TAO*PB` | tiny | 4.6 | two-syllable | eighteen |
| solo | 4.6 | `#S*EL` | sully | 3.1 | two-syllable | sully |
| candid | 3.5 | `KA*EUPBD` | kinda | 4.6 | two-syllable | candid |
| kinda | 4.6 | `KA*EUPBD` | candy | 4.3 | two-syllable | candy |
| detail | 4.6 | `#TKA*UL` | ideal | 4.4 | two-syllable | ideal |
| ideal | 4.4 | `#TKA*UL` | detail | 4.6 | two-syllable | ideal |
| pattern | 4.6 | `PRA*PBT` | panther | 3.6 | two-syllable | panther |
| premiere | 4.2 | `PHRAO*R` | premier | 4.6 | two-syllable | premiere |
| revere | 3.1 | `#SRAO*R` | severe | 4.6 | two-syllable | revere |
| severe | 4.6 | `#SRAO*R` | revere | 3.1 | two-syllable | revere |
| vantage | 3.3 | `SR-PBLGT` | victim | 4.6 | two-syllable | vantage |
| victim | 4.6 | `SR-PBLGT` | vantage | 3.3 | two-syllable | vantage |
| arrest | 4.5 | `#R*ETS` | rusty | 3.6 | two-syllable | rusty |
| relief | 4.5 | `#HRAO*F` | relieve | 3.8 | two-syllable | relieve |
| relieve | 3.8 | `#HRAO*F` | relief | 4.5 | two-syllable | relieve |
| manage | 4.5 | `#PH*PBLG` | mimic | 3.5 | two-syllable | mimic |
| mimic | 3.5 | `#PH*PBLG` | manage | 4.5 | two-syllable | mimic |
| vowel | 3.3 | `#SRO*EL` | solar | 4.5 | two-syllable | vowel |
| licence | 4.0 | `HRAO*UPBS` | license | 4.5 | two-syllable | licence |
| license | 4.5 | `HRAO*UPBS` | licence | 4.0 | two-syllable | licence |
| campus | 4.5 | `KA*UPLS` | capsule | 3.7 | two-syllable | capsule |
| capsule | 3.7 | `KA*UPLS` | campus | 4.5 | two-syllable | capsule |
| contain | 4.5 | `TKPHAO*EUPB` | domain | 4.3 | two-syllable | domain |
| domain | 4.3 | `TKPHAO*EUPB` | contain | 4.5 | two-syllable | domain |
| tonic | 3.4 | `#TO*EUPBG` | topic | 4.5 | two-syllable | tonic |
| topic | 4.5 | `#TO*EUPBG` | tonic | 3.4 | two-syllable | tonic |
| evoke | 3.3 | `#SRO*BG` | soccer | 4.5 | two-syllable | evoke |
| revoke | 3.3 | `#SRO*BG` | soccer | 4.5 | two-syllable | revoke |
| defend | 4.5 | `TKP-PBD` | depend | 4.2 | two-syllable | depend |
| depend | 4.2 | `TKP-PBD` | defend | 4.5 | two-syllable | depend |
| angel | 4.5 | `#A*EPBLG` | angle | 4.4 | two-syllable | angle |
| angle | 4.4 | `#A*EPBLG` | angel | 4.5 | two-syllable | angle |
| ankle | 4.0 | `#A*EPBLG` | angel | 4.5 | two-syllable | ankle |
| protest | 4.5 | `PRO*TS` | poster | 4.2 | two-syllable | poster |
| reveal | 4.4 | `#SRA*L` | valor | 3.2 | two-syllable | valor |
| alight | 3.0 | `#HRO*EUT` | alright | 4.4 | two-syllable | alight |
| alright | 4.4 | `#HRO*EUT` | alight | 3.0 | two-syllable | alight |
| stomach | 4.4 | `STAO*PBLG` | stoppage | 3.2 | two-syllable | stoppage |
| stoppage | 3.2 | `STAO*PBLG` | stomach | 4.4 | two-syllable | stoppage |
| elite | 4.4 | `#HRAO*T` | lotta | 3.2 | two-syllable | lotta |
| lotta | 3.2 | `#HRAO*T` | elite | 4.4 | two-syllable | lotta |
| fallen | 4.4 | `TPAEUPBL` | famine | 3.6 | two-syllable | famine |
| famine | 3.6 | `TPAEUPBL` | fallen | 4.4 | two-syllable | famine |
| inflict | 3.3 | `TPHR*BGT` | reflect | 4.4 | two-syllable | inflict |
| reflect | 4.4 | `TPHR*BGT` | inflict | 3.3 | two-syllable | inflict |
| ribbon | 3.8 | `#RO*EUPB` | robin | 4.3 | two-syllable | ribbon |
| robin | 4.3 | `#RO*EUPB` | ribbon | 3.8 | two-syllable | ribbon |
| forgave | 3.0 | `TKPWRA*EUF` | grave | 4.3 | word | forgave |
| mandate | 4.0 | `PH*PBTD` | midnight | 4.3 | two-syllable | mandate |
| midnight | 4.3 | `PH*PBTD` | mandate | 4.0 | two-syllable | mandate |
| retake | 3.3 | `#RO*EBGT` | rocket | 4.3 | two-syllable | retake |
| rocket | 4.3 | `#RO*EBGT` | retake | 3.3 | two-syllable | retake |
| regime | 4.3 | `#SHRAO*PL` | slimy | 3.1 | two-syllable | slimy |
| shalom | 3.0 | `#SHRAO*PL` | regime | 4.3 | two-syllable | shalom |
| obese | 3.7 | `PWA*ES` | beast | 4.3 | word | obese |
| mama | 4.2 | `#PH*UPL` | mamma | 3.3 | two-syllable | mamma |
| mamma | 3.3 | `#PH*UPL` | mama | 4.2 | two-syllable | mamma |
| asset | 4.2 | `#A*ETS` | ethos | 3.3 | two-syllable | ethos |
| ethos | 3.3 | `#A*ETS` | asset | 4.2 | two-syllable | ethos |
| closest | 4.2 | `KHRO*TS` | cloths | 3.1 | fold of cloth | cloths |
| nonsense | 4.2 | `TPHO*EPBS` | knowns | 1.9 | word | knowns |
| sunken | 3.3 | `S*EUPBG` | sink | 4.2 | word | sunken |
| villa | 4.2 | `#SRA*EUL` | sailor | 3.8 | two-syllable | sailor |
| coastal | 4.2 | `KAO*ELTS` | coolest | 3.6 | two-syllable | coolest |
| coolest | 3.6 | `KAO*ELTS` | coastal | 4.2 | two-syllable | coolest |
| refill | 3.2 | `#RAO*UFL` | rifle | 4.2 | two-syllable | refill |
| rifle | 4.2 | `#RAO*UFL` | refill | 3.2 | two-syllable | refill |
| barrel | 4.2 | `#PWA*ERL` | barley | 3.5 | two-syllable | barley |
| resolve | 4.2 | `SRO*EFL` | revolve | 3.2 | two-syllable | revolve |
| revolve | 3.2 | `SRO*EFL` | resolve | 4.2 | two-syllable | revolve |
| racket | 3.3 | `#RA*EBGT` | react | 4.2 | two-syllable | racket |
| react | 4.2 | `#RA*EBGT` | racket | 3.3 | two-syllable | racket |
| retain | 4.2 | `#TRA*UPB` | trainee | 3.4 | two-syllable | trainee |
| trainee | 3.4 | `#TRA*UPB` | retain | 4.2 | two-syllable | trainee |
| akin | 3.7 | `KA*EUPB` | cabin | 4.1 | two-syllable | akin |
| cabin | 4.1 | `KA*EUPB` | akin | 3.7 | two-syllable | akin |
| jungle | 4.1 | `SKWR*EUPBLG` | ginger | 3.9 | two-syllable | ginger |
| cannon | 4.0 | `KAO*PB` | canon | 4.1 | two-syllable | cannon |
| canon | 4.1 | `KAO*PB` | cannon | 4.0 | two-syllable | cannon |
| payroll | 3.8 | `#PAO*EURL` | poorly | 4.1 | two-syllable | payroll |
| gentile | 3.0 | `SKWRUPBLT` | gently | 4.1 | two-syllable | gentile |
| boycott | 3.8 | `PW*EUBGT` | bucket | 4.1 | two-syllable | boycott |
| bucket | 4.1 | `PW*EUBGT` | boycott | 3.8 | two-syllable | boycott |
| petit | 3.3 | `#P*UT` | petty | 4.1 | two-syllable | petit |
| ballot | 4.1 | `#PWAO*LT` | beetle | 3.5 | two-syllable | beetle |
| beetle | 3.5 | `#PWAO*LT` | ballot | 4.1 | two-syllable | beetle |
| fibre | 3.7 | `#TPRAO*EUB` | fiber | 4.1 | two-syllable | fibre |
| fragile | 3.9 | `TPRA*EUPBLG` | frankly | 4.0 | two-syllable | fragile |
| salvage | 3.6 | `S-FPBLG` | savage | 4.0 | two-syllable | salvage |
| savage | 4.0 | `S-FPBLG` | salvage | 3.6 | two-syllable | salvage |
| lineup | 4.0 | `#HRAO*UPB` | lunar | 3.8 | two-syllable | lunar |
| reggae | 3.5 | `#RA*EG` | eager | 4.0 | two-syllable | reggae |
| elect | 4.0 | `#HR*BGT` | lockout | 3.1 | two-syllable | lockout |
| lockout | 3.1 | `#HR*BGT` | elect | 4.0 | two-syllable | lockout |
| carol | 4.0 | `#KAO*RL` | coral | 3.9 | two-syllable | coral |
| choral | 3.2 | `#KAO*RL` | carol | 4.0 | two-syllable | choral |
| coral | 3.9 | `#KAO*RL` | carol | 4.0 | two-syllable | coral |
| salmon | 4.0 | `SAOPBL` | semen | 3.4 | two-syllable | semen |
| semen | 3.4 | `SAOPBL` | salmon | 4.0 | two-syllable | semen |
| borough | 3.8 | `#PW*R` | borrow | 4.0 | two-syllable | borough |
| borrow | 4.0 | `#PW*R` | borough | 3.8 | two-syllable | borough |
| magna | 3.3 | `#PH*PBG` | manga | 4.0 | two-syllable | magna |
| manga | 4.0 | `#PH*PBG` | magna | 3.3 | two-syllable | magna |
| silo | 3.0 | `#SA*EUL` | sally | 4.0 | two-syllable | silo |
| parole | 3.8 | `#PRO*EL` | polar | 4.0 | two-syllable | parole |
| amid | 4.0 | `#PHA*EUD` | midday | 3.4 | two-syllable | midday |
| hollow | 4.0 | `#H*L` | hilly | 3.2 | two-syllable | hilly |
| appease | 3.2 | `#P*EZ` | oppose | 3.9 | two-syllable | appease |
| oppose | 3.9 | `#P*EZ` | appease | 3.2 | two-syllable | appease |
| cystic | 3.1 | `S*BGTS` | sixty | 3.9 | two-syllable | cystic |
| applause | 3.8 | `#PHR*UZ` | plaza | 3.9 | two-syllable | applause |
| plaza | 3.9 | `#PHR*UZ` | applause | 3.8 | two-syllable | applause |
| fifteenth | 3.3 | `TPAO*UPBT` | fountain | 3.9 | two-syllable | fifteenth |
| fountain | 3.9 | `TPAO*UPBT` | fifteenth | 3.3 | two-syllable | fifteenth |
| hostess | 3.4 | `HO*ETS` | hottest | 3.9 | two-syllable | hostess |
| hottest | 3.9 | `HO*ETS` | hostess | 3.4 | two-syllable | hostess |
| peanut | 3.9 | `#PA*EUPBT` | python | 3.8 | two-syllable | python |
| python | 3.8 | `#PA*EUPBT` | peanut | 3.9 | two-syllable | python |
| awhile | 3.9 | `#WO*EUL` | willow | 3.7 | two-syllable | willow |
| willow | 3.7 | `#WO*EUL` | awhile | 3.9 | two-syllable | willow |
| thirteen | 3.9 | `TH*URPBT` | thirteenth | 3.4 | two-syllable | thirteenth |
| thirteenth | 3.4 | `TH*URPBT` | thirteen | 3.9 | two-syllable | thirteenth |
| fortnight | 3.4 | `TPO*EURPBT` | fourteen | 3.9 | two-syllable | fortnight |
| fourteen | 3.9 | `TPO*EURPBT` | fortnight | 3.4 | two-syllable | fortnight |
| fourteenth | 3.4 | `TPO*EURPBT` | fourteen | 3.9 | two-syllable | fourteenth |
| shannon | 3.8 | `#SHAO*PB` | shiny | 3.9 | two-syllable | shannon |
| fulfil | 3.5 | `TPHR*EL` | fulfill | 3.9 | two-syllable | fulfil |
| fulfill | 3.9 | `TPHR*EL` | fulfil | 3.5 | two-syllable | fulfil |
| hydro | 3.6 | `#HA*EURD` | hardy | 3.9 | two-syllable | hydro |
| resin | 3.6 | `#R*UPBZ` | risen | 3.9 | two-syllable | resin |
| risen | 3.9 | `#R*UPBZ` | resin | 3.6 | two-syllable | resin |
| lava | 3.7 | `#HR*UF` | levy | 3.9 | two-syllable | lava |
| relive | 3.2 | `#HR*UF` | levy | 3.9 | two-syllable | relive |
| primate | 3.1 | `PRO*EUPLT` | promptly | 3.8 | two-syllable | primate |
| emir | 3.0 | `#PH*UR` | merry | 3.8 | two-syllable | emir |
| rascal | 3.1 | `R-BLGS` | reckless | 3.8 | two-syllable | rascal |
| reckless | 3.8 | `R-BLGS` | rascal | 3.1 | two-syllable | rascal |
| pancake | 3.4 | `P*PBG` | picnic | 3.8 | two-syllable | pancake |
| picnic | 3.8 | `P*PBG` | pancake | 3.4 | two-syllable | pancake |
| hammock | 3.1 | `#HAO*PBLG` | hygiene | 3.8 | two-syllable | hammock |
| hygiene | 3.8 | `#HAO*PBLG` | hammock | 3.1 | two-syllable | hammock |
| fairness | 3.8 | `TPA*URPBS` | furnace | 3.6 | two-syllable | furnace |
| furnace | 3.6 | `TPA*URPBS` | fairness | 3.8 | two-syllable | furnace |
| slightest | 3.8 | `SHRAO*UTS` | sleuths | 2.2 | fold of sleuth | sleuths |
| canoe | 3.5 | `TKPHOU` | dunno | 3.8 | two-syllable | canoe |
| dunno | 3.8 | `TKPHOU` | undo | 3.6 | two-syllable | undo |
| undo | 3.6 | `TKPHOU` | dunno | 3.8 | two-syllable | undo |
| recap | 3.5 | `#RO*EPBG` | reckon | 3.8 | two-syllable | recap |
| reckon | 3.8 | `#RO*EPBG` | recap | 3.5 | two-syllable | recap |
| bonnet | 3.4 | `PWO*EPBT` | bounty | 3.7 | two-syllable | bonnet |
| palette | 3.7 | `#PA*ELT` | pallet | 3.1 | two-syllable | pallet |
| pallet | 3.1 | `#PA*ELT` | palette | 3.7 | two-syllable | pallet |
| petal | 3.0 | `#PA*ELT` | palette | 3.7 | two-syllable | petal |
| chili | 3.7 | `#KH*L` | chilly | 3.5 | two-syllable | chilly |
| averse | 3.1 | `#SRA*ERS` | versa | 3.7 | two-syllable | averse |
| versa | 3.7 | `#SRA*ERS` | averse | 3.1 | two-syllable | averse |
| petrol | 3.7 | `POERLT` | poultry | 3.5 | two-syllable | poultry |
| parcel | 3.7 | `PA*ERLS` | parsley | 3.3 | two-syllable | parsley |
| camel | 3.6 | `#KA*EPL` | calmly | 3.6 | two-syllable | calmly |
| chemo | 3.2 | `#KA*EPL` | camel | 3.6 | two-syllable | chemo |
| coco | 3.5 | `K*EBG` | cocoa | 3.6 | two-syllable | coco |
| cocoa | 3.6 | `K*EBG` | coco | 3.5 | two-syllable | coco |
| paddle | 3.5 | `#PA*ELD` | pedal | 3.6 | two-syllable | paddle |
| pedal | 3.6 | `#PA*ELD` | paddle | 3.5 | two-syllable | paddle |
| brittle | 3.3 | `PWR*ULT` | rebuilt | 3.6 | two-syllable | brittle |
| rebuilt | 3.6 | `PWR*ULT` | brittle | 3.3 | two-syllable | brittle |
| aloe | 3.1 | `#AO*L` | alloy | 3.6 | two-syllable | aloe |
| austere | 3.0 | `#STO*ER` | storey | 3.6 | two-syllable | austere |
| bourbon | 3.6 | `PW*URPB` | burne | 2.3 | word | burne |
| flawless | 3.6 | `TPHRA*EULS` | flails | 2.1 | fold of flail | flails |
| rapport | 3.2 | `#PRAO*R` | poorer | 3.6 | two-syllable | rapport |
| rotate | 3.6 | `#RAO*ET` | ether | 3.4 | two-syllable | ether |
| lima | 3.6 | `#HRO*EUPL` | limo | 3.3 | two-syllable | limo |
| limo | 3.3 | `#HRO*EUPL` | lima | 3.6 | two-syllable | limo |
| crescent | 3.6 | `KR*PBTS` | kristin | 3.3 | two-syllable | kristin |
| kristin | 3.3 | `KR*PBTS` | crescent | 3.6 | two-syllable | kristin |
| amend | 3.5 | `#PHA*EPBD` | madden | 3.5 | two-syllable | madden |
| madden | 3.5 | `#PHA*EPBD` | amend | 3.5 | two-syllable | madden |
| derive | 3.5 | `#TKRAO*EUF` | diver | 3.4 | two-syllable | diver |
| rewrite | 3.5 | `#RAO*UT` | router | 3.5 | two-syllable | rewrite |
| axel | 3.3 | `AEBLGS` | axle | 3.5 | two-syllable | axel |
| axle | 3.5 | `AEBLGS` | axel | 3.3 | two-syllable | axel |
| leakage | 3.2 | `#HR*EPBLG` | ledger | 3.5 | two-syllable | leakage |
| lingo | 3.0 | `#HRO*EUPBG` | login | 3.5 | two-syllable | lingo |
| login | 3.5 | `#HRO*EUPBG` | lingo | 3.0 | two-syllable | lingo |
| weaken | 3.5 | `WA*PBG` | wank | 2.9 | word | wank |
| penal | 3.5 | `POEPBL` | pollen | 3.5 | two-syllable | penal |
| pollen | 3.5 | `POEPBL` | penal | 3.5 | two-syllable | penal |
| rejoice | 3.5 | `SKWRO*US` | joust | 2.5 | word | joust |
| marquee | 3.2 | `KPHROE` | marquis | 3.5 | two-syllable | marquee |
| marquis | 3.5 | `KPHROE` | marquee | 3.2 | two-syllable | marquee |
| sulfur | 3.4 | `SR*UFL` | sulphur | 3.2 | two-syllable | sulphur |
| sulphur | 3.2 | `SR*UFL` | sulfur | 3.4 | two-syllable | sulphur |
| hobo | 3.1 | `#H*EB` | hubby | 3.4 | two-syllable | hobo |
| moustache | 3.2 | `PHAURBTS` | mustache | 3.4 | two-syllable | moustache |
| mustache | 3.4 | `PHAURBTS` | moustache | 3.2 | two-syllable | moustache |
| reprint | 3.4 | `PR*UPBT` | punter | 3.0 | two-syllable | punter |
| woken | 3.4 | `WO*PBG` | wonk | 2.3 | word | wonk |
| palate | 3.4 | `#P*LT` | pellet | 3.1 | two-syllable | pellet |
| pellet | 3.1 | `#P*LT` | palate | 3.4 | two-syllable | pellet |
| canine | 3.3 | `KA*PB` | cocoon | 3.1 | two-syllable | cocoon |
| cocoon | 3.1 | `KA*PB` | canine | 3.3 | two-syllable | cocoon |
| carton | 3.2 | `KAO*RPBT` | contra | 3.3 | two-syllable | carton |
| contra | 3.3 | `KAO*RPBT` | carton | 3.2 | two-syllable | carton |
| donut | 3.3 | `TKO*EUPBT` | doughnut | 3.2 | two-syllable | doughnut |
| doughnut | 3.2 | `TKO*EUPBT` | donut | 3.3 | two-syllable | doughnut |
| horrid | 3.3 | `#HO*EURD` | hydra | 3.3 | two-syllable | horrid |
| hydra | 3.3 | `#HO*EURD` | horrid | 3.3 | two-syllable | horrid |
| anew | 3.2 | `#TPHO*U` | uno | 3.3 | two-syllable | anew |
| uno | 3.3 | `#TPHO*U` | anew | 3.2 | two-syllable | anew |
| inflate | 3.0 | `TPHRA*T` | flatter | 3.3 | two-syllable | inflate |
| apiece | 3.2 | `#PO*ES` | peso | 3.0 | two-syllable | peso |
| peso | 3.0 | `#PO*ES` | apiece | 3.2 | two-syllable | peso |
| spectre | 3.2 | `SPR*EBGT` | specter | 3.1 | two-syllable | specter |
| bagel | 3.2 | `PWA*ULG` | bawling | 2.8 | fold of bawl | bawling |
| slowest | 3.1 | `SHRO*TS` | sloths | 2.7 | fold of sloth | sloths |
| stampede | 3.1 | `STPHO*ED` | snowed | 2.9 | word | snowed |

## Why a `*` mark collides

Scheme: `*` added to every two-syllable stroke that is not free, current placement. Rivals as above.

```
two-syllable words that cannot take *: 291
* stroke meets a one-syllable word, by why that word has *:
  spelling bit     47  agree/grow, amid/maid, amin/moan, annoy/nigh, arose/rows, arrow/oar
  other            15  around/'round, bourbon/burne, bully/belle, burner/burne, eaten/ant, harass/las
  -th               9  better/berth, bitter/birth, cadet/death, fatal/filth, fatty/faith, greta/growth
  -mp               7  demi/dump, lama/lump, papal/pimp, ripple/rump, trauma/trump, triple/trump
  -st               6  apex/angst, fussy/fest, obese/beast, pussy/pest, rejoice/joust, starry/stair
  -v                6  avoid/vied, coffee/cove, forgave/grave, savoy/vie, surreal/veil, valley/veil
  -nk               5  chicken/chunk, pagan/pink, token/tonk, weaken/wank, woken/wonk
  -rf               4  server/serf, sever/serf, suffer/surf, tougher/turf
  -lk               2  bagel/balk, ugly/elk
  -rsh              2  harbor/harsh, harbour/harsh
  -rk               1  acre/ark
  -rb               1  barber/barb
  -ph               1  mover/morph
  -mb               1  nipple/numb
why those 291 already have *: [('spelling bit', 133), ('-th', 62), ('-nk', 26), ('other', 24), ('-st', 20), ('-c', 10), ('-v', 7), ('-rsh', 3), ('-rf', 3), ('-rb', 1), ('-rv', 1), ('initial z', 1)]
```

## How full the middle keys are

One-syllable rule strokes only, no scheme.

```
middle zone H R | A O * E U | F R: 512 patterns, 480 with a vowel key
  used by one-syllable words: 292
  used by fewer than 3 one-syllable words: 78
  used by no one-syllable word but by two-syllable strokes: 82 patterns, 245 words

vowel chord x -R, one-syllable words using it (r-coloured rows are sparse):
  chord     plain  plain*   +R   +R*
  A         421     70    128     15
  O         322     48     69      7
  E         268     50     50      2
  U         285     66     69      8
  AO        164     12     11      1
  AE        178     45     49      7
  AU        160     20      5      0
  OE        162    122     37     19
  OU        128     14     16      3
  EU        381     82     44      5
  AOE       213     25     35      2
  AOU       114     26     10      1
  AEU       267    141     40     17
  OEU        54      5      2      0
  AOEU      240     67      8      1
```
