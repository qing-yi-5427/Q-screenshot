#!/usr/bin/env python3
"""Inspect exact APK JNI ABI, ZIP offsets and ELF PT_LOAD 16KiB alignment."""
import argparse,json,pathlib,struct,zipfile,sys,hashlib
p=argparse.ArgumentParser(description=__doc__);p.add_argument('apk',type=pathlib.Path);p.add_argument('--output',type=pathlib.Path);a=p.parse_args();results=[]
with a.apk.open('rb') as raw,zipfile.ZipFile(a.apk) as z:
 for info in z.infolist():
  if not info.filename.endswith('.so'):continue
  data=z.read(info);assert data[:4]==b'\x7fELF';is64=data[4]==2;endian='<'if data[5]==1 else'>'
  phoff=struct.unpack_from(endian+('Q'if is64 else'I'),data,32 if is64 else 28)[0];entsize,phnum=struct.unpack_from(endian+'HH',data,54 if is64 else 42);aligns=[]
  for i in range(phnum):
   off=phoff+i*entsize;kind=struct.unpack_from(endian+'I',data,off)[0]
   if kind==1:aligns.append(struct.unpack_from(endian+('Q'if is64 else'I'),data,off+(48 if is64 else 28))[0])
  raw.seek(info.header_offset);header=raw.read(30);n,x=struct.unpack_from('<HH',header,26);offset=info.header_offset+30+n+x
  results.append(dict(path=info.filename,size=len(data),compression=info.compress_type,zipDataOffset=offset,zip16kAligned=offset%16384==0,loadAlignments=aligns,elf16kCompatible=all(x>=16384 for x in aligns)))
report=dict(apk=str(a.apk),sha256=hashlib.sha256(a.apk.read_bytes()).hexdigest(),libraries=results,passed=bool(results)and all(r['elf16kCompatible']and(r['compression']!=0 or r['zip16kAligned'])for r in results))
text=json.dumps(report,indent=2);print(text)
if a.output:a.output.write_text(text+'\n')
sys.exit(0 if report['passed']else 1)
