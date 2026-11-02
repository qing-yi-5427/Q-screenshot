"""Assemble the public immutable-release package index from verified local entries."""
import argparse,json,hashlib,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--entries',type=Path,default=ROOT/'.cache/model-library-entries');p.add_argument('--packages',type=Path,default=ROOT/'deliverables/model-library');p.add_argument('--output',type=Path,default=ROOT/'model-library/catalog.json');a=p.parse_args()
 meta=json.loads((ROOT/'model-library/builtin/xiaomi-18-fold/package.json').read_text())
 devices=[dict(id=meta['id'],name=meta['name'],states=meta['states'],defaultState=meta['defaultState'],notes=meta['notes'],modelVersion=meta['modelVersion'],minAppVersionCode=8000,builtin=True,downloadable=False,packageUrl=None,packageSha256=None,packageBytes=0,previewUrl=None,verificationStatus='partial_reconstruction')]
 for path in sorted(a.entries.glob('*.json')):
  entry=json.loads(path.read_text());package=a.packages/entry['packageUrl'].rsplit('/',1)[1];data=package.read_bytes()
  if len(data)!=entry['packageBytes'] or hashlib.sha256(data).hexdigest()!=entry['packageSha256']:raise ValueError('Index/package mismatch '+str(package))
  with zipfile.ZipFile(package) as z:
   meta=json.loads(z.read('package.json'))
   if meta['id']!=entry['id'] or meta['modelVersion']!=entry['modelVersion']:raise ValueError('Package identity/version mismatch')
  devices.append(entry)
 if len({d['id'] for d in devices})!=len(devices):raise ValueError('Duplicate device identity')
 catalog={'schemaVersion':1,'updatedAt':'2026-11-02','devices':devices};a.output.parent.mkdir(parents=True,exist_ok=True);a.output.write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'devices':len(devices),'downloadable':sum(d['downloadable'] for d in devices),'packageBytes':sum(d['packageBytes'] for d in devices)}))
if __name__=='__main__':main()
