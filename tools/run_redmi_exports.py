#!/usr/bin/env python3
"""Run isolated Redmi debug exports after explicit device-owner handoff.

Never installs APKs, clears application data, reads the gallery, changes display
settings, or reads files/project. --dry-run makes no ADB calls. Every ADB call
uses the one allowed serial. App test_session isolation must already be built.
"""
from adb_target import explicit_serial
import argparse
import datetime
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import time
import zipfile
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'deliverables/evidence/redmi-k90-pro-max'
PACKAGE='dev.qingframe'
CASES=('R01-front','R02-left','R02-right','R03-back','R04-transparent','R05-shadow',
       'R06-portrait','R06-landscape','R07-zero','R07-default','R07-strong',
       'R08-lasttexture','R09-snapshot','R10','R11-modelswitch','R12-hero')

def atomic_json(path,value):
    path.parent.mkdir(parents=True,exist_ok=True)
    temporary=path.with_suffix(path.suffix+'.tmp')
    temporary.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')
    temporary.replace(path)

def start_command(session,case=None,edge=None,import_fixture=False):
    command=['shell','am','start','-W','-n',f'{PACKAGE}/.MainActivity','--es','test_session',session]
    if case: command+=['--es','test_case',case]
    if edge: command+=['--ei','test_edge',str(edge)]
    if import_fixture: command+=['--ez','test_import_fixture','true']
    return command

def png_report(data,case,expected=None):
    if not data.startswith(b'\x89PNG\r\n\x1a\n') or not data.endswith(b'\x00\x00\x00\x00IEND\xaeB`\x82'):
        raise RuntimeError('PNG is empty, incomplete, or missing its final IEND chunk')
    with Image.open(io.BytesIO(data)) as image:
        image.verify()
    with Image.open(io.BytesIO(data)) as image:
        image.load()
        if image.format!='PNG' or min(image.size)<=0: raise RuntimeError('Invalid PNG pixels')
        if expected and image.size!=expected: raise RuntimeError(f'PNG dimensions {image.size} do not match {expected}')
        report={'width':image.width,'height':image.height,'mode':image.mode,'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        if case in ('R04-transparent','R05-shadow'):
            if image.mode!='RGBA': raise RuntimeError('Transparent case has no RGBA channel')
            alpha=image.getchannel('A');histogram=alpha.histogram()
            corners=[alpha.getpixel(p) for p in [(0,0),(image.width-1,0),(0,image.height-1),(image.width-1,image.height-1)]]
            if any(corners) or not histogram[0] or not histogram[255]: raise RuntimeError('Transparent canvas or opaque subject alpha check failed')
            report['alpha']={'cornerValues':corners,'zeroPixels':histogram[0],'opaquePixels':histogram[255],'partialPixels':sum(histogram[1:255])}
        return report

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('cases',nargs='*',choices=CASES)
    p.add_argument('--serial',required=True,type=explicit_serial,help='Explicit adb target from adb devices')
    p.add_argument('--session',required=True,help='Isolated redmi-* session; never the normal project')
    p.add_argument('--apk',type=Path,required=True,help='Already-installed APK artifact; runner only verifies its SHA')
    p.add_argument('--edge',type=int,choices=range(256,3841),metavar='256..3840')
    p.add_argument('--timeout',type=int,default=180,help='Per-case deadline; R10 may need --timeout 300')
    p.add_argument('--output',type=Path,help='Must stay under the Redmi evidence directory; defaults to runs/<session>')
    p.add_argument('--import-fixture',action='store_true',help='Start the isolated editor with its own FileProvider fixture URI, no test_case; no gallery access')
    p.add_argument('--dry-run',action='store_true',help='Print command/path contract without calling ADB or creating reports')
    a=p.parse_args()
    if not re.fullmatch(r'redmi-[A-Za-z0-9-]{1,42}',a.session): p.error('--session must match the App contract: redmi-* with letters/digits/hyphens, at most 48 characters')
    if bool(a.cases)==a.import_fixture: p.error('Specify either one or more cases, or --import-fixture')
    if a.timeout<5 or a.timeout>600:p.error('--timeout must be 5..600 seconds')
    output=(a.output or EVIDENCE/'runs'/a.session).resolve()
    if not output.is_relative_to(EVIDENCE.resolve()):p.error('--output must stay under deliverables/evidence/redmi-k90-pro-max')
    if not a.apk.is_file():p.error('--apk does not exist')
    artifact_sha=hashlib.sha256(a.apk.read_bytes()).hexdigest()
    remote_root=f'files/exports/{a.session}'
    adb=['adb','-s',a.serial]
    if a.dry_run:
        print(json.dumps({'serial':a.serial,'session':a.session,'artifactSha256':artifact_sha,'localOutput':str(output.relative_to(ROOT)),
          'isolatedProject':f'files/test-projects/{a.session}','remoteExports':remote_root,
          'commands':[[*adb,*start_command(a.session,case,a.edge)] for case in a.cases] if a.cases else [[*adb,*start_command(a.session,import_fixture=True)]],
          'note':'Execution additionally force-stops only this app between cases; it never clears data. No ADB command was executed.'},indent=2));return 0
    try:
        with zipfile.ZipFile(a.apk) as archive:
            contract=json.loads(archive.read('assets/debug-test-session-contract.json'))
        required={'schemaVersion':1,'intentExtra':'test_session','projectRoot':'files/test-projects/{session}','exportRoot':'files/exports/{session}'}
        if any(contract.get(k)!=value for k,value in required.items()): raise ValueError('session contract does not match runner')
    except (OSError,KeyError,ValueError,zipfile.BadZipFile) as error:
        p.error(f'APK lacks the required isolated-session contract; no ADB called: {error}')
    if (output/'run.json').exists():p.error('This session already has a run.json; use a new session to preserve evidence')
    output.mkdir(parents=True,exist_ok=True)
    report={'startedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'serial':a.serial,'session':a.session,'artifactSha256':artifact_sha,
      'artifact':str(a.apk.resolve()),'isolatedProject':f'files/test-projects/{a.session}','remoteExports':remote_root,
      'memoryScope':'Post-export PSS is not peak CPU/GPU memory','visualReview':'required separately','status':'running','cases':[]}
    def run(*args,timeout=20):
        result=subprocess.run([*adb,*args],capture_output=True,timeout=timeout)
        if result.returncode:raise RuntimeError(f'ADB command failed: {args[0:3]}: {result.stderr.decode(errors="replace").strip()}')
        return result.stdout
    def save():atomic_json(output/'run.json',report)
    save()
    try:
        if run('get-state').decode().strip()!='device':raise RuntimeError('Authorized target is not online; do not reconnect or authorize another device here')
        package_paths=run('shell','pm','path',PACKAGE).decode().splitlines()
        base=next((line.removeprefix('package:') for line in package_paths if line.endswith('/base.apk')),None)
        if not base or not re.fullmatch(r'/data/app/[A-Za-z0-9_~./+=-]+/base\.apk',base):raise RuntimeError('Cannot identify installed APK safely')
        installed_sha=run('shell','sha256sum',base).decode().split()[0]
        report['installedApkSha256']=installed_sha
        if installed_sha!=artifact_sha:raise RuntimeError('Installed APK SHA does not match --apk; device owner must install the intended artifact first')
        report['androidSdk']=run('shell','getprop','ro.build.version.sdk').decode().strip()
        report['model']=run('shell','getprop','ro.product.model').decode().strip()
        save()
        actions=a.cases if a.cases else [None]
        for case in actions:
            item={'case':case or 'isolated-fixture-import','status':'started'};report['cases'].append(item);save()
            run('shell','am','force-stop',PACKAGE)
            start=time.monotonic();run(*start_command(a.session,case,a.edge,a.import_fixture),timeout=45)
            pid=''
            for _ in range(30):
                try:pid=run('shell','pidof',PACKAGE).decode().strip().split()[0];break
                except (RuntimeError,IndexError):time.sleep(.1)
            if not pid.isdigit():raise RuntimeError('App PID unavailable after Activity launch')
            item['pid']=int(pid)
            if a.import_fixture:
                item['status']='launched-not-verified';item['note']='App fixture URI import is launched; inspect the isolated project and UI separately. No gallery command was used.'
                save();continue
            logs='';complete=False
            while time.monotonic()-start<a.timeout:
                logs=run('logcat','-d','--pid',pid,'-s','QF_EXPORT:I','QF_RENDER:I','QF_TEST:I','AndroidRuntime:E').decode(errors='replace')
                complete=('EXPORTS=10 COMPLETE' in logs) if case=='R10' else f'COMPLETE {case} ' in logs
                if complete or 'FAILED ' in logs or 'FATAL EXCEPTION' in logs:break
                time.sleep(.5)
            (output/f'{case}-logcat.txt').write_text(logs)
            item['readbackMs']=[int(ms) for ms in re.findall(r'readback \d+x\d+ elapsedMs=(\d+)',logs)]
            if not complete:raise RuntimeError(f'{case}: no successful completion; see isolated log')
            names=[f'R10-{i:02}' for i in range(1,11)] if case=='R10' else [case]
            item['images']=[]
            for name in names:
                remote=f'{remote_root}/{name}.png';data=b'';verification_error='No bytes read'
                for _ in range(10):
                    try:
                        # Completion must be observed first; pull only this session's PNG.
                        data=run('exec-out','run-as',PACKAGE,'cat',remote)
                        expected=None if a.edge else {'R06-portrait':(2160,3840),'R06-landscape':(3840,2160)}.get(case)
                        image_info=png_report(data,case,expected);break
                    except (RuntimeError,OSError,ValueError) as error:
                        verification_error=str(error)
                        time.sleep(.2)
                else:
                    if data.startswith(b'\x89PNG\r\n\x1a\n') and data.endswith(b'\x00\x00\x00\x00IEND\xaeB`\x82'):
                        (output/f'{name}-failed-verification.png').write_bytes(data)
                    raise RuntimeError(f'{name}: export completion observed, PNG verification failed: {verification_error}')
                (output/f'{name}.png').write_bytes(data);item['images'].append({'file':f'{name}.png',**image_info})
            memory=run('shell','dumpsys','meminfo',PACKAGE).decode(errors='replace');(output/f'{case}-meminfo.txt').write_text(memory)
            match=re.search(r'TOTAL PSS:\s+(\d+)',memory);item['postExportPssKb']=int(match[1]) if match else None
            item['wallSeconds']=round(time.monotonic()-start,3);item['status']='complete';save();print(json.dumps(item),flush=True)
        report['status']='launched-not-verified' if a.import_fixture else 'complete';save();return 0
    except (RuntimeError,subprocess.TimeoutExpired,OSError) as error:
        report['status']='failed';report['error']=str(error)
        if report['cases'] and report['cases'][-1]['status']=='started':report['cases'][-1].update(status='failed',error=str(error))
        save();print(str(error),file=sys.stderr);return 1
if __name__=='__main__':sys.exit(main())
