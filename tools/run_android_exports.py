#!/usr/bin/env python3
"""Run explicit debug export scenarios on an already authorized Android device."""
from adb_target import explicit_serial
import argparse,json,pathlib,subprocess,time,sys
ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('cases',nargs='+');p.add_argument('--serial',required=True,type=explicit_serial,help='Explicit adb target from adb devices');p.add_argument('--edge',type=int);p.add_argument('--timeout',type=int,default=240);p.add_argument('--output',type=pathlib.Path,default=ROOT/'deliverables/renders');a=p.parse_args();adb=['adb','-s',a.serial]
 def run(*args,**kw):return subprocess.run([*adb,*args],check=True,capture_output=True,**kw)
 a.output.mkdir(parents=True,exist_ok=True);records=[]
 for case in a.cases:
  if not all(c.isalnum()or c in '-_'for c in case):p.error('case must be alphanumeric with hyphen/underscore')
  run('shell','am','force-stop','dev.qingframe');start=time.monotonic()
  command=['shell','am','start','-W','-n','dev.qingframe/.MainActivity','--es','test_case',case]
  if a.edge:command+=['--ei','test_edge',str(a.edge)]
  run(*command);pid=run('shell','pidof','dev.qingframe').stdout.decode().strip().split()[0];logs='';ok=False
  while time.monotonic()-start<a.timeout:
   logs=run('logcat','-d','--pid',pid,'-s','QF_EXPORT:I','QF_RENDER:I','QF_TEST:I','AndroidRuntime:E').stdout.decode(errors='replace')
   done=('EXPORTS=10 COMPLETE'in logs)if case=='S10'else(f'COMPLETE {case} 'in logs)
   if done:ok=True;break
   if 'FAILED 'in logs:break
   time.sleep(1)
  log=ROOT/f'deliverables/evidence/build-and-test-logs/{case}-logcat.txt';log.parent.mkdir(parents=True,exist_ok=True);log.write_text(logs)
  if not ok:raise RuntimeError(f'{case}: no successful completion; see {log}')
  names=[f'S10-{i:02}'for i in range(1,11)]if case=='S10'else[case]
  for name in names:
   data=run('exec-out','run-as','dev.qingframe','cat',f'files/exports/{name}.png').stdout
   if not data.startswith(b'\x89PNG\r\n\x1a\n'):raise RuntimeError('Readback file is not PNG')
   target=a.output/f'{name}.png';target.write_bytes(data)
  record=dict(case=case,pid=pid,wallSeconds=round(time.monotonic()-start,3),files=[str(a.output/f'{n}.png')for n in names],log=str(log));records.append(record);print(json.dumps(record),flush=True)
 (a.output/'android-run.json').write_text(json.dumps(records,indent=2)+'\n')
if __name__=='__main__':
 try:main()
 except (subprocess.CalledProcessError,RuntimeError)as e:print(str(e),file=sys.stderr);sys.exit(1)
