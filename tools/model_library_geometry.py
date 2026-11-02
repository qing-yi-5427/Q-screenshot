"""Original front/side geometry from each reviewed device profile (meters, +Z front)."""
import sys, math
from pathlib import Path
import bpy
sys.path.insert(0,str(Path(__file__).parent/'blender'))
from build_device import shape,screen,material,cv

def build(profile,target,export=True):
    g=profile['geometry']; details=g.get('frontDetails',{})
    w,h,d,r,sw,sh,sr=[g[k]*.001 for k in ('width_mm','height_mm','thickness_mm','corner_radius_mm','screen_width_mm','screen_height_mm','screen_corner_radius_mm')]
    metal=material('frame_estimated',(.65,.65,.65),.9,.3)
    back=material('back_estimated',(.35,.35,.35),0,.4)
    dark=material('bezel_estimated',(.002,.002,.002),0,.3)
    optic=material('front_optic_estimated',(.004,.006,.009),0,.15)
    content=material('screen_content',(.025,.07,.11),0,1)
    frame_curve=details.get('frameCurve_mm',0)*.001
    shape('chassis_main',w,h,d,r,(0,0,0),metal,frame_curve or .00035)
    shape('back_panel_main',w-.0004,h-.0004,.0002,r-.0002,(0,0,-d/2),back,.00004)
    shape('screen_bezel',w-.0004,h-.0004,.0001,r-.0002,(0,0,d/2),dark,.00002)
    screen('screen_main_content',sw,sh,sr,d/2+.00025,content)
    camera=details.get('camera')
    if camera:
        x,y,rad=[camera[k]*.001 for k in ('x_mm','y_mm','radius_mm')]
        shape('front_camera_rim',rad*2+.0002,rad*2+.0002,.00006,rad+.0001,(x,y,d/2+.00033),dark,.00001)
        shape('front_camera_optic',rad*2,rad*2,.00004,rad,(x,y,d/2+.00038),optic,.00001)
    for i,b in enumerate(details.get('buttons',[])):
        length=b['length_mm']*.001;depth=b.get('depth_mm',.35)*.001;side=b['side'];y=b['y_mm']*.001
        shape(f'side_button_{i}',depth,length,min(d*.42,.0028),min(depth*.4,.00015),((-1 if side=='left' else 1)*(w/2+depth*.3),y,b.get('z_mm',0)*.001),metal,.00008)
    # These are original dark inset proxies, not manufacturer CAD cut-outs.
    for i,p in enumerate(details.get('ports',[])):
        pw=p['width_mm']*.001;pd=p['height_mm']*.001
        ob=shape(f'port_proxy_{i}',pw,.00005,pd,min(pw,pd)*.35,(p.get('x_mm',0)*.001,(-1 if p.get('edge','bottom')=='bottom' else 1)*(h/2+.00002),p.get('z_mm',0)*.001),dark,.00001)
    for i,b in enumerate(details.get('antennaBands',[])):
        side=b.get('side','right');x=(-1 if side=='left' else 1)*(w/2+.000015)
        shape(f'antenna_band_{i}',.00004,b.get('width_mm',.6)*.001,d*.86,.000015,(x,b['y_mm']*.001,0),back,.000008)
    root=bpy.data.objects.new('device_root',None);bpy.context.collection.objects.link(root)
    for ob in list(bpy.context.scene.objects):
        if ob!=root:ob.parent=root
    if export:
        target=Path(target);target.parent.mkdir(parents=True,exist_ok=True)
        bpy.ops.export_scene.gltf(filepath=str(target),export_format='GLB',export_yup=True,export_apply=True,export_extras=True,export_cameras=False,export_lights=False)
    return root
