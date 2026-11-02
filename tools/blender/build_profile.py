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
    elif p['adapter'] == 'slab-front':
        from build_device import shape, screen, material
        bpy.ops.object.select_all(action='SELECT')
        bpy.ops.object.delete(use_global=False)
        g = p['geometry']
        w,h,d,r,sw,sh,sr = [g[k]*.001 for k in ('width_mm','height_mm','thickness_mm','corner_radius_mm','screen_width_mm','screen_height_mm','screen_corner_radius_mm')]
        metal = material('frame_estimated', (.65,.65,.65), .9, .3)
        back = material('back_estimated', (.35,.35,.35), 0, .4)
        dark = material('bezel_estimated', (.002,.002,.002), 0, .3)
        content = material('screen_content', (.025,.07,.11), 0, 1)
        shape('chassis_main',w,h,d,r,(0,0,0),metal)
        shape('back_panel_main',w-.0004,h-.0004,.0002,r-.0002,(0,0,-d/2),back,.00004)
        shape('screen_bezel',w-.0004,h-.0004,.0001,r-.0002,(0,0,d/2),dark,.00002)
        screen('screen_main_content',sw,sh,sr,d/2+.00025,content)
        root = bpy.data.objects.new('device_root',None)
        bpy.context.collection.objects.link(root)
        for ob in list(bpy.context.scene.objects):
            if ob != root: ob.parent=root
        target.parent.mkdir(parents=True,exist_ok=True)
        bpy.ops.export_scene.gltf(filepath=str(target),export_format='GLB',export_yup=True,export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
    else:
        raise ValueError('Unknown adapter')
