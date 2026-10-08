"""
@Jalexu on discord had an idea for fingerspelling using the whole keyboard, not just one letter at a time
Key considerations were that it wasn't tailored for English, as this is mostly for foreign words/names
"""

# Forked from the downloaded original. Local changes:
# - standalone "^" is the only entry stroke (replaces SHREUFRPL/KWR*/KWR/
#   TPH*/KAPS); an entry stroke matches whole, so ^SH does not enter Shrimple
# - lookup returns {#} instead of "": Plover formats a falsy translation as
#   raw steno (same bug as wm.py)

entry_strokes={
    "^" : {"prefix":"",
           "suffix":""},
}

#dedicated key settings:
dedicated_key = '🦐'            #Disabled: '+' is reserved for another use. A
                                #shrimp can never appear in a steno stroke, so
                                #every `dedicated_key in stroke` test is False
                                #and the dedicated-key entry path stays inert.
                                #Enter Shrimple via the entry_strokes above.
make_words_done_with_dedicated_key_exit_immediately = True
joiner_strokes={ #If Shrimple automatically exits, maybe you wanna use KWR to stay in Shrimple
    "left-hand joiner" : "^SKWR",
    #"right-hand joiner" : "FPL"
}


make_starter_letters_have_left_to_right_priority = True
starter_letter={
    "" : "",

    "^":"{^^}",

    "S" : "s",
    "STK": "dis",
    "STKPW": "z",
    "*SKP": "&",
    "SKWR": "j",
    "SKHR": "shr", #Josiah theory
    "SH" : "sh",
    "SHR" : "sl", #Josiah theory
    "SR" : "v",

    "T" : "t",
    "TK": "d",
    "TKPW":"g",
    "TKR": "dr",
    "TP": "f",
    "TPH": "n",

    "K":"k",
    "KP":"x",
    "KW":"q",
    "KWH":"y",
    "KWR":"",
    "KH":"ch",
    "KR":"c",

    "P":"p",
    "PW":"b",
    "PH":"m",
    "PHR":"pl", #conflict with mr

    "W":"w",

    "H":"h",
    "HR":"l",

    "R":"r"
}



vowels={
    "-":"",
    
    "":"",

    "*A":"u",
    "*AO":"i",
    "*AOEU":"uy",
    "*AOEU":"uoy",
    "*AEU":"ay",
    "*O":"e",
    "*OEU":"oy",
    "*OE":"ow",
    "*OU":"ow",
     #"*EU":"y",

    "A"   :"a",
    "AO"  :"oo",
    "AOE" :"ee",
    "AOEU":"i[e]",
    "AOU" :"u[e]",
    "AE"  :"ea",
    "AEU" :"a[e]",
    "AU"  :"au",

    "O"   :"o",
    "OE"  :"o[e]",
    "OEU" :"oi",
    "OU"  :"ou",

    "E"   :"e",
    "EU"  :"i",

    "U"   :"u"
}

make_ender_letters_have_left_to_right_priority = True
ender_letter={
    "":"",
    #"*":"", #asterisk on its own is invalid

    "*FT":"st",
    "*FTD":"sted",
    "*PBG":"nk",
    "*PZ":"h",
    "*BG":"ck",
    "*BGD":"cked",
    "*LG":"lk",
    "*T":"th",
    "*TD":"thed",
    "*S":"c[e]",
    "*SZ":"c",
    "*D":"[y]",
    "*Z":"z",

    "F":"f",
    "FRP":"mp",
    "FRPB":"rch",
    "FRPBG":"nk",
    "FRPL":"mpl",
    "FRB":"mb",
    "FRL":"ml",
    "FRBL":"mbl",
    "FP":"ch",
    "FPL":"",
    "FB":"v",
    "FT":"ft",

    "R":"r", 
    "RB":"sh", #unless AU to make it rb carb barb
    "RBG":"rk",
    "RBGT":"rket",
    "RBGTS":"rkets",

    "P":"p",
    "PB":"n",
    "PBLG":"j",
    "PBG":"ng",
    "PL":"m",

    "B":"b",
    "BG":"k",
    "BGT":"cket",
    "BGTS":"ckets",
    "BGS":"x",

    "L":"l",

    "G":"g",
    "GT":"ght",
    "GTS":"ghts",

    "T":"t",
    "TS":"ts",
    "TD":"ted",
    "TZ":"",

    "S":"s", #might be some logic here for c? Realtime uses `SZ` for c
    #"SZ":"ss",

    "D":"d",

    "Z":"[s]",

}



strokes_you_can_use_to_exit_shrimple_with=[

    #punctuation
    "TK-LS",    #no space
    "S-P",      #space
    "KPA",      #caps
    "KPA*",     #caps no space
    "R-R",      #enter
    "TP-PL",    #.
    "TA*B",     #can't remember
    "TPWA*",    #left hand tab
    #"R*",       #left hand return
    "KW-PL",    #?
    "TP-BG",    #o
    "KW-BG",    #,
    "AEZ",      #'s
    "A*ES",     #s'
    "AES",      #'s
    "HAERB",    ##
    "KWRA*T",   #@
    "P-P",      #.
    "H-N",      #-
    "H*N",      #-
    "TPHO*FRL", #normal
    "*",        #delete
    "PW*FP",    #control backspace
    "EFBG",     #escape
    "^*",
    "^S",
    "TKUPT",
    "STKHR",    #delete
    "TKHR",     #backspace

    #navigation
    "STPH-R",
    "STPH-RB",
    "STPH-P",
    "STPH-B",
    "STPH-BG",
    "STPH-G",
    "STPH-FR",
    "STPH-LG",
    "PW-FP",
    
]

left_finger_chords_you_can_use_to_exit_shrimple_with={

    #Emily's stuff (might also have to do this for my phrasing too?)
    "SKWH",


}

left_hand_chords_you_can_use_to_exit_shrimple_with={


}


right_finger_chords_you_can_use_to_exit_shrimple_with={

    #Emily's stuff (might also have to do this for my phrasing too?)
    "LTZ"
    "RLTZ"
}

left_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with=[
    #"KWR"
]

right_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with=[
    #"TZ"
]


dedicated_key_you_can_use_during_the_final_stroke_to_exit_shrimple_with = "$"


































































import re

LONGEST_KEY = 40


if make_starter_letters_have_left_to_right_priority:
    print('@Harrri to remind him he forgot this part')


if make_ender_letters_have_left_to_right_priority:
    print('@Harrri to remind him he forgot this part')


numbers_to_letters = {
    "1": "S",
    "2": "T",
    "3": "P",
    "4": "H",
    "5": "A",
    "6": "F",
    "7": "P",
    "8": "L",
    "9": "T",
    "0": "O"
    }

#when the hash key is pressed, it interferes with the letters, I'm not interested in that, so I'm just undoing that here
def aericks_denumberizer(old_outline):

    old_strokes = old_outline.split("/")
    new_strokes = []

    for stroke in old_strokes:

        new_strokes.append(stroke)

        for match in numbers_to_letters.keys():

            if match in stroke:

                if new_strokes[-1][0] != "#":
                    new_strokes[-1] = "#" + new_strokes[-1]

                new_strokes[-1] = new_strokes[-1].replace(match, numbers_to_letters[match])

        if new_strokes == []:
            new_strokes = old_strokes

    return "/".join(new_strokes)



def slices(s, n):
    if n == 1:
        yield [s]
    else:
        for split in range(1, len(s)):
            for others in slices(s[split:], n-1):
                yield [s[:split]] + others

def star_positions(parts):
    for i in range(len(parts)):
        yield ["*" + s if i == j else s for j, s in enumerate(parts)]

# print([x for x in slices("#STKPWHR", 1)])
# # print([[pos for pos in star_positions(x)] for x in slices("#STKPWHR", 1)])
# print([x for x in slices("#STKPWHR", 2)])
# # print([[pos for pos in star_positions(x)] for x in slices("#STKPWHR", 2)])
# print([x for x in slices("#STKPWHR", 3)])
# # print([[pos for pos in star_positions(x)] for x in slices("#STKPWHR", 3)])
# print([x for x in slices("S", 1)])
# print([x for x in slices("S", 2)])


#this function constructs the stroke recursively, if the stroke isn't found, then a match using 2 strokes is searched for, then 3, then 4 etc, etc
def construct_stroke_2(target_chord, chord_dictionary, has_asterisk = False):
    for num_parts in range(1, min(6, len(target_chord)+1)):
        # When the target chord is made of `num_parts` definitions that are in the dictionary
        for parts in slices(target_chord, num_parts):
            if not has_asterisk:
                if all(nth_half in chord_dictionary for nth_half in parts):
                    return "".join(chord_dictionary[nth_half] for nth_half in parts)
            else:
                for starred_parts in star_positions(parts):
                    if all(nth_half in chord_dictionary for nth_half in starred_parts):
                        return "".join(chord_dictionary[nth_half] for nth_half in starred_parts)

    return ""

def lookup(strokes, construct_stroke=construct_stroke_2):

    output_string= ""


    for stroke_number, stroke in enumerate(strokes):

        if stroke_number == 0:
            if not dedicated_key in stroke:
                stroke_valid = False
                if stroke in entry_strokes:
                    stroke_valid = True
                if not stroke_valid:
                    raise KeyError
                if len(strokes)==1:
                    return '{Shrimple}'
        else:
            if not dedicated_key in stroke:
                stroke_valid = False
                if stroke in entry_strokes:
                    stroke_valid = True
                if stroke_valid:
                    raise KeyError
                

            

        if stroke==dedicated_key:
            raise KeyError


        if stroke in strokes_you_can_use_to_exit_shrimple_with:
            raise KeyError


        match = re.fullmatch(r'(#?\^?S?T?K?P?W?H?R?)(A?O?)(\*?\-?E?U?)(F?R?P?B?L?G?T?S?D?Z?)', stroke.replace(dedicated_key,""))
        if match:

            if match[1] in left_finger_chords_you_can_use_to_exit_shrimple_with:
                raise KeyError
            if match[1]+match[2] in left_hand_chords_you_can_use_to_exit_shrimple_with:
                raise KeyError
            if match[4] in right_finger_chords_you_can_use_to_exit_shrimple_with:
                raise KeyError


            if ((match[1] in left_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with) or (match[4] in right_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with) or (dedicated_key_you_can_use_during_the_final_stroke_to_exit_shrimple_with in match[1])) and not stroke_number+1 == len(strokes):
                futurematch = re.fullmatch(r'(#?\^?S?T?K?P?W?H?R?)(A?O?)(\*?\-?E?U?)(F?R?P?B?L?G?T?S?D?Z?)', strokes[stroke_number+1].replace(dedicated_key,""))
                if not (futurematch[1] in left_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with or futurematch[4] in right_finger_chords_you_can_use_during_the_final_stroke_to_exit_shrimple_with):
                    raise KeyError


        if make_words_done_with_dedicated_key_exit_immediately and dedicated_key in strokes[0]:# and not "^" in strokes[0]:
            if stroke_number == 0 and not dedicated_key in stroke:
                raise KeyError
            elif not stroke_number == 0 and not (match[1] == joiner_strokes['left-hand joiner'] or match[4] == joiner_strokes["right-hand joiner"]):
                raise KeyError
            





    if (len(strokes)) == 1 and dedicated_key not in strokes[0]:
        if strokes[0]==entry_strokes['starterattached']:
            return ("{^}")
        else:
            return("{^ ^}")

    for stroke_number in range(len(strokes)):



        if not stroke_number == 0 and (strokes[stroke_number] in entry_strokes.values() or dedicated_key in strokes[stroke_number]):
            raise KeyError

        #match the strokes
        match= re.fullmatch(
            #dissect the string to starter_letters, vowels and ender_letters
            r'(#?)(\^?S?T?K?P?W?H?R?)(A?O?\*?\-?E?U?)(F?R?P?B?L?G?T?S?D?Z?)',

            #this string:
            aericks_denumberizer(strokes[stroke_number].replace(dedicated_key,"")))

        if not match:
            raise KeyError


        start_thing=construct_stroke(match[2], starter_letter)
        #if there's an asterisk, you gotta find where to implement it
        if "*" in match[3]:

            end_thing=construct_stroke(match[4], ender_letter, has_asterisk = True)


            if end_thing == "":

                end_thing = construct_stroke(match[4], ender_letter, has_asterisk = False)
                middle_thing=construct_stroke(match[3].replace("*",""), vowels, has_asterisk=True)

                if middle_thing==construct_stroke(match[3].replace("*",""), vowels, has_asterisk=False):
                    start_thing=construct_stroke(match[2], starter_letter, has_asterisk=True)

            else:
                middle_thing=construct_stroke(match[3].replace("*",""), vowels)




        else:
            end_thing=construct_stroke(match[4], ender_letter)
            middle_thing=construct_stroke(match[3], vowels)

        #now do stuff with like [e]:
        if '[e]' in middle_thing:
            end_thing+="[e]"
            middle_thing=middle_thing.replace("[e]","")

        if '[e]' in end_thing:
            end_thing+="e"
            end_thing=end_thing.replace("[e]","")

        if '[y]' in end_thing:
            end_thing+="{^y}"
            end_thing=end_thing.replace("[y]","")

        if '[s]' in end_thing:
            end_thing+="{^s}"
            end_thing=end_thing.replace("[s]","")


        if not stroke_number==0 or (dedicated_key in strokes[0]):
            if "#" in match[1]:
                output_string+=(
                    (start_thing+
                    middle_thing+
                    end_thing
                    ).capitalize())

            else:
                output_string+=(
                    start_thing+
                    middle_thing+
                    end_thing
                    )


    if strokes[0] in entry_strokes:
        output_string = entry_strokes[strokes[0]]["prefix"] + output_string + entry_strokes[strokes[0]]["suffix"]
    # Fork: "" would format as the raw steno; {#} is a truthy no-op.
    return output_string or "{#}"

def test(x,construct_stroke=construct_stroke_2):
    try:
        return lookup(x, construct_stroke=construct_stroke)
    except Exception as e:
        return e


if __name__ == "__main__":
    print(("DIFFERENCES: ", {
        x: results for x in [
            ("+KAPZ","KWROU"),
            ("+WA*U"),
            ("KAPS", "STA*RTD"),
            ("KWR", "A*"),
            ("KAPS", "STKHRA*RTD"),
            ("KAPS", "WA*TD"),
            ("KAPS", "WA*TD", "KWRAL"),
            ("KAPS", "WA*TD", "KWRAL", "PAL")]
            if (results := (test(x), test(x,  construct_stroke=construct_stroke_2)))
            if str(results[0]) != str(results[1])
            }))
# print(lookup(("+KAPZ","KWROU")))
# print(lookup(("+WA*U")))
# print(lookup(("KAPS", "STA*RTD")))
# print(lookup(("KWR", "A*")))
# print(lookup(("KAPS", "STKHRA*RTD")))
# print(lookup(("KAPS", "WA*TD")))
# print(lookup(("KAPS", "WA*TD", "KWRAL")))
# print(lookup(("KAPS", "WA*TD", "KWRAL", "PAL")))

