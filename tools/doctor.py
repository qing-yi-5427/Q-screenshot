#!/usr/bin/env python3
"""Read-only local Android toolchain inventory. Never installs or accepts licenses."""
import argparse, datetime, json, os, pathlib, platform, shutil, subprocess

def command(args):
    try:
        p = subprocess.run(args, capture_output=True, text=True, timeout=20)
        return {'command': args, 'exitCode': p.returncode, 'output': (p.stdout+p.stderr).strip()}
    except (OSError, subprocess.TimeoutExpired) as e:
        return {'command': args, 'exitCode': None, 'output': str(e)}

def main():
    ap=argparse.ArgumentParser(description=__doc__); ap.add_argument('--json',action='store_true'); ap.add_argument('--sdk',type=pathlib.Path,help='SDK directory; defaults to environment or platform installation'); args=ap.parse_args()
    default_sdk=pathlib.Path.home()/('Library/Android/sdk' if platform.system()=='Darwin' else 'Android/Sdk')
    if platform.system()=='Windows': default_sdk=pathlib.Path(os.environ.get('LOCALAPPDATA',str(pathlib.Path.home()/'AppData/Local')))/'Android/Sdk'
    sdk=args.sdk or pathlib.Path(os.environ.get('ANDROID_HOME',os.environ.get('ANDROID_SDK_ROOT',str(default_sdk))))
    blender=next((str(p) for p in [pathlib.Path('.cache/tools/Blender.app/Contents/MacOS/Blender'),pathlib.Path('/Applications/Blender.app/Contents/MacOS/Blender')] if p.exists()),shutil.which('blender') or 'blender')
    commands={'java':['java','-version'],'python':[shutil.which('python3') or 'python3','--version'], 'adb':[str(sdk/'platform-tools/adb'),'version'], 'devices':[str(sdk/'platform-tools/adb'),'devices','-l'], 'emulator':[str(sdk/'emulator/emulator'),'-version'], 'avds':[str(sdk/'emulator/emulator'),'-list-avds'], 'git':['git','status','--short'], 'blender':[blender,'--version']}
    d={'timestamp':datetime.datetime.now(datetime.timezone.utc).isoformat(),'host':platform.platform(),'architecture':platform.machine(),'sdk':str(sdk),'diskFreeBytes':shutil.disk_usage('.').free,'platforms':sorted(p.name for p in (sdk/'platforms').glob('*')),'buildTools':sorted(p.name for p in (sdk/'build-tools').glob('*')),'systemImages':sorted(str(p.relative_to(sdk)) for p in (sdk/'system-images').glob('*/*/*/package.xml')),'gradleDistributions':sorted(p.name for p in (pathlib.Path.home()/'.gradle/wrapper/dists').glob('*')),'tools':{k:command(v) for k,v in commands.items()}}
    print(json.dumps(d,ensure_ascii=False,indent=2) if args.json else '\n'.join(f'{k}: {v}' for k,v in d.items()))
if __name__=='__main__': main()
