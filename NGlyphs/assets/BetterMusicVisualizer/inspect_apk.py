import zipfile, re
from pathlib import Path
apk = Path('Base.apk')
if not apk.exists():
    print('MISSING_BASE_APK')
    raise SystemExit(1)
with zipfile.ZipFile(apk) as z:
    names = z.namelist()
    print('FILES:'+','.join(names[:20]) + (',...' if len(names)>20 else ''))
    if 'AndroidManifest.xml' in names:
        data = z.read('AndroidManifest.xml')
        txt = data.decode('utf-8', errors='ignore')
        pkg = re.search(r'package="([^"]+)"', txt)
        print('PACKAGE:'+pkg.group(1) if pkg else 'NO_PACKAGE')
        acts = re.findall(r'android:name="([^"]+)"', txt)
        print('ACTS:'+','.join(acts))
    else:
        print('NO_MANIFEST')
