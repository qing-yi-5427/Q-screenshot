package dev.qingframe.model

/** Metadata remains usable even when a model is not installed. Never substitute a different device. */
data class DeviceChoice(val id:String,val title:String,val compactTitle:String,val states:List<String>,val defaultState:String,val notes:String,val modelVersion:String="")
object DeviceCatalog {
 const val FOLD="xiaomi-18-fold"
 val legacyDeviceIds=setOf("wide-fold","mix-fold-4")
 fun canonicalId(id:String)=if(id in legacyDeviceIds)FOLD else id
 private val seed=listOf(
  DeviceChoice(FOLD,"Xiaomi 18 Fold · 外观重建","Xiaomi 18 Fold",listOf("folded","unfolded"),"folded","内置机型；局部尺寸与 PBR 为估计。"),
  DeviceChoice("redmi-k90-pro-max","REDMI K90 Pro Max","REDMI K90 Pro Max",listOf("portrait"),"portrait","此机型需要下载资源。"))
 @Volatile private var entries=seed.associateBy{it.id}
 @Volatile private var versions=emptyMap<String,DeviceChoice>()
 val choices:List<DeviceChoice> get()=entries.values.toList()
 fun register(choice:DeviceChoice,current:Boolean=true){synchronized(this){if(current)entries=entries+ (choice.id to choice);if(choice.modelVersion.isNotBlank())versions=versions+("${choice.id}@${choice.modelVersion}" to choice)}}
 fun find(id:String,version:String=""):DeviceChoice {
  val canonical=canonicalId(id)
  if(version.isNotBlank())return versions["$canonical@$version"]?:DeviceChoice(canonical,entries[canonical]?.title?:canonical,entries[canonical]?.compactTitle?:canonical,emptyList(),"portrait","此草稿需要 $version 版机型资源，原状态已保留。",version)
  return entries[canonical]?:DeviceChoice(canonical,canonical,canonical,emptyList(),"portrait","此草稿的机型资源尚不可用，请刷新机型库或下载后重试。")
 }
 fun stateLabel(state:String)=when(state){"portrait"->"直板 · 主屏";"unfolded"->"展开 · 内屏";"folded"->"合拢 · 外屏";else->state}
 fun normalize(state:ProjectState):ProjectState {
  val canonical=canonicalId(state.deviceId)
  val device=find(canonical,state.modelVersion)
  val legacyPortrait=state.deviceId in legacyDeviceIds&&state.stateId=="portrait"
  val requested=if(legacyPortrait)"unfolded"else state.stateId
  // Missing metadata is not evidence that this is a Fold. Keep old drafts/defaults verbatim.
  val target=if(device.states.isEmpty())requested else requested.takeIf{it in device.states}?:device.defaultState
  return state.copy(deviceId=canonical,stateId=target,portrait=if(device.states.size==1)false else state.portrait||legacyPortrait,colorwayId=state.colorwayId.ifBlank{ColorwayCatalog.defaultId(canonical,state.modelVersion)})
 }
 fun recommend(deviceId:String,w:Int,h:Int):Pair<String,Boolean> {
  require(w>0&&h>0);val device=find(deviceId)
  return if(device.states.size==1)device.defaultState to false else if(device.states.containsAll(listOf("folded","unfolded")))recommendState(w,h)else device.defaultState to false
 }
}

/** Pure, versioned manifest geometry shared by camera constraints and demo dimensions. */
data class DeviceGeometry(val width:Float,val height:Float,val depth:Float,val inputWidth:Int,val inputHeight:Int)
object DeviceGeometryCatalog {
 @Volatile private var versions=emptyMap<String,DeviceGeometry>()
 @Volatile private var active=emptyMap<String,DeviceGeometry>()
 fun register(id:String,version:String,state:String,geometry:DeviceGeometry,current:Boolean=true){synchronized(this){versions=versions+("$id@$version/$state" to geometry);if(current)active=active+("$id/$state" to geometry)}}
 fun find(s:ProjectState):DeviceGeometry? {
  val id=DeviceCatalog.canonicalId(s.deviceId)
  if(s.modelVersion.isNotBlank())return versions["$id@${s.modelVersion}/${s.stateId}"]
  active["$id/${s.stateId}"]?.let{return it}
  return when {id=="redmi-k90-pro-max"->DeviceGeometry(.07782f,.16333f,.0079f,1200,2608);id==DeviceCatalog.FOLD&&s.stateId=="unfolded"->DeviceGeometry(.1638f,.1178f,.00502f,2364,1672);id==DeviceCatalog.FOLD&&s.stateId=="folded"->DeviceGeometry(.0836f,.1178f,.01068f,1168,1712);else->null}
 }
}
