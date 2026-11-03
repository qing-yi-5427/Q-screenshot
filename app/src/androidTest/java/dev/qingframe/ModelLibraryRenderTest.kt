package dev.qingframe
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import dev.qingframe.render.RenderController
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
@RunWith(AndroidJUnit4::class)
class ModelLibraryRenderTest {
 private val ins get()=InstrumentationRegistry.getInstrumentation()
 private val target get()=ins.targetContext
 @Test fun allPublishedPackagesAndPalettesActuallyLoadOfflineInFilament()=runBlocking<Unit> {
  val root=File(target.cacheDir,"library-render-${UUID.randomUUID()}").apply{mkdirs()}
  val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  val batch=InstrumentationRegistry.getArguments().getString("library_batch")?:"library-validation";require(batch.matches(Regex("[a-z0-9-]+")));val evidence=File(target.getExternalFilesDir(null),batch).apply{mkdirs()};val results=JSONArray();val highRecords=JSONArray();val skipHigh=InstrumentationRegistry.getArguments().getString("library_skip_high_sha").orEmpty().split(',').toSet()
  var renderer:RenderController?=null
  try{
   val library=ModelLibrary.isolated(context);val catalog=JSONObject(ins.context.assets.open("library/catalog.json").bufferedReader().use{it.readText()}).getJSONArray("devices")
   val installed=mutableListOf<ModelSnapshot>()
   for(i in 0 until catalog.length()){
    val row=catalog.getJSONObject(i);if(!row.optBoolean("downloadable"))continue
    val id=row.getString("id");val zip=File(root,"$id.zip");ins.context.assets.open("library/$id.zip").use{input->zip.outputStream().use{input.copyTo(it)}}
    val states=row.getJSONArray("states");val choice=DeviceChoice(id,row.getString("name"),id,(0 until states.length()).map{states.getString(it)},row.getString("defaultState"),row.getString("notes"),row.getString("modelVersion"))
    installed+=library.installVerified(LibraryEntry(choice,row.getString("packageUrl"),row.getString("packageSha256"),row.getLong("packageBytes"),row.getInt("minAppVersionCode"),true),zip);zip.delete()
   }
   val reopened=ModelLibrary.isolated(context) // No network: only persisted immutable files and active pointers.
   val store=ProjectStore(context,"render-library");val source=File(context.cacheDir,"exports/library-qa.png").apply{parentFile!!.mkdirs()}
   val uri=androidx.core.content.FileProvider.getUriForFile(target,"${target.packageName}.files",source)
   var failure="";ins.runOnMainSync{renderer=RenderController(context,store,reopened){if(it.isNotBlank())failure=it}}
   for(snap in installed){
    val hint=snap.manifest.getJSONObject("states").getJSONObject(snap.choice.defaultState).getJSONArray("inputSizeHints").getJSONObject(0);writeQaImage(source,hint.getInt("width"),hint.getInt("height"),snap.choice.id);val (path,w,h)=store.import(uri)
    for(state in snap.choice.states)for(color in snap.colors){
     failure="";val project=ProjectState(deviceId=snap.choice.id,stateId=state,colorwayId=color.id,modelVersion=snap.choice.modelVersion,imagePath=path,imageWidth=w,imageHeight=h,background="transparent",environmentRotation=90f,shadow=0f,reflection=.08f,canvasWidth=4,canvasHeight=5,exportLongEdge=320)
     ins.runOnMainSync{renderer!!.applyState(project)};val deadline=SystemClock.uptimeMillis()+15000;var ready=false
     while(!ready&&SystemClock.uptimeMillis()<deadline){ins.runOnMainSync{ready=renderer!!.ready};assertEquals("${snap.choice.id}/${color.id}","",failure);SystemClock.sleep(25)};assertTrue("Ready ${snap.choice.id}/${color.id}",ready)
     val latch=CountDownLatch(1);var bitmap:Bitmap?=null;var error:Throwable?=null
     ins.runOnMainSync{renderer!!.export(project,256,320){bitmap=it.getOrNull();error=it.exceptionOrNull();latch.countDown()}}
     assertTrue(latch.await(20,TimeUnit.SECONDS));assertNull(error);val out=requireNotNull(bitmap)
     try{val pixels=IntArray(out.width*out.height);out.getPixels(pixels,0,out.width,0,0,out.width,out.height);assertTrue("Visible model ${snap.choice.id}/${color.id}",pixels.count{it ushr 24>200}>200);assertEquals(0,out.getPixel(0,0) ushr 24)
      if(color.id==snap.defaultColor)File(evidence,"${snap.choice.id}-$state.png").outputStream().use{out.compress(Bitmap.CompressFormat.PNG,100,it)}
     }finally{out.recycle()}
     if(color.id==snap.defaultColor&&snap.sha256 !in skipHigh){
      for((view,pose) in listOf("front" to Quaternion(),"left" to Quaternion.euler(-28f,12f,-6f),"right" to Quaternion.euler(28f,12f,6f))){
       val scene=project.copy(pose=pose,orthographic=view=="front",reflection=.08f,shadow=.16f,softness=.85f,background="solid",colorA=0xfffafbf8.toInt())
       val high=renderBitmap(renderer!!,scene,1638,2048)
       try{File(evidence,"${snap.choice.id}-$state-2048-$view.png").outputStream().use{high.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{high.recycle()}
       highRecords.put(JSONObject().put("id",snap.choice.id).put("packageSha256",snap.sha256).put("view",view).put("scene",JSONObject(StateJson.encode(scene))));Log.i("QF_LIBRARY_TEST","2048 PASS ${snap.choice.id}/$view 1638x2048")
      }
     }
     results.put(JSONObject().put("id",snap.choice.id).put("state",state).put("color",color.id).put("version",snap.choice.modelVersion).put("packageSha256",snap.sha256).put("sourceWidth",w).put("sourceHeight",h).put("status","PASS"));Log.i("QF_LIBRARY_TEST","PASS ${snap.choice.id}/$state/${color.id}")
    }
   }
   File(evidence,"results.json").writeText(JSONObject().put("source","Android emulator Filament actual bitmap exports").put("models",installed.size).put("checks",results).put("highResolution",highRecords).put("reusedHighSha256",org.json.JSONArray(skipHigh.filter{it.isNotBlank()})).toString(2));Log.i("QF_LIBRARY_TEST","COMPLETE models=${installed.size} palettes=${results.length()} evidence=${evidence.path}")
  }finally{ins.runOnMainSync{renderer?.close()};root.deleteRecursively()}
 }
 private fun renderBitmap(renderer:RenderController,scene:ProjectState,width:Int,height:Int):Bitmap {
  val done=CountDownLatch(1);var bitmap:Bitmap?=null;var failure:Throwable?=null
  ins.runOnMainSync{renderer.export(scene,width,height){bitmap=it.getOrNull();failure=it.exceptionOrNull();done.countDown()}}
  assertTrue("Actual ${width}x$height output",done.await(45,TimeUnit.SECONDS));assertNull(failure);return requireNotNull(bitmap).also{assertEquals(width,it.width);assertEquals(height,it.height)}
 }
 @Test fun realHttpsIndexAndPackagesStayPinnedToPublicRepository()=runBlocking<Unit> {
  assumeTrue(InstrumentationRegistry.getArguments().getString("library_network")=="true")
  val root=File(target.cacheDir,"library-https-${UUID.randomUUID()}").apply{mkdirs()};val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  try{val library=ModelLibrary.isolated(context);library.refresh();assertNotEquals("error",library.status.value.phase)
   val available=library.choices.filter{library.entry(it.id)?.downloadable==true};assertTrue(available.size>=24)
   val fetched=JSONArray();for(choice in available){Log.i("QF_LIBRARY_TEST","HTTPS START ${choice.id}");val installed=library.download(choice.id);fetched.put(JSONObject().put("id",installed.choice.id).put("version",installed.choice.modelVersion).put("packageSha256",installed.sha256));assertEquals(choice.id,installed.choice.id);assertTrue(library.available(ProjectState(deviceId=choice.id,stateId=choice.defaultState,modelVersion=installed.choice.modelVersion)))}
   File(target.getExternalFilesDir(null),"library-https-results.json").writeText(JSONObject().put("origin",LibraryPolicy.INDEX).put("packages",fetched).toString(2));Log.i("QF_LIBRARY_TEST","HTTPS COMPLETE packages=${available.size}")
  }finally{root.deleteRecursively()}
 }
 @Test fun reviewOneCandidatePackageAt2048()=runBlocking<Unit>{
  val args=InstrumentationRegistry.getArguments();val filename=args.getString("library_file").orEmpty();assumeTrue(filename.matches(Regex("[A-Za-z0-9._-]+\\.zip")))
  val label=args.getString("library_label")?:"candidate";require(label.matches(Regex("[A-Za-z0-9._-]{1,80}")))
  val zip=File(target.getExternalFilesDir(null),"library-review-input/$filename");require(zip.isFile)
  val metadata=java.util.zip.ZipFile(zip).use{z->JSONObject(z.getInputStream(z.getEntry("package.json")).bufferedReader().use{it.readText()})};val id=metadata.getString("id");val states=metadata.getJSONArray("states");val choice=DeviceChoice(id,metadata.getString("name"),id,(0 until states.length()).map{states.getString(it)},metadata.getString("defaultState"),metadata.getString("notes"),metadata.getString("modelVersion"));val hash=java.security.MessageDigest.getInstance("SHA-256").digest(zip.readBytes()).joinToString(""){"%02x".format(it)}
  val root=File(target.cacheDir,"library-one-${UUID.randomUUID()}").apply{mkdirs()};val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this};var renderer:RenderController?=null
  val evidence=File(target.getExternalFilesDir(null),"library-reviews/$label").apply{mkdirs()}
  try{
   val library=ModelLibrary.isolated(context);val snapshot=library.installVerified(LibraryEntry(choice,"https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-review/candidate.zip",hash,zip.length(),8000,true),zip)
   val state=snapshot.manifest.getJSONObject("states").getJSONObject(choice.defaultState);val pixels=state.getJSONArray("inputSizeHints").getJSONObject(0);val w=pixels.getInt("width");val h=pixels.getInt("height");val store=ProjectStore(context,"review-one");val image=File(target.cacheDir,"exports/review-one-qa.png").apply{parentFile!!.mkdirs()};writeQaImage(image,w,h,id);val uri=androidx.core.content.FileProvider.getUriForFile(target,"${target.packageName}.files",image);val imported=store.import(uri)
   var error="";ins.runOnMainSync{renderer=RenderController(context,store,library){if(it.isNotBlank())error=it}}
   val project=ProjectState(deviceId=id,stateId=choice.defaultState,modelVersion=choice.modelVersion,colorwayId=args.getString("library_color")?:snapshot.defaultColor,imagePath=imported.first,imageWidth=w,imageHeight=h,background="solid",colorA=0xfffafbf8.toInt(),environmentRotation=90f,reflection=.08f,shadow=.16f,softness=.85f,canvasWidth=4,canvasHeight=5,exportLongEdge=2048)
   ins.runOnMainSync{renderer!!.applyState(project)};val end=SystemClock.uptimeMillis()+15000;var ready=false;while(!ready&&SystemClock.uptimeMillis()<end){ins.runOnMainSync{ready=renderer!!.ready};assertEquals("",error);SystemClock.sleep(30)};assertTrue(ready)
   val views=listOf(Triple("front",Quaternion(),true),Triple("left",Quaternion.euler(-28f,12f,-6f),false),Triple("right",Quaternion.euler(28f,12f,6f),false));val scenes=JSONArray()
   for((name,pose,ortho) in views){if(args.getString("library_views")?.split(',')?.contains(name)==false)continue;val scene=project.copy(pose=pose,orthographic=ortho);val out=renderBitmap(renderer!!,scene,1638,2048);try{File(evidence,"$id-${choice.modelVersion}-2048-$name.png").outputStream().use{out.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{out.recycle()};scenes.put(JSONObject().put("view",name).put("scene",JSONObject(StateJson.encode(scene))))}
   File(evidence,"parameters.json").writeText(JSONObject().put("source","Android emulator Filament actual 1638x2048 exports; original QA source generated at manifest pixel ratio").put("packageSha256",hash).put("scenes",scenes).toString(2));image.delete();Log.i("QF_LIBRARY_TEST","ONE COMPLETE $id version=${choice.modelVersion} $label")
  }finally{ins.runOnMainSync{renderer?.close()};root.deleteRecursively()}
 }
 private fun writeQaImage(file:File,w:Int,h:Int,id:String){
  require(w in 256..5000&&h in 256..5000);val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);canvas.drawColor(0xfff3f5fa.toInt());paint.textSize=w*.035f;paint.color=0xff26313f.toInt();canvas.drawText("Q-screenshot / ORIGINAL QA",w*.07f,h*.10f,paint);canvas.drawText("$id  $w x $h",w*.07f,h*.14f,paint)
  val colors=listOf(0xffff0000.toInt(),0xff00ff00.toInt(),0xff0000ff.toInt(),0xff00ffff.toInt(),0xffff00ff.toInt(),0xffffff00.toInt());colors.forEachIndexed{i,c->paint.color=c;canvas.drawRect(w*(.07f+i*.145f),h*.24f,w*(.07f+(i+1)*.145f),h*.34f,paint)}
  for((text,x,y,color) in listOf(arrayOf<Any>("TL",0f,0f,0xffed3b52.toInt()),arrayOf<Any>("TR",w*.82f,0f,0xff329c69.toInt()),arrayOf<Any>("BL",0f,h*.95f,0xff386cd2.toInt()),arrayOf<Any>("BR",w*.82f,h*.95f,0xffa23cc7.toInt()))){val xx=x as Float;val yy=y as Float;paint.color=color as Int;canvas.drawRect(xx,yy,xx+w*.18f,yy+h*.05f,paint);paint.color=android.graphics.Color.WHITE;canvas.drawText(text as String,xx+w*.03f,yy+h*.035f,paint)}
  paint.color=0xff26313f.toInt();paint.textSize=w*.025f;canvas.drawText("Synthetic source / no personal data",w*.07f,h*.48f,paint)
  for(y in 0..17)for(x in 0..17){paint.color=if((x+y)%2==0)android.graphics.Color.BLACK else android.graphics.Color.WHITE;canvas.drawRect(w*(.07f+x*.025f),h*(.65f+y*.011f),w*(.07f+(x+1)*.025f),h*(.65f+(y+1)*.011f),paint)}
  try{file.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
 }

}
