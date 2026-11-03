package dev.qingframe
import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
@RunWith(AndroidJUnit4::class)
class ModelLibraryShareTest {
 @Test fun unavailableDefaultWaitsWithSourceAndCancelNeverSubstitutesFold(){
  val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext;val session="share-missing-${UUID.randomUUID().toString().take(12)}"
  val defaults=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",colorwayId="denim",zoom=1.3f,canvasWidth=9,canvasHeight=16)
  DefaultSettingsStore(context,session).save(defaults)
  val source=File(context.cacheDir,"exports/share-library-fixture.png").apply{parentFile!!.mkdirs()};context.assets.open("fixtures/redmi.png").use{input->source.outputStream().use{input.copyTo(it)}}
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",source);lateinit var model:ShareViewModel
  try{
   ins.runOnMainSync{model=ShareViewModel(context.applicationContext as Application,SavedStateHandle());model.accept(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra("test_session",session))}
   val end=SystemClock.uptimeMillis()+10000;while(model.status.value.phase in setOf("idle","importing")&&SystemClock.uptimeMillis()<end)SystemClock.sleep(50)
   val status=model.status.value;assertEquals("waiting-resource",status.phase)
   val store=ProjectStore(context,status.id,true);val pending=store.load();assertEquals(defaults.deviceId,pending.deviceId);assertEquals(defaults.colorwayId,pending.colorwayId);assertEquals(defaults.zoom,pending.zoom,0f);assertEquals(defaults.canvasWidth,pending.canvasWidth);assertTrue(store.imageFile(pending.imagePath).isFile);assertEquals("",status.output)
   ins.runOnMainSync{model.cancel()};assertEquals("cancelled",model.status.value.phase)
  }finally{source.delete();File(context.filesDir,"test-projects/$session").deleteRecursively()}
 }
 @Test fun networkFailureLeavesShareRetryableInsteadOfBusyAndRetryUsesSameImage(){
  val ins=InstrumentationRegistry.getInstrumentation();val context=ins.targetContext;val root=File(context.cacheDir,"share-network-${UUID.randomUUID()}").apply{mkdirs()};val session="share-retry-${UUID.randomUUID().toString().take(12)}"
  val wrapped=object:android.content.ContextWrapper(context){override fun getFilesDir()=root;override fun getApplicationContext():android.content.Context=this}
  val data=ins.context.assets.open("library/k90.zip").use{it.readBytes()};var online=false
  val library=ModelLibrary.isolated(wrapped){url->object:java.net.HttpURLConnection(java.net.URL(url)){
   override fun connect(){};override fun disconnect(){};override fun usingProxy()=false
   override fun getResponseCode():Int{if(!online)throw java.net.SocketTimeoutException("Synthetic disconnected network");return 200}
   override fun getContentLengthLong()=data.size.toLong();override fun getInputStream():java.io.InputStream=java.io.ByteArrayInputStream(data)
  }}
  val defaults=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",colorwayId="denim",zoom=1.15f,canvasWidth=4,canvasHeight=5,exportLongEdge=320)
  DefaultSettingsStore(context,session).save(defaults);val source=File(context.cacheDir,"exports/share-retry-fixture.png").apply{parentFile!!.mkdirs()};context.assets.open("fixtures/redmi.png").use{input->source.outputStream().use{input.copyTo(it)}};val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",source)
  lateinit var model:ShareViewModel
  fun waitFor(phases:Set<String>,timeout:Long=15000){val end=SystemClock.uptimeMillis()+timeout;while(model.status.value.phase !in phases&&SystemClock.uptimeMillis()<end)SystemClock.sleep(40);assertTrue(model.status.value.toString(),model.status.value.phase in phases)}
  try{
   ins.runOnMainSync{model=ShareViewModel(context.applicationContext as Application,SavedStateHandle(),library);model.accept(Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM,uri).putExtra("test_session",session))};waitFor(setOf("waiting-resource"))
   val pending=ProjectStore(context,model.status.value.id,true).load();val image=pending.imagePath
   ins.runOnMainSync{model.downloadResource()};waitFor(setOf("resource-error"));assertFalse(model.status.value.busy);assertTrue(model.status.value.message.contains("连接超时"));assertEquals("error",library.status.value.phase);assertEquals(image,ProjectStore(context,model.status.value.id,true).load().imagePath)
   online=true;ins.runOnMainSync{model.downloadResource()};waitFor(setOf("ready","error"),30000);assertEquals("ready",model.status.value.phase);assertTrue(model.outputFile(model.status.value.output).isFile);model.outputFile(model.status.value.output).delete()
  }finally{source.delete();root.deleteRecursively();File(context.filesDir,"test-projects/$session").deleteRecursively()}
 }

}
