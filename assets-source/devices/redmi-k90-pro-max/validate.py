#!/usr/bin/env python3
"""Validate dedicated REDMI runtime contract without changing other device reports."""
from pathlib import Path
import json,struct,hashlib,subprocess
ROOT=Path(__file__).resolve().parents[3];D=ROOT/'assets-source/devices/redmi-k90-pro-max';OUT=ROOT/'deliverables/evidence/redmi-k90-pro-max';P=ROOT/'app/src/main/assets/devices/redmi-k90-pro-max/portrait.glb'
b=P.read_bytes();assert b[:4]==b'glTF';n=struct.unpack_from('<I',b,12)[0];g=json.loads(b[20:20+n]);binary=b[28+n:]
def read(i):
 a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];N={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];fmt={5126:'f',5125:'I',5123:'H'}[a['componentType']];sz=struct.calcsize(fmt)*N
 return [struct.unpack_from('<'+fmt*N,binary,v.get('byteOffset',0)+a.get('byteOffset',0)+j*v.get('byteStride',sz)) for j in range(a['count'])]
d=json.loads((D/'device.json').read_text());state=d['states']['portrait'];ns=[n for n in g['nodes'] if n.get('name')=='screen_main_content'];assert len(ns)==1;pr=g['meshes'][ns[0]['mesh']]['primitives'];assert len(pr)==1;pr=pr[0];assert g['materials'][pr['material']]['name']=='screen_content'
pos=read(pr['attributes']['POSITION']);uv=read(pr['attributes']['TEXCOORD_0']);normal=read(pr['attributes']['NORMAL']);sw,sh=state['screenSizeMeters'];assert all(v[2]>.999 for v in normal);assert all(abs(t[0]-(v[0]/sw+.5))<1e-5 and abs(t[1]-(.5-v[1]/sh))<1e-5 for v,t in zip(pos,uv));assert abs(sw/sh-1200/2608)<1e-8
names={n.get('name') for n in g['nodes']};assert not any('hinge' in n or 'secondary' in n for n in names if n);assert {'chassis_unibody','camera_volcano_platform','rear_speaker_perforations','periscope_window','ports_and_loudspeaker_slots','side_buttons'}.issubset(names)
assert not g.get('images'),'Vendor images/textures must not be embedded';assert all(all(v>0 for v in n.get('scale',[1,1,1])) for n in g['nodes'])
points=[pt for m in g['meshes'] for p in m['primitives'] for pt in read(p['attributes']['POSITION'])];bounds=[[min(p[i] for p in points),max(p[i] for p in points)] for i in range(3)];tris=sum(g['accessors'][p['indices']]['count']//3 for m in g['meshes'] for p in m['primitives']);assert tris<100000
report={'asset':str(P.relative_to(ROOT)),'sha256':hashlib.sha256(b).hexdigest(),'bytes':len(b),'triangles':tris,'renderables':len(g['meshes']),'boundsMeters':bounds,'bodyMeters':state['bodySizeMeters'],'screenMeters':state['screenSizeMeters'],'screenAspect':sw/sh,'activeScreen':'screen_main_content','normals':'+Z all screen vertices verified','uv':'top-left(0,0); every vertex checked','bodyType':'single slab; no hinge or secondary chassis','vendorImagesEmbedded':False,'contract':'PASS'}
OUT.mkdir(parents=True,exist_ok=True);(OUT/'model-contract.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
js="const fs=require('fs'),v=require(process.argv[1]);v.validateBytes(new Uint8Array(fs.readFileSync(process.argv[2])),{uri:process.argv[2],maxIssues:10000}).then(r=>{fs.writeFileSync(process.argv[3],JSON.stringify(r,null,2));console.log(r.issues);if(r.issues.numErrors)process.exit(1)});"
subprocess.run(['node','-e',js,str(ROOT/'.cache/tools/gltf-validator/node_modules/gltf-validator'),str(P),str(OUT/'model-khronos.json')],check=True)
