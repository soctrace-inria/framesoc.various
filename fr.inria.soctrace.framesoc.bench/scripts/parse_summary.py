import fileinput
import re

def get_category(factors):
    results=''
    if factors[0] == "true":
        results="index"
    else:
        results="noindex"
        
    if factors[1] == "true":
        results+="_param"
    else:
        results+="_noparam"

    if factors[2] == "0":
        results+="_all"
    else:
        results+="_interval"

    return results

print "size,  config,      imean,       ierror,        tmean,       terror"
for line in fileinput.input():
    if line.strip() == '':
        continue
    fields = line.split()
    newline=fields[1] + ", " + get_category(fields[2:5]) 
    for i in range(5,9):
        newline+=(", " + fields[i])
    print newline





