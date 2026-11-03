package dev.qingframe.model

import android.content.Context
import dev.qingframe.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipFile

class ModelDownloadException(message:String,cause:Throwable):java.io.IOException(message,cause)
class MissingModelException(val deviceId:String):IllegalStateException("${DeviceCatalog.find(deviceId).compactTitle} 的机型资源尚未下载")
data class LibraryEntry(val choice:DeviceChoice,val packageUrl:String,val sha256:String,val bytes:Long,val minimumApp:Int,val downloadable:Boolean)
data class LibraryStatus(val revision:Int=0,val deviceId:String="",val phase:String="idle",val received:Long=0,val total:Long=0,val message:String="") {
 val busy get()=phase in setOf("catalog","downloading","verifying")
}
data class ModelSnapshot(val choice:DeviceChoice,val directory:File?,val manifest:JSONObject,val colors:List<DeviceColorway>,val defaultColor:String,val sha256:String){
 fun read(context:Context,path:String):ByteArray {
  require(path.startsWith("devices/${choice.id}/")&&LibraryPolicy.entry(path)){"模型路径不安全"}
  return if(directory==null)context.assets.open(path).use{it.readBytes()}else File(directory,path).also{require(it.canonicalPath.startsWith(directory.canonicalPath+File.separator))}.readBytes()
 }
}

/** Immutable version directories stay available to drafts, undo and in-flight exports. */
class ModelLibrary private constructor(private val context:Context,private val openConnection:(String)->HttpURLConnection={URL(it).openConnection() as HttpURLConnection}){
 private val root=File(context.filesDir,"model-library").apply{mkdirs()}
 private val mutable=MutableStateFlow(LibraryStatus())
 val status=mutable.asStateFlow()
 private val lock=Mutex()
 @Volatile private var entries=emptyMap<String,LibraryEntry>()
 @Volatile private var snapshots=emptyMap<String,ModelSnapshot>()
 @Volatile private var active=emptyMap<String,String>()
 @Volatile private var connection:HttpURLConnection?=null
 @Volatile private var activeJob:Job?=null
 @Volatile private var activeOwner=""
 @Volatile private var cancelRequested=false
 init {
  val builtin=readPackage(JSONObject(context.assets.open("devices/${DeviceCatalog.FOLD}/package.json").bufferedReader().use{it.readText()}),null,"")
  val archived=runCatching{archiveBuiltin(builtin)}.getOrElse{android.util.Log.w("QF_LIBRARY","Built-in version backup unavailable; APK model remains usable",it);builtin}
  register(archived,true)
  runCatching{context.assets.open("library/catalog.json").bufferedReader().use{parseIndex(it.readText())}}
  runCatching{File(root,"catalog.json").takeIf{it.isFile}?.let{parseIndex(it.readText())}}
  root.listFiles()?.filter{it.name.startsWith("tmp-")}?.forEach{it.deleteRecursively()}
  root.listFiles()?.filter{it.isDirectory&&!it.name.startsWith("tmp-")}?.forEach{device->
   device.listFiles()?.filter{it.isDirectory}?.forEach{dir->runCatching{val p=JSONObject(File(dir,"package.json").readText());val snap=readPackage(p,dir,dir.name.substringAfterLast('_'));validateFiles(snap);register(snap,false)}}
   if(device.name!=DeviceCatalog.FOLD)runCatching{val version=File(device,"active").readText().trim();if(snapshots.containsKey("${device.name}@$version"))active=active+(device.name to version)}
  }
  // Register active metadata last; archived versions must never replace the visible current version.
  active.forEach{(id,v)->snapshots["$id@$v"]?.let{register(it,true)}}
 }
 val choices:List<DeviceChoice> get()=(DeviceCatalog.choices+snapshots.values.map{it.choice}).distinctBy{it.id}
 fun entry(id:String)=entries[DeviceCatalog.canonicalId(id)]
 fun snapshot(state:ProjectState):ModelSnapshot {
  val id=DeviceCatalog.canonicalId(state.deviceId);val v=state.modelVersion.ifBlank{active[id].orEmpty()}
  return snapshots["$id@$v"]?:throw MissingModelException(id)
 }
 fun available(state:ProjectState)=runCatching{snapshot(state)}.isSuccess
 fun version(id:String)=active[DeviceCatalog.canonicalId(id)].orEmpty()
 fun pin(state:ProjectState):ProjectState=if(state.modelVersion.isBlank()&&available(state))state.copy(modelVersion=snapshot(state).choice.modelVersion)else state
 private fun register(s:ModelSnapshot,current:Boolean){
  snapshots=snapshots+("${s.choice.id}@${s.choice.modelVersion}" to s)
  DeviceCatalog.register(s.choice,current)
  for(state in s.choice.states){val row=s.manifest.getJSONObject("states").getJSONObject(state);val b=row.getJSONArray("bodySizeMeters");val hint=row.getJSONArray("inputSizeHints").getJSONObject(0);DeviceGeometryCatalog.register(s.choice.id,s.choice.modelVersion,state,DeviceGeometry(b.getDouble(0).toFloat(),b.getDouble(1).toFloat(),b.getDouble(2).toFloat(),hint.getInt("width"),hint.getInt("height")),current)}
  if(current){active=active+(s.choice.id to s.choice.modelVersion)}
  // Versioned palettes never change; unversioned aliases follow only the active package.
  ColorwayCatalog.register(s.choice.id,s.choice.modelVersion,s.colors,s.defaultColor,current)
 }
 private fun publish(phase:String,id:String="",message:String="",received:Long=0,total:Long=0){mutable.value=LibraryStatus(mutable.value.revision+1,id,phase,received,total,message)}
 private fun parseIndex(text:String){
  require(text.toByteArray().size<=1024*1024){"机型索引过大"};val j=JSONObject(text);require(j.getInt("schemaVersion")==1){"机型库版本不兼容"};val rows=j.getJSONArray("devices");require(rows.length()<=100){"机型索引数量超限"};val parsed=mutableMapOf<String,LibraryEntry>()
  for(i in 0 until rows.length()){
   val row=rows.getJSONObject(i);val choice=choice(row);require(!parsed.containsKey(choice.id)){"重复机型"}
   val url=row.optString("packageUrl","");val sha=row.optString("packageSha256","");val bytes=row.optLong("packageBytes",0);val minimum=row.optInt("minAppVersionCode",8000)
   val downloadable=row.optBoolean("downloadable",false)&&row.optString("verificationStatus")!="generic_approximation"
   if(downloadable)require(LibraryPolicy.packageUrl(url)&&sha.matches(Regex("[a-f0-9]{64}"))&&bytes in 1..LibraryPolicy.MAX_ZIP){"机型包索引不安全：${choice.id}"}
   parsed[choice.id]=LibraryEntry(choice,url,sha,bytes,minimum,downloadable)
  }
  entries=parsed
  parsed.values.forEach{e->if(!active.containsKey(e.choice.id))DeviceCatalog.register(e.choice)}
 }
 suspend fun refresh(owner:String="library-refresh"){
  if(!lock.tryLock())return
  try{cancelRequested=false;activeOwner=owner;activeJob=currentCoroutineContext()[Job];publish("catalog",message="正在更新机型列表…");val bytes=withContext(Dispatchers.IO){fetch(LibraryPolicy.INDEX,1024*1024,null)};parseIndex(bytes.toString(Charsets.UTF_8));withContext(Dispatchers.IO){atomicWrite(File(root,"catalog.json"),bytes)};publish("idle",message="机型列表已更新")}
  catch(e:CancellationException){publish("idle");throw e}catch(e:Exception){publish("error",message="机型列表更新失败，已下载机型仍可使用；请重试")}
  finally{connection?.disconnect();connection=null;activeJob=null;activeOwner="";lock.unlock()}
 }
 suspend fun download(id:String,owner:String="library-download"):ModelSnapshot {
  val e=entry(id)?:throw IllegalStateException("请先刷新机型库，再下载此机型")
  require(e.downloadable){"此机型资源尚未完成，暂不可下载"};require(e.minimumApp<=BuildConfig.VERSION_CODE){"请先升级 App 后再下载此机型"}
  if(!lock.tryLock())throw IllegalStateException("另一项下载正在进行，请等待或取消")
  var temp:File?=null;cancelRequested=false;activeOwner=owner;activeJob=currentCoroutineContext()[Job]
  try{return withContext(Dispatchers.IO){
   activeJob=currentCoroutineContext()[Job];publish("downloading",id,"正在下载 ${e.choice.compactTitle}",0,e.bytes)
   val archive=File(root,"tmp-${UUID.randomUUID()}.zip");temp=archive
   fetch(e.packageUrl,LibraryPolicy.MAX_ZIP,e.bytes,archive){received->publish("downloading",id,"正在下载 ${e.choice.compactTitle}",received,e.bytes)}
   currentCoroutineContext().ensureActive();publish("verifying",id,"正在校验机型资源…",e.bytes,e.bytes)
   val installed=installVerified(e,archive);publish("idle",id,"${e.choice.compactTitle} 已可离线使用");installed
  }}catch(e:CancellationException){publish("cancelled",id,"已取消下载；原有资源未改变");throw e}
  catch(e:Exception){if(cancelRequested||!currentCoroutineContext().isActive){publish("cancelled",id,"已取消下载；原有资源未改变");throw CancellationException("已取消下载").apply{initCause(e)}};val message=when(e){is java.net.SocketTimeoutException->"连接超时，请检查网络后重试";is javax.net.ssl.SSLException->"下载连接失败，请检查网络后重试";is java.io.IOException->"下载失败，请检查网络或存储空间后重试";else->e.message?.takeIf{it.any{c->c in '\u4e00'..'\u9fff'}}?:"机型资源下载或校验失败，请重试"};android.util.Log.w("QF_LIBRARY","Download failed: $id",e);publish("error",id,message);throw ModelDownloadException(message,e)}
  finally{temp?.delete();connection?.disconnect();connection=null;activeJob=null;activeOwner="";lock.unlock()}
 }
 fun owns(owner:String)=activeOwner==owner
 fun cancel(owner:String){if(!owns(owner))return;cancelRequested=true;activeJob?.cancel();connection?.disconnect()}
 private suspend fun fetch(initial:String,limit:Long,expected:Long?,destination:File?=null,progress:((Long)->Unit)?=null):ByteArray {
  require(initial==LibraryPolicy.INDEX||LibraryPolicy.packageUrl(initial)){"不允许的机型库来源"};var url=initial
  repeat(5){
   currentCoroutineContext().ensureActive();val c=openConnection(url);connection=c;c.instanceFollowRedirects=false;c.connectTimeout=15000;c.readTimeout=15000;c.setRequestProperty("Accept","application/octet-stream");c.setRequestProperty("Accept-Encoding","identity")
   val code=c.responseCode
   if(code in listOf(301,302,303,307,308)){
    val next=c.getHeaderField("Location")?:error("下载重定向缺少目标");require(initial!=LibraryPolicy.INDEX&&LibraryPolicy.redirectUrl(next)){"机型包重定向不安全"};url=next;c.disconnect()
   }else{
    require(code==200){"下载失败（HTTP $code），请检查网络后重试"};val length=c.contentLengthLong;require(length<=limit&&(expected==null||length<0||length==expected)){"下载大小与索引不符"}
    val memory=if(destination==null)java.io.ByteArrayOutputStream()else null;val output:java.io.OutputStream=memory?:destination!!.outputStream();var total=0L;output.use{sink->c.inputStream.use{input->val b=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;total+=n;require(total<=limit&&(expected==null||total<=expected)){"下载超过大小限制"};sink.write(b,0,n);progress?.invoke(total)}}};c.disconnect();require(expected==null||total==expected){"下载不完整，请重试"};return memory?.toByteArray()?:ByteArray(0)
   }
  };error("下载重定向次数超限")
 }
 /** Package bytes are fully checked before any published directory/pointer changes. Also used by isolated tests. */
 internal suspend fun installVerified(e:LibraryEntry,archive:File):ModelSnapshot {
  require(e.choice.id!=DeviceCatalog.FOLD){"内置机型不接受下载覆盖"};require(archive.length()==e.bytes&&shaFile(archive)==e.sha256){"机型包校验失败，请重新下载"};LibraryPolicy.rejectLinks(archive)
  val temp=File(root,"tmp-${UUID.randomUUID()}").apply{check(mkdir())};var moved=false
  try{
   val names=mutableSetOf<String>();var total=0L
   ZipFile(archive).use{zip->val rows=zip.entries();var count=0;while(rows.hasMoreElements()){
    currentCoroutineContext().ensureActive();val row=rows.nextElement();require(++count<=LibraryPolicy.MAX_FILES){"ZIP 文件数超限"};val name=row.name.removeSuffix("/");require(LibraryPolicy.entry(name)&&names.add(name)){"ZIP 路径不安全或重复"}
    require(name=="package.json"||name=="devices"||name=="devices/${e.choice.id}"||name.startsWith("devices/${e.choice.id}/")){"ZIP 含其他机型或未知文件"}
    val target=File(temp,name);require(target.canonicalPath.startsWith(temp.canonicalPath+File.separator)){"ZIP 路径越界"}
    if(row.isDirectory){target.mkdirs();continue};require(name=="package.json"||name=="devices/${e.choice.id}/device.json"||name.matches(Regex("devices/${e.choice.id}/[a-z0-9-]+\\.glb"))){"机型包只允许元数据与 GLB"};val fileLimit=if(name.endsWith(".json"))1024L*1024 else 32L*1024*1024;require(row.size in 0..fileLimit){"解压单文件大小超限"};var fileBytes=0L;target.parentFile!!.mkdirs()
    zip.getInputStream(row).use{input->target.outputStream().use{out->val b=ByteArray(65536);while(true){currentCoroutineContext().ensureActive();val n=input.read(b);if(n<0)break;total+=n;fileBytes+=n;require(total<=LibraryPolicy.MAX_EXPANDED&&fileBytes<=fileLimit){"机型包解压大小超限"};out.write(b,0,n)}}}
   }}
   val snap=readPackage(JSONObject(File(temp,"package.json").readText()),temp,e.sha256)
   require(snap.choice.id==e.choice.id&&snap.choice.modelVersion==e.choice.modelVersion&&snap.choice.states==e.choice.states&&snap.choice.defaultState==e.choice.defaultState){"机型包身份或版本与索引不一致"}
   validateFiles(snap);currentCoroutineContext().ensureActive()
   val device=File(root,snap.choice.id).apply{mkdirs()};val destination=File(device,"${snap.choice.modelVersion}_${e.sha256}")
   val sameVersion=snapshots["${snap.choice.id}@${snap.choice.modelVersion}"]
   require(sameVersion==null||sameVersion.sha256==e.sha256){"同一机型版本的校验发生变化，拒绝覆盖"}
   val used=root.walkTopDown().filter{it.isFile&&!it.path.startsWith(temp.path)}.sumOf{it.length()};require(destination.exists()||used+total<384L*1024*1024){"机型库空间不足，请保留现有资源并稍后重试"}
   if(!destination.exists()){check(temp.renameTo(destination)){"机型资源安装失败"};moved=true}
   val installed=readPackage(JSONObject(File(destination,"package.json").readText()),destination,e.sha256)
   currentCoroutineContext().ensureActive();atomicWrite(File(device,"active"),installed.choice.modelVersion.toByteArray());register(installed,true);return installed
  }finally{if(!moved)temp.deleteRecursively()}
 }
 private fun archiveBuiltin(builtin:ModelSnapshot):ModelSnapshot {
  val device=File(root,DeviceCatalog.FOLD).apply{mkdirs()}
  // An assetVersion is immutable, including its palettes. Never repoint old projects
  // to a changed APK model that accidentally reused the same version string.
  device.listFiles()?.filter{it.isDirectory}?.forEach{dir->
   val existing=runCatching{readPackage(JSONObject(File(dir,"package.json").readText()),dir,dir.name.substringAfterLast('_')).also{validateFiles(it)}}.getOrNull()
   if(existing?.choice?.modelVersion==builtin.choice.modelVersion)return existing
  }
  val names=mutableSetOf("devices/${DeviceCatalog.FOLD}/package.json","devices/${DeviceCatalog.FOLD}/device.json")
  for(state in builtin.choice.states){val st=builtin.manifest.getJSONObject("states").getJSONObject(state);names.add(st.getString("modelPath"));st.optJSONObject("colorwayAssets")?.let{c->c.keys().forEach{names.add(c.getString(it))}}}
  val digest=MessageDigest.getInstance("SHA-256");val contents=names.sorted().associateWith{path->context.assets.open(path).use{it.readBytes()}.also{digest.update(path.toByteArray());digest.update(it)}}
  val hash=digest.digest().joinToString(""){"%02x".format(it)};val temp=File(root,"tmp-${UUID.randomUUID()}").apply{check(mkdir())}
  try{
   contents.forEach{(name,bytes)->val target=File(temp,if(name.endsWith("/package.json"))"package.json"else name);target.parentFile!!.mkdirs();target.writeBytes(bytes)}
   val snapshot=readPackage(JSONObject(File(temp,"package.json").readText()),temp,hash);validateFiles(snapshot)
   val destination=File(device,"${builtin.choice.modelVersion}_$hash");check(temp.renameTo(destination));return readPackage(JSONObject(File(destination,"package.json").readText()),destination,hash)
  }finally{temp.deleteRecursively()}
 }
 private fun choice(j:JSONObject):DeviceChoice {
  val id=j.getString("id");val version=j.getString("modelVersion");require(LibraryPolicy.slug(id)&&LibraryPolicy.version(version)){"机型或版本标识不安全"};val a=j.getJSONArray("states");require(a.length() in 1..4);val states=(0 until a.length()).map{a.getString(it).also{v->require(LibraryPolicy.slug(v))}};require(states.distinct().size==states.size);val default=j.getString("defaultState");require(default in states);val name=j.getString("name");require(name.length in 1..200);return DeviceChoice(id,name,name.substringBefore(" ·"),states,default,j.optString("notes").take(4000),version)
 }
 private fun readPackage(p:JSONObject,directory:File?,hash:String):ModelSnapshot {
  require(p.getInt("schemaVersion")==1){"机型包版本不兼容"};val c=choice(p);val manifest=if(directory==null)JSONObject(context.assets.open("devices/${c.id}/device.json").bufferedReader().use{it.readText()})else JSONObject(File(directory,"devices/${c.id}/device.json").readText());require(manifest.getInt("schemaVersion")==1&&manifest.getString("deviceId")==c.id&&manifest.getString("assetVersion")==c.modelVersion)
  require(manifest.getJSONObject("states").keys().asSequence().toSet()==c.states.toSet())
  val a=p.getJSONArray("colors");require(a.length() in 1..64);val colors=(0 until a.length()).map{i->val row=a.getJSONObject(i);val id=row.getString("id");require(LibraryPolicy.slug(id));val materials=row.getJSONObject("materials");require(materials.length() in 1..24);val mapped=materials.keys().asSequence().associateWith{name->require(name.length in 1..160);val m=materials.getJSONObject(name);val rgb=m.getString("srgb");require(rgb.matches(Regex("#[0-9a-fA-F]{6}")));val metal=m.getDouble("metallic");val rough=m.getDouble("roughness");require(metal.isFinite()&&metal in 0.0..1.0&&rough.isFinite()&&rough in 0.0..1.0);BodyMaterial(rgb.substring(1).toInt(16),metal.toFloat(),rough.toFloat())};val depth=if(row.has("thicknessMeters"))row.getDouble("thicknessMeters").also{require(it.isFinite()&&it in .001.. .03)}.toFloat()else null;DeviceColorway(c.id,id,row.getString("title").take(100),row.optString("backMaterial"),depth,mapped,row.optString("notes").take(2000))}
  require(colors.map{it.id}.distinct().size==colors.size);val default=p.getString("defaultColorwayId");require(colors.any{it.id==default});return ModelSnapshot(c,directory,manifest,colors,default,hash)
 }
 private fun validateFiles(s:ModelSnapshot){
  val expected=mutableSetOf("package.json","devices/${s.choice.id}/device.json")
  for(stateId in s.choice.states){val st=s.manifest.getJSONObject("states").getJSONObject(stateId);val paths=mutableMapOf(st.getString("modelPath") to st.getString("assetHash"));require(st.optString("asset",st.getString("modelPath"))==st.getString("modelPath"));val variants=st.optJSONObject("colorwayAssets");variants?.keys()?.forEach{color->require(s.colors.any{it.id==color});paths[variants.getString(color)]=st.getJSONObject("colorwayAssetHashes").getString(color)}
   for(key in listOf("bodySizeMeters","screenSizeMeters")){val d=st.getJSONArray(key);require(d.length()==if(key=="bodySizeMeters")3 else 2);for(i in 0 until d.length())require(d.getDouble(i).let{it.isFinite()&&it>0&&it<.5})}
   val aspect=st.getDouble("screenAspectRatio");val screen=st.getJSONArray("screenSizeMeters");require(aspect.isFinite()&&aspect in .2..5.0&&kotlin.math.abs(aspect-screen.getDouble(0)/screen.getDouble(1))<1e-6);val bindings=st.getJSONArray("screenBindings");val active=(0 until bindings.length()).map{bindings.getJSONObject(it)}.filter{it.optBoolean("active")};require(active.size==1&&active.single().getString("contentNode")==st.getString("activeScreenNode")&&active.single().getInt("materialSlot")==0)
   paths.forEach{(path,hash)->require(path.startsWith("devices/${s.choice.id}/")&&path.endsWith(".glb")&&LibraryPolicy.entry(path));expected.add(path);val bytes=s.read(context,path);require(bytes.size in 28..32*1024*1024&&sha(bytes)==hash){"模型文件校验失败"};validateGlb(bytes,st,s.colors)}
  }
  if(s.directory!=null)require(s.directory.walkTopDown().filter{it.isFile}.map{it.relativeTo(s.directory).invariantSeparatorsPath}.toSet()==expected){"机型包含多余或缺失文件"}
 }
 private fun validateGlb(bytes:ByteArray,state:JSONObject,colors:List<DeviceColorway>){
  val b=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);require(b.int==0x46546c67&&b.int==2&&b.int==bytes.size){"GLB 头部无效"};val length=b.int;require(length>0&&length<=bytes.size-28&&b.int==0x4e4f534a);val json=JSONObject(bytes.copyOfRange(20,20+length).toString(Charsets.UTF_8));require(json.optJSONArray("images")?.length().let{it==null||it==0}&&json.optJSONArray("textures")?.length().let{it==null||it==0}){"机型包不允许图片或纹理"}
  b.position(20+length);val bin=b.int;require(b.int==0x004e4942&&bin==bytes.size-28-length);val buffers=json.getJSONArray("buffers");require(buffers.length()==1&&!buffers.getJSONObject(0).has("uri"));val nodes=json.getJSONArray("nodes");val active=state.getString("activeScreenNode");val matches=(0 until nodes.length()).map{nodes.getJSONObject(it)}.filter{it.optString("name")==active};require(matches.size==1);val primitives=json.getJSONArray("meshes").getJSONObject(matches.single().getInt("mesh")).getJSONArray("primitives");require(primitives.length()==1);val materials=json.getJSONArray("materials");require(materials.getJSONObject(primitives.getJSONObject(0).getInt("material")).getString("name")=="screen_content");validateScreenAccessors(json,bytes,28+length,primitives.getJSONObject(0),state);val names=(0 until materials.length()).map{materials.getJSONObject(it).optString("name")}.toSet();require(colors.flatMap{it.materials.keys}.all{it in names}){"模型配色材质不匹配"}
 }
 private fun validateScreenAccessors(json:JSONObject,bytes:ByteArray,binOffset:Int,primitive:JSONObject,state:JSONObject){
  val attributes=primitive.getJSONObject("attributes");val accessors=json.getJSONArray("accessors");val views=json.getJSONArray("bufferViews")
  fun floats(index:Int,components:Int):List<FloatArray>{
   val a=accessors.getJSONObject(index);require(a.getInt("componentType")==5126&&a.getString("type")=="VEC$components"&&!a.has("sparse"));val count=a.getInt("count");require(count in 3..200000);val v=views.getJSONObject(a.getInt("bufferView"));require(v.optInt("buffer",0)==0);val stride=v.optInt("byteStride",components*4);require(stride>=components*4&&stride<=256);val base=binOffset+v.optInt("byteOffset",0)+a.optInt("byteOffset",0);require(base>=binOffset&&base.toLong()+(count-1L)*stride+components*4<=bytes.size)
   val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);return (0 until count).map{i->FloatArray(components){c->buffer.getFloat(base+i*stride+c*4).also{require(it.isFinite())}}}
  }
  val pos=floats(attributes.getInt("POSITION"),3);val uv=floats(attributes.getInt("TEXCOORD_0"),2);val normal=floats(attributes.getInt("NORMAL"),3);require(pos.size==uv.size&&pos.size==normal.size);val size=state.getJSONArray("screenSizeMeters");val sw=size.getDouble(0);val sh=size.getDouble(1)
  for(i in pos.indices){require(normal[i][2]>.999f);require(kotlin.math.abs(uv[i][0]-(pos[i][0]/sw+.5))<1e-5&&kotlin.math.abs(uv[i][1]-(.5-pos[i][1]/sh))<1e-5){"屏幕 UV 合同不匹配"};require(kotlin.math.abs(pos[i][0])<.3&&kotlin.math.abs(pos[i][1])<.3)}
 }
 private fun atomicWrite(file:File,bytes:ByteArray){val temp=File(file.parentFile,"${file.name}.${UUID.randomUUID()}.tmp");try{temp.outputStream().use{it.write(bytes);it.fdSyncIfPossible()};check(temp.renameTo(file)){"机型库索引写入失败"}}finally{temp.delete()}}
 private fun java.io.OutputStream.fdSyncIfPossible(){if(this is java.io.FileOutputStream)fd.sync()}
 companion object {
  @Volatile private var instance:ModelLibrary?=null
  fun get(context:Context):ModelLibrary=instance?:synchronized(this){instance?:ModelLibrary(context.applicationContext).also{instance=it}}
  internal fun isolated(context:Context,connectionFactory:((String)->HttpURLConnection)?=null)=if(connectionFactory==null)ModelLibrary(context)else ModelLibrary(context,connectionFactory)
  private fun shaFile(file:File):String {val digest=MessageDigest.getInstance("SHA-256");file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}};return digest.digest().joinToString(""){"%02x".format(it)}}
  private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
 }
}
