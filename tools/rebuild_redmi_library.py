"""Rebuild reviewed REDMI library profiles into fresh staging; never touches APK assets."""
import argparse,concurrent.futures,json,os,subprocess,sys
from pathlib import Path
from datetime import datetime,timezone
from model_library_pack import pack
ROOT=Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument('--staging-root',type=Path,default=ROOT/'.cache/model-library-staging'/('redmi-r3-'+datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')));p.add_argument('--packages',type=Path,default=ROOT/'deliverables/model-library-r3-final');p.add_argument('--entries',type=Path,default=ROOT/'.cache/model-library-r3-final-entries');p.add_argument('--workers',type=int,default=3);a=p.parse_args()
 if a.staging_root.exists():raise ValueError('Fresh staging root required')
 a.staging_root.mkdir(parents=True);a.packages.mkdir(parents=True,exist_ok=True);a.entries.mkdir(parents=True,exist_ok=True)
 def one(profile):
  slug=profile.parent.name;stage=a.staging_root/slug
  subprocess.run([sys.executable,str(ROOT/'tools/build_assets.py'),'--profile',str(profile),'--staging-root',str(stage)],check=True,capture_output=True,text=True)
  metadata=json.loads((profile.parent/'package.json').read_text());report=json.loads((stage/'build-report.json').read_text());metadata['validation'].update(contract='PASS',khronos=report['khronos']);(profile.parent/'package.json').write_text(json.dumps(metadata,ensure_ascii=False,indent=2)+'\n')
  entry=pack(stage/'assets',slug,metadata,a.packages/(slug+'-'+metadata['modelVersion']+'.zip'));entry['packageUrl']=entry['packageUrl'].replace('models-2026-11/','models-2026-11-r3/');(a.entries/(slug+'.json')).write_text(json.dumps(entry,ensure_ascii=False,indent=2)+'\n');(profile.parent/'build-report.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(slug,entry['packageSha256'],flush=True);return slug
 profiles=sorted((ROOT/'model-library/source').glob('redmi-*/profile.json'))
 if any(x.parent.name=='redmi-k90-pro-max' for x in profiles):raise ValueError('AcceptedK90ProMax must not be rebuilt')
 list(concurrent.futures.ThreadPoolExecutor(max_workers=a.workers).map(one,profiles));print('Built',len(profiles),'REDMI profiles; acceptedK90ProMax untouched')
if __name__=='__main__':main()
