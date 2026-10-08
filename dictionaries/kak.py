import re

# https://pypi.org/project/plover-python-dictionary/
debug=True
logfile="/Users/bendean/.config/plover/kak.log"
def log(s):
	if not debug:
		return
        a=1
        with open(logfile, "a") as f:
            f.write(str(s)+'\n')

print('huh')

"""
Text objects: word, Word, )}]>, sent, para, 

	text obj actions: Forward, back, inner, outer, swap
		; change, apnd, insert

Surrounding -- change, delete, add

registers, numbers

selection commands---
"""

class O:
	def __init__(self, k, pair=None):
		self.k = k
                self.pair = pair# TODO unused so far

# LHS bindings for text objs.
text_objs={
	"PR"  : O("b"), # close paren
	"PW"  : O("B"), # open curly   
	"R"   : O("r"),
	"WS"  : O("a"),
	"KW"  : O("Q"),
	"SKW" : O("q"),
	"TPKW": O("g"),
	"W"   : O("w"),
	"SW"  : O("{#alt(w)}"),
	"A"   : O("s"),
	"P"   : O("p"),
	"SP"  : O("{^ ^}"),
	"T"   : O("i"),
	"TPH" : O("n"),
	"WR"  : O("u")
}

# RHS bindings for movement commands.
movement={
	"F": ("{#alt{#braceleft}}"),
	"T": ("{#alt(#braceright)}"),
	"PL": ("{#alt(a)}"),
	"P": ("{#alt(i)}"),
}

def stroke_parts(stroke):
	parts=re.split("([AEOU#*]+)|-", stroke)
	log(len(parts))
	beg=parts[0]
	mid="" if len(parts)<=1 else parts[1]
	end="" if len(parts)<=2 else parts[2]
	if beg == None:
		beg=""
	if mid == None:
		mid=""
	if end == None:
		end=""
	return (beg, mid, end)
def mid_number(mid):
    (a, o, e, u) = (1 if (v in mid) else 0 for v in "AOEU")
    return a + 2 * o + 4 * e + 8 * u
LONGEST_KEY=1
def lookup(key):
	stroke=key[0]

	# Special case: Return to insert mode.
	if stroke == "-RBGS":
		return "{MODE:RESET}{PLOVER:END_SOLO_DICT}{^}{#i}"

	(beg, mid, end)=stroke_parts(key[0])

	log("====== " + str(key[0]))
	out="{MODE:SET_SPACE:}"
	num=mid_number(mid)
	log("num: " + str(num))
	if num>0:
		out += str(num)

	# Simple movement commands.
	if end == "F": out+="h"
	elif end == "T": out+="l"
	elif end =="P": out+="j"
	elif end =="L": out+="k"

	if end == "R": out += "{#alt{#braceleft}}"
	elif end == "S": out += "{#alt(#braceright)}"
	elif end == "BG": out += "{#alt(a)}"
	elif end == "B": out += "{#alt(i)}"

	if beg in text_objs:
		# Selection within text objects.
		out += text_objs[beg].k

	log("out: " + str(out))
	return out

	if 1 == 2:
		raise KeyError

	

