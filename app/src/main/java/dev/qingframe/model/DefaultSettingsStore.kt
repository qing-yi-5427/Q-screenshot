package dev.qingframe.model

import android.content.Context
import org.json.JSONObject
import java.io.File

/** A render configuration, deliberately independent of source images and editor drafts. */
class DefaultSettingsStore(context:Context,testSession:String?=null) {
 private val file=if(testSession==null)File(context.filesDir,"default-render-settings.json")else {
  require(testSession.matches(Regex("[A-Za-z0-9-]{1,48}")))
  File(context.filesDir,"test-projects/$testSession/default-render-settings.json")
 }
 @Synchronized fun save(state:ProjectState){
  val settings=JSONObject(StateJson.encode(state.safe())).apply{remove("imagePath");remove("imageWidth");remove("imageHeight")}
  val document=JSONObject().put("version",1).put("settings",settings)
  file.parentFile!!.mkdirs();val temp=File(file.parentFile,file.name+".tmp")
  try{temp.writeText(document.toString(2));check(temp.renameTo(file)){"默认参数保存失败"}}finally{temp.delete()}
 }
 @Synchronized fun loadOrNull():ProjectState?=runCatching{
  if(!file.isFile)return null
  val document=JSONObject(file.readText());require(document.getInt("version")==1)
  val settings=document.getJSONObject("settings");settings.remove("imagePath");settings.remove("imageWidth");settings.remove("imageHeight")
  val state=StateJson.decode(settings.toString())
  val size=when {state.deviceId=="redmi-k90-pro-max"->1200 to 2608;state.stateId=="folded"->1168 to 1712;state.portrait->1672 to 2364;else->2364 to 1672}
  state.copy(imagePath="",imageWidth=size.first,imageHeight=size.second)
 }.getOrNull()
 fun applyToImage(base:ProjectState,path:String,w:Int,h:Int):ProjectState=(loadOrNull()?:base).copy(imagePath=path,imageWidth=w,imageHeight=h).safe()
}
