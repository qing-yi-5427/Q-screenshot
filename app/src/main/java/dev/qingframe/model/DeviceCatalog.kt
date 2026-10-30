package dev.qingframe.model

/** Stable app-level choices. GLB node/material contracts still come from each device.json. */
data class DeviceChoice(val id:String,val title:String,val compactTitle:String,val states:List<String>,val defaultState:String,val notes:String)
object DeviceCatalog {
 const val FOLD="xiaomi-18-fold"
 val legacyDeviceIds=setOf("wide-fold","mix-fold-4")
 fun canonicalId(id:String)=if(id in legacyDeviceIds)FOLD else id
 val choices=listOf(
  DeviceChoice(FOLD,"Xiaomi 18 Fold · 外观重建","Xiaomi 18 Fold",listOf("folded","unfolded"),"folded","官方机身尺寸/屏幕比例与多视角重建；玻璃背盖/7系铝中框，局部尺寸与PBR估计。"),
  DeviceChoice("redmi-k90-pro-max","REDMI K90 Pro Max","REDMI K90 Pro Max",listOf("portrait"),"portrait","依据官方长宽厚、直屏比例与多视角重建；6系铝中框/玻纤背盖为原创PBR近似，局部尺寸估计。")
 )
 fun find(id:String)=choices.firstOrNull{it.id==canonicalId(id)}?:choices.first()
 fun stateLabel(state:String)=when(state){"portrait"->"直板 · 主屏";"unfolded"->"展开 · 内屏";else->"合拢 · 外屏"}
 fun normalize(state:ProjectState):ProjectState {
  val device=find(state.deviceId)
  val legacyPortrait=state.deviceId in legacyDeviceIds&&state.stateId=="portrait"
  val requested=if(legacyPortrait)"unfolded"else state.stateId
  return state.copy(deviceId=device.id,stateId=requested.takeIf{it in device.states}?:device.defaultState,portrait=if(device.states.size==1)false else state.portrait||legacyPortrait,colorwayId=ColorwayCatalog.find(device.id,state.colorwayId).id)
 }
 fun recommend(deviceId:String,w:Int,h:Int):Pair<String,Boolean> {
  require(w>0&&h>0)
  return if(deviceId=="redmi-k90-pro-max")"portrait" to false else recommendState(w,h)
 }
}
