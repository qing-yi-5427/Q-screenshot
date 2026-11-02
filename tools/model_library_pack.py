"""Deterministic, image-free device packages; never rewrites runtime assets."""
import argparse, hashlib, json, struct, zipfile, re, math
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
def sha(data): return hashlib.sha256(data).hexdigest()
def metadata(device, colors):
    result={'schemaVersion':1,'id':device['id'],'name':device['name'],'modelVersion':device['assetVersion'],'states':list(device['states']),'defaultState':next(iter(device['states'])),'notes':device['approximationNotes'],'defaultColorwayId':colors['defaultColorwayId'],'colors':[]}
    for c in colors['colorways']:
        materials={}
        for role,names in colors['materialMapping'].items():
            if role not in c: continue
            v=c[role]
            for name in names: materials[name]={'srgb':'#'+''.join(f'{x:02X}' for x in v['baseColorSrgb8']),'metallic':v['metallic'],'roughness':v['roughness']}
        item={'id':c['id'],'title':c['officialNameZh'],'backMaterial':c['backMaterial'],'materials':materials,'notes':c.get('notes','')+' '+colors['estimateNotice']}
        thickness=c.get('geometry',{}).get('bodyThicknessMm')
        if thickness is not None: item['thicknessMeters']=thickness/1000
        result['colors'].append(item)
    return result
def pack(asset_root, device_id, meta, output):
    if not re.fullmatch(r'[a-z0-9]+(?:-[a-z0-9]+)*',device_id): raise ValueError('Invalid device ID')
    if meta.get('schemaVersion')!=1 or meta.get('id')!=device_id: raise ValueError('Metadata identity/schema mismatch')
    if not re.fullmatch(r'[a-zA-Z0-9._-]+',meta.get('modelVersion','')): raise ValueError('Invalid model version')
    colors=meta.get('colors',[])
    if not colors or len({c['id'] for c in colors})!=len(colors) or meta.get('defaultColorwayId') not in {c['id'] for c in colors}: raise ValueError('Invalid colorway IDs/default')
    for c in colors:
        for material in c['materials'].values():
            if not re.fullmatch(r'#[0-9a-fA-F]{6}',material['srgb']): raise ValueError('Invalid sRGB')
            for k in ('metallic','roughness'):
                if type(material[k]) not in (int,float) or not math.isfinite(material[k]) or not 0<=material[k]<=1: raise ValueError('Invalid PBR parameter')
    folder=asset_root/'devices'/device_id
    device=json.loads((folder/'device.json').read_text())
    if device.get('assetVersion')!=meta['modelVersion']: raise ValueError('Manifest/metadata version mismatch')
    if device['id']!=device_id or set(meta['states'])!=set(device['states']) or meta['defaultState'] not in device['states']: raise ValueError('Manifest identity/states mismatch')
    files={'package.json':(json.dumps(meta,ensure_ascii=False,indent=2)+'\n').encode(),f'devices/{device_id}/device.json':(folder/'device.json').read_bytes()}
    for s in device['states'].values():
        paths={s['modelPath']:s['assetHash'],**{p:s['colorwayAssetHashes'][c] for c,p in s.get('colorwayAssets',{}).items()}}
        for path,expected in paths.items():
            if not re.fullmatch(r'devices/'+re.escape(device_id)+r'/[a-zA-Z0-9._-]+\.glb',path): raise ValueError('Unsafe asset path')
            data=(asset_root/path).read_bytes()
            if sha(data)!=expected: raise ValueError('Asset hash mismatch: '+path)
            if len(data)<20 or data[:4]!=b'glTF' or struct.unpack_from('<I',data,4)[0]!=2 or struct.unpack_from('<I',data,8)[0]!=len(data) or data[16:20]!=b'JSON': raise ValueError('Invalid GLB container')
            length=struct.unpack_from('<I',data,12)[0]
            if length>len(data)-20: raise ValueError('Invalid GLB JSON length')
            gltf=json.loads(data[20:20+length])
            if any(b.get('uri') for b in gltf.get('buffers',[])): raise ValueError('External GLB buffers forbidden')
            if gltf.get('images') or gltf.get('textures'): raise ValueError('Embedded reference images forbidden')
            files[path]=data
    if len(files)>32 or sum(map(len,files.values()))>128*1024**2: raise ValueError('Expanded package limit')
    output.parent.mkdir(parents=True,exist_ok=True)
    with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
        for name,data in sorted(files.items()):
            info=zipfile.ZipInfo(name,(2026,1,1,0,0,0)); info.compress_type=zipfile.ZIP_DEFLATED; info.external_attr=0o644<<16
            z.writestr(info,data)
    data=output.read_bytes()
    if len(data)>64*1024**2: raise ValueError('Compressed package limit')
    return {'id':device_id,'name':meta['name'],'states':meta['states'],'defaultState':meta['defaultState'],'notes':meta['notes'],'modelVersion':meta['modelVersion'],'minAppVersionCode':8000,'packageUrl':'https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-2026-11/'+output.name,'packageSha256':sha(data),'packageBytes':len(data),'previewUrl':None,'downloadable':True,'verificationStatus':device.get('verificationStatus','partial_reconstruction')}
def main():
    parser=argparse.ArgumentParser(); parser.add_argument('--device',required=True); parser.add_argument('--asset-root',type=Path,default=ROOT/'app/src/main/assets'); parser.add_argument('--metadata',type=Path); parser.add_argument('--output',type=Path); args=parser.parse_args()
    folder=args.asset_root/'devices'/args.device
    device=json.loads((folder/'device.json').read_text())
    meta=json.loads(args.metadata.read_text()) if args.metadata else metadata(device,json.loads((folder/'colorways.json').read_text()))
    target=ROOT/'model-library'/('builtin' if args.device=='xiaomi-18-fold' else 'source')/args.device/'package.json'; target.parent.mkdir(parents=True,exist_ok=True); target.write_text(json.dumps(meta,ensure_ascii=False,indent=2)+'\n')
    if args.output: print(json.dumps(pack(args.asset_root,args.device,meta,args.output),ensure_ascii=False,indent=2))
if __name__=='__main__':main()
