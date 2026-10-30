#!/usr/bin/env python3
"""Deterministic geometry-only GLB derivation from the original Blender-produced K90.
No material, XY, UV, topology or rear-detail additions. No third-party dependency.
Official denim nominal depth8.3mm; regional depth offsets remain a reconstruction.
"""
from pathlib import Path
import argparse,hashlib,json,math,struct
ROOT=Path(__file__).resolve().parents[2]
DEFAULT=ROOT/'app/src/main/assets/devices/redmi-k90-pro-max'
FRONT={'screen_bezel','screen_main_content','selfie_punch_hole','earpiece_grille'}
SIDE={'side_buttons','ports_and_loudspeaker_slots'}
CHASSIS='chassis_unibody'
def sha(b):return hashlib.sha256(b).hexdigest()
def derive(source,out):
 raw=source.read_bytes();assert raw[:4]==b'glTF';jl,jtype=struct.unpack_from('<II',raw,12);assert jtype==0x4e4f534a;g=json.loads(raw[20:20+jl]);bl,btype=struct.unpack_from('<II',raw,20+jl);assert btype==0x004e4942;binary=bytearray(raw[28+jl:28+jl+bl]);assert not g.get('animations') and not g.get('skins')
 original_binary=bytes(binary)
 nodes={n['name']:n for n in g['nodes']};assert FRONT|SIDE|{CHASSIS} <= nodes.keys()
 for node in g['nodes']:
  assert not node.get('matrix') and node.get('rotation',[0,0,0,1])==[0,0,0,1] and node.get('scale',[1,1,1])==[1,1,1]
 def read(i):
  a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];assert a['componentType']==5126 and a['type']=='VEC3' and 'sparse' not in a;off=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',12)
  return [struct.unpack_from('<fff',binary,off+k*stride) for k in range(a['count'])]
 def write(i,vs):
  a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];off=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',12)
  for k,xyz in enumerate(vs):struct.pack_into('<fff',binary,off+k*stride,*xyz)
  if 'min' in a:a['min']=[min(v[k] for v in vs) for k in range(3)]
  if 'max' in a:a['max']=[max(v[k] for v in vs) for k in range(3)]
 def bound(name):
  node=nodes[name];t=node.get('translation',[0,0,0]);ps=[v for p in g['meshes'][node['mesh']]['primitives'] for v in read(p['attributes']['POSITION'])]
  return [[min(p[i]+t[i] for p in ps),max(p[i]+t[i] for p in ps)] for i in range(3)]
 before={name:bound(name) for name,n in nodes.items() if 'mesh' in n};old=before[CHASSIS][2];olddepth=old[1]-old[0];assert abs(olddepth-.0076)<1e-6;scale=(olddepth+.0004)/olddepth
 used={}
 for name,n in nodes.items():
  if 'mesh' in n:
   for p in g['meshes'][n['mesh']]['primitives']:
    for attr,i in p['attributes'].items():used.setdefault(i,set()).add(name)
 for p in g['meshes'][nodes[CHASSIS]['mesh']]['primitives']:
  attrs=p['attributes'];assert 'TANGENT' not in attrs
  for attr in ['POSITION','NORMAL']:
   i=attrs[attr];assert used[i]=={CHASSIS};vs=read(i)
   if attr=='POSITION':changed=[(x,y,z*scale) for x,y,z in vs]
   else:
    changed=[]
    for x,y,z in vs:
     z/=scale;l=math.sqrt(x*x+y*y+z*z);changed.append((x/l,y/l,z/l))
   write(i,changed)
 rules={}
 for name,node in nodes.items():
  if 'mesh' not in node:continue
  if name==CHASSIS:rules[name]={'operation':'scale_vertices_z_only','scaleZ':scale,'normalTransform':'inverse transpose normalized'};continue
  dz=.0002 if name in FRONT else (0 if name in SIDE else -.0002)
  t=node.get('translation',[0,0,0]);node['translation']=[t[0],t[1],t[2]+dz];rules[name]={'operation':'rigid_translation','deltaMeters':[0,0,dz]}
 after={name:bound(name) for name,n in nodes.items() if 'mesh' in n}
 # Verify all XY and all rigid component extents preserved; front clearance invariant.
 for name in before:
  assert before[name][:2]==after[name][:2]
  if name!=CHASSIS:assert abs((after[name][2][1]-after[name][2][0])-(before[name][2][1]-before[name][2][0]))<1e-12
 def clearance(b):return b['screen_main_content'][2][0]-b['screen_bezel'][2][1]
 assert abs(clearance(before)-clearance(after))<1e-12
 # Verify unchanged UV/index bytes across all actual primitive accessors.
 protected=[]
 for m in g['meshes']:
  for pr in m['primitives']:
   protected.extend(i for key,i in pr['attributes'].items() if key.startswith('TEXCOORD_'));protected.append(pr['indices'])
 for i in set(protected):
  a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];components={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];width={5121:1,5123:2,5125:4,5126:4}[a['componentType']]*components;offset=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',width)
  assert all(binary[offset+k*stride:offset+k*stride+width]==original_binary[offset+k*stride:offset+k*stride+width] for k in range(a['count']))
 g.setdefault('asset',{}).setdefault('extras',{})['denimDerivation']={'baseSha256':sha(raw),'nominalDepthMeters':.0083,'deltaDepthMeters':.0004,'geometryStatus':'official_nominal_dimension_original_component_reconstruction'}
 jb=json.dumps(g,ensure_ascii=False,separators=(',',':'),sort_keys=True).encode();jb+=b' '*((-len(jb))%4);binary+=b'\0'*((-len(binary))%4);result=struct.pack('<III',0x46546c67,2,12+8+len(jb)+8+len(binary))+struct.pack('<II',len(jb),0x4e4f534a)+jb+struct.pack('<II',len(binary),0x004e4942)+binary
 out.parent.mkdir(parents=True,exist_ok=True);out.write_bytes(result)
 meta={'schemaVersion':1,'deviceId':'redmi-k90-pro-max','variantId':'denim','stateId':'portrait','asset':out.name,'sourceAsset':source.name,'sourceSha256':sha(raw),'assetHash':sha(result),'bytes':len(result),'nominalBodySizeMeters':[.07782,.16333,.0083],'nominalDimensionConfidence':'official_specs_body','componentAdaptationConfidence':'estimated_parametric_reconstruction_not_factory_CAD','sourceUrl':'https://mishop-pc.cnbj1.mi-fds.com/mishop-pc/production/mishop_pc/redmi-k90-pro-max/specs/entry-1778678001254.js','operations':rules,'beforeBoundsMeters':before,'afterBoundsMeters':after,'frontScreenToMaskClearanceMeters':clearance(after),'frontScreenToMaskNote':'Preserved original45µm, not225µm; no new depth fix introduced','contracts':{'rootCenterMeters':[0,0,0],'xyUnchanged':True,'uvBinaryUnchanged':True,'topologyUnchanged':True,'materialsUnchanged':True,'frontComponentsRigidShiftMeters':.0002,'rearComponentsRigidShiftMeters':-.0002,'sideComponentsNotStretched':True},'limitations':'Rear components retained from existing simplified/reconstructed asset; only body depth adapted. Optical material values, active-screen dimensions and component offsets remain estimates. Physical body envelope including selfie point differs slightly from advertised nominal depth; all bounds reported explicitly.'}
 out.with_suffix('.geometry.json').write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n');return meta
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source',type=Path,default=DEFAULT/'portrait.glb');p.add_argument('--output',type=Path,default=DEFAULT/'portrait-denim.glb');args=p.parse_args();m=derive(args.source,args.output);print(json.dumps({k:m[k] for k in ['assetHash','sourceSha256','bytes','frontScreenToMaskClearanceMeters']},indent=2))
