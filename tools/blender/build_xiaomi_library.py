"""Original front-only Xiaomi equal-depth micro-curved cover perimeter.

The screenshot remains a flat replaceable surface under the cover. The curved
perimeter and rounded frame are visual estimates, never manufacturer CAD.
"""
import sys
from pathlib import Path
import bpy
sys.path.insert(0, str(Path(__file__).parents[1]))
from model_library_geometry import build as build_front
from build_device import outline, cv


def _knurl(profile, root):
    g=profile['geometry'];details=g['frontDetails'];knurl=details.get('knurl')
    if not knurl:return
    pitch=knurl['pitch_mm'];width=knurl['ridge_width_mm'];projection=knurl['projection_mm']
    if not .2 <= pitch <= 2 or not 0 < width < pitch or not 0 < projection <= .15:
        raise ValueError('Invalid estimated knurl geometry')
    vertices,faces=[],[]
    for side in (-1,1):
        y=knurl['start_y_mm']
        while y <= knurl['end_y_mm']:
            blocked=any(b['side']==('left' if side<0 else 'right') and
                abs(y-b['y_mm']) < b['length_mm']/2+1.5 for b in details.get('buttons',[]))
            if not blocked:
                x=side*(g['width_mm']/2+projection/2)
                z=g['thickness_mm']*.34
                first=len(vertices)
                vertices.extend(cv((vx*.001,vy*.001,vz*.001)) for vx,vy,vz in
                    [(x-projection/2,y-width/2,-z),(x+projection/2,y-width/2,-z),
                     (x+projection/2,y+width/2,-z),(x-projection/2,y+width/2,-z),
                     (x-projection/2,y-width/2,z),(x+projection/2,y-width/2,z),
                     (x+projection/2,y+width/2,z),(x-projection/2,y+width/2,z)])
                faces.extend(tuple(first+i for i in face) for face in
                    [(3,2,1,0),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)])
            y+=pitch
    mesh=bpy.data.meshes.new('leica_estimated_knurl_ridges');mesh.from_pydata(vertices,[],faces);mesh.update()
    ob=bpy.data.objects.new('frame_knurl_ridges',mesh);bpy.context.collection.objects.link(ob);ob.parent=root
    mesh.materials.append(bpy.data.materials['frame_estimated'])


def _export(target):
    target=Path(target);target.parent.mkdir(parents=True,exist_ok=True)
    bpy.ops.export_scene.gltf(filepath=str(target),export_format='GLB',export_yup=True,
        export_apply=True,export_extras=True,export_cameras=False,export_lights=False)


def build(profile, target):
    g = profile['geometry']
    details = g['frontDetails']
    curve = details.get('coverEdgeCurve_mm', 0) * .001
    if not 0 <= curve <= .00025:
        raise ValueError('Cover perimeter depth must stay above the chassis')
    root = build_front(profile, target, export=False)
    _knurl(profile, root)
    if not curve:
        _export(target)
        return root
    bezel = bpy.data.objects.get('screen_bezel')
    mat = bezel.data.materials[0]
    bpy.data.objects.remove(bezel, do_unlink=True)
    w,h,d,r,sw,sh,sr = [g[k]*.001 for k in
        ('width_mm','height_mm','thickness_mm','corner_radius_mm',
         'screen_width_mm','screen_height_mm','screen_corner_radius_mm')]
    inset = details.get('blackBezelInset_mm', 0)*.001
    mask = details.get('blackMaskWidth_mm')
    if mask is not None:
        mask *= .001
        outer_w, outer_h, outer_r = sw+2*mask, sh+2*mask, sr+mask
    else:
        outer_w, outer_h = (w-2*inset, h-2*inset) if inset else (w-.0004,h-.0004)
        outer_r = r-inset if inset else r-.0002
    if (inset or mask is not None) and (curve > .00014 or outer_w <= sw or outer_h <= sh or outer_w >= w or outer_h >= h):
        raise ValueError('Reviewed microcurve mask must stay above metal lip and outside content')
    rings, vertices = 12, []
    for layer in range(rings + 1):
        t = layer / rings
        # Equal-depth perimeter: curve away from the planar screenshot along
        # all four edges; the screen's authored UV contract stays unchanged.
        rw = sw + (outer_w - sw)*t
        rh = sh + (outer_h - sh)*t
        rr = sr + (outer_r - sr)*t
        z = d/2 + .00025 - curve*t*t
        vertices.extend(cv((x,y,z)) for x,y in outline(rw,rh,rr,32))
    count = len(vertices)//(rings+1)
    faces = []
    for layer in range(rings):
        for i in range(count):
            j=(i+1)%count
            faces.append((layer*count+i,(layer+1)*count+i,
                          (layer+1)*count+j,layer*count+j))
    mesh=bpy.data.meshes.new('xiaomi_microcurve_cover_perimeter')
    mesh.from_pydata(vertices,[],faces);mesh.update()
    ob=bpy.data.objects.new('microcurve_cover_perimeter',mesh)
    bpy.context.collection.objects.link(ob);ob.parent=root
    mesh.materials.append(mat)
    for face in mesh.polygons:face.use_smooth=True
    _export(target)
    return root
