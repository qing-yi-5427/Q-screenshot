#!/usr/bin/env python3
"""Explicit-target, isolated debug export; no UI injection or normal-draft reads."""
from adb_target import explicit_serial
import argparse,hashlib,json,re,subprocess,time,zipfile
from pathlib import Path
from PIL import Image
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True,type=explicit_serial,help='Explicit adb target from adb devices');p.add_argument('--session',required=True);p.add_argument('--apk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);p.add_argument('--edge',type=int);p.add_argument('--environment',type=float);p.add_argument('cases',nargs='+');a=p.parse_args()
assert re.fullmatch(r'xiaomi18-[A-Za-z0-9-]{1,39}',a.session)
assert a.output.resolve().is_relative_to((Path(__file__).resolve().parents[1]/'deliverables/xiaomi18-refresh').resolve())
assert all(re.fullmatch(r'X[0-9]{2}(?:-[A-Za-z]+)*',c) for c in a.cases)
with zipfile.ZipFile(a.apk) as z:assert json.loads(z.read('assets/debug-test-session-contract.json'))['projectRoot']=='files/test-projects/{session}'
a.output.mkdir(parents=True,exist_ok=True);adb=['adb','-s',a.serial]
def run(*args):return subprocess.check_output(adb+list(args),timeout=30)
sha=hashlib.sha256(a.apk.read_bytes()).hexdigest();base=run('shell','pm','path','dev.qingframe').decode().strip().removeprefix('package:');assert base.endswith('/base.apk');installed=run('shell','sha256sum',base).decode().split()[0];assert installed==sha
r={'serial':a.serial,'session':a.session,'apkSha256':sha,'installedApkSha256':installed,'edgeOverride':a.edge,'environmentOverride':a.environment,'cases':[]}
for case in a.cases:
 run('shell','am','force-stop','dev.qingframe');cmd=['shell','am','start','-W','-n','dev.qingframe/.MainActivity','--es','test_session',a.session,'--es','test_case',case]
 if a.edge:cmd+=['--ei','test_edge',str(a.edge)]
 if a.environment is not None:cmd+=['--ef','test_environment',str(a.environment)]
 start=time.monotonic();run(*cmd);pid=run('shell','pidof','dev.qingframe').decode().strip().split()[0]
 while True:
  logs=run('logcat','-d','--pid',pid,'-s','QF_EXPORT:I','QF_RENDER:I','QF_TEST:I','AndroidRuntime:E').decode(errors='replace')
  if f'COMPLETE {case} ' in logs:break
  if 'FAILED ' in logs or 'FATAL EXCEPTION' in logs or time.monotonic()-start>150:raise RuntimeError(logs)
  time.sleep(.5)
 (a.output/f'{case}-logcat.txt').write_text(logs);data=run('exec-out','run-as','dev.qingframe','cat',f'files/exports/{a.session}/{case}.png');assert data.endswith(b'\x00\x00\x00\x00IEND\xaeB`\x82');path=a.output/f'{case}.png';path.write_bytes(data)
 with Image.open(path) as im:
  im.load()
  if not a.edge and case.startswith('X08'):assert im.size==((3840,2160) if 'landscape' in case else (2160,3840))
  info={'case':case,'command':adb+cmd,'size':list(im.size),'sha256':hashlib.sha256(data).hexdigest(),'readbackMs':re.findall(r'readback \d+x\d+ elapsedMs=(\d+)',logs),'wallSeconds':round(time.monotonic()-start,3)}
  if case.startswith(('X06','X07')):
   al=im.getchannel('A');corners=[al.getpixel(q) for q in [(0,0),(im.width-1,0),(0,im.height-1),(im.width-1,im.height-1)]];assert corners==[0,0,0,0];info['cornerAlpha']=corners;hist=al.histogram();assert hist[0]>0 and hist[255]>0;info['alphaCounts']={'zero':hist[0],'opaque':hist[255],'partial':sum(hist[1:255])}
 r['cases'].append(info);(a.output/'run.json').write_text(json.dumps(r,indent=2)+'\n');print(info,flush=True)
