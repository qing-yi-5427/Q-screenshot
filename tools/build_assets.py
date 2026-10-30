#!/usr/bin/env python3
"""Rebuild active dedicated device models. Historical generic/MIX outputs are preserved."""
import argparse,hashlib,json,os,shutil,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--device',choices=['xiaomi-18-fold','redmi-k90-pro-max']);p.add_argument('--all-states',action='store_true');p.add_argument('--dry-run',action='store_true');p.add_argument('--delivery-root',type=Path,default=ROOT/'deliverables/colors-panels');a=p.parse_args()
blender=os.environ.get('BLENDER') or next((str(x) for x in [Path('/Applications/Blender.app/Contents/MacOS/Blender'),ROOT/'.cache/tools/Blender.app/Contents/MacOS/Blender'] if x.is_file()),None) or shutil.which('blender')
if not blender:raise SystemExit('Set BLENDER to Blender4.5')
active=['xiaomi-18-fold','redmi-k90-pro-max']
for name in active:
 if a.device and name!=a.device:continue
 states=['folded','unfolded'] if name=='xiaomi-18-fold' else ['portrait']
 for state in states:
  script='build_xiaomi18_fold.py' if name=='xiaomi-18-fold' else 'build_redmi_k90.py'
  command=[blender,'-b','--python',str(ROOT/'tools/blender'/script)]
  if name=='xiaomi-18-fold':command+=['--',state]
  if a.dry_run:print(name,state,command);continue
  subprocess.run(command,check=True)
if a.dry_run:raise SystemExit(0)
subprocess.run([os.sys.executable,str(ROOT/'tools/sync_colorways.py')],check=True)
subprocess.run([os.sys.executable,str(ROOT/'tools/blender/build_redmi_denim_variant.py')],check=True)
catalog=[]
for name in active:
 source=ROOT/'assets-source/devices'/name/'device.json';d=json.loads(source.read_text())
 for state in d['states'].values():state['assetHash']=hashlib.sha256((ROOT/'app/src/main/assets'/state['modelPath']).read_bytes()).hexdigest()
 text=json.dumps(d,ensure_ascii=False,indent=2)+'\n';source.write_text(text);target=ROOT/'app/src/main/assets/devices'/name;(target/'device.json').write_text(text)
 shutil.copytree(target,a.delivery_root/'models'/name,dirs_exist_ok=True);catalog.append(d)
text=json.dumps({'schemaVersion':1,'defaultDevice':'redmi-k90-pro-max','devices':catalog},ensure_ascii=False,indent=2)+'\n'
(ROOT/'app/src/main/assets/devices/catalog.json').write_text(text);(a.delivery_root/'models/catalog.json').write_text(text)
