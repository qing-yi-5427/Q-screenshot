"""Profiles adapt existing geometry/manifest contracts; all output is staging-only."""
import copy
import hashlib
import json
import math
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import struct
from .research import save, digest

ROOT = Path(__file__).resolve().parents[2]
ADAPTERS = {'redmi-k90-pro-max': 'redmi-k90', 'xiaomi-18-fold': 'xiaomi18-fold'}


def read(path):
    return json.loads(Path(path).read_text())


def safe_output(path):
    path = Path(path).resolve()
    for protected in (ROOT/'app', ROOT/'assets-source', ROOT/'tools', ROOT/'tests', ROOT/'docs', ROOT/'.git'):
        if path == protected or path.is_relative_to(protected) or protected.is_relative_to(path):
            raise ValueError('Output overlaps protected source/runtime tree')
    if path.exists():
        raise ValueError('Output must be a new directory; use a fresh run name')
    return path


def existing(device, output):
    if device not in ADAPTERS:
        raise ValueError('Unknown existing adapter')
    out = safe_output(output)
    source = ROOT/'assets-source/devices'/device
    manifest = read(source/'device.json')
    # The replay scope is the primary model per state, not the derived denim colorway.
    for state in manifest['states'].values():
        for key in ('colorwayAssets', 'colorwayAssetHashes', 'colorwayBodySizeMeters'):
            state.pop(key, None)
    manifest.pop('colorwayManifest', None)
    out.mkdir(parents=True)
    profile = dict(schemaVersion=1, deviceId=device, adapter=ADAPTERS[device],
                   geometry=read(source/'geometry.json'), device=manifest,
                   research=read(source/'research.json'), sources=read(source/'sources.json'),
                   provenance='Repository replay; inherited claims, not independently reverified live',
                   inputHashes={p.name: digest(p.read_bytes()) for p in source.glob('*.json')},
                   reviewRequired=['Visual silhouette', 'Android Filament material/export test'])
    save(out/'profile.json', profile)
    return profile


def compile_slab(research, output):
    """Build a front-focused prototype from evidence; never reuse K90 rear hardware."""
    out = safe_output(output)
    fields = copy.deepcopy(research['fields'])
    def value(key):
        f = fields.get(key, {})
        if f.get('confidence') not in ('official', 'measured', 'estimated') or f.get('value') is None:
            raise ValueError(f'Missing supported field {key}; no zero/implicit fallback')
        if f['confidence'] == 'official' and (not f.get('sourceIds') or not f.get('evidence')):
            raise ValueError(f'{key}: official claim requires resolved evidence')
        if f['confidence'] in ('measured', 'estimated') and not f.get('notes'):
            raise ValueError(f'{key}: estimate/measurement needs method notes')
        return f['value']
    w, h, d = [value(k) for k in ('width_mm', 'height_mm', 'thickness_mm')]
    pixels = value('display_pixels')
    if len(pixels) != 2 or not all(isinstance(x, int) and x > 0 for x in pixels):
        raise ValueError('display_pixels must be explicit [width,height] in model orientation')
    if pixels[0] >= pixels[1]:
        raise ValueError('Slab adapter requires explicit portrait pixel order; do not silently swap')
    ratio = pixels[0]/pixels[1]
    if 'screen_height_mm' in fields:
        sh = value('screen_height_mm')
    else:
        diagonal = value('diagonal_inches') * 25.4
        sh = diagonal/math.sqrt(1+ratio*ratio)
        fields['screen_height_mm'] = dict(value=sh, confidence='estimated', confidenceScore=.5,
                                         notes='Nominal full-rectangle diagonal/pixel aspect calculation; visible area unverified')
    sw = sh*ratio
    fields['screen_width_mm'] = dict(value=sw, confidence='estimated', confidenceScore=.5,
                                     notes='Screen height times explicitly oriented pixel aspect; not official active area')
    radius = value('corner_radius_mm')
    screen_radius = value('screen_corner_radius_mm')
    g = dict(width_mm=w, height_mm=h, thickness_mm=d, screen_width_mm=sw,
             screen_height_mm=sh, corner_radius_mm=radius, screen_corner_radius_mm=screen_radius)
    device_id = research['deviceId']
    status='partial_reconstruction' if all(fields[k]['confidence'] in ('official','measured') for k in ('width_mm','height_mm','thickness_mm')) else 'generic_approximation'
    state = dict(id='portrait', modelPath=f'devices/{device_id}/portrait.glb',
                 asset=f'devices/{device_id}/portrait.glb', activeScreenNode='screen_main_content',
                 screenMaterial='screen_content', screenSizeMeters=[sw/1000, sh/1000],
                 bodySizeMeters=[w/1000, h/1000, d/1000], glassNode=None,
                 frontDirection=[0,0,1], uvOrigin='top-left', verificationStatus=status,
                 screenBindings=[dict(contentNode='screen_main_content', glassNode=None, materialSlot=0, active=True)],
                 screenAspectRatio=ratio, inputSizeHints=[dict(width=pixels[0], height=pixels[1], source='research')],
                 orientationPresets=['portrait','landscape'], assetHash='pending')
    manifest = dict(schemaVersion=1, id=device_id, deviceId=device_id,
                    name=research['formalName']+' · 未验收近似模型', displayName=research['formalName']+' · 未验收近似模型',
                    manufacturer='Xiaomi', verificationStatus=status, assetVersion='0.0.0-staging',
                    physicalDimensions=dict(units='meters', includesCameraBump=False, states={'portrait':state['bodySizeMeters']}),
                    states={'portrait':state}, materialVariants=[dict(id='neutral-estimate', label='中性材质估计')],
                    defaultPose=dict(yaw=-22,pitch=12,roll=-5), cameraFit=dict(margin=1.18),
                    attribution='Original parametric geometry; no vendor imagery embedded.',
                    approximationNotes='Front-focused prototype. Radius, active area and neutral PBR estimates; camera/ports/punch not reconstructed.')
    research = dict(research, fields=fields)
    profile = dict(schemaVersion=1, deviceId=device_id, adapter='slab-front', geometry=g,
                   device=manifest, research=research, sources=research.get('sources', []),
                   reviewRequired=['Front/side silhouette and punch geometry', 'Colorway PBR tuning', 'Android Filament export'])
    validate_profile(profile)
    out.mkdir(parents=True)
    save(out/'profile.json', profile)
    return profile


def validate_profile(p):
    if p.get('schemaVersion') != 1 or not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*', p['deviceId']):
        raise ValueError('Invalid profile schema/deviceId')
    adapter = p['adapter']
    if adapter not in (*ADAPTERS.values(), 'slab-front', 'xiaomi-library'):
        raise ValueError('Unknown adapter')
    if adapter in ADAPTERS.values() and ADAPTERS.get(p['deviceId']) != adapter:
        raise ValueError('Dedicated adapters cannot be relabeled as a different phone')
    if p['device']['deviceId'] != p['deviceId']:
        raise ValueError('Manifest identity mismatch')
    expected_states = {'folded','unfolded'} if adapter == 'xiaomi18-fold' else {'portrait'}
    if set(p['device']['states']) != expected_states:
        raise ValueError('Adapter/state mismatch')
    for state, manifest in p['device']['states'].items():
        g = p['geometry'][state] if adapter == 'xiaomi18-fold' else p['geometry']
        vals = [g[k] for k in ('width_mm','height_mm','thickness_mm','screen_width_mm','screen_height_mm')]
        if not all(type(v) in (int,float) and math.isfinite(v) and v > 0 for v in vals):
            raise ValueError('Nonpositive/nonfinite geometry')
        w,h,d,sw,sh = vals
        if not (20 < w < 300 and 20 < h < 300 and 1 < d < 30 and sw < w and sh < h):
            raise ValueError('Impossible body/screen dimensions; do not clamp conflicting data')
        for key, actual in [('bodySizeMeters',[w/1000,h/1000,d/1000]),('screenSizeMeters',[sw/1000,sh/1000])]:
            if len(manifest[key]) != len(actual) or any(abs(x-y)>1e-9 for x,y in zip(manifest[key],actual)):
                raise ValueError(f'{state}: geometry/manifest {key} mismatch')
        if abs(manifest['screenAspectRatio'] - sw/sh) > 1e-6:
            raise ValueError('Screen aspect does not match geometry')
        details=g.get('frontDetails',{})
        for detail_key in ('frontLip_mm','blackBezelInset_mm','blackMaskWidth_mm'):
            if detail_key in details:
                v=details[detail_key]
                if type(v) not in (int,float) or not math.isfinite(v) or v<0:
                    raise ValueError('Invalid front layer width')
        mask=details.get('blackMaskWidth_mm')
        if mask is not None and (mask<=0 or sw+2*mask>=w-.2 or sh+2*mask>=h-.2):
            raise ValueError('Black mask offset must leave positive front metal lip')
        if manifest['modelPath'] != f"devices/{p['deviceId']}/{state}.glb":
            raise ValueError('Unsafe or mismatched modelPath')
        body_radius=p['geometry'].get('outer_corner_radius_mm',g.get('corner_radius_mm'))
        if type(body_radius) not in (int,float) or not math.isfinite(body_radius) or not .2<body_radius<min(w,h)/2:
            raise ValueError('Impossible body radius')
        radii = g.get('screen_corner_radii_mm', [g.get('screen_corner_radius_mm', p['geometry'].get('corner_radius_mm',1))])
        if not all(type(r) in (int,float) and math.isfinite(r) and 0<r<min(sw,sh)/2 for r in radii):
            raise ValueError('Impossible screen radius')


def compare_glb(left, right):
    """Exact JSON + BIN comparison except explicitly reported exporter metadata."""
    def load(path):
        data=Path(path).read_bytes()
        size=struct.unpack_from('<I',data,12)[0]
        document=json.loads(data[20:20+size])
        generator=document.get('asset',{}).pop('generator',None)
        return document,data[20+size:],generator
    a,ab,ag=load(left);b,bb,bg=load(right)
    return dict(semanticIdentical=a==b and ab==bb, binaryIdentical=ab==bb,
                jsonExceptGeneratorIdentical=a==b, acceptedGenerator=ag, generatedGenerator=bg)


def build(profile_path, output, blender=None, dry_run=False):
    profile_path = Path(profile_path).resolve()
    p = read(profile_path)
    validate_profile(p)
    out = safe_output(output)
    blender = blender or os.environ.get('BLENDER') or shutil.which('blender')
    if not blender and not dry_run:
        raise ValueError('Blender unavailable: set BLENDER (tested 4.5.3); nothing was built')
    command = [blender or 'blender', '-b', '--factory-startup', '--python-exit-code', '1',
               '--python', str(ROOT/'tools/blender/build_profile.py'), '--', str(profile_path), str(out/'assets')]
    if dry_run:
        return {'command':command, 'output':str(out), 'status':'NOT_RUN'}
    out.mkdir(parents=True)
    save(out/'input-profile.json', p)
    with (out/'blender.log').open('w') as log:
        subprocess.run(command, check=True, stdout=log, stderr=subprocess.STDOUT)
    manifest = copy.deepcopy(p['device'])
    comparisons = []
    for state in manifest['states'].values():
        path = out/'assets'/state['modelPath']
        state['assetHash'] = digest(path.read_bytes())
        accepted = ROOT/'app/src/main/assets'/state['modelPath']
        if not accepted.exists():
            accepted = ROOT/'model-library/baselines'/p['deviceId']/Path(state['modelPath']).name
        comparison=dict(modelPath=state['modelPath'], sha256=state['assetHash'],
                                acceptedSha256=digest(accepted.read_bytes()) if accepted.exists() else None,
                                byteIdentical=accepted.exists() and accepted.read_bytes()==path.read_bytes())
        if accepted.exists(): comparison.update(compare_glb(accepted,path))
        comparisons.append(comparison)
    save(out/'assets/devices'/p['deviceId']/'device.json', manifest)
    save(out/'assets/devices/catalog.json', dict(schemaVersion=1, defaultDevice=p['deviceId'], devices=[manifest]))
    with (out/'validation.log').open('w') as log:
        subprocess.run([sys.executable, str(ROOT/'tools/validate_assets.py'), '--assets-root', str(out/'assets'),
                        '--evidence-dir', str(out/'evidence')], check=True, stdout=log, stderr=subprocess.STDOUT)
    report = dict(status='contract_passed', models=comparisons, profileSha256=digest(profile_path.read_bytes()),
                  blenderVersion=subprocess.check_output([blender,'--version'],text=True).splitlines()[0],
                  khronos='PASS' if list((out/'evidence').glob('model-khronos-*.json')) else 'NOT_RUN',
                  scope='Primary model per state; derived denim excluded', visualReview='NOT_RUN', androidFilament='NOT_RUN')
    save(out/'build-report.json', report)
    return report


def reconcile(profile, research, output):
    """Attach newly resolved evidence only when it reproduces existing parameters."""
    out=safe_output(output)
    p=copy.deepcopy(profile)
    validate_profile(p)
    if research['deviceId'] != p['deviceId']:
        raise ValueError('Research/profile identity mismatch')
    checked=[]
    for key, field in research['fields'].items():
        node=p['geometry']
        parts=key.split('.')
        for part in parts:
            if not isinstance(node,dict) or part not in node:
                raise ValueError(f'{key}: no geometry mapping; resolve only mapped fields for reconciliation')
            node=node[part]
        if field.get('confidence') != 'official' or not field.get('sourceIds') or not field.get('evidence'):
            raise ValueError(f'{key}: resolved official evidence required')
        if type(node) not in (float,int) or type(field['value']) not in (float,int) or not math.isfinite(field['value']) or abs(node-field['value'])>1e-9:
            raise ValueError(f'{key}: new evidence conflicts with existing geometry; never overwrite accepted parameters')
        checked.append(key)
    if not checked:raise ValueError('No mapped evidence supplied')
    p['research']['fields'].update(research['fields'])
    sources={x['sourceId']:x for x in p.get('sources',[])}
    sources.update({x['sourceId']:x for x in research['sources']})
    p['sources']=list(sources.values())
    p['reconciledFields']=checked
    p['provenance']='Existing geometry replay with newly resolved matching official evidence; other claims remain inherited'
    out.mkdir(parents=True)
    save(out/'profile.json',p)
    return p
