import zipfile,re
apk='Base.apk'
with zipfile.ZipFile(apk) as z:
    data=z.read('AndroidManifest.xml')
    txt=data.decode('utf-8','ignore')
    m=re.search(r'package="([^"]+)"', txt)
    print('PACKAGE:'+m.group(1) if m else 'NO_PACKAGE')
    acts=re.findall(r'android:name="([^"]+)"', txt)
    print('ACTS:'+','.join(acts))
