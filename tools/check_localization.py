#!/usr/bin/env python3
"""Check English resource coverage and untranslated German UI literals before a release."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]

def resources(directory):
    values={}
    for path in (ROOT/'app/src/main/res'/directory).glob('*.xml'):
        for entry in ET.parse(path).getroot():
            if entry.tag in ('string','plurals','string-array'):
                values[entry.attrib['name']]=''.join(entry.itertext())
    return values

english,german=resources('values'),resources('values-de')
missing=set(german)-set(english)
if missing:raise SystemExit('Missing English resources: '+', '.join(sorted(missing)))
words=re.compile(r'[äöüÄÖÜß]|\b(?:nicht|bitte|Fehler|keine|wird|werden|Staffel|Folge|Filme|Serien|Einstellungen|Daten|und|oder)\b')
leaks=[name for name,text in english.items() if words.search(text)]
if leaks:raise SystemExit('Review German text in English resources: '+', '.join(leaks))
source=(ROOT/'app/src/main/java/de/lowfin/tv/I18n.java').read_text()
keys=set(re.findall(r'IDS.put\("((?:[^"\\]|\\.)*)"',source))
missing_literals=[]
for path in (ROOT/'app/src/main/java/de/lowfin/tv').glob('*.java'):
    if path.name=='I18n.java':continue
    for value in re.findall(r'I18n.text\("((?:[^"\\]|\\.)*)"\)',path.read_text()):
        if value not in keys and words.search(value):missing_literals.append(path.name)
if missing_literals:raise SystemExit('Unmapped German UI literals: '+', '.join(sorted(set(missing_literals))))
print(f'English coverage OK: {len(english)} resource entries; {len(german)} German entries covered; literal mappings checked.')
