package dev.qingframe.model

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.io.FileNotFoundException
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dev.qingframe.BuildConfig
import dev.qingframe.render.RenderController
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class ShareStatus(val id:String="",val phase:String="idle",val message:String="",val output:String="",val autoShared:Boolean=false) {
 val busy get()=phase in setOf("importing","rendering","saving")
}
/** Owns a headless Filament renderer on main; survives Activity recreation, never uses editor draft. */
class ShareViewModel(app:Application,private val saved:SavedStateHandle):AndroidViewModel(app) {
 private val mutable=MutableStateFlow(ShareStatus())
 val status=mutable.asStateFlow()
 private var worker:Job?=null
 private var controller:RenderController?=null
 val hasRequest get()=saved.get<String>("request")!=null
 private fun publish(s:ShareStatus){mutable.value=s;saved["phase"]=s.phase;saved["output"]=s.output;saved["autoShared"]=s.autoShared}
 init {
  val id=saved.get<String>("request")
  if(id!=null){
   val output=saved.get<String>("output").orEmpty()
   if(output.isNotBlank()&&outputFile(output).isFile)publish(ShareStatus(id,"ready","已生成套壳截图",output,saved.get<Boolean>("autoShared")?:false))
   else if(File(app.filesDir,"share-projects/$id/draft.json").isFile){
    val store=ProjectStore(app,id,true);publish(ShareStatus(id,"rendering","正在恢复生成…"));render(store,store.load(),id)
   }else publish(ShareStatus(id,"error","上次生成未完成，请从原应用重新分享图片"))
  }
 }
 @Suppress("DEPRECATION") fun accept(intent:Intent,newDelivery:Boolean=false){
  if(!newDelivery&&hasRequest)return
  if(mutable.value.busy){publish(mutable.value.copy(message="正在处理上一张图片；请完成或取消后重新分享"));return}
  val uri=runCatching{if(android.os.Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri::class.java)else intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)}.getOrNull()
  if(intent.action!=Intent.ACTION_SEND||intent.type?.startsWith("image/")!=true||uri==null||uri.scheme!="content"){
   publish(ShareStatus(phase="error",message="请从其他应用分享一张可读取的图片"));return
  }
  val namespace=if(BuildConfig.DEBUG)intent.getStringExtra("test_session")?.takeIf{it.matches(Regex("[A-Za-z0-9-]{1,48}"))}else null
  val id="share-${UUID.randomUUID()}";saved["request"]=id;saved["namespace"]=namespace
  publish(ShareStatus(id,"importing","正在读取分享的图片…"))
  val store=ProjectStore(getApplication(),id,true)
  val defaultsSnapshot=DefaultSettingsStore(getApplication(),namespace).loadOrNull()?:store.productState()
  worker=viewModelScope.launch {
   try{
    val (path,w,h)=withContext(Dispatchers.IO){
     // Reap only abandoned, app-owned share sessions after process death; never editor projects.
     File(getApplication<Application>().filesDir,"share-projects").listFiles()?.filter{it.isDirectory&&it.name!=id&&it.name.matches(Regex("share-[0-9a-f-]{36}"))&&it.lastModified()<System.currentTimeMillis()-7L*24*60*60*1000}?.forEach{it.deleteRecursively()}
     store.import(uri)
    }
    val snapshot=withContext(Dispatchers.IO){defaultsSnapshot.copy(imagePath=path,imageWidth=w,imageHeight=h).safe().also{store.save(it)}}
    generate(store,snapshot,id)
   }catch(e:TimeoutCancellationException){if(mutable.value.id==id)publish(ShareStatus(id,"error",friendlyError(e)))}
   catch(e:CancellationException){throw e}
   catch(e:Exception){if(mutable.value.id==id)publish(ShareStatus(id,"error",friendlyError(e)));Log.e("QF_SHARE","FAILED $id",e)}
   finally{withContext(NonCancellable+Dispatchers.IO){store.deleteShareSources()}}
  }
 }
 private fun render(store:ProjectStore,snapshot:ProjectState,id:String){worker=viewModelScope.launch{
  try{generate(store,snapshot,id)}catch(e:TimeoutCancellationException){if(mutable.value.id==id)publish(ShareStatus(id,"error",friendlyError(e)))}catch(e:CancellationException){throw e}catch(e:Exception){if(mutable.value.id==id)publish(ShareStatus(id,"error",friendlyError(e)))}
  finally{withContext(NonCancellable+Dispatchers.IO){store.deleteShareSources()}}
 }}
 private suspend fun generate(store:ProjectStore,snapshot:ProjectState,id:String){
  currentCoroutineContext().ensureActive()
  publish(ShareStatus(id,"rendering","正在按默认参数生成…"))
  var failure="";val renderer=RenderController(getApplication(),store){if(it.isNotEmpty())failure=it};controller=renderer
  try {
  renderer.applyState(snapshot)
  withTimeout(30_000){while(!renderer.ready){if(failure.isNotEmpty())error(failure);delay(50)}}
  val (w,h)=snapshot.outputSize()
  val bitmap=suspendCancellableCoroutine<Bitmap>{continuation->
   continuation.invokeOnCancellation{renderer.cancelExport()}
   renderer.export(snapshot,w,h){result->result.fold(onSuccess={if(continuation.isActive)continuation.resume(it)else it.recycle()},onFailure={if(continuation.isActive)continuation.resumeWithException(it)})}
  }
  publish(ShareStatus(id,"saving","正在准备分享 PNG…"))
  val name="$id.png"
  try{withContext(Dispatchers.IO){
   val directory=File(getApplication<Application>().cacheDir,"exports/share-results").apply{mkdirs()}
   // Keep grants usable after chooser launch. Only this app's week-old share files are pruned.
   directory.listFiles()?.filter{it.isFile&&it.lastModified()<System.currentTimeMillis()-7L*24*60*60*1000}?.forEach{it.delete()}
   val temp=File(directory,"$name.tmp")
   try{temp.outputStream().use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"PNG 编码失败"}};currentCoroutineContext().ensureActive();check(temp.renameTo(File(directory,name))){"PNG 保存失败"}}
   finally{temp.delete()}
  }}finally{bitmap.recycle()}
  currentCoroutineContext().ensureActive()
  publish(ShareStatus(id,"ready","已生成 $w × $h PNG",name))
  Log.i("QF_SHARE","READY $id ${w}x$h colorway=${snapshot.colorwayId} output=$name")
  }finally{renderer.close();if(controller===renderer)controller=null}
 }
 private fun friendlyError(e:Exception):String=when(e){
  is SecurityException->"没有读取这张图片的权限，请从原应用重新分享"
  is FileNotFoundException->"分享的图片已不可用，请从原应用重新分享"
  is ImageDecoder.DecodeException->"图片无法解码，请分享一张有效图片"
  is TimeoutCancellationException->"生成超时，请重新分享图片"
  else->if(e.message?.startsWith("图片超过")==true||e.message=="图片像素过大")e.message!!else"无法生成套壳截图，请重新分享图片"
 }
 fun outputFile(name:String):File {require(name.matches(Regex("share-[A-Za-z0-9-]+\\.png")));return File(getApplication<Application>().cacheDir,"exports/share-results/$name")}
 fun chooserStarted(){publish(mutable.value.copy(autoShared=true));Log.i("QF_SHARE","CHOOSER ${mutable.value.id}")}
 fun chooserFailed(message:String){publish(mutable.value.copy(phase="share-error",message="无法打开分享：$message"))}
 fun cancel(){if(!mutable.value.busy)return;worker?.cancel();controller?.cancelExport();publish(mutable.value.copy(phase="cancelled",message="已取消生成"))}
 override fun onCleared(){worker?.cancel();controller?.cancelExport();controller?.close();controller=null;super.onCleared()}
}
