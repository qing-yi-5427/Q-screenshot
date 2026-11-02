package dev.qingframe.render

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Choreographer
import android.view.Surface
import android.view.TextureView
import com.google.android.filament.*
import com.google.android.filament.gltfio.*
import com.google.android.filament.utils.*
import dev.qingframe.model.*
import dev.qingframe.model.Quaternion
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import java.util.concurrent.Executors
import kotlin.math.*

/** All native calls and callbacks are owned by Android's main thread. */
class RenderController(private val context:Context, private val store:ProjectStore,private val onStatus:(String)->Unit):AutoCloseable {
 companion object { init { Utils.init(); Gltfio.init() } }
 val engine:Engine=Engine.create(Engine.Backend.OPENGL)
 private val renderer=engine.createRenderer()
 private val scene=engine.createScene()
 private val view=engine.createView()
 private val cameraEntity=EntityManager.get().create()
 private val camera=engine.createCamera(cameraEntity)
 private val provider=UbershaderProvider(engine)
 private val loader=AssetLoader(engine,provider,EntityManager.get())
 private val resources=ResourceLoader(engine,true)
 private val handler=Handler(Looper.getMainLooper())
 private val io=Executors.newSingleThreadExecutor()
 private var asset:FilamentAsset?=null
 private val colorMaterials=mutableMapOf<String,MaterialInstance>()
 private var appliedColorKey=""
 private var swapChain:SwapChain?=null
 private var nativeSurface:Surface?=null
 private var textureView:TextureView?=null
 private var width=1;private var height=1
 private val memoryManager=context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
 private val lowMemory=memoryManager.isLowRamDevice||memoryManager.memoryClass<256
 private val maxTextureSize=Texture.getMaxTextureSize(engine,Texture.Sampler.SAMPLER_2D)
 var debugNativeSampling=false
 var debugCustomResolve=false
 var debugAllowSampling=false
 private var closeRequested=false;private var closed=false;private var paused=false;private var frames=0;private var scheduled=false
 private var state=ProjectState();private var applied:ProjectState?=null
 private var screenAspect=1f;private var bodyWidth=.084f;private var bodyHeight=.12f;private var bodyDepth=.0108f
 private var screenTexture:Texture?=null;private var sourceKey="";private var loadGeneration=0;private var loadFailure:String?=null
 private val screenMaterial=loadMaterial("screen")
 private val screenInstance=screenMaterial.createInstance()
 private val backgroundMaterial=loadMaterial("background")
 private val backgroundInstance=backgroundMaterial.createInstance()
 private val shadowMaterial=loadMaterial("shadow")
 private val shadowInstance=shadowMaterial.createInstance()
 private val linearMapper=ToneMapper.Linear()
 private val colorGrading=ColorGrading.Builder().toneMapper(linearMapper).gamutMapping(false).luminanceScaling(false).quality(ColorGrading.QualityLevel.ULTRA).format(ColorGrading.LutFormat.FLOAT).build(engine)
 private val iblBundle=KTX1Loader.createIndirectLight(engine,readAsset("environments/studio_ibl.ktx"))
 private val ibl=iblBundle.indirectLight?:error("摄影棚环境加载失败")
 private val studio=iblBundle.cubemap?:error("摄影棚反射加载失败")
 private val light=EntityManager.get().create()
 private val fillLight=EntityManager.get().create()
 private val background=makePlane(false,backgroundInstance)
 private val ground=makePlane(true,shadowInstance)
 var debugAllocationFailure:Int=0
 private fun failAllocation(stage:Int){if(dev.qingframe.BuildConfig.DEBUG&&debugAllocationFailure==stage){debugAllocationFailure=0;throw IllegalStateException("TEST injected allocation failure at $stage")}}
 var ready:Boolean=false;private set
 var exporting:Boolean=false;private set
 private var pendingState:ProjectState?=null
 private val diagnosticMeshes=mutableListOf<Mesh>()
 private val diagnosticMaterials=mutableListOf<MaterialInstance>()
 private var reportedSoftness=Float.NaN
 private var exportCancel=false
 private var screenshotBuffer:ByteBuffer?=null
 private val frameCallback=Choreographer.FrameCallback { time->
  scheduled=false
  if(!closed&&!paused&&!exporting){
   if(frames>0 && ready){
    swapChain?.let{chain-> if(renderer.beginFrame(chain,time)){renderer.render(view);renderer.endFrame();frames--} }
    if(frames>0) schedule()
   }
  }
 }
 init {
  checkOwner();view.scene=scene;view.camera=camera;configureView(view)
  scene.indirectLight=ibl
  LightManager.Builder(LightManager.Type.DIRECTIONAL).color(1f,.96f,.9f).intensity(65000f).direction(-.5f,-.8f,-1f).castShadows(true).shadowOptions(LightManager.ShadowOptions().apply{mapSize=2048;normalBias=.25f;constantBias=.0002f;shadowFar=2f;shadowBulbRadius=.03f}).build(engine,light)
  LightManager.Builder(LightManager.Type.DIRECTIONAL).color(.7f,.82f,1f).intensity(12000f).direction(.7f,.3f,-1f).castShadows(false).build(engine,fillLight)
  scene.addEntity(light);scene.addEntity(fillLight)
  screenInstance.setParameter("studio",studio,TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,TextureSampler.MagFilter.LINEAR,TextureSampler.WrapMode.CLAMP_TO_EDGE))
  renderer.clearOptions=Renderer.ClearOptions().apply{clear=true;discard=true;setClearColor(doubleArrayOf(0.0,0.0,0.0,0.0))}
  applyState(state)
 }
 private fun configureView(v:com.google.android.filament.View,supersampled:Boolean=false){v.blendMode=com.google.android.filament.View.BlendMode.TRANSLUCENT;v.colorGrading=colorGrading;v.antiAliasing=if(supersampled)com.google.android.filament.View.AntiAliasing.NONE else com.google.android.filament.View.AntiAliasing.FXAA;v.isPostProcessingEnabled=true;v.isShadowingEnabled=true;v.shadowType=com.google.android.filament.View.ShadowType.VSM;v.vsmShadowOptions=com.google.android.filament.View.VsmShadowOptions().apply{mipmapping=true;anisotropy=1;lightBleedReduction=.1f};v.multiSampleAntiAliasingOptions=com.google.android.filament.View.MultiSampleAntiAliasingOptions().apply{enabled=true;sampleCount=4;customResolve=dev.qingframe.BuildConfig.DEBUG&&debugCustomResolve}}
 private fun checkOwner(){check(Looper.myLooper()==Looper.getMainLooper()){ "Filament owner thread violation" }}
 private fun readAsset(path:String)=context.assets.open(path).use{val bytes=it.readBytes();ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes).apply{flip()}}
 private fun loadMaterial(name:String):Material {val data=readAsset("materials/$name.filamat");return Material.Builder().payload(data,data.remaining()).build(engine)}
 fun attach(target:TextureView){checkOwner();textureView=target;target.isOpaque=false;target.surfaceTextureListener=object:TextureView.SurfaceTextureListener {
  override fun onSurfaceTextureAvailable(t:android.graphics.SurfaceTexture,w:Int,h:Int){nativeSurface=Surface(t);swapChain=engine.createSwapChain(nativeSurface!!,SwapChain.CONFIG_TRANSPARENT);resize(w,h);requestFrames()}
  override fun onSurfaceTextureSizeChanged(t:android.graphics.SurfaceTexture,w:Int,h:Int){resize(w,h)}
  override fun onSurfaceTextureDestroyed(t:android.graphics.SurfaceTexture):Boolean {swapChain?.let{engine.destroySwapChain(it)};swapChain=null;engine.flushAndWait();nativeSurface?.release();nativeSurface=null;return true}
  override fun onSurfaceTextureUpdated(t:android.graphics.SurfaceTexture){}
 };if(target.isAvailable){target.surfaceTexture?.let{target.surfaceTextureListener?.onSurfaceTextureAvailable(it,target.width,target.height)}} }
 private fun resolution(w:Int,h:Int,preview:Boolean):RenderResolution {
  val pressure=android.app.ActivityManager.MemoryInfo().also{memoryManager.getMemoryInfo(it)}.lowMemory
  return renderResolution(w,h,preview,((lowMemory||pressure)&&!(dev.qingframe.BuildConfig.DEBUG&&debugAllowSampling))||(dev.qingframe.BuildConfig.DEBUG&&debugNativeSampling),maxTextureSize)
 }
 private fun resize(w:Int,h:Int){
  val plan=resolution(max(1,w),max(1,h),true);width=plan.width;height=plan.height
  textureView?.surfaceTexture?.setDefaultBufferSize(width,height)
  configureView(view,plan.exceeds(w,h));view.viewport=Viewport(0,0,width,height)
  if(dev.qingframe.BuildConfig.DEBUG)Log.i("QF_RENDER","AA preview ${w}x$h -> ${width}x$height msaa=4 fxaa=${!plan.exceeds(w,h)} lowMemory=$lowMemory")
  updateScene(state,width,height);requestFrames()
 }
 fun applyState(new:ProjectState){checkOwner();if(closed)return;if(exporting){pendingState=new;return};state=new.safe();try {
  val modelChanged=applied?.deviceId!=state.deviceId || applied?.stateId!=state.stateId || (applied?.colorwayId=="denim")!=(state.colorwayId=="denim")
  if(modelChanged)loadModel(state)
  updateScene(state,width,height)
  val key="${if(state.imagePath.isEmpty())"demo:${state.deviceId}"else state.imagePath}:${state.imageWidth}:${state.imageHeight}"
  if(sourceKey!=key || screenTexture==null){sourceKey=key;loadImage(state,2048)}
  applied=state;requestFrames()
 }catch(e:Exception){onStatus("模型加载失败：${e.message}");Log.e("QF_RENDER","apply",e)} }
 private fun loadModel(s:ProjectState){
  ready=false;colorMaterials.clear();appliedColorKey="";asset?.let{scene.removeEntities(it.entities);loader.destroyAsset(it)};asset=null
  val manifest=JSONObject(context.assets.open("devices/${s.deviceId}/device.json").bufferedReader().readText()).getJSONObject("states").getJSONObject(s.stateId)
  val size=manifest.getJSONArray("bodySizeMeters");bodyWidth=size.getDouble(0).toFloat();bodyHeight=size.getDouble(1).toFloat();bodyDepth=ColorwayCatalog.depth(s,size.getDouble(2).toFloat())
  val screenSize=manifest.getJSONArray("screenSizeMeters");screenAspect=(screenSize.getDouble(0)/screenSize.getDouble(1)).toFloat()
  val assetPath=manifest.optJSONObject("colorwayAssets")?.optString(s.colorwayId)?.takeIf{it.isNotBlank()}?:manifest.getString("asset")
  val loaded=loader.createAsset(readAsset(assetPath))?:error("GLB 无法解析")
  resources.loadResources(loaded)
  val expected=manifest.getString("activeScreenNode");val entity=loaded.entities.singleOrNull{loaded.getName(it)==expected}?:run{loader.destroyAsset(loaded);error("屏幕节点合同不匹配")}
  val instance=engine.renderableManager.getInstance(entity);require(instance!=0){"屏幕不是可绘制实体"};require(engine.renderableManager.getPrimitiveCount(instance)==1){"屏幕材质槽不唯一"}
  engine.renderableManager.setMaterialInstanceAt(instance,0,screenInstance)
  val regions=ColorwayCatalog.find(s.deviceId,s.colorwayId).materials.keys
  for(item in loaded.renderableEntities){val ri=engine.renderableManager.getInstance(item);for(slot in 0 until engine.renderableManager.getPrimitiveCount(ri)){
   val mi=engine.renderableManager.getMaterialInstanceAt(ri,slot)
   if(mi.name in regions)colorMaterials[mi.name]=mi
  }}
  require(colorMaterials.keys.containsAll(regions)){"机身材质分区不匹配：${regions-colorMaterials.keys}"}
  asset=loaded;scene.addEntities(loaded.entities);loaded.releaseSourceData();ready=screenTexture!=null
  Log.i("QF_RENDER","GLB loaded ${s.deviceId}/${s.stateId} entities=${loaded.entities.size}")
 }
 private fun loadImage(s:ProjectState,maxEdge:Int,onReady:(()->Unit)?=null){ready=false;loadFailure=null;val generation=++loadGeneration;io.execute{try{val bitmap=store.decode(s,maxEdge);handler.post{if(closed||generation!=loadGeneration){bitmap.recycle();return@post};try{uploadImage(bitmap);ready=asset!=null;onStatus("");requestFrames();onReady?.invoke()}catch(e:Exception){loadFailure="截图上传失败：${e.message}";onStatus(loadFailure!!)}}}catch(e:Exception){handler.post{if(generation==loadGeneration&&!closed){loadFailure="截图读取失败：${e.message}";onStatus(loadFailure!!)}}}}}
 private fun uploadImage(bitmap:Bitmap){
  Log.i("QF_RENDER","upload image ${bitmap.width}x${bitmap.height}")
  val buffer=ByteBuffer.allocateDirect(bitmap.byteCount).order(ByteOrder.nativeOrder());bitmap.copyPixelsToBuffer(buffer);buffer.flip();val old=screenTexture
  val tex=Texture.Builder().width(bitmap.width).height(bitmap.height).levels(0xff).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.SRGB8_A8).usage(Texture.Usage.DEFAULT or Texture.Usage.GEN_MIPMAPPABLE).build(engine)
  tex.setImage(engine,0,Texture.PixelBufferDescriptor(buffer,Texture.Format.RGBA,Texture.Type.UBYTE,1,0,0,0,handler,Runnable{bitmap.recycle()}));tex.generateMipmaps(engine)
  screenTexture=tex;screenInstance.setParameter("screenshot",tex,TextureSampler(TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,TextureSampler.MagFilter.LINEAR,TextureSampler.WrapMode.CLAMP_TO_EDGE).apply{anisotropy=8f})
  old?.let{engine.destroyTexture(it)}
 }
 private fun applyColorway(s:ProjectState){
  val key="${s.deviceId}/${s.stateId}/${s.colorwayId}";if(appliedColorKey==key)return
  val palette=ColorwayCatalog.find(s.deviceId,s.colorwayId)
  for((name,pbr) in palette.materials){
   val mi=colorMaterials[name]?:error("机身材质不存在：$name");val rgb=pbr.linear()
   mi.setParameter("baseColorFactor",rgb[0],rgb[1],rgb[2],1f)
   mi.setParameter("metallicFactor",pbr.metallic);mi.setParameter("roughnessFactor",pbr.roughness)
  }
  appliedColorKey=key
  if(dev.qingframe.BuildConfig.DEBUG)Log.i("QF_RENDER","COLORWAY $key regions=${colorMaterials.keys} bodyDepth=$bodyDepth")
 }
 private fun updateScene(requested:ProjectState,w:Int,h:Int){
  val s=requested.safe();if(asset!=null)applyColorway(s)
  asset?.let{a->val matrix=(s.pose * if(s.portrait)Quaternion.axis(0f,0f,1f,90f) else Quaternion()).matrix();engine.transformManager.setTransform(engine.transformManager.getInstance(a.root),matrix)}
  val aspect=w.toFloat()/h;val bw=if(s.portrait)bodyHeight else bodyWidth;val bh=if(s.portrait)bodyWidth else bodyHeight
  val transform=(s.pose * if(s.portrait)Quaternion.axis(0f,0f,1f,90f)else Quaternion()).matrix()
  val fit=fitCamera(bodyWidth,bodyHeight,bodyDepth+.006f,transform,aspect,s.fov,s.orthographic)
  val distance=fit.distance/s.zoom
  if(s.orthographic){val extent=fit.halfHeight/s.zoom;camera.setProjection(Camera.Projection.ORTHO,-extent*aspect,extent*aspect,-extent,extent,.001,3.0)}else camera.setProjection(s.fov.toDouble(),aspect.toDouble(),.001,3.0,Camera.Fov.VERTICAL)
  camera.lookAt(-s.offsetX*bw.toDouble(),s.offsetY*bh.toDouble()+(if(s.orthographic)0.0 else distance*.18),max(distance,.035),-s.offsetX*bw.toDouble(),s.offsetY*bh.toDouble(),0.0,0.0,1.0,0.0)
  val mapping=screenMapping(s.imageWidth,s.imageHeight,if(s.portrait)1/screenAspect else screenAspect,s.fitMode=="fill",s.cropX,s.cropY)
  screenInstance.setParameter("mapping",mapping.scaleX,mapping.scaleY,mapping.offsetX,mapping.offsetY);screenInstance.setParameter("portrait",if(s.portrait)1f else 0f);screenInstance.setParameter("reflection",s.reflection);screenInstance.setParameter("environmentRotation",s.environmentRotation*PI.toFloat()/180)
  val r=s.lightDirection*PI.toFloat()/180;val lm=engine.lightManager;val li=lm.getInstance(light)
  lm.setDirection(li,sin(r)*.6f,-1f,-cos(r)*.4f);val multiplier=when(s.lighting){"cool"->1.25f;"rim"->1.6f;else->1f};lm.setIntensity(li,32000f*s.lightIntensity*multiplier)
  lm.setShadowOptions(li,LightManager.ShadowOptions().apply{mapSize=2048;normalBias=.25f;constantBias=.0002f;shadowFar=2f;shadowBulbRadius=.003f+.04f*s.softness;vsm=LightManager.ShadowOptions.Vsm(false,4f+24f*s.softness)});lm.setColor(li,if(s.lighting=="cool").82f else 1f,if(s.lighting=="rim").88f else .96f,1f)
  lm.setIntensity(lm.getInstance(fillLight),if(s.lighting=="rim")8000f else 6000f*s.lightIntensity)
  if(dev.qingframe.BuildConfig.DEBUG&&reportedSoftness!=s.softness){reportedSoftness=s.softness;Log.i("QF_RENDER","shadow native blurWidth=${lm.getShadowOptions(li).vsm.blurWidth}")}
  ibl.intensity=(if(s.lighting=="rim")18000f else 16000f)*s.lightIntensity
  val er=s.environmentRotation*PI.toFloat()/180;ibl.setRotation(floatArrayOf(cos(er),0f,-sin(er),0f,1f,0f,sin(er),0f,cos(er)))
  shadowInstance.setParameter("strength",s.shadow)
  val poseMatrix=(s.pose * if(s.portrait)Quaternion.axis(0f,0f,1f,90f) else Quaternion()).matrix();val minY=-(abs(poseMatrix[1])*bodyWidth+abs(poseMatrix[5])*bodyHeight+abs(poseMatrix[9])*bodyDepth)/2
  val groundMatrix=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,minY-(if(s.grounded).001f else .003f),0f,1f)
  engine.transformManager.setTransform(engine.transformManager.getInstance(ground.entity),groundMatrix)
  if(s.shadow>0)scene.addEntity(ground.entity)else scene.remove(ground.entity)
  if(s.background=="transparent")scene.remove(background.entity)else{scene.addEntity(background.entity);val a=linearColor(s.colorA);val b=if(s.background=="gradient")linearColor(s.colorB)else a;backgroundInstance.setParameter("colorA",a[0],a[1],a[2]);backgroundInstance.setParameter("colorB",b[0],b[1],b[2]);backgroundInstance.setParameter("gradient",if(s.background=="gradient")1f else 0f)}
 }
 private fun linearColor(c:Int)=floatArrayOf((c shr 16 and 255)/255f,(c shr 8 and 255)/255f,(c and 255)/255f).map{if(it<=.04045f)it/12.92f else ((it+.055f)/1.055f).pow(2.4f)}.toFloatArray()
 private data class Mesh(val entity:Int,val vb:VertexBuffer,val ib:IndexBuffer)
 private fun makePlane(horizontal:Boolean,material:MaterialInstance):Mesh {
  val coords=if(horizontal)floatArrayOf(-.35f,0f,-.35f,.35f,0f,-.35f,.35f,0f,.35f,-.35f,0f,.35f) else floatArrayOf(-4f,-4f,-.7f,4f,-4f,-.7f,4f,4f,-.7f,-4f,4f,-.7f)
  val data=ByteBuffer.allocateDirect(coords.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(coords).apply{flip()}
  val vb=VertexBuffer.Builder().vertexCount(4).bufferCount(2).attribute(VertexBuffer.VertexAttribute.POSITION,0,VertexBuffer.AttributeType.FLOAT3,0,12).attribute(VertexBuffer.VertexAttribute.TANGENTS,1,VertexBuffer.AttributeType.FLOAT4,0,16).build(engine);vb.setBufferAt(engine,0,data)
  val tangent=if(horizontal)floatArrayOf(-.70710677f,0f,0f,.70710677f)else floatArrayOf(0f,0f,0f,1f)
  val tangentData=ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer();repeat(4){tangentData.put(tangent)};tangentData.flip();vb.setBufferAt(engine,1,tangentData)
  val indices=ByteBuffer.allocateDirect(12).order(ByteOrder.nativeOrder()).asShortBuffer().put(if(horizontal)shortArrayOf(0,2,1,0,3,2)else shortArrayOf(0,1,2,0,2,3)).apply{flip()}
  val ib=IndexBuffer.Builder().indexCount(6).bufferType(IndexBuffer.Builder.IndexType.USHORT).build(engine);ib.setBuffer(engine,indices)
  val entity=EntityManager.get().create();RenderableManager.Builder(1).boundingBox(if(horizontal)Box(0f,0f,0f,.35f,.001f,.35f)else Box(0f,0f,-.7f,4f,4f,.001f)).geometry(0,RenderableManager.PrimitiveType.TRIANGLES,vb,ib).material(0,material).culling(false).castShadows(false).receiveShadows(horizontal).priority(if(horizontal)6 else 7).build(engine,entity)
  engine.transformManager.create(entity);return Mesh(entity,vb,ib)
 }
 fun addAxisDiagnostics(){
  for((y,color) in listOf(.068f to floatArrayOf(1f,0f,0f),-.068f to floatArrayOf(0f,0f,1f))){
   val material=backgroundMaterial.createInstance();material.setParameter("colorA",color[0],color[1],color[2]);material.setParameter("colorB",color[0],color[1],color[2]);material.setParameter("gradient",0f)
   val mesh=makePlane(false,material);engine.renderableManager.setPriority(engine.renderableManager.getInstance(background.entity),0);val scale=.0015f
   engine.transformManager.setTransform(engine.transformManager.getInstance(mesh.entity),floatArrayOf(scale,0f,0f,0f,0f,scale,0f,0f,0f,0f,scale,0f,-.045f,y,.02f,1f))
   scene.addEntity(mesh.entity);diagnosticMeshes+=mesh;diagnosticMaterials+=material
  }
 }
 fun requestFrames(){frames=max(frames,4);schedule()}
 private fun schedule(){if(!scheduled&&!paused&&!closed){scheduled=true;Choreographer.getInstance().postFrameCallback(frameCallback)}}
 fun pause(){paused=true;Choreographer.getInstance().removeFrameCallback(frameCallback);scheduled=false}
 fun resume(){paused=false;requestFrames()}
 fun cancelExport(){exportCancel=true}
 fun export(snapshot:ProjectState,w:Int,h:Int,done:(Result<Bitmap>)->Unit){
  checkOwner();Log.i("QF_EXPORT","start ${w}x${h} ready=$ready");if(exporting){done(Result.failure(IllegalStateException("已有导出任务")));return};if(!ready){done(Result.failure(IllegalStateException(loadFailure?:"模型与截图仍在加载")));return};if(!validateOutput(w,h)){done(Result.failure(IllegalArgumentException("导出范围为 256–3840 像素")));return}
  if(w>maxTextureSize||h>maxTextureSize){done(Result.failure(IllegalArgumentException("此设备支持的导出边长上限为 $maxTextureSize 像素")));return}
  val front=snapshot.safe();if(dev.qingframe.BuildConfig.DEBUG){val e=front.pose.euler();Log.i("QF_EXPORT","front yaw=${e[0]} pitch=${e[1]} roll=${e[2]} facing=${FrontRotation.facing(front)}")};exporting=true;exportCancel=false;state=front;applyExport(front,w,h,done)
 }
 private fun applyExport(snapshot:ProjectState,w:Int,h:Int,done:(Result<Bitmap>)->Unit){
  // Full source decode is separate from preview, then uploaded on the owner thread.
  io.execute{try{val bitmap=store.decode(snapshot,4096);handler.post{if(closed){bitmap.recycle();return@post};try{uploadImage(bitmap);performReadback(snapshot,w,h,done)}catch(e:Throwable){finishExport();done(Result.failure(e))}}}catch(e:Throwable){handler.post{finishExport();done(Result.failure(e))}}}
 }
 private class ExportResources {
  var color:Texture?=null;var depth:Texture?=null;var target:RenderTarget?=null;var view:com.google.android.filament.View?=null
  private var released=false
  fun close(engine:Engine){
   if(released)return;released=true
   view?.let{it.renderTarget=null;engine.destroyView(it)}
   target?.let{engine.destroyRenderTarget(it)}
   color?.let{engine.destroyTexture(it)};depth?.let{engine.destroyTexture(it)}
  }
 }
 private fun performReadback(snapshot:ProjectState,w:Int,h:Int,done:(Result<Bitmap>)->Unit){
  Log.i("QF_EXPORT","performReadback ${w}x${h}")
  val start=System.nanoTime();val allocations=ExportResources()
  val plan=resolution(w,h,false);val rw=plan.width;val rh=plan.height
  Log.i("QF_EXPORT","AA target ${w}x$h -> ${rw}x$rh msaa=4 fxaa=${!plan.exceeds(w,h)} custom=$debugCustomResolve")
  val cleanup={allocations.close(engine);screenshotBuffer=null;finishExport()}
  try {
   if(exportCancel){cleanup();done(Result.failure(IllegalStateException("导出已取消")));return}
   val color=Texture.Builder().width(rw).height(rh).levels(1).sampler(Texture.Sampler.SAMPLER_2D).format(Texture.InternalFormat.RGBA8).usage(Texture.Usage.COLOR_ATTACHMENT or Texture.Usage.BLIT_SRC or Texture.Usage.SAMPLEABLE).build(engine).also{allocations.color=it;failAllocation(1)}
   val depth=Texture.Builder().width(rw).height(rh).levels(1).format(Texture.InternalFormat.DEPTH24).usage(Texture.Usage.DEPTH_ATTACHMENT).build(engine).also{allocations.depth=it;failAllocation(2)}
   val target=RenderTarget.Builder().texture(RenderTarget.AttachmentPoint.COLOR0,color).texture(RenderTarget.AttachmentPoint.DEPTH,depth).build(engine).also{allocations.target=it;failAllocation(3)}
   val exportView=engine.createView().also{allocations.view=it;failAllocation(4)}
   exportView.scene=scene;exportView.camera=camera;configureView(exportView,plan.exceeds(w,h))
   // Temporal display dithering can add 1/255 alpha to an empty RGBA canvas on
   // Adreno. A still PNG needs exact transparent pixels and stable color values.
   exportView.dithering=com.google.android.filament.View.Dithering.NONE
   exportView.viewport=Viewport(0,0,rw,rh);exportView.renderTarget=target
   updateScene(snapshot,rw,rh)
   val buffer=ByteBuffer.allocateDirect(rw*rh*4).order(ByteOrder.nativeOrder());screenshotBuffer=buffer
   repeat(2){renderer.renderStandaloneView(exportView);Log.i("QF_EXPORT","standalone frame $it submitted")}
   renderer.readPixels(target,0,0,rw,rh,Texture.PixelBufferDescriptor(buffer,Texture.Format.RGBA,Texture.Type.UBYTE,1,0,0,0,handler,Runnable {
    if(closed)return@Runnable
    val cancelled=exportCancel;cleanup()
    if(cancelled){done(Result.failure(IllegalStateException("导出已取消")));return@Runnable}
    io.execute{try{
     buffer.rewind();val pixels=IntArray(rw*rh)
     // Target readback is top-left RGBA, verified against preview / world-axis markers.
     for(y in 0 until rh)for(x in 0 until rw){val r=buffer.get().toInt()and 255;val g=buffer.get().toInt()and 255;val b=buffer.get().toInt()and 255;val a=buffer.get().toInt()and 255;pixels[y*rw+x]=unpremultiplyArgb(r,g,b,a)}
     val sampled=Bitmap.createBitmap(rw,rh,Bitmap.Config.ARGB_8888)
     val result=try{
      sampled.setPixels(pixels,0,rw,0,0,rw,rh)
      // Android filters its premultiplied Bitmap, so transparent edges never acquire a matte.
      if(plan.exceeds(w,h))Bitmap.createScaledBitmap(sampled,w,h,true)else sampled
     }catch(e:Throwable){sampled.recycle();throw e}
     if(result!==sampled)sampled.recycle()
     Log.i("QF_EXPORT","readback ${w}x${h} elapsedMs=${(System.nanoTime()-start)/1_000_000}")
     handler.post{if(exportCancel||closed){result.recycle();done(Result.failure(IllegalStateException("导出已取消")))}else done(Result.success(result))}
    }catch(e:Throwable){handler.post{done(Result.failure(e))}}}
   }))
   Log.i("QF_EXPORT","readPixels submitted");engine.flush();Log.i("QF_EXPORT","async flush submitted")
   // FEngine::flush does not purge deferred readback callbacks. A zero-timeout
   // flushAndWait pumps that owner-thread queue without waiting for GPU completion.
   val pump=object:Runnable {
    override fun run(){
     if(closed||!exporting)return
     try{engine.flushAndWait(0)}catch(e:Throwable){cleanup();done(Result.failure(e));return}
     if(!closed&&exporting)handler.postDelayed(this,50)
    }
   }
   handler.post(pump)
  }catch(e:Throwable){cleanup();done(Result.failure(e))}
 }
 private fun finishExport(){exporting=false;if(closeRequested){close();return};val next=pendingState?:applied?:state;pendingState=null;state=next;applyState(next);requestFrames()}
 override fun close(){checkOwner();if(closed)return;if(exporting){closeRequested=true;exportCancel=true;return};closed=true;loadGeneration++;pause();textureView?.surfaceTextureListener=null;swapChain?.let{engine.destroySwapChain(it)};engine.flushAndWait();nativeSurface?.release()
  asset?.let{scene.removeEntities(it.entities);loader.destroyAsset(it)};asset=null;resources.destroy();loader.destroy();provider.destroyMaterials();provider.destroy()
  for(mesh in listOf(background,ground)+diagnosticMeshes){scene.remove(mesh.entity);engine.destroyEntity(mesh.entity);engine.destroyVertexBuffer(mesh.vb);engine.destroyIndexBuffer(mesh.ib);EntityManager.get().destroy(mesh.entity)}
  for(e in listOf(light,fillLight)){engine.destroyEntity(e);EntityManager.get().destroy(e)}
  diagnosticMaterials.forEach{engine.destroyMaterialInstance(it)};screenTexture?.let{engine.destroyTexture(it)};engine.destroyMaterialInstance(screenInstance);engine.destroyMaterialInstance(backgroundInstance);engine.destroyMaterialInstance(shadowInstance);engine.destroyMaterial(screenMaterial);engine.destroyMaterial(backgroundMaterial);engine.destroyMaterial(shadowMaterial)
  scene.indirectLight=null;engine.destroyIndirectLight(ibl);engine.destroyTexture(studio);view.colorGrading=null;engine.destroyColorGrading(colorGrading);engine.destroyView(view);engine.destroyRenderer(renderer);engine.destroyScene(scene);engine.destroyCameraComponent(cameraEntity);EntityManager.get().destroy(cameraEntity);engine.destroy();io.shutdown()
 }
}
