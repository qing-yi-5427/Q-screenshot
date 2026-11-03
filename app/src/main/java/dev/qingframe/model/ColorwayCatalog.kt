package dev.qingframe.model

import kotlin.math.pow

/** Official names; reflectance values are original estimates, not manufacturer colorimetry. */
data class BodyMaterial(val srgb:Int,val metallic:Float,val roughness:Float) {
 fun linear():FloatArray= listOf(srgb shr 16 and 255,srgb shr 8 and 255,srgb and 255).map{val v=it/255f;if(v<=.04045f)v/12.92f else ((v+.055f)/1.055f).pow(2.4f)}.toFloatArray()
}
data class DeviceColorway(val deviceId:String,val id:String,val title:String,val backMaterial:String,val thicknessMeters:Float?,val materials:Map<String,BodyMaterial>,val notes:String) {
 val swatch:Int get()=0xff000000.toInt() or (materials.values.lastOrNull()?.srgb?:0xcccccc)
}
object ColorwayCatalog {
 @Volatile private var palettes=emptyMap<String,List<DeviceColorway>>()
 @Volatile private var defaults=emptyMap<String,String>()
 fun register(deviceId:String,version:String,colors:List<DeviceColorway>,default:String,current:Boolean=true){synchronized(this){palettes=palettes+("$deviceId@$version" to colors);defaults=defaults+("$deviceId@$version" to default);if(current){palettes=palettes+(deviceId to colors);defaults=defaults+(deviceId to default)}}}
 fun choices(deviceId:String,version:String=""):List<DeviceColorway> = if(version.isNotBlank())palettes["${DeviceCatalog.canonicalId(deviceId)}@$version"]?:emptyList()else palettes[DeviceCatalog.canonicalId(deviceId)]?:emptyList()
 fun defaultId(deviceId:String,version:String=""):String = if(version.isNotBlank())defaults["${DeviceCatalog.canonicalId(deviceId)}@$version"]?:"neutral-estimate"else defaults[DeviceCatalog.canonicalId(deviceId)]?:if(deviceId=="redmi-k90-pro-max")"flowing-gold-white"else if(deviceId==DeviceCatalog.FOLD)"warm-gold-white"else "neutral-estimate"
 fun find(deviceId:String,id:String,version:String=""):DeviceColorway = choices(deviceId,version).firstOrNull{it.id==id}?:choices(deviceId,version).firstOrNull{it.id==defaultId(deviceId,version)}?:DeviceColorway(deviceId,id.ifBlank{defaultId(deviceId,version)},id.ifBlank{"未下载配色"},"",null,emptyMap(),"机型资源未下载")
 fun depth(state:ProjectState,base:Float):Float = find(state.deviceId,state.colorwayId,state.modelVersion).thicknessMeters?:base
}
