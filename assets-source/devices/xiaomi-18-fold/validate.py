#!/usr/bin/env python3
"""Validate genuine Xiaomi18Fold two-state contract without touching other assets/reports."""
from pathlib import Path
import json,struct,hashlib,subprocess,sys
ROOT=Path(__file__).resolve().parents[3];SRC=ROOT/'assets-source/devices/xiaomi-18-fold';OUT=Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'deliverables/xiaomi18-refresh/evidence';d=json.loads((SRC/'device.json').read_text());reports=[]
for state in ['folded','unfolded']:
 p=ROOT/'app/src/main/assets/devices/xiaomi-18-fold'/(state+'.glb');data=p.read_bytes();assert data[:4]==b'glTF';n=struct.unpack_from('<I',data,12)[0];g=json.loads(data[20:20+n]);binary=data[28+n:]
 def read(i):
  a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];N={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];fmt={5126:'f',5125:'I',5123:'H'}[a['componentType']];step=struct.calcsize(fmt)*N
  return [struct.unpack_from('<'+fmt*N,binary,v.get('byteOffset',0)+a.get('byteOffset',0)+j*v.get('byteStride',step)) for j in range(a['count'])]
 s=d['states'][state];nodes={n.get('name'):n for n in g['nodes']};assert 'device_root' in nodes;assert s['activeScreenNode'] in nodes;assert len([n for n in g['nodes'] if n.get('name')==s['activeScreenNode']])==1
 primitives=g['meshes'][nodes[s['activeScreenNode']]['mesh']]['primitives'];assert len(primitives)==1;pr=primitives[0];assert g['materials'][pr['material']]['name']=='screen_content';uv=read(pr['attributes']['TEXCOORD_0']);normal=read(pr['attributes']['NORMAL']);pos=read(pr['attributes']['POSITION']);sw,sh=s['screenSizeMeters'];assert all(n[2]>.999 for n in normal)
 translation=nodes[s['activeScreenNode']].get('translation',[0,0,0]); expected=s.get('screenCenterMeters',[0,0,0]); assert abs(translation[0]-expected[0])<1e-7 and abs(translation[1]-expected[1])<1e-7
 assert all(abs(t[0]-(v[0]/sw+.5))<1e-5 and abs(t[1]-(.5-v[1]/sh))<1e-5 for v,t in zip(pos,uv))
 assert {'chassis_main','chassis_secondary','hinge_shell','back_panel_main','ports_and_speaker','side_buttons'}.issubset(nodes)
 active_uses=sum(1 for m in g['meshes'] for pr in m['primitives'] if g['materials'][pr['material']]['name']=='screen_content');assert active_uses==1
 assert not any('camera_' in name or 'lens_' in name or 'inactive' in name for name in nodes if name)
 assert sum(1 for x in s['screenBindings'] if x['active'])==1
 assert not g.get('images');assert all(all(v>0 for v in n.get('scale',[1,1,1])) for n in g['nodes']);assert all(sv>0 and sv<.3 for sv in s['bodySizeMeters'])
 tris=sum(g['accessors'][pr['indices']]['count']//3 for m in g['meshes'] for pr in m['primitives']);points=[pt for m in g['meshes'] for pr in m['primitives'] for pt in read(pr['attributes']['POSITION'])];bounds=[[min(p[i] for p in points),max(p[i] for p in points)] for i in range(3)];sha=hashlib.sha256(data).hexdigest();assert sha==s['assetHash']
 reports.append({'state':state,'asset':str(p.relative_to(ROOT)),'sha256':sha,'bytes':len(data),'triangles':tris,'renderables':len(g['meshes']),'boundsMeters':bounds,'bodyMeters':s['bodySizeMeters'],'screenMeters':s['screenSizeMeters'],'activeScreenNode':s['activeScreenNode'],'screenNormal':'+Z','uv':'Every vertex checked: x/w+.5,.5-y/h','vendorImagesEmbedded':False,'uniqueActiveContent':True,'contract':'PASS'})
OUT.mkdir(parents=True,exist_ok=True);(OUT/'contract-validation.json').write_text(json.dumps(reports,indent=2));print(json.dumps(reports,indent=2))
