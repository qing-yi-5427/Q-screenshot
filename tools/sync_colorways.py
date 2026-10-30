#!/usr/bin/env python3
"""Generate the pure Kotlin palette and small runtime manifests from sourced original PBR estimates."""
from pathlib import Path
import json,shutil
R=Path(__file__).resolve().parents[1]
header='''package dev.qingframe.model

import kotlin.math.pow

/** Official names; reflectance values are original estimates, not manufacturer colorimetry. */
data class BodyMaterial(val srgb:Int,val metallic:Float,val roughness:Float) {
 fun linear():FloatArray= listOf(srgb shr 16 and 255,srgb shr 8 and 255,srgb and 255).map{val v=it/255f;if(v<=.04045f)v/12.92f else ((v+.055f)/1.055f).pow(2.4f)}.toFloatArray()
}
data class DeviceColorway(val deviceId:String,val id:String,val title:String,val backMaterial:String,val thicknessMeters:Float?,val materials:Map<String,BodyMaterial>,val notes:String) {
 val swatch:Int get()=0xff000000.toInt() or materials.values.last().srgb
}
object ColorwayCatalog {
 private val all=listOf(
'''
rows=[];defaults={}
q=lambda x:json.dumps(x,ensure_ascii=False)
f=lambda x:format(x,'.9g')+('f' if '.' in format(x,'.9g') else '.0f')
for device in ['xiaomi-18-fold','redmi-k90-pro-max']:
 source=R/f'assets-source/devices/{device}/colorways.json';j=json.loads(source.read_text());defaults[device]=j['defaultColorwayId']
 runtime={'deviceId':device,'defaultColorwayId':j['defaultColorwayId'],'estimateNotice':j['estimateNotice'],'materialMapping':j['materialMapping'],'colorways':j['colorways']}
 (R/f'app/src/main/assets/devices/{device}/colorways.json').write_text(json.dumps(runtime,ensure_ascii=False,indent=2)+'\n')
 for c in j['colorways']:
  mats=[]
  for region,names in j['materialMapping'].items():
   for name in names:
    m=c[region];rgb=m['baseColorSrgb8'];value=(rgb[0]<<16)|(rgb[1]<<8)|rgb[2];mats.append(f'{q(name)} to BodyMaterial(0x{value:06x},{f(m["metallic"])},{f(m["roughness"])})')
  thick=c['geometry'].get('bodyThicknessMm');ts='null' if thick is None else f(thick/1000)
  note='玻璃背盖 · PBR近似' if c['backMaterial'].startswith('Xiaomi') else '氮化硅陶瓷 · PBR近似' if c['id']=='ceramic-special' else '丹宁材质 · 8.3mm · PBR近似' if c['id']=='denim' else '冠军版白色 · 前侧配色近似，背部联名图案省略' if c['id']=='champion-white' else '玻纤背盖 · PBR近似'
  rows.append(f'  DeviceColorway({q(device)},{q(c["id"])},{q(c["officialNameZh"])},{q(c["backMaterial"])},{ts},mapOf('+','.join(mats)+f'),{q(note)})')
footer='''
 )
 fun choices(deviceId:String):List<DeviceColorway> = all.filter{it.deviceId==DeviceCatalog.find(deviceId).id}
 fun defaultId(deviceId:String):String = if(DeviceCatalog.find(deviceId).id=="redmi-k90-pro-max")"flowing-gold-white"else"warm-gold-white"
 fun find(deviceId:String,id:String):DeviceColorway = choices(deviceId).firstOrNull{it.id==id}?:choices(deviceId).first{it.id==defaultId(deviceId)}
 fun depth(state:ProjectState,base:Float):Float = find(state.deviceId,state.colorwayId).thicknessMeters?:base
}
'''
(R/'app/src/main/java/dev/qingframe/model/ColorwayCatalog.kt').write_text(header+',\n'.join(rows)+footer)
print('Generated',len(rows),'colorways')
