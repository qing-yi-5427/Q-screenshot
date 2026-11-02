#!/usr/bin/env python3
"""Build into new staging directories; explicitly update only the bundled Fold."""
import argparse,json,os,shutil
from datetime import datetime,timezone
from pathlib import Path
from device_pipeline import model
ROOT=Path(__file__).resolve().parents[1]

def install_builtin_fold(staging):
 """Copy validated Fold outputs and its existing metadata; never install a slab."""
 assets=Path(staging)/'assets';manifest=json.loads((assets/'devices/xiaomi-18-fold/device.json').read_text())
 if manifest['id']!='xiaomi-18-fold' or set(manifest['states'])!={'folded','unfolded'}:
  raise ValueError('Only Xiaomi 18 Fold folded/unfolded may update bundled resources')
 original=json.loads((ROOT/'assets-source/devices/xiaomi-18-fold/device.json').read_text())
 for state,entry in manifest['states'].items():
  if entry['modelPath']!=f'devices/xiaomi-18-fold/{state}.glb':raise ValueError('Unexpected Fold asset path')
  original['states'][state]['assetHash']=manifest['states'][state]['assetHash']
 target=ROOT/'app/src/main/assets/devices/xiaomi-18-fold';target.mkdir(parents=True,exist_ok=True)
 for state in manifest['states'].values():
  shutil.copyfile(assets/state['modelPath'],ROOT/'app/src/main/assets'/state['modelPath'])
 (target/'device.json').write_text(json.dumps(original,ensure_ascii=False,indent=2)+'\n')
 shutil.copyfile(ROOT/'assets-source/devices/xiaomi-18-fold/colorways.json',target/'colorways.json')
 shutil.copyfile(ROOT/'model-library/builtin/xiaomi-18-fold/package.json',target/'package.json')
 catalog={'schemaVersion':1,'defaultDevice':'xiaomi-18-fold','devices':[original]}
 (target.parent/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')

def run(argv=None):
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--profile',type=Path);p.add_argument('--device',choices=list(model.ADAPTERS));p.add_argument('--staging-root','--delivery-root',dest='staging_root',type=Path)
 p.add_argument('--blender');p.add_argument('--dry-run',action='store_true');p.add_argument('--all-states',action='store_true',help='Compatibility option: all profile states are always built')
 p.add_argument('--update-builtin-fold',action='store_true',help='After staging validation, explicitly copy Fold-only GLBs/metadata into APK assets')
 a=p.parse_args(argv)
 if a.profile and a.device:p.error('--profile and --device are mutually exclusive')
 if a.update_builtin_fold and (a.profile or a.device not in (None,'xiaomi-18-fold')):p.error('--update-builtin-fold only accepts the existing Xiaomi 18 Fold source')
 blender=a.blender or os.environ.get('BLENDER') or next((str(x) for x in [ROOT/'.cache/tools/Blender.app/Contents/MacOS/Blender',Path('/Applications/Blender.app/Contents/MacOS/Blender')] if x.is_file()),None) or shutil.which('blender')
 stamp=datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')
 output=(a.staging_root or ROOT/'.cache/pipeline'/('build-'+stamp)).resolve()
 model.safe_output(output)
 if a.profile:
  report=model.build(a.profile,output,blender,a.dry_run)
 else:
  device=a.device or 'xiaomi-18-fold'
  # Keep replay input beside the new output, never under runtime or source trees.
  profile_dir=output.with_name(output.name+'-profile')
  if a.dry_run:
   report={'status':'NOT_RUN','device':device,'stagingRoot':str(output),'profileRoot':str(profile_dir),'states':['folded','unfolded'] if device=='xiaomi-18-fold' else ['portrait']}
  else:
   model.existing(device,profile_dir)
   report=model.build(profile_dir/'profile.json',output,blender)
 if a.update_builtin_fold:
  report['builtinUpdate']='NOT_RUN (dry run)' if a.dry_run else 'xiaomi-18-fold only'
  if not a.dry_run:install_builtin_fold(output)
 print(json.dumps(report,ensure_ascii=False,indent=2))
 return report
if __name__=='__main__':run()
