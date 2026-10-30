#!/usr/bin/env python3
"""Build Android in an isolated temporary output tree, then copy the actual APK."""
import argparse,hashlib,os,pathlib,shutil,subprocess,sys,tempfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--connected',action='store_true',help='Also run tests on an already authorized device/emulator');p.add_argument('--build-root',type=pathlib.Path,help='Override isolated output root');p.add_argument('--assemble-only',action='store_true');p.add_argument('--output',type=pathlib.Path,default=ROOT/'deliverables/defaults-share/apk/qing-frame-defaults-share-debug.apk',help='APK destination; defaults to current 0.7.0 delivery, preserving earlier APKs');a=p.parse_args()
 identity=hashlib.sha256(str(ROOT).encode()).hexdigest()[:10]
 work=a.build_root or pathlib.Path(tempfile.gettempdir())/f'qing-frame-{identity}'
 work.mkdir(parents=True,exist_ok=True);env=os.environ.copy();env['QINGFRAME_BUILD_ROOT']=str((work/'build').resolve())
 wrapper=ROOT/('gradlew.bat'if os.name=='nt'else'gradlew')
 tasks=[':app:assembleDebug'] if a.assemble_only else ['test',':app:lintDebug',':app:assembleDebug']
 if a.connected:tasks.append(':app:connectedDebugAndroidTest')
 command=[str(wrapper),'--no-daemon','--no-watch-fs','--project-cache-dir',str((work/'gradle-cache').resolve()),*tasks]
 print('Command:',*command,flush=True);subprocess.run(command,cwd=ROOT,env=env,check=True)
 built=work/'build/app/outputs/apk/debug/app-debug.apk'
 if not built.is_file():raise RuntimeError('Gradle succeeded but expected actual APK is missing')
 target=a.output.resolve();target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(built,target)
 digest=hashlib.sha256(target.read_bytes()).hexdigest();assert digest==hashlib.sha256(built.read_bytes()).hexdigest()
 print(f'APK: {target}\nSHA256: {digest}\nBuild root: {work}',flush=True)
if __name__=='__main__':
 try:main()
 except subprocess.CalledProcessError as e:sys.exit(e.returncode)
