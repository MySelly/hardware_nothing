import re
from pathlib import Path
p = Path('Base.apk')
with p.open('rb') as f:
    data = f.read()
pattern = re.compile(rb'[\x20-\x7E]{4,}')
seen = set()
for m in pattern.finditer(data):
    s = m.group(0).decode('ascii', errors='ignore')
    if 'com.' in s or 'org.' in s or 'activity' in s or 'MainActivity' in s or 'LAUNCHER' in s:
        if s not in seen:
            seen.add(s)
            print(s)
