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
 val store=ProjectStore(app,testSession)
 private val defaults=DefaultSettingsStore(app,testSession)
 private val mutable=MutableStateFlow(store.load())
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
  val old=mutable.value;val new=block(old).safe();if(old==new)return
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
 fun saveDefaultSettings(){val snapshot=mutable.value.copy();viewModelScope.launch{runCatching{withContext(Dispatchers.IO){defaults.save(snapshot)}}.fold(onSuccess={message.value="已保存默认参数；以后导入和分享图片时自动使用"},onFailure={message.value="默认参数保存失败：${it.message}"})}}
 fun reset(){update{current->val preset=defaults.loadOrNull();val base=preset?:store.automaticCanvas(store.productState().copy(deviceId=current.deviceId,stateId=current.stateId,portrait=current.portrait));base.copy(imagePath=current.imagePath,imageWidth=current.imageWidth,imageHeight=current.imageHeight)}}
}
