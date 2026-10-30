#!/usr/bin/env python3
"""Contract checks including actual accessor UV/normal/position binary values."""
import json,struct,hashlib,subprocess,argparse,math
ap=argparse.ArgumentParser(description=__doc__);ap.add_argument("--all",action="store_true");ap.add_argument("--evidence-dir",default="deliverables/xiaomi18-refresh/evidence");args=ap.parse_args()
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
OUT=(ROOT/args.evidence_dir).resolve();OUT.mkdir(parents=True,exist_ok=True)
catalog=json.loads((ROOT/'app/src/main/assets/devices/catalog.json').read_text())
assert {d['deviceId'] for d in catalog['devices']}=={p.parent.name for p in (ROOT/'app/src/main/assets/devices').glob('*/device.json')},'Runtime catalog/manifest set differs'
expected={ROOT/'app/src/main/assets'/st['modelPath'] for d in catalog['devices'] for st in d['states'].values()}
assert expected==set((ROOT/'app/src/main/assets/devices').glob('*/*.glb')),'Missing or obsolete runtime GLB'

def load(p):
 b=p.read_bytes();assert b[:4]==b'glTF';n,t=struct.unpack_from('<II',b,12);g=json.loads(b[20:20+n]);ln,typ=struct.unpack_from('<II',b,20+n);return g,b[28+n:28+n+ln]
def accessor(g,b,i):
 a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];size={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];fmt={5126:'f',5123:'H',5125:'I'}[a['componentType']];step=struct.calcsize(fmt)*size
 return [struct.unpack_from('<'+fmt*size,b,v.get('byteOffset',0)+a.get('byteOffset',0)+j*v.get('byteStride',step)) for j in range(a['count'])]
reports=[]
for p in sorted((ROOT/'app/src/main/assets/devices').glob('*/*.glb')):
 g,b=load(p);device=json.loads((p.parent/'device.json').read_text())
 assert device['schemaVersion']==1
 for field in ['deviceId','displayName','manufacturer','assetVersion','physicalDimensions','states','materialVariants','defaultPose','cameraFit','attribution','approximationNotes']:assert field in device,f'missing {field}'
 assert device['deviceId']==p.parent.name
 assert device['verificationStatus'] in {'verified_reconstruction','partial_reconstruction','generic_approximation'}
 assert device['physicalDimensions']['units']=='meters'
 state=device['states'][p.stem]
 for field in ['modelPath','screenBindings','screenAspectRatio','inputSizeHints','orientationPresets','assetHash']:assert field in state,f'missing {field}'
 assert state['modelPath']==str(p.relative_to(ROOT/'app/src/main/assets'))
 assert state['assetHash']==hashlib.sha256(p.read_bytes()).hexdigest()
 for dimensions in [state['bodySizeMeters'],state['screenSizeMeters']]:assert all(isinstance(v,(int,float)) and math.isfinite(v) and v>0 for v in dimensions)
 assert 0.3<state['screenAspectRatio']<3
 assert len([x for x in state['screenBindings'] if x['active']])==1
 active=state['activeScreenNode'];ns=[n for n in g['nodes'] if n.get('name')==active];assert len(ns)==1
 prim=g['meshes'][ns[0]['mesh']]['primitives'];assert len(prim)==1;prim=prim[0];assert g['materials'][prim['material']]['name']=='screen_content'
 pos=accessor(g,b,prim['attributes']['POSITION']);uv=accessor(g,b,prim['attributes']['TEXCOORD_0']);norm=accessor(g,b,prim['attributes']['NORMAL']);assert all(n[2]>.999 for n in norm),'screen normal wrong'
 sw,sh=state['screenSizeMeters']
 assert all(abs(t[0]-(v[0]/sw+.5))<1e-5 and abs(t[1]-(.5-v[1]/sh))<1e-5 for v,t in zip(pos,uv)),'UV orientation wrong'
 assert max(abs(v[0]) for v in pos)<.2 and max(abs(v[1]) for v in pos)<.2
 assert all(not n.get('scale') or all(v>0 for v in n['scale']) for n in g['nodes'])
 points=[pt for mesh in g['meshes'] for pr in mesh['primitives'] for pt in accessor(g,b,pr['attributes']['POSITION'])];bounds=[[min(p[i] for p in points),max(p[i] for p in points)] for i in range(3)]
 triangles=sum(g['accessors'][pr['indices']]['count']//3 for m in g['meshes'] for pr in m['primitives'])
 reports.append({'path':str(p.relative_to(ROOT)),'bytes':p.stat().st_size,'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'renderables':len(g['meshes']),'triangles':triangles,'boundsMeters':bounds,'activeScreen':active,'screenNormal':'+Z','uv':'top-left (0,0), bottom-right (1,1); checked every vertex','contract':'PASS'})
(OUT/'model-contract-validation.json').write_text(json.dumps(reports,indent=2));print(json.dumps(reports,indent=2))
# Official validator installed separately at the pinned version. No fake success if absent.
module=ROOT/'.cache/tools/gltf-validator/node_modules/gltf-validator'
if module.exists():
 js="""const fs=require('fs'),v=require(process.argv[1]); (async()=>{let failed=false;for(const p of process.argv.slice(3)){let r=await v.validateBytes(new Uint8Array(fs.readFileSync(p)),{uri:p,maxIssues:10000});fs.writeFileSync(process.argv[2]+'/model-khronos-'+p.split('/').slice(-2).join('-')+'.json',JSON.stringify(r,null,2));console.log(p,r.issues.numErrors,r.issues.numWarnings);failed ||= r.issues.numErrors>0;}if(failed)process.exit(1);})();"""
 subprocess.run(['node','-e',js,str(module),str(OUT)]+[str(p.relative_to(ROOT)) for p in sorted((ROOT/'app/src/main/assets/devices').glob('*/*.glb'))],cwd=ROOT,check=True)
else:print('Khronos validator NOT RUN: install pinned npm package as documented')
