package dev.qingframe.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

object StateJson {
 fun encode(s:ProjectState):String=JSONObject().apply {
  put("schemaVersion",1);put("deviceId",s.deviceId);put("stateId",s.stateId);put("portrait",s.portrait);put("colorwayId",s.colorwayId);put("modelVersion",s.modelVersion);put("imagePath",s.imagePath);put("imageWidth",s.imageWidth);put("imageHeight",s.imageHeight)
  put("pose",JSONObject().put("x",s.pose.x).put("y",s.pose.y).put("z",s.pose.z).put("w",s.pose.w))
  put("zoom",s.zoom);put("offsetX",s.offsetX);put("offsetY",s.offsetY);put("fitMode",s.fitMode);put("cropX",s.cropX);put("cropY",s.cropY);put("fov",s.fov);put("orthographic",s.orthographic)
  put("lighting",s.lighting);put("lightDirection",s.lightDirection);put("lightIntensity",s.lightIntensity);put("environmentRotation",s.environmentRotation);put("reflection",s.reflection);put("shadow",s.shadow);put("softness",s.softness);put("grounded",s.grounded)
  put("background",s.background);put("colorA",s.colorA);put("colorB",s.colorB);put("canvasWidth",s.canvasWidth);put("canvasHeight",s.canvasHeight);put("exportLongEdge",s.exportLongEdge)
 }.toString(2)
 fun decode(text:String):ProjectState {
  val j=JSONObject(text);require(j.optInt("schemaVersion",1)<=1){"草稿来自更新版本，请更新应用"};val d=ProjectState();val q=j.optJSONObject("pose")
  val originalId=j.optString("deviceId",d.deviceId)
  val canonicalId=DeviceCatalog.canonicalId(originalId)
  val rawState=j.optString("stateId",d.stateId)
  val legacyPortrait=originalId in DeviceCatalog.legacyDeviceIds&&rawState=="portrait"
  fun f(k:String,v:Float)=j.optDouble(k,v.toDouble()).toFloat().let{if(it.isFinite())it else v}
  return d.copy(colorwayId=j.optString("colorwayId",""),deviceId=canonicalId.takeIf{LibraryPolicy.slug(it)}?:d.deviceId,stateId=(if(legacyPortrait)"unfolded"else rawState).takeIf{LibraryPolicy.slug(it)}?:d.stateId,modelVersion=j.optString("modelVersion","").takeIf{it.isBlank()||LibraryPolicy.version(it)}?:"",portrait=j.optBoolean("portrait",false)||legacyPortrait,imagePath=j.optString("imagePath","").takeIf{!it.contains("..")&&!it.startsWith("/")}?:"",imageWidth=j.optInt("imageWidth",1168).coerceAtLeast(1),imageHeight=j.optInt("imageHeight",1712).coerceAtLeast(1),pose=if(q==null)d.pose else Quaternion(q.optDouble("x",0.0).toFloat(),q.optDouble("y",0.0).toFloat(),q.optDouble("z",0.0).toFloat(),q.optDouble("w",1.0).toFloat()),zoom=f("zoom",1f),offsetX=f("offsetX",0f),offsetY=f("offsetY",0f),fitMode=j.optString("fitMode","fit"),cropX=f("cropX",0f),cropY=f("cropY",0f),fov=f("fov",35f),orthographic=j.optBoolean("orthographic",false),lighting=j.optString("lighting","soft"),lightDirection=f("lightDirection",-35f),lightIntensity=f("lightIntensity",1f),environmentRotation=f("environmentRotation",0f),reflection=f("reflection",.12f),shadow=f("shadow",.55f),softness=f("softness",.6f),grounded=j.optBoolean("grounded",false),background=j.optString("background","gradient"),colorA=j.optInt("colorA",d.colorA),colorB=j.optInt("colorB",d.colorB),canvasWidth=j.optInt("canvasWidth",4),canvasHeight=j.optInt("canvasHeight",5),exportLongEdge=j.optInt("exportLongEdge",2560)).safe()
 }
}
class ProjectStore(private val context:Context, private val testSession:String?=null,private val shareSession:Boolean=false,private val modelLibrary:ModelLibrary?=null) {
 private val root=(if(testSession==null)File(context.filesDir,"project") else {
  require(testSession.matches(Regex("[A-Za-z0-9-]{1,48}"))){"Invalid isolated test session"}
  File(context.filesDir,if(shareSession)"share-projects/$testSession"else"test-projects/$testSession")
 }).apply{mkdirs()}
 var migrationNotice:String?=null;private set
 fun load():ProjectState {
  migrationNotice=null
  val draft=File(root,"draft.json")
  if(!draft.isFile)return freshState()
  val text=runCatching{draft.readText()}.getOrElse{return freshState()}
  val decoded=runCatching{StateJson.decode(text)}.getOrElse{return freshState()}
  val original=JSONObject(text).optString("deviceId","")
  val raw=JSONObject(text).optJSONObject("pose")
  val rawPose=if(raw==null)ProjectState().pose else Quaternion(raw.optDouble("x",0.0).toFloat(),raw.optDouble("y",0.0).toFloat(),raw.optDouble("z",0.0).toFloat(),raw.optDouble("w",1.0).toFloat())
  val poseAdjusted=!FrontRotation.equivalent(rawPose,decoded.pose)
  val modelAdjusted=original in DeviceCatalog.legacyDeviceIds
  if(modelAdjusted||poseAdjusted){
   val backup=File(root,if(modelAdjusted)"draft-before-model-update.json"else"draft-before-front-rotation.json")
   runCatching{
    if(!backup.exists()){
     val temp=File(root,backup.name+".tmp")
     try{temp.writeText(text);check(temp.renameTo(backup)){"草稿备份写入失败"}}finally{temp.delete()}
    }
    check(backup.isFile&&backup.length()>0){"草稿备份不可用"}
    save(decoded)
   }.fold(
    onSuccess={migrationNotice=listOfNotNull(if(modelAdjusted){if(original=="mix-fold-4")"MIX Fold4 已移除，草稿已迁移到 Xiaomi 18 Fold"else"近似折叠模型已更新为 Xiaomi 18 Fold"}else null,if(poseAdjusted)"旋转已调整至正面可见范围"else null,"截图与构图参数已保留").joinToString("；")},
    onFailure={migrationNotice="草稿已安全调整，但更新保存失败，请检查存储空间"}
   )
  }
  return decoded
 }
 fun demoState(s:ProjectState):ProjectState {
  if(s.imagePath.isNotEmpty())return s
  val size=demoSize(s);return s.copy(imageWidth=size.first,imageHeight=size.second)
 }
 private fun isSlab(s:ProjectState)=DeviceCatalog.find(s.deviceId,s.modelVersion).states==listOf("portrait")
 private fun demoSize(s:ProjectState):Pair<Int,Int> {
  if(!isSlab(s))return 1168 to 1712
  return runCatching{(modelLibrary?:ModelLibrary.get(context)).snapshot(s).manifest.getJSONObject("states").getJSONObject(s.stateId).getJSONArray("inputSizeHints").getJSONObject(0).let{it.getInt("width") to it.getInt("height")}}.getOrDefault(1200 to 2608)
 }
 fun freshState()=DefaultSettingsStore(context,testSession).loadOrNull()?.let{demoState(it)}?:productState()
 fun productState()=automaticCanvas(ProjectState(deviceId=DeviceCatalog.FOLD,stateId="folded",colorwayId="warm-gold-white",imageWidth=1168,imageHeight=1712,environmentRotation=90f,reflection=.08f,shadow=.16f,softness=.85f,background="solid",colorA=0xfffafbf8.toInt()))
 /** Explicit fit action and new projects only; existing stored canvas dimensions are retained. */
 fun automaticCanvas(s:ProjectState):ProjectState {
  val library=ModelLibrary.get(context);if(!library.available(s))return s
  val device=library.snapshot(s).manifest.getJSONObject("states").getJSONObject(s.stateId)
  val b=device.getJSONArray("bodySizeMeters");val width=b.getDouble(if(s.portrait)1 else 0);val height=b.getDouble(if(s.portrait)0 else 1)
  val ratio=(width/height*1.14).coerceIn(.3,3.0)
  val cw=(ratio*1000).toInt();val ch=1000
  fun gcd(a:Int,b:Int):Int=if(b==0)a else gcd(b,a%b)
  val d=gcd(cw,ch)
  return s.copy(canvasWidth=cw/d,canvasHeight=ch/d)
 }
 @Synchronized fun save(s:ProjectState){ val temp=File(root,"draft.tmp");temp.writeText(StateJson.encode(s));check(temp.renameTo(File(root,"draft.json"))){"草稿写入失败"} }
 fun imageFile(path:String)=File(root,path).also{require(!path.contains("..")&&!path.startsWith("/"))}
 fun import(uri:Uri):Triple<String,Int,Int> {
  val mime=context.contentResolver.getType(uri);require(mime?.startsWith("image/")!=false){"请选择图片文件"}
  val tmp=File(root,"import.tmp");try {
   context.contentResolver.openInputStream(uri)?.use { input -> tmp.outputStream().use { output -> val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=48L*1024*1024){"图片超过 48 MB，请选择较小文件"};output.write(buffer,0,n)} } }?:error("无法打开图片")
   var width=0;var height=0
   val bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(tmp)){ decoder,info,_->width=info.size.width;height=info.size.height;require(width>0&&height>0&&width.toLong()*height<=120_000_000){"图片像素过大"};decoder.setTargetSampleSize(maxOf(1,maxOf(width,height)/256));decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE }
   bitmap.recycle();val hash=MessageDigest.getInstance("SHA-256").digest(tmp.readBytes()).joinToString(""){"%02x".format(it)};val path="source-$hash.img";val target=File(root,path);if(target.exists())tmp.delete() else check(tmp.renameTo(target)){"图片复制失败"};return Triple(path,width,height)
  } finally{tmp.delete()}
 }
 fun decode(s:ProjectState,maxEdge:Int):Bitmap {
  val source=if(s.imagePath.isEmpty())ImageDecoder.createSource(context.assets,if(isSlab(s))"demo-redmi.png"else"demo.png") else {
   val file=imageFile(s.imagePath);check(file.isFile){"源截图已丢失，请重新导入；不会替换为示例图"};ImageDecoder.createSource(file)
  }
  val decoded=ImageDecoder.decodeBitmap(source){ decoder,info,_->val largest=maxOf(info.size.width,info.size.height);if(largest>maxEdge){val scale=maxEdge.toFloat()/largest;decoder.setTargetSize(maxOf(1,(info.size.width*scale).toInt()),maxOf(1,(info.size.height*scale).toInt()))};decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE;decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));decoder.isMutableRequired=false }
  if(s.imagePath.isNotEmpty()||!isSlab(s))return decoded
  // Crop only our bundled demo; selected photos and historical framing remain untouched.
  val (w,h)=demoSize(s);val ratio=w.toFloat()/h;val actual=decoded.width.toFloat()/decoded.height
  val cw=if(actual>ratio)(decoded.height*ratio).toInt().coerceAtLeast(1)else decoded.width
  val ch=if(actual<ratio)(decoded.width/ratio).toInt().coerceAtLeast(1)else decoded.height
  val cropped=Bitmap.createBitmap(decoded,(decoded.width-cw)/2,(decoded.height-ch)/2,cw,ch)
  if(cropped!==decoded)decoded.recycle();return cropped
 }
 fun deleteShareSources(){check(shareSession){"Only a dedicated share session can be removed"};root.deleteRecursively()}
 @Synchronized fun pruneSources(keep:Set<String>){
  root.listFiles()?.filter{it.isFile&&it.name.startsWith("source-")&&it.name.endsWith(".img")&&it.name !in keep}?.forEach{it.delete()}
 }
}
