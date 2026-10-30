#!/usr/bin/env python3
"""Offline migration fixtures and artifact checks for the Xiaomi 18 Fold refresh.

No ADB, installation, gallery access, or normal-project reads are implemented.
Compare takes actual test-session drafts captured later; it is not an App migration.
"""
import argparse
import copy
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import struct
import sys
import zipfile

ROOT=Path(__file__).resolve().parents[1]
EVIDENCE=ROOT/'deliverables/xiaomi18-refresh'
LEGACY={'mix-fold-4','wide-fold'}
TARGET='xiaomi-18-fold'
REDMI='redmi-k90-pro-max'
FLOAT_FIELDS={'zoom','offsetX','offsetY','cropX','cropY','fov','lightDirection','lightIntensity','environmentRotation','reflection','shadow','softness'}


def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def write(path,value):
    path=Path(path)
    if not path.resolve().is_relative_to(EVIDENCE.resolve()):raise ValueError('Evidence output must stay within deliverables/xiaomi18-refresh')
    path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')


def generate(output):
    output=Path(output)
    if not output.resolve().is_relative_to(EVIDENCE.resolve()):raise ValueError('Fixture output must stay within deliverables/xiaomi18-refresh')
    output.mkdir(parents=True,exist_ok=True)
    base={'schemaVersion':1,'deviceId':'mix-fold-4','stateId':'folded','portrait':False,
      'imagePath':'source-outer-test.img','imageWidth':1168,'imageHeight':1712,
      'pose':{'x':0.0,'y':0.2588190451,'z':0.0,'w':0.9659258263},
      'zoom':1.37,'offsetX':0.23,'offsetY':-0.19,'fitMode':'fill','cropX':0.34,'cropY':-0.27,
      'fov':42.5,'orthographic':True,'lighting':'cool','lightDirection':128.5,'lightIntensity':1.35,
      'environmentRotation':123.75,'reflection':0.31,'shadow':0.27,'softness':0.83,'grounded':True,
      'background':'gradient','colorA':-2235157,'colorB':-13747639,'canvasWidth':1440,'canvasHeight':1800,'exportLongEdge':2560}
    raw_pose={'x':.11,'y':.23,'z':-.07,'w':.96}
    pose_length=math.sqrt(sum(v*v for v in raw_pose.values()))
    base['pose']={key:value/pose_length for key,value in raw_pose.items()}
    sources=[('outer-1168x1712.png','source-outer-test.img'),('inner-landscape-2364x1672.png','source-inner-landscape-test.img'),('inner-portrait-1672x2364.png','source-inner-portrait-test.img')]
    source_entries=[]
    for original,name in sources:
        source=ROOT/'tests/fixtures'/original;target=output/'sources'/name;target.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(source,target)
        source_entries.append({'file':str(target.relative_to(output)),'sha256':sha(target),'origin':f'tests/fixtures/{original}','privacy':'original synthetic fixture'})
    cases=[]
    for legacy in sorted(LEGACY):
        for state,portrait,name,w,h,source in [
          ('folded',False,'folded',1168,1712,'source-outer-test.img'),
          ('unfolded',False,'unfolded-landscape',2364,1672,'source-inner-landscape-test.img'),
          ('unfolded',True,'unfolded-portrait',1672,2364,'source-inner-portrait-test.img'),
          ('portrait',False,'legacy-state-portrait',1672,2364,'source-inner-portrait-test.img')]:
            item=copy.deepcopy(base);item.update(deviceId=legacy,stateId=state,portrait=portrait,imageWidth=w,imageHeight=h,imagePath=source)
            if portrait or state=='portrait':item.update(canvasWidth=9,canvasHeight=16)
            elif state=='unfolded':item.update(canvasWidth=16,canvasHeight=9)
            filename=f'{legacy}-{name}.json';write(output/filename,item)
            cases.append({'file':filename,'oldDeviceId':legacy,'expectedDeviceId':TARGET,'expectedStateId':'unfolded' if state=='portrait' else state,'expectedPortrait':True if state=='portrait' else portrait,'source':f'sources/{source}'})
    control=copy.deepcopy(base);control.update(deviceId=REDMI,stateId='portrait',portrait=False,imagePath='source-inner-portrait-test.img',imageWidth=1672,imageHeight=2364)
    write(output/'redmi-control.json',control);cases.append({'file':'redmi-control.json','oldDeviceId':REDMI,'expectedDeviceId':REDMI,'expectedStateId':'portrait','expectedPortrait':False,'source':'sources/source-inner-portrait-test.img'})
    manifest={'status':'fixtures-only-not-app-tested','schemaVersion':1,'migrationContract':{'legacyIds':sorted(LEGACY),'target':TARGET,'legacyPortraitState':'unfolded + portrait=true','otherFields':'preserve valid existing serialized editing fields'},'cases':cases,'sources':source_entries}
    write(output/'manifest.json',manifest);return manifest


def compare(before,after,source_before=None,source_after=None):
    original=before.get('deviceId');errors=[];details=[]
    if original not in LEGACY|{TARGET,REDMI}:raise ValueError('Input is not a supported legacy/new/control fixture')
    expected_id=TARGET if original in LEGACY else original
    if after.get('deviceId')!=expected_id:errors.append(f'deviceId: expected {expected_id}, got {after.get("deviceId")}')
    legacy_portrait=original in LEGACY and before.get('stateId')=='portrait'
    for key,value in before.items():
        if key=='deviceId':continue
        expected='unfolded' if key=='stateId' and legacy_portrait else True if key=='portrait' and legacy_portrait else value
        actual=after.get(key)
        if key=='pose':
            try:
                a=[float(value[k]) for k in ['x','y','z','w']];b=[float(actual[k]) for k in ['x','y','z','w']]
                an=math.sqrt(sum(v*v for v in a));bn=math.sqrt(sum(v*v for v in b))
                if not an or not bn:raise ValueError('zero quaternion')
                angle=math.degrees(2*math.acos(min(1,abs(sum(x*y for x,y in zip(a,b))/(an*bn)))))
                equal=angle<=0.01;details.append({'field':'pose','orientationDifferenceDegrees':angle})
            except (KeyError,TypeError,ValueError):equal=False
        elif key in FLOAT_FIELDS:
            equal=isinstance(actual,(float,int)) and not isinstance(actual,bool) and math.isfinite(actual) and math.isclose(float(expected),float(actual),abs_tol=0.00001,rel_tol=0.00001)
        else:equal=type(expected)==type(actual) and expected==actual
        if not equal:errors.append(f'{key}: expected {expected!r}, got {actual!r}')
    source_check={'status':'not-checked','note':'Only imagePath/size compared; do not claim source bytes preserved without both exported test files.'}
    if bool(source_before)!=bool(source_after):raise ValueError('Provide both source-before and source-after or neither')
    if source_before:
        left=sha(source_before);right=sha(source_after);source_check={'status':'pass' if left==right else 'fail','beforeSha256':left,'afterSha256':right}
        if left!=right:errors.append('Source image bytes changed')
    return {'status':'pass' if not errors else 'fail','scope':'Actual captured draft field comparison only; not Android migration execution or visual acceptance','originalDeviceId':original,'expectedDeviceId':expected_id,'legacyPortraitMappingApplied':legacy_portrait,'errors':errors,'details':details,'sourceBytes':source_check}


def check_apk(apk,baseline=None):
    errors=[];models={};old_prefixes=('assets/devices/mix-fold-4/','assets/devices/wide-fold/')
    with zipfile.ZipFile(apk) as z:
        names=set(z.namelist());legacy=sorted(name for name in names if name.startswith(old_prefixes))
        if legacy:errors.append('Removed legacy model resources are still packaged')
        manifests=sorted(name for name in names if re.fullmatch(r'assets/devices/[^/]+/device\.json',name))
        for name in manifests:
            device=json.loads(z.read(name));identity=device.get('deviceId',device.get('id'));states=device.get('states',{});models[identity]={'path':name,'states':sorted(states),'verificationStatus':device.get('verificationStatus'),'approximationNotes':device.get('approximationNotes')}
            for state_id,state in states.items():
                asset='assets/'+state.get('modelPath',state.get('asset',''))
                if asset not in names:errors.append(f'{identity}/{state_id}: missing model {asset}');continue
                data=z.read(asset)
                if len(data)<12 or struct.unpack('<III',data[:12])!=(0x46546c67,2,len(data)):errors.append(f'{asset}: invalid GLB header/length')
                if not state.get('activeScreenNode'):errors.append(f'{identity}/{state_id}: missing active-screen contract')
            if identity==TARGET:
                if set(states)!={'folded','unfolded'}:errors.append('New Fold must contain exactly folded/unfolded states')
                for state_id,expected in {'folded':[.0836,.1178,.01068],'unfolded':[.1638,.1178,.00502]}.items():
                    actual=states.get(state_id,{}).get('bodySizeMeters',[])
                    if len(actual)!=3 or any(not isinstance(v,(float,int)) or not math.isfinite(v) or not math.isclose(v,e,abs_tol=1e-7) for v,e in zip(actual,expected)):errors.append(f'{state_id}: body dimensions differ from verified contract')
                if not device.get('verificationStatus') or not device.get('approximationNotes'):errors.append('New model provenance/approximation labeling missing')
        if set(models)!={TARGET,REDMI}:errors.append(f'Packaged model manifests must be exactly new Fold + Redmi, got {sorted(models)}')
        redmi_comparison={'status':'not-checked'}
        if baseline:
            with zipfile.ZipFile(baseline) as old:
                files=[name for name in names if name.startswith('assets/devices/redmi-k90-pro-max/') and not name.endswith('/')]
                changed=[name for name in files if name not in old.namelist() or z.read(name)!=old.read(name)]
                redmi_comparison={'status':'pass' if not changed else 'fail','changed':changed,'fileCount':len(files)}
                if changed:errors.append('Unrelated Redmi model resources changed from supplied baseline')
    return {'status':'pass' if not errors else 'fail','apkSha256':sha(apk),'scope':'APK structure/removal/dimension/provenance-field checks only. This does not prove model identity, source-photo fidelity, shader quality, or a successful Android run. Legacy strings in code are intentionally allowed for migration.','models':models,'legacyResourcePaths':legacy,'redmiAssets':redmi_comparison,'errors':errors}


def main():
    p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='mode',required=True)
    g=sub.add_parser('fixtures');g.add_argument('--output',type=Path,default=EVIDENCE/'migration-fixtures')
    c=sub.add_parser('compare');c.add_argument('--before',type=Path,required=True);c.add_argument('--after',type=Path,required=True);c.add_argument('--source-before',type=Path);c.add_argument('--source-after',type=Path);c.add_argument('--output',type=Path,required=True)
    a=sub.add_parser('apk');a.add_argument('--apk',type=Path,required=True);a.add_argument('--baseline-apk',type=Path);a.add_argument('--output',type=Path,required=True)
    i=sub.add_parser('intent-plan',help='Print only; no ADB execution');i.add_argument('--serial',required=True,choices=['emulator-5554']);i.add_argument('--session',required=True);i.add_argument('--case',required=True)
    args=p.parse_args()
    if args.mode=='fixtures':result=generate(args.output)
    elif args.mode=='compare':result=compare(json.loads(args.before.read_text()),json.loads(args.after.read_text()),args.source_before,args.source_after);write(args.output,result)
    elif args.mode=='apk':result=check_apk(args.apk,args.baseline_apk);write(args.output,result)
    else:
        if not re.fullmatch(r'xiaomi18-[A-Za-z0-9-]{1,39}',args.session):p.error('Session must be a safe xiaomi18-* identifier, total <=48 chars')
        if not re.fullmatch(r'X[A-Za-z0-9-]{1,40}',args.case):p.error('Await the confirmed X-case contract; use only a safe X identifier')
        result={'status':'plan-only-no-adb-called','command':['adb','-s','emulator-5554','shell','am','start','-W','-n','dev.qingframe/.MainActivity','--es','test_session',args.session,'--es','test_case',args.case]}
    print(json.dumps(result,ensure_ascii=False,indent=2));return 1 if result.get('status')=='fail' else 0
if __name__=='__main__':sys.exit(main())
