"""Blender diagnostic renders of an explicit staged GLB, never Android evidence."""
import sys,math
from pathlib import Path
import bpy
from mathutils import Vector
sys.path.insert(0,str(Path(__file__).parent/'blender'))
from build_device import cv
args=sys.argv[sys.argv.index('--')+1:];model=Path(args[0]);out=Path(args[1]);out.mkdir(parents=True,exist_ok=True)
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete(use_global=False)
bpy.ops.import_scene.gltf(filepath=str(model))
scene=bpy.context.scene;scene.render.engine='CYCLES';scene.cycles.samples=8;scene.cycles.use_denoising=True
scene.render.resolution_x=400;scene.render.resolution_y=480;scene.render.resolution_percentage=100;scene.render.image_settings.file_format='PNG';scene.world.color=(.4,.4,.4);scene.view_settings.view_transform='AgX'
m=bpy.data.materials.get('screen_content')
if m:
 m.use_nodes=True;n=m.node_tree.nodes;n.clear();o=n.new('ShaderNodeOutputMaterial');e=n.new('ShaderNodeEmission');e.inputs[0].default_value=(.035,.16,.24,1);e.inputs[1].default_value=.8;m.node_tree.links.new(e.outputs[0],o.inputs[0])
for pos,energy,size in [((.12,.2,.22),.8,.25),((-.2,.02,.1),.5,.22)]:
 bpy.ops.object.light_add(type='AREA',location=cv(pos));l=bpy.context.object;l.data.energy=energy;l.data.size=size;l.rotation_euler=(-l.location).to_track_quat('-Z','Y').to_euler()
bpy.ops.object.camera_add();camera=bpy.context.object;scene.camera=camera;camera.data.type='ORTHO';camera.data.ortho_scale=.185
for name,yaw in [('front',0),('left',-30),('right',30),('side',85)]:
 y=math.radians(yaw);camera.location=cv((.5*math.sin(y),0,.5*math.cos(y)));camera.rotation_euler=(-camera.location).to_track_quat('-Z','Y').to_euler();scene.render.filepath=str(out/(name+'.png'));bpy.ops.render.render(write_still=True)
