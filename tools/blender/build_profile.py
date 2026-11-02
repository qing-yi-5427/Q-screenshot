"""Staging adapter: existing generators or shared front-focused slab primitives."""
import json
import sys
from pathlib import Path
import bpy
sys.path.insert(0, str(Path(__file__).parent))
args = sys.argv[sys.argv.index('--')+1:]
p = json.loads(Path(args[0]).read_text())
out = Path(args[1])
for state, manifest in p['device']['states'].items():
    # Match independent legacy invocations: prevent .001 material/node suffixes.
    bpy.ops.wm.read_factory_settings(use_empty=True)
    target = out/manifest['modelPath']
    if p['adapter'] == 'redmi-k90':
        from build_redmi_k90 import build
        build(target, geometry=p['geometry'])
    elif p['adapter'] == 'xiaomi18-fold':
        from build_xiaomi18_fold import build
        build(state, target, geometry=p['geometry'])
    elif p['adapter'] in ('slab-front', 'xiaomi-library'):
        sys.path.insert(0, str(Path(__file__).parents[1]))
        if p['adapter'] == 'xiaomi-library':
            from build_xiaomi_library import build
        else:
            from model_library_geometry import build
        build(p, target)
    else:
        raise ValueError('Unknown adapter')
