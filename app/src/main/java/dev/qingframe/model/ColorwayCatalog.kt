package dev.qingframe.model

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
  DeviceColorway("xiaomi-18-fold","warm-gold-white","暖金白","Xiaomi_DragonCrystalGlass_3.0",null,mapOf("x18_7series_warm_silver" to BodyMaterial(0xdddbd2,0.95f,0.3f),"x18_front_satin_aluminum" to BodyMaterial(0xdddbd2,0.95f,0.38f),"x18_warm_gold_white_AG_glass" to BodyMaterial(0xede8de,0.0f,0.37f)),"玻璃背盖 · PBR近似"),
  DeviceColorway("xiaomi-18-fold","black","黑色","Xiaomi_DragonCrystalGlass_3.0",null,mapOf("x18_7series_warm_silver" to BodyMaterial(0x404143,0.95f,0.3f),"x18_front_satin_aluminum" to BodyMaterial(0x404143,0.95f,0.38f),"x18_warm_gold_white_AG_glass" to BodyMaterial(0x202122,0.0f,0.37f)),"玻璃背盖 · PBR近似"),
  DeviceColorway("xiaomi-18-fold","xiye-red","西野红","Xiaomi_DragonCrystalGlass_3.0",null,mapOf("x18_7series_warm_silver" to BodyMaterial(0xaa1d2e,0.95f,0.3f),"x18_front_satin_aluminum" to BodyMaterial(0xaa1d2e,0.95f,0.38f),"x18_warm_gold_white_AG_glass" to BodyMaterial(0x761529,0.0f,0.37f)),"玻璃背盖 · PBR近似"),
  DeviceColorway("xiaomi-18-fold","ceramic-special","陶瓷特别版","silicon_nitride_ceramic",null,mapOf("x18_7series_warm_silver" to BodyMaterial(0xd7b6af,0.95f,0.3f),"x18_front_satin_aluminum" to BodyMaterial(0xd7b6af,0.95f,0.38f),"x18_warm_gold_white_AG_glass" to BodyMaterial(0xe8cbc4,0.0f,0.24f)),"氮化硅陶瓷 · PBR近似"),
  DeviceColorway("redmi-k90-pro-max","flowing-gold-white","流金白","glass_fiber",0.0079f,mapOf("k90_aircraft_aluminum" to BodyMaterial(0xdedfdc,0.95f,0.21f),"k90_sculpted_aluminum_deco" to BodyMaterial(0xcdcdc7,0.9f,0.26f),"k90_warm_white_glass_fiber" to BodyMaterial(0xe5e2d8,0.0f,0.42f)),"玻纤背盖 · PBR近似"),
  DeviceColorway("redmi-k90-pro-max","black","黑色","glass_fiber",0.0079f,mapOf("k90_aircraft_aluminum" to BodyMaterial(0x3f4245,0.95f,0.21f),"k90_sculpted_aluminum_deco" to BodyMaterial(0x3f4143,0.9f,0.26f),"k90_warm_white_glass_fiber" to BodyMaterial(0x27292c,0.0f,0.42f)),"玻纤背盖 · PBR近似"),
  DeviceColorway("redmi-k90-pro-max","denim","丹宁色","Tech-Denim",0.0083f,mapOf("k90_aircraft_aluminum" to BodyMaterial(0xb8bec6,0.95f,0.21f),"k90_sculpted_aluminum_deco" to BodyMaterial(0xc4c9ce,0.9f,0.26f),"k90_warm_white_glass_fiber" to BodyMaterial(0x405576,0.0f,0.52f)),"丹宁材质 · 8.3mm · PBR近似"),
  DeviceColorway("redmi-k90-pro-max","champion-white","K90 Pro Max 冠军版","glass_fiber_family_exact_champion_substrate_not_separately_stated",null,mapOf("k90_aircraft_aluminum" to BodyMaterial(0x9c9e9e,0.95f,0.21f),"k90_sculpted_aluminum_deco" to BodyMaterial(0x1e2122,0.9f,0.26f),"k90_warm_white_glass_fiber" to BodyMaterial(0xe2e2db,0.0f,0.42f)),"冠军版白色 · 前侧配色近似，背部联名图案省略")
 )
 fun choices(deviceId:String):List<DeviceColorway> = all.filter{it.deviceId==DeviceCatalog.find(deviceId).id}
 fun defaultId(deviceId:String):String = if(DeviceCatalog.find(deviceId).id=="redmi-k90-pro-max")"flowing-gold-white"else"warm-gold-white"
 fun find(deviceId:String,id:String):DeviceColorway = choices(deviceId).firstOrNull{it.id==id}?:choices(deviceId).first{it.id==defaultId(deviceId)}
 fun depth(state:ProjectState,base:Float):Float = find(state.deviceId,state.colorwayId).thicknessMeters?:base
}
