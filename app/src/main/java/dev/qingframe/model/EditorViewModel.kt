package dev.qingframe.model

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EditorViewModel(app:Application,testSession:String?=null):AndroidViewModel(app) {
 private val downloadOwner="editor-${java.util.UUID.randomUUID()}"
 val library=ModelLibrary.get(app)
 val store=ProjectStore(app,testSession)
 private val defaults=DefaultSettingsStore(app,testSession)
 private val mutable=MutableStateFlow(library.pin(store.load()))
 val state=mutable.asStateFlow()
 private val history=History<ProjectState>()
 private var saveJob:Job?=null
 private val saveMutex=Mutex()
 @Volatile private var saveGeneration=0L
 val message=MutableStateFlow(store.migrationNotice.orEmpty())
 val importBusy=MutableStateFlow(false)
 val historyRevision=MutableStateFlow(0)
 val canUndo get()=history.canUndo
 val canRedo get()=history.canRedo
 private fun scheduleSave(snapshot:ProjectState,delayMs:Long){
  saveJob?.cancel();val generation=++saveGeneration
  saveJob=viewModelScope.launch {
   delay(delayMs)
   withContext(Dispatchers.IO){saveMutex.withLock{
    if(generation==saveGeneration)runCatching{store.save(snapshot)}.onFailure{message.value="草稿保存失败，请检查存储空间"}
   }}
  }
 }
 fun update(record:Boolean=true,block:(ProjectState)->ProjectState){
  val old=mutable.value;val new=library.pin(block(old).safe());if(old==new)return
  if(record)history.push(old,new)
  mutable.value=new;historyRevision.value++;scheduleSave(new,450)
 }
 fun beginGesture(){history.begin(mutable.value)}
 fun endGesture(){history.commit(mutable.value);historyRevision.value++}
 fun undo(){mutable.value=history.undo(mutable.value).safe();historyRevision.value++;saveNow()}
 fun redo(){mutable.value=history.redo(mutable.value).safe();historyRevision.value++;saveNow()}
 fun saveNow(){scheduleSave(mutable.value,0)}
 fun import(uri:Uri){
  if(importBusy.value)return
  importBusy.value=true
  viewModelScope.launch {
   try{
    val(p,w,h)=withContext(Dispatchers.IO){store.import(uri)}
    val recommendation=DeviceCatalog.recommend(mutable.value.deviceId,w,h)
    update{defaults.applyToImage(it.copy(stateId=recommendation.first,portrait=recommendation.second,fitMode="fit",cropX=0f,cropY=0f),p,w,h)}
    // Imports are serialized. Preserve every image reachable by current / undo / redo history.
    val keep=history.referencedStates(mutable.value).map{it.imagePath}.filter{it.isNotEmpty()}.toSet()
    withContext(Dispatchers.IO){store.pruneSources(keep)}
    message.value="已导入 $w × $h · ${if(mutable.value.fitMode=="fit")"完整显示"else"填满屏幕"}"
   }catch(e:Exception){message.value=e.message?:"图片无法读取"}
   finally{importBusy.value=false}
  }
 }
 fun cancelModelDownload(){library.cancel(downloadOwner)}
 fun refreshLibrary(){viewModelScope.launch{library.refresh(downloadOwner)}}
 fun selectDevice(id:String,onSelected:()->Unit={}){viewModelScope.launch{
  try{
   val known=library.version(id)
   val snapshot=if(known.isNotBlank())library.snapshot(ProjectState(deviceId=id,modelVersion=known))else library.download(id,downloadOwner)
   val device=snapshot.choice
   update{store.demoState(it.copy(deviceId=id,modelVersion=device.modelVersion,stateId=device.defaultState,colorwayId=snapshot.defaultColor,portrait=false,cropX=0f,cropY=0f))}
   onSelected();message.value="${device.compactTitle} 已可使用"
  }catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.message?:"下载失败，请重试"}
 }}
 fun downloadNewVersion(id:String){viewModelScope.launch{try{library.download(id,downloadOwner);message.value="新版资源已下载；点选机型后才会使用，当前作品保持原版本"}catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.message?:"新版下载失败，已下载版本仍可使用"}}}
 fun downloadCurrent(){val before=mutable.value;viewModelScope.launch{
  try{
   val entry=library.entry(before.deviceId)
   require(before.modelVersion.isBlank()||entry?.choice?.modelVersion==before.modelVersion){"此草稿需要 ${before.modelVersion} 版资源；请重新选择机型后明确使用新版"}
   val snapshot=library.download(before.deviceId,downloadOwner)
   if(mutable.value.deviceId==before.deviceId&&mutable.value.modelVersion==before.modelVersion)update(false){it.copy(modelVersion=snapshot.choice.modelVersion)}
  }catch(e:CancellationException){throw e}catch(e:Exception){message.value=e.message?:"机型下载失败，请重试"}
 }}
 fun saveDefaultSettings(){val snapshot=mutable.value.copy();viewModelScope.launch{runCatching{withContext(Dispatchers.IO){defaults.save(snapshot)}}.fold(onSuccess={message.value="已保存默认参数；以后导入和分享图片时自动使用"},onFailure={message.value="默认参数保存失败：${it.message}"})}}
 override fun onCleared(){library.cancel(downloadOwner);super.onCleared()}
 fun reset(){
  val current=mutable.value
  val base=defaults.loadOrNull()?:store.automaticCanvas(store.productState().copy(deviceId=current.deviceId,modelVersion=current.modelVersion,stateId=current.stateId,colorwayId=ColorwayCatalog.defaultId(current.deviceId,current.modelVersion),portrait=current.portrait))
  if(!library.available(base)){message.value="默认机型资源未下载；请在机型库下载后再重置，当前作品已保留";return}
  update{base.copy(imagePath=it.imagePath,imageWidth=it.imageWidth,imageHeight=it.imageHeight)}
 }
}
