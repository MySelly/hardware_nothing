import re
from pathlib import Path
p = Path('Base.apk')
if not p.exists():
    print('MISSING_BASE_APK')
    raise SystemExit(1)
with p.open('rb') as f:
    data = f.read()
pattern = re.compile(rb'[\x20-\x7E]{4,}')
for m in pattern.finditer(data):
    s = m.group(0).decode('ascii', errors='ignore')
    if any(kw in s for kw in ['package="', 'android:name="', 'android.intent.action', 'MainActivity', 'LAUNCHER', 'action="', 'activity']) or s.startswith('com.') or s.startswith('org.'):
        print(s)
