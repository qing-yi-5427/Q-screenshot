#!/usr/bin/env python3
"""Rebuild reviewed Xiaomi front-only sources and color-specific body depths in staging."""
import argparse
import copy
import json
from pathlib import Path
import shutil
import subprocess
import sys
from device_pipeline.model import ROOT, build, read, safe_output
from device_pipeline.research import digest, save


def build_device(device_id, output, blender):
    source = ROOT/'model-library/source'/device_id
    if not device_id.startswith('xiaomi-') or source.parent != ROOT/'model-library/source':
        raise ValueError('Expected reviewed Xiaomi library source')
    profile_path = source/'profile.json'
    profile, metadata = read(profile_path), read(source/'package.json')
    if profile['deviceId'] != device_id or metadata['id'] != device_id:
        raise ValueError('Source identity mismatch')
    out = safe_output(output)
    out.mkdir(parents=True)
    report = build(profile_path, out/'build', blender)
    assets = out/'build/assets'
    manifest_path = assets/'devices'/device_id/'device.json'
    manifest = read(manifest_path)
    state = manifest['states']['portrait']
    default = profile['geometry']['thickness_mm']
    depths = {default: (state['modelPath'], state['assetHash'])}
    for color in metadata['colors']:
        depth = round(color['thicknessMeters']*1000, 6)
        if depth == default:
            continue
        if depth not in depths:
            variant = copy.deepcopy(profile)
            variant['geometry']['thickness_mm'] = depth
            variant['device']['states']['portrait']['bodySizeMeters'][2] = depth/1000
            variant['device']['physicalDimensions']['states']['portrait'][2] = depth/1000
            # Numeric depth names are independent of externally supplied color IDs.
            variant_root = out/('depth-'+str(depth))
            save(variant_root/'profile.json', variant)
            build(variant_root/'profile.json', variant_root/'build', blender)
            relative = f'devices/{device_id}/portrait-depth-{depth}.glb'
            target = assets/relative
            shutil.copyfile(variant_root/'build/assets'/state['modelPath'], target)
            depths[depth] = relative, digest(target.read_bytes())
        source_relative, sha = depths[depth]
        relative = f"devices/{device_id}/portrait-{color['id']}.glb"
        shutil.copyfile(assets/source_relative, assets/relative)
        state.setdefault('colorwayAssets', {})[color['id']] = relative
        state.setdefault('colorwayAssetHashes', {})[color['id']] = sha
        state.setdefault('colorwayBodySizeMeters', {})[color['id']] = [
            profile['geometry']['width_mm']/1000, profile['geometry']['height_mm']/1000, depth/1000]
    for depth,(relative,_) in depths.items():
        if depth != default:
            (assets/relative).unlink()
    save(manifest_path, manifest)
    save(assets/'devices/catalog.json', dict(schemaVersion=1, defaultDevice=device_id, devices=[manifest]))
    with (out/'variants-validation.log').open('w') as log:
        subprocess.run([sys.executable, str(ROOT/'tools/validate_assets.py'), '--all',
            '--assets-root', str(assets), '--evidence-dir', str(out/'variants-evidence')],
            check=True, stdout=log, stderr=subprocess.STDOUT)
    summary = dict(id=device_id, main=report,
                   thicknessVariants={str(k): {'sha256':v[1]} for k,v in depths.items()},
                   mergedVariantContract='PASS', androidFilament='NOT_RUN')
    save(out/'summary.json', summary)
    return summary


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device', required=True)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--blender', required=True)
    args = parser.parse_args()
    print(json.dumps(build_device(args.device, args.output, args.blender), indent=2))
