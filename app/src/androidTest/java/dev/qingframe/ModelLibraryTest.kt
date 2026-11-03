package dev.qingframe
import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
@RunWith(AndroidJUnit4::class)
class ModelLibraryTest {
 private val ins get()=InstrumentationRegistry.getInstrumentation()
 private val target get()=ins.targetContext
 private fun isolated(block:suspend (ModelLibrary,File)->Unit)=runBlocking {
  val root=File(target.cacheDir,"library-test-${UUID.randomUUID()}").apply{mkdirs()}
  val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  try{block(ModelLibrary.isolated(context),root)}finally{root.deleteRecursively()}
 }
 private fun fixture(root:File)=File(root,"k90.zip").also{out->ins.context.assets.open("library/k90.zip").use{input->out.outputStream().use{input.copyTo(it)}}}
 private fun entry(file:File):LibraryEntry {
  val meta=ZipFile(file).use{z->JSONObject(z.getInputStream(z.getEntry("package.json")).bufferedReader().use{it.readText()})};val id=meta.getString("id");val sha=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
  return LibraryEntry(DeviceChoice(id,meta.getString("name"),id,listOf("portrait"),"portrait","",meta.getString("modelVersion")),"https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-test/test.zip",sha,file.length(),8000,true)
 }
 @Test fun packageIsAtomicOfflineAndCannotReplaceBuiltin()=isolated{library,root->
  assertTrue(library.available(ProjectState()));assertFalse(library.available(ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait")))
  val zip=fixture(root);val e=entry(zip);val installed=library.installVerified(e,zip);val state=ProjectState(deviceId=e.choice.id,stateId="portrait",colorwayId="denim",modelVersion=installed.choice.modelVersion)
  assertTrue(library.available(state));assertEquals(4,library.snapshot(state).colors.size);assertTrue(library.snapshot(state).manifest.getJSONObject("states").getJSONObject("portrait").getJSONObject("colorwayAssets").has("denim"))
  val before=library.snapshot(state).read(target,"devices/${e.choice.id}/portrait.glb")
  assertThrows(IllegalArgumentException::class.java){runBlocking{library.installVerified(e.copy(sha256="0".repeat(64)),zip)}}
  assertArrayEquals(before,library.snapshot(state).read(target,"devices/${e.choice.id}/portrait.glb"))
  assertThrows(IllegalArgumentException::class.java){runBlocking{library.installVerified(e.copy(choice=e.choice.copy(id=DeviceCatalog.FOLD)),zip)}}
  val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  assertTrue(ModelLibrary.isolated(context).available(state))
 }
 @Test fun maliciousArchiveAndCancelledInstallLeaveOldPackageUsable()=isolated{library,root->
  val zip=fixture(root);val e=entry(zip);library.installVerified(e,zip);val state=ProjectState(deviceId=e.choice.id,stateId="portrait",modelVersion=e.choice.modelVersion)
  val bad=File(root,"bad.zip");ZipOutputStream(bad.outputStream()).use{out->ZipFile(zip).use{z->z.entries().asSequence().forEach{en->out.putNextEntry(ZipEntry(en.name));z.getInputStream(en).use{it.copyTo(out)};out.closeEntry()}};out.putNextEntry(ZipEntry("../../escape"));out.write(1);out.closeEntry()}
  val sha=MessageDigest.getInstance("SHA-256").digest(bad.readBytes()).joinToString(""){"%02x".format(it)}
  assertThrows(Exception::class.java){runBlocking{library.installVerified(e.copy(sha256=sha,bytes=bad.length()),bad)}}
  assertTrue(library.available(state));assertFalse(File(root.parentFile,"escape").exists())
  coroutineScope{val child=launch{cancel();library.installVerified(e,zip)};child.join()};assertTrue(library.available(state))
  assertFalse(File(root,"model-library").listFiles().orEmpty().any{it.name.startsWith("tmp-")})
 }
 @Test fun missingRedmiDraftAndDefaultsKeepIdentityImageAndAllSettings()=isolated{_,root->
  val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  val s=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",colorwayId="denim",imagePath="source-test.img",imageWidth=1200,imageHeight=2608,zoom=1.37f,canvasWidth=9,canvasHeight=16)
  val old=JSONObject(StateJson.encode(s)).apply{remove("modelVersion")}.toString();val loaded=StateJson.decode(old)
  assertEquals(s,loaded);val store=ProjectStore(context,"migration");store.save(loaded);assertEquals(s,store.load());DefaultSettingsStore(context,"migration").save(s);val defaults=DefaultSettingsStore(context,"migration").loadOrNull()!!;assertEquals(s.deviceId,defaults.deviceId);assertEquals(s.colorwayId,defaults.colorwayId);assertEquals(s.zoom,defaults.zoom,0f);assertEquals(s.canvasWidth,defaults.canvasWidth)
 }
 @Test fun oldDownloadedVersionRetainsGeometryAndPaletteAfterInstallingUpdate()=isolated{library,root->
  fun copy(name:String)=File(root,name).also{out->ins.context.assets.open("library/$name").use{input->out.outputStream().use{input.copyTo(it)}}}
  val old=copy("xiaomi-15-legacy-1.0.0.zip");val oldEntry=entry(old);library.installVerified(oldEntry,old)
  val state=ProjectState(deviceId="xiaomi-15",stateId="portrait",modelVersion="1.0.0",colorwayId="color-1")
  val snapshot=library.snapshot(state);val path=snapshot.manifest.getJSONObject("states").getJSONObject("portrait").getString("modelPath");val bytes=snapshot.read(target,path);val colors=snapshot.colors
  val update=copy("xiaomi-15.zip");val latest=library.installVerified(entry(update),update)
  assertEquals("1.0.2",latest.choice.modelVersion);assertEquals(48,latest.colors.size);assertTrue(library.available(state));assertEquals("1.0.0",library.snapshot(state).choice.modelVersion);assertEquals(colors,library.snapshot(state).colors);assertArrayEquals(bytes,library.snapshot(state).read(target,path))
  val store=ProjectStore(target,"library-demo-regression",modelLibrary=library);val demo=store.demoState(state.copy(modelVersion=latest.choice.modelVersion,imagePath=""));val hints=latest.manifest.getJSONObject("states").getJSONObject("portrait").getJSONArray("inputSizeHints").getJSONObject(0)
  assertEquals(hints.getInt("width"),demo.imageWidth);assertEquals(hints.getInt("height"),demo.imageHeight)
  val bitmap=store.decode(demo,1024);try{assertEquals(demo.imageWidth.toFloat()/demo.imageHeight,bitmap.width.toFloat()/bitmap.height,.003f)}finally{bitmap.recycle()}
  val real=state.copy(imagePath="kept.img",imageWidth=333,imageHeight=777,zoom=1.4f);assertEquals(real,store.demoState(real))

 }

 @Test fun cancellationOnlyAffectsTheOwnerAndCleansTemporaryDownload()=runBlocking {
  val root=File(target.cacheDir,"library-owner-${UUID.randomUUID()}").apply{mkdirs()};val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this}
  val zip=fixture(root);val data=zip.readBytes();val started=java.util.concurrent.CountDownLatch(1);val released=java.util.concurrent.CountDownLatch(1);var disconnected=false
  val library=ModelLibrary.isolated(context){url->object:java.net.HttpURLConnection(java.net.URL(url)){
   override fun connect(){};override fun usingProxy()=false;override fun disconnect(){disconnected=true;released.countDown()}
   override fun getResponseCode()=200;override fun getContentLengthLong()=data.size.toLong()
   override fun getInputStream():java.io.InputStream=object:java.io.ByteArrayInputStream(data){override fun read(b:ByteArray,off:Int,len:Int):Int{started.countDown();check(released.await(5,java.util.concurrent.TimeUnit.SECONDS));if(disconnected)throw java.io.IOException("cancelled");return super.read(b,off,len)}}
  }}
  try{
   val worker=async(Dispatchers.IO){library.download("redmi-k90-pro-max","editor-owner")}
   assertTrue(started.await(5,java.util.concurrent.TimeUnit.SECONDS));library.cancel("share-some-other-request");assertFalse(disconnected);assertTrue(worker.isActive)
   library.cancel("editor-owner");try{worker.await();fail("Expected cancellation")}catch(expected:CancellationException){}
   assertTrue(disconnected);assertTrue(library.available(ProjectState()));assertFalse(File(root,"model-library").listFiles().orEmpty().any{it.name.startsWith("tmp-")})
  }finally{released.countDown();root.deleteRecursively()}
 }

 @Test fun archivedBuiltinVersionSurvivesAnApkVersionChange()=isolated{library,root->
  val current=library.snapshot(ProjectState());val original=requireNotNull(current.directory);val hash="0".repeat(64);val old=File(original.parentFile,"0.0.1-old_$hash");original.copyRecursively(old)
  val metadata=JSONObject(File(old,"package.json").readText()).put("modelVersion","0.0.1-old");File(old,"package.json").writeText(metadata.toString())
  val manifestFile=File(old,"devices/${DeviceCatalog.FOLD}/device.json");manifestFile.writeText(JSONObject(manifestFile.readText()).put("assetVersion","0.0.1-old").toString())
  val context=object:ContextWrapper(target){override fun getFilesDir()=root;override fun getApplicationContext():Context=this};val reopened=ModelLibrary.isolated(context)
  assertEquals(current.choice.modelVersion,reopened.version(DeviceCatalog.FOLD));val oldState=ProjectState(modelVersion="0.0.1-old");assertTrue(reopened.available(oldState));assertTrue(reopened.available(ProjectState(modelVersion=current.choice.modelVersion)))
  val path=current.manifest.getJSONObject("states").getJSONObject("folded").getString("modelPath");assertArrayEquals(current.read(target,path),reopened.snapshot(oldState).read(target,path));assertEquals("0.0.1-old",reopened.snapshot(oldState).manifest.getString("assetVersion"))
 }

 @Test fun accepts64PalettesButRejects65WithoutChangingInstalledVersion()=isolated{library,root->
  val original=fixture(root)
  fun make(count:Int):File {
   val out=File(root,"colors-$count.zip");val version="1.2.1-limit$count"
   ZipOutputStream(out.outputStream()).use{sink->ZipFile(original).use{zip->zip.entries().asSequence().forEach{item->
    sink.putNextEntry(ZipEntry(item.name));val bytes=zip.getInputStream(item).use{it.readBytes()}
    val rewritten=when(item.name){"package.json"->{val meta=JSONObject(bytes.toString(Charsets.UTF_8));val colors=meta.getJSONArray("colors");val first=colors.getJSONObject(0).toString();while(colors.length()<count)colors.put(JSONObject(first).put("id","limit-color-${colors.length()}"));meta.put("modelVersion",version).toString().toByteArray()};"devices/redmi-k90-pro-max/device.json"->JSONObject(bytes.toString(Charsets.UTF_8)).put("assetVersion",version).toString().toByteArray();else->bytes}
    sink.write(rewritten);sink.closeEntry()
   }}};return out
  }
  val valid=make(64);val e=entry(valid);val installed=library.installVerified(e,valid);assertEquals(64,installed.colors.size)
  val invalid=make(65);assertThrows(IllegalArgumentException::class.java){runBlocking{library.installVerified(entry(invalid),invalid)}}
  assertEquals(installed.choice.modelVersion,library.version(installed.choice.id));assertEquals(64,library.snapshot(ProjectState(deviceId=installed.choice.id,modelVersion=installed.choice.modelVersion,stateId="portrait")).colors.size)
 }

}
