import struct
from pathlib import Path
p=Path('Base.apk');
import zipfile
with zipfile.ZipFile(p) as z:
    data=z.read('AndroidManifest.xml')
# helper
def read_u32(data, offset):
    return struct.unpack('<I', data[offset:offset+4])[0]
def read_u16(data, offset):
    return struct.unpack('<H', data[offset:offset+2])[0]

# parse string pool
if read_u16(data, 0)!=0x0003:
    print('BAD_HEADER', hex(read_u16(data,0)))
    raise SystemExit(1)
# skip header
chunk_type=read_u16(data,0)
chunk_size=read_u32(data,4)
string_pool_off=0
# string pool starts at first chunk after header
# Actually data starts with file header 0x00080003? Let's search for string pool type
# We'll parse sequentially.
idx=0
strings=[]
resource_map=None
while idx < len(data):
    ctype=read_u16(data,idx)
    if ctype==0:
        # maybe start of file with chunk type as u16 + u16? use u32 directly
        ctype=read_u32(data,idx)
    if idx+8>len(data): break
    csize=read_u32(data,idx+4)
    if csize==0: break
    if ctype==0x0001:
        # string pool
        string_count=read_u32(data,idx+8)
        style_count=read_u32(data,idx+12)
        flags=read_u32(data,idx+16)
        strings_start=read_u32(data,idx+20)
        styles_start=read_u32(data,idx+24)
        is_utf8=flags & 0x100
        offsets=[]
        for i in range(string_count):
            offsets.append(read_u32(data, idx+28 + 4*i))
        base=idx+strings_start
        for off in offsets:
            s_off=base+off
            if is_utf8:
                # utf-8 string: skip utf-16 length, utf-8 length
                u16len = data[s_off]
                if u16len & 0x80: u16len = ((u16len & 0x7f) << 7) | data[s_off+1]
                s_off += 1 if u16len < 0x80 else 2
                u8len = data[s_off]
                if u8len & 0x80: u8len = ((u8len & 0x7f) << 7) | data[s_off+1]
                s_off += 1 if u8len < 0x80 else 2
                s = data[s_off:s_off+u8len].decode('utf-8', errors='ignore')
            else:
                u16len = read_u16(data,s_off)
                if u16len & 0x8000:
                    u16len = ((u16len & 0x7fff) << 15) | (read_u16(data,s_off+2) & 0x7fff)
                    s_off += 4
                else:
                    s_off += 2
                s = data[s_off:s_off+u16len*2].decode('utf-16le', errors='ignore')
            strings.append(s)
    elif ctype==0x0180:
        count=(csize-8)//4
        resource_map=[read_u32(data, idx+8+4*i) for i in range(count)]
    idx += csize
# parse start tags
idx=chunk_size
pkg=None
activities=[]
while idx < len(data):
    if idx+8>len(data): break
    ctype = read_u16(data,idx)
    csize = read_u32(data,idx+4)
    if ctype==0x0102:
        # start element
        namespace_idx=read_u32(data,idx+8)
        name_idx=read_u32(data,idx+12)
        flags=read_u32(data,idx+16)
        attribute_count=read_u16(data,idx+20)
        class_attr=read_u16(data,idx+22)
        attr_off=idx+36
        elem_name = strings[name_idx] if name_idx < len(strings) else None
        attrs=[]
        for i in range(attribute_count):
            attr_ns = read_u32(data,attr_off+20*i)
            attr_name = read_u32(data,attr_off+20*i+4)
            attr_raw = read_u32(data,attr_off+20*i+8)
            attr_type = read_u16(data,attr_off+20*i+16)
            attr_data = read_u32(data,attr_off+20*i+20)
            name = strings[attr_name] if attr_name < len(strings) else None
            value = None
            if attr_raw != 0xFFFFFFFF and attr_raw < len(strings):
                value = strings[attr_raw]
            elif attr_type>>24 == 0x03:
                value = strings[attr_data] if attr_data < len(strings) else None
            else:
                value = str(attr_data)
            attrs.append((name,value))
        if elem_name=='manifest':
            for name,value in attrs:
                if name=='package':
                    pkg=value
        if elem_name=='activity':
            actname=None
            for name,value in attrs:
                if name=='name': actname=value
            if actname: activities.append(actname)
    idx += csize if csize>0 else 8
print('PACKAGE:'+str(pkg))
print('ACTIVITIES:'+','.join(activities))
