package dev.qingframe

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import dev.qingframe.model.*
import dev.qingframe.render.RenderController
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

class MainActivity:ComponentActivity() {
 private fun isolatedSession():String? = if(BuildConfig.DEBUG)intent.getStringExtra("test_session")?.takeIf{it.matches(Regex("[A-Za-z0-9-]{1,48}"))}else null
 private val vm:EditorViewModel by viewModels {
  object:androidx.lifecycle.ViewModelProvider.Factory {
   @Suppress("UNCHECKED_CAST") override fun <T:androidx.lifecycle.ViewModel> create(modelClass:Class<T>):T = EditorViewModel(application,isolatedSession()) as T
  }
 }
 private var controller:RenderController?=null
 private var testScheduled=false
 private var testCase:String?=null
 private var initialTestState:ProjectState?=null
 private val mainHandler=Handler(Looper.getMainLooper())
 private var exportBusy by mutableStateOf(false)
 private var exportStatus by mutableStateOf("")
 private val cancelRequested=java.util.concurrent.atomic.AtomicBoolean(false)
 private var lastShare by mutableStateOf<Uri?>(null)
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
  androidx.core.view.WindowCompat.getInsetsController(window,window.decorView).apply{isAppearanceLightStatusBars=true;isAppearanceLightNavigationBars=true}
  if(BuildConfig.DEBUG){testCase=intent.getStringExtra("test_case");testCase?.let{initialTestState=makeTestState(it).let{s->s.copy(colorwayId=intent.getStringExtra("test_colorway")?:s.colorwayId,exportLongEdge=intent.getIntExtra("test_edge",s.exportLongEdge),environmentRotation=intent.getFloatExtra("test_environment",s.environmentRotation),pose=if(intent.hasExtra("test_yaw")||intent.hasExtra("test_pitch")||intent.hasExtra("test_roll"))Quaternion.euler(intent.getFloatExtra("test_yaw",0f),intent.getFloatExtra("test_pitch",0f),intent.getFloatExtra("test_roll",0f))else s.pose)};vm.update(false){initialTestState!!}}}
  setContent{QingTheme{Editor()}}
  if(BuildConfig.DEBUG&&isolatedSession()!=null&&intent.getBooleanExtra("test_import_fixture",false)){
   val model=intent.getStringExtra("test_import_model")?.takeIf{id->DeviceCatalog.choices.any{it.id==id}}?:"redmi-k90-pro-max"
   val source=if(model=="xiaomi-18-fold")"outer"else"redmi"
   vm.update(false){it.copy(deviceId=model,stateId=DeviceCatalog.find(model).defaultState,portrait=false)}
   lifecycleScope.launch{
    val file=withContext(Dispatchers.IO){File(cacheDir,"exports/isolated-fixture-$source.png").apply{parentFile!!.mkdirs();assets.open("fixtures/$source.png").use{input->outputStream().use{input.copyTo(it)}}}}
    vm.import(FileProvider.getUriForFile(this@MainActivity,"${packageName}.files",file))
   }
  }
 }
 override fun onPause(){controller?.pause();vm.saveNow();super.onPause()}
 override fun onResume(){super.onResume();controller?.resume()}
 override fun onDestroy(){controller?.close();controller=null;super.onDestroy()}
 private fun makeTestState(case:String):ProjectState {
  if(case.startsWith("R"))return makeRedmiTestState(case)
  if(case.startsWith("X"))return makeXiaomiTestState(case)
  val inner=case.startsWith("E03")||case.startsWith("E04");val portrait=case.startsWith("E04");val fixture=if(portrait)"inner-portrait" else if(inner)"inner-landscape"else"outer"
  val relative="fixture-$fixture.png";val f=vm.store.imageFile(relative);if(!f.exists())assets.open("fixtures/$fixture.png").use{input->f.outputStream().use{input.copyTo(it)}}
  var s=ProjectState(imagePath=relative,imageWidth=if(inner&&!portrait)2364 else if(portrait)1672 else 1168,imageHeight=if(inner&&!portrait)1672 else if(portrait)2364 else 1712,stateId=if(inner)"unfolded"else"folded",portrait=portrait,pose=Quaternion(),reflection=0f,shadow=0f,background="solid",colorA=0xffe7ebe8.toInt(),canvasWidth=4,canvasHeight=5,exportLongEdge=2048,orthographic=true)
  when {
   case.startsWith("E02")->s=s.copy(pose=Quaternion.euler(if(case.contains("right"))32f else -32f,14f,-7f),reflection=.35f,shadow=.55f,orthographic=false)
   case.startsWith("E03")->s=s.copy(canvasWidth=5,canvasHeight=4,pose=Quaternion.euler(-18f,12f,-5f),orthographic=false,reflection=.12f,shadow=.5f)
   case.startsWith("E04")->s=s.copy(pose=Quaternion.euler(-15f,10f,-4f),orthographic=false,shadow=.5f)
   case.startsWith("E05")->s=s.copy(imageWidth=1168,imageHeight=1712,stateId="unfolded",canvasWidth=5,canvasHeight=4,fitMode=if(case.contains("fill"))"fill"else"fit",cropX=.5f)
   case.startsWith("E06")->s=s.copy(background="transparent",pose=Quaternion.euler(-25f,12f,-5f),orthographic=false)
   case.startsWith("E07")->s=s.copy(background="transparent",pose=Quaternion.euler(-25f,12f,-5f),shadow=.7f,orthographic=false)
   case.startsWith("E08")->s=s.copy(canvasWidth=if(case.contains("landscape"))16 else 9,canvasHeight=if(case.contains("landscape"))9 else 16,exportLongEdge=3840,pose=Quaternion.euler(-20f,10f,-6f),orthographic=false,shadow=.5f)
   case.startsWith("E09")->s=s.copy(pose=Quaternion.euler(-45f,20f,-6f),reflection=if(case.contains("strong"))1f else if(case.contains("zero"))0f else .12f,orthographic=false)
   case.startsWith("E10")->s=s.copy(background=if(case.contains("solid"))"solid"else"gradient",colorA=0xff26303c.toInt(),colorB=0xff11171f.toInt(),lighting="rim",pose=Quaternion.euler(-22f,12f,-5f),reflection=.3f,shadow=.55f,orthographic=false)
   case.startsWith("E11")->s=s.copy(pose=Quaternion.euler(12f,8f,0f),orthographic=false)
   case.startsWith("E12")->s=s.copy(pose=Quaternion.euler(-22f,12f,-6f),orthographic=false)
  }
  if(case.startsWith("D")) {
   s=ProjectState(imagePath="",imageWidth=1168,imageHeight=1712,pose=Quaternion.euler(if(case.contains("right"))30f else -28f,14f,-7f),reflection=.25f,shadow=.6f,background="gradient",exportLongEdge=2560)
   if(case.contains("open"))s=s.copy(stateId="unfolded",canvasWidth=5,canvasHeight=4)
   if(case.contains("back"))s=s.copy(pose=Quaternion.euler(155f,12f,-7f))
   if(case.contains("cool"))s=s.copy(lighting="cool",colorA=0xffc9d3df.toInt(),colorB=0xff8c9eac.toInt())
  }
  return s
 }
 private fun makeXiaomiTestState(case:String):ProjectState {
  val opened=case.startsWith("X03")||case.startsWith("X04")||case.startsWith("X08")||case.contains("open")
  val portrait=case.startsWith("X04")
  val fixture=if(portrait)"inner-portrait"else if(opened)"inner-landscape"else"outer"
  val path="fixture-$fixture.png";val file=vm.store.imageFile(path)
  if(!file.exists())assets.open("fixtures/$fixture.png").use{input->file.outputStream().use{input.copyTo(it)}}
  var s=ProjectState(deviceId="xiaomi-18-fold",stateId=if(opened)"unfolded"else"folded",portrait=portrait,imagePath=path,imageWidth=if(portrait)1672 else if(opened)2364 else 1168,imageHeight=if(portrait)2364 else if(opened)1672 else 1712,pose=Quaternion(),reflection=0f,shadow=0f,environmentRotation=90f,background="solid",colorA=0xfffafbf8.toInt(),canvasWidth=if(opened&&!portrait)5 else 4,canvasHeight=if(opened&&!portrait)4 else 5,exportLongEdge=2048,orthographic=true)
  when {
   case.startsWith("X02")->s=s.copy(pose=Quaternion.euler(if(case.contains("right"))32f else -28f,12f,-6f),reflection=.08f,shadow=.16f,softness=.85f,orthographic=false)
   case.startsWith("X05")->s=s.copy(pose=Quaternion.euler(155f,12f,-6f),reflection=.08f,shadow=.16f,softness=.85f,orthographic=false)
   case.startsWith("X06")||case.startsWith("X07")->s=s.copy(background="transparent",shadow=if(case.startsWith("X07")) .2f else 0f,softness=.85f,pose=Quaternion.euler(-28f,12f,-6f),orthographic=false)
   case.startsWith("X08")->s=s.copy(canvasWidth=if(case.contains("landscape"))16 else 9,canvasHeight=if(case.contains("landscape"))9 else 16,exportLongEdge=3840,pose=Quaternion.euler(-18f,12f,-5f),reflection=.08f,shadow=.16f,softness=.85f,orthographic=false)
   case.startsWith("X09")->s=s.copy(imagePath=if(opened)path else"",pose=Quaternion.euler(if(case.contains("back"))155f else -28f,12f,-6f),reflection=.08f,shadow=.16f,softness=.85f,orthographic=false,exportLongEdge=2560).let{vm.store.automaticCanvas(it)}
  }
  return s
 }
 private fun makeRedmiTestState(case:String):ProjectState {
  val path="fixture-redmi.png";val file=vm.store.imageFile(path)
  if(!file.exists())assets.open("fixtures/redmi.png").use{input->file.outputStream().use{input.copyTo(it)}}
  var s=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",portrait=false,imagePath=path,imageWidth=1200,imageHeight=2608,pose=Quaternion(),reflection=0f,shadow=0f,background="solid",colorA=0xffe7ebe8.toInt(),canvasWidth=4,canvasHeight=5,exportLongEdge=2048,orthographic=true)
  when {
   case.startsWith("R02")->s=s.copy(pose=Quaternion.euler(if(case.contains("right"))32f else -32f,12f,-5f),orthographic=false,reflection=.25f,shadow=.55f)
   case.startsWith("R03")->s=s.copy(pose=Quaternion.euler(155f,12f,-5f),orthographic=false,reflection=.2f,shadow=.55f)
   case.startsWith("R04")->s=s.copy(background="transparent",pose=Quaternion.euler(-25f,12f,-5f),orthographic=false)
   case.startsWith("R05")->s=s.copy(background="transparent",pose=Quaternion.euler(-25f,12f,-5f),orthographic=false,shadow=.65f)
   case.startsWith("R06")->s=s.copy(canvasWidth=if(case.contains("landscape"))16 else 9,canvasHeight=if(case.contains("landscape"))9 else 16,exportLongEdge=3840,pose=Quaternion.euler(-22f,12f,-5f),orthographic=false,reflection=.12f,shadow=.55f)
   case.startsWith("R07")->s=s.copy(pose=Quaternion.euler(-45f,15f,-5f),orthographic=false,reflection=if(case.contains("strong"))1f else if(case.contains("zero"))0f else .12f)
   case.startsWith("R08")->s=s.copy(pose=Quaternion.euler(12f,8f,0f),orthographic=false)
   case.startsWith("R09")->s=s.copy(pose=Quaternion.euler(-22f,12f,-6f),orthographic=false)
   case.startsWith("R12")->s=s.copy(imagePath="",pose=Quaternion.euler(-28f,12f,-6f),environmentRotation=90f,reflection=.08f,shadow=.16f,softness=.85f,orthographic=false,background="solid",colorA=0xfffafbf8.toInt(),exportLongEdge=2560)
  }
  return s
 }
 private fun scheduleTest(){
  if(testCase==null||testScheduled)return
  testScheduled=true
  val attempt=object:Runnable {
   var retries=0
   override fun run(){
    val c=controller
    if(c?.ready!=true){if(retries++<60)mainHandler.postDelayed(this,250)else Log.e("QF_EXPORT","FAILED $testCase renderer not ready");return}
    var s=initialTestState!!
    if(testCase=="E11"||testCase?.startsWith("R08")==true){
     val path="fixture-inner-portrait.png";val file=vm.store.imageFile(path)
     if(!file.exists())assets.open("fixtures/inner-portrait.png").use{input->file.outputStream().use{input.copyTo(it)}}
     val original=s
     repeat(30){i->s=if(i%2==0)original else original.copy(imagePath=path,imageWidth=1672,imageHeight=2364);c.applyState(s)}
     vm.update(false){s};Log.i("QF_TEST","REPLACEMENTS=30 final=1672x2364")
    }
    if(testCase=="S30"||testCase?.startsWith("R11")==true){
     repeat(30){i->s=if(testCase?.startsWith("R11")==true)s.copy(deviceId=if(i%2==0)"xiaomi-18-fold"else"redmi-k90-pro-max",stateId=if(i%2==0)"folded"else"portrait")else s.copy(stateId=if(i%2==0)"folded"else"unfolded");c.applyState(s)}
     s=s.copy(canvasWidth=5,canvasHeight=4);vm.update(false){s};Log.i("QF_TEST","MODEL_SWITCHES=30")
    }
    val (w,h)=s.outputSize();c.applyState(s)
    var readyRetries=0
    fun exportOne(index:Int){
     if(!c.ready){
      if(readyRetries++<120)mainHandler.postDelayed({exportOne(index)},100)
      else Log.e("QF_EXPORT","FAILED $testCase latest screenshot readiness timed out")
      return
     }
     readyRetries=0

     val sequence=testCase=="S10"||testCase=="R10"
     val outputName=if(sequence)"${testCase}-%02d".format(index)else testCase!!
     c.export(s,w,h){result->result.fold(onSuccess={bitmap->lifecycleScope.launch(Dispatchers.IO){
      val out=File(filesDir,"exports/${isolatedSession()?.let{it+"/"}?:""}$outputName.png");out.parentFile!!.mkdirs()
      out.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
      Log.i("QF_EXPORT","COMPLETE $outputName ${w}x$h path=files/exports/${isolatedSession()?.let{it+"/"}?:""}$outputName.png")
      if(sequence&&index<10)mainHandler.post{exportOne(index+1)}
      else if(sequence)Log.i("QF_TEST","EXPORTS=10 COMPLETE")
     }},onFailure={Log.e("QF_EXPORT","FAILED $outputName",it)})}
    }
    if(testCase=="F01"){
     c.debugAllocationFailure=3
     c.export(s,w,h){failure->
      if(failure.isFailure){Log.i("QF_TEST","EXPECTED allocation failure; retry same renderer");exportOne(1)}
      else {failure.getOrNull()?.recycle();Log.e("QF_EXPORT","FAILED F01 injected failure was not observed")}
     }
    }else exportOne(1)
    if(testCase=="E12"||testCase?.startsWith("R09")==true)vm.update(false){it.copy(pose=Quaternion.euler(60f,-20f,10f))}
   }
  }
  mainHandler.postDelayed(attempt,700)
 }
 private fun saveExport(s:ProjectState){val c=controller?:return;if(exportBusy)return;val(w,h)=s.outputSize();exportBusy=true;cancelRequested.set(false);exportStatus="正在渲染 $w × $h…";val frozen=s.copy();c.export(frozen,w,h){result->result.fold(onSuccess={bitmap->exportStatus="正在保存 PNG…";lifecycleScope.launch{try{val uri=withContext(Dispatchers.IO){saveBitmap(bitmap)};lastShare=uri;exportStatus="已保存到 Pictures / Q-screenshot · $w × $h"}catch(e:Exception){exportStatus="保存失败：${e.message}"}finally{bitmap.recycle();exportBusy=false}}},onFailure={exportBusy=false;exportStatus="导出失败：${it.message}"})}}
 private fun saveBitmap(bitmap:Bitmap):Uri {
  if(cancelRequested.get())throw IllegalStateException("导出已取消")
  val name="Q-screenshot-${System.currentTimeMillis()}.png";val values=ContentValues().apply{put(MediaStore.Images.Media.DISPLAY_NAME,name);put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/Q-screenshot");put(MediaStore.Images.Media.IS_PENDING,1)}
  val uri=contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values)?:error("无法创建相册文件")
  try{contentResolver.openOutputStream(uri)?.use{check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)){"PNG 编码失败"}}?:error("无法打开保存位置");if(cancelRequested.get())throw IllegalStateException("导出已取消");contentResolver.update(uri,ContentValues().apply{put(MediaStore.Images.Media.IS_PENDING,0)},null,null);return uri}catch(e:Exception){contentResolver.delete(uri,null,null);throw e}
 }
 @OptIn(ExperimentalMaterial3Api::class)
 @Composable private fun Editor(){
  val s by vm.state.collectAsState();val message by vm.message.collectAsState();val importing by vm.importBusy.collectAsState();val revision by vm.historyRevision.collectAsState()
  var tab by remember{mutableIntStateOf(0)};var panelOpen by remember{mutableStateOf(false)};var showExport by remember{mutableStateOf(false)};var renderError by remember{mutableStateOf("")}
  val picker=rememberLauncherForActivityResultCompat{uri->uri?.let{vm.import(it)}}
  val fontScale=LocalDensity.current.fontScale
  val labels=listOf("设备","背景","变换","光影","画布")
  val automatic=remember(s.deviceId,s.stateId,s.portrait){vm.store.automaticCanvas(s)}
  val ratioLabel=if(s.canvasWidth==automatic.canvasWidth&&s.canvasHeight==automatic.canvasHeight)"自动比例"else "${s.canvasWidth}:${s.canvasHeight}"
  val openTools={index:Int->tab=index;panelOpen=true}
  val fit={vm.update{vm.store.automaticCanvas(it).copy(zoom=1f,offsetX=0f,offsetY=0f)}}
  val preview:@Composable (Modifier)->Unit={modifier->
   BoxWithConstraints(modifier.semantics{contentDescription="qf-preview"}.padding(horizontal=12.dp,vertical=4.dp),contentAlignment=Alignment.Center){
    val ratio=s.canvasWidth.toFloat()/s.canvasHeight
    val canvasModifier=if(maxWidth/maxHeight>ratio)Modifier.fillMaxHeight().aspectRatio(ratio)else Modifier.fillMaxWidth().aspectRatio(ratio)
    Box(canvasModifier.clip(RoundedCornerShape(4.dp)).border(1.dp,Color(0xffe0e1df),RoundedCornerShape(4.dp)).background(if(s.background=="transparent")Color(0xffd8dbd8)else Color(s.colorA))){
       AndroidView(factory={context->TextureView(context).also{target->try{val c=RenderController(context,vm.store){renderError=it};controller=c;target.tag=c;if(testCase=="AXIS")c.addAxisDiagnostics();c.attach(target);c.applyState(s);scheduleTest()}catch(e:Throwable){renderError="渲染器启动失败：${e.message}";Log.e("QF_RENDER","init",e)}}},onReset=null,onRelease={target->val c=target.tag as? RenderController;if(controller===c)controller=null;c?.close()},update={controller?.applyState(s)},modifier=Modifier.fillMaxSize().pointerInput(Unit){detectTapGestures(onDoubleTap={vm.update{it.copy(zoom=1f,offsetX=0f,offsetY=0f)}})}.pointerInput(Unit){awaitEachGesture{awaitFirstDown(requireUnconsumed=false);vm.beginGesture();do{val e=awaitPointerEvent();val changes=e.changes.filter{it.pressed};if(changes.isNotEmpty()){val pan=e.calculatePan();if(changes.size==1){vm.update(false){FrontRotation.drag(it,pan.x,pan.y)}}else{val z=e.calculateZoom();val rotate=e.calculateRotation();vm.update(false){FrontRotation.roll(it.copy(zoom=it.zoom*z,offsetX=it.offsetX+pan.x/size.width*2,offsetY=it.offsetY+pan.y/size.height*2).safe(),-rotate)}};e.changes.forEach{it.consume()}}}while(e.changes.any{it.pressed});vm.endGesture()}})
     if(renderError.isNotEmpty())Text(renderError,Modifier.align(Alignment.Center).padding(24.dp),color=MaterialTheme.colorScheme.error)
    }
   }
  }
  val tools:@Composable (Modifier)->Unit={modifier->
   Column(modifier.padding(horizontal=12.dp),horizontalAlignment=Alignment.CenterHorizontally){
    Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically){
     TextButton(onClick={openTools(tab)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(horizontal=10.dp)){
      Text(when(tab){0->if(s.deviceId=="redmi-k90-pro-max")"REDMI K90 Pro Max"else DeviceCatalog.find(s.deviceId).compactTitle;1->when(s.background){"transparent"->"透明 PNG";"gradient"->"双色渐变";else->"背景颜色"};2->"角度与透视";3->"摄影棚光影";else->"画布与尺寸"},modifier=Modifier.weight(1f,fill=false),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
      Text(" ⌄",maxLines=1,softWrap=false)
     }
     TextButton(onClick=fit,modifier=Modifier.heightIn(min=48.dp)){Text("适配")}
     FilledTonalButton(onClick={picker()},enabled=!importing,modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=12.dp)){Text(if(importing)"导入中…"else"＋ 截图")}
    }
    if(!panelOpen)Row(Modifier.fillMaxWidth().height(48.dp),verticalAlignment=Alignment.CenterVertically){
     Text("缩放",style=MaterialTheme.typography.labelMedium,modifier=Modifier.width(42.dp))
     Slider(value=s.zoom,onValueChange={value->vm.beginGesture();vm.update(false){it.copy(zoom=value)}},onValueChangeFinished={vm.endGesture()},valueRange=.35f..3f,modifier=Modifier.weight(1f))
     TextButton(onClick={openTools(2)},modifier=Modifier.width(58.dp),contentPadding=PaddingValues(0.dp)){Text("${(s.zoom*100).roundToInt()}%",style=MaterialTheme.typography.labelMedium)}
    }
    Surface(shape=RoundedCornerShape(28.dp),color=Color.White,tonalElevation=0.dp,border=BorderStroke(1.dp,Color(0xffe1e2df)),modifier=Modifier.fillMaxWidth().padding(bottom=6.dp)){
     Row(Modifier.fillMaxWidth().padding(4.dp)){
      labels.forEachIndexed{i,label->
       TextButton(onClick={tab=i;panelOpen=true},modifier=Modifier.weight(1f).heightIn(min=48.dp).semantics{contentDescription="qf-tab-${listOf("device","background","angle","light","canvas")[i]}"},shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(horizontal=2.dp),colors=ButtonDefaults.textButtonColors(containerColor=if(tab==i)Color(0xffe7ebe7)else Color.Transparent,contentColor=if(tab==i)Color(0xff203c30)else Color(0xff626b64))){Text(label,style=MaterialTheme.typography.labelLarge,maxLines=1)}
      }
     }
    }
   }
  }
  val panel:@Composable (Modifier)->Unit={modifier->
   Surface(modifier.semantics{contentDescription="qf-panel"},color=Color(0xfffafbf8),shape=RoundedCornerShape(topStart=20.dp,topEnd=20.dp),border=BorderStroke(1.dp,Color(0xffdce1da))){
    Column(Modifier.fillMaxSize()){
     Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically){
      Text(labels[tab],style=MaterialTheme.typography.titleMedium,modifier=Modifier.weight(1f))
      TextButton(onClick={panelOpen=false},modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="qf-panel-collapse"}){Text("收起")}
     }
     key(tab){Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=16.dp)){
      when(tab){0->DevicePanel(s);1->BackgroundPanel(s);2->AnglePanel(s);3->LightingPanel(s);4->{Button(onClick=fit,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("自动比例 · 适配当前机型")};CanvasPanel(s)}}
      TextButton(onClick={vm.reset()},modifier=Modifier.heightIn(min=48.dp).padding(top=8.dp)){Text("重置场景（保留截图）")}
      Spacer(Modifier.height(12.dp))
     }}
    }
   }
  }
  Scaffold(containerColor=Color(0xfff2f3f0),topBar={
   Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=12.dp).heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically){
    Text("QS",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(end=6.dp))
    TextButton(onClick={vm.undo()},enabled=revision>=0&&vm.canUndo,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),contentPadding=PaddingValues(0.dp)){Text("↶",fontSize=26.sp,modifier=Modifier.semantics{contentDescription="撤销"})}
    TextButton(onClick={vm.redo()},enabled=revision>=0&&vm.canRedo,modifier=Modifier.sizeIn(minWidth=48.dp,minHeight=48.dp),contentPadding=PaddingValues(0.dp)){Text("↷",fontSize=26.sp,modifier=Modifier.semantics{contentDescription="重做"})}
    TextButton(onClick={openTools(4)},modifier=Modifier.weight(1f).heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=2.dp)){Text("$ratioLabel ⌄",maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis,style=MaterialTheme.typography.labelLarge)}
    Button(onClick={showExport=true},enabled=!exportBusy,modifier=Modifier.heightIn(min=48.dp),shape=RoundedCornerShape(24.dp),contentPadding=PaddingValues(horizontal=18.dp)){Text("保存")}
   }
  }){padding->
   BoxWithConstraints(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()){
    val inlinePanelHeight=(maxHeight*.34f).coerceIn(150.dp,340.dp)
    val landscape=maxWidth>maxHeight*1.15f;val controlsWidth=if(maxWidth>900.dp||fontScale>1.2f)320.dp else 260.dp
    if(landscape)Row(Modifier.fillMaxSize()){
     preview(Modifier.weight(1f).fillMaxHeight())
     Column(Modifier.width(controlsWidth).fillMaxHeight().padding(top=6.dp)){
      tools(Modifier.fillMaxWidth())
      if(panelOpen)panel(Modifier.fillMaxWidth().weight(1f))
      else Text("单指旋转 · 双指缩放 / 平移\n双击适配 · 点选下方参数",style=MaterialTheme.typography.bodySmall,color=Color(0xff657069),modifier=Modifier.padding(16.dp))
     }
    }else Column(Modifier.fillMaxSize()){
     preview(Modifier.fillMaxWidth().weight(1f))
     tools(Modifier.fillMaxWidth())
     if(panelOpen)panel(Modifier.fillMaxWidth().height(inlinePanelHeight))
    }
   }
  }
  if(showExport)AlertDialog(onDismissRequest={if(!exportBusy)showExport=false},title={Text("保存作品")},text={Column{Text("PNG · SDR / sRGB",style=MaterialTheme.typography.bodySmall);Spacer(Modifier.height(12.dp));Choices(listOf("2048","2560","3840"),s.exportLongEdge.toString()){v->vm.update{it.copy(exportLongEdge=v.toInt())}};val(w,h)=s.outputSize();Text("$w × $h 像素",Modifier.padding(vertical=12.dp));Text(if(s.background=="transparent")"透明背景 · 无水印"else"当前背景 · 无水印",style=MaterialTheme.typography.bodySmall);if(exportStatus.isNotEmpty())Text(exportStatus,Modifier.padding(top=12.dp));if(exportBusy)LinearProgressIndicator(Modifier.fillMaxWidth().padding(top=12.dp))}},confirmButton={Button(onClick={saveExport(s)},enabled=!exportBusy){Text("保存到相册")}},dismissButton={Row{if(lastShare!=null&&!exportBusy)TextButton(onClick={startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,lastShare);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"分享作品"))}){Text("分享")};TextButton(onClick={if(exportBusy){cancelRequested.set(true);controller?.cancelExport()}else showExport=false}){Text(if(exportBusy)"取消导出"else"完成")}}})
  LaunchedEffect(message){if(message.isNotEmpty()){delay(5000);vm.message.value=""}}
  if(message.isNotEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopCenter){Surface(color=Color(0xffdfebe1),shape=RoundedCornerShape(12.dp),modifier=Modifier.statusBarsPadding().padding(top=64.dp,start=20.dp,end=20.dp)){Text(message,color=Color(0xff203a2b),modifier=Modifier.padding(12.dp))}}
 }
 @Composable private fun rememberLauncherForActivityResultCompat(onResult:(Uri?)->Unit):()->Unit {
  val saf=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->onResult(uri)}
  val picker=androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->onResult(uri)}
  return {if(ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(this))picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))else saf.launch(arrayOf("image/*"))}
 }
 @Composable private fun Label(text:String){Text(text,style=MaterialTheme.typography.labelMedium,color=Color(0xff52665a),modifier=Modifier.padding(top=10.dp,bottom=8.dp))}
 @Composable private fun Choices(labels:List<String>,selected:String,onClick:(String)->Unit){Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){labels.forEach{label->FilterChip(selected=selected==label,onClick={onClick(label)},label={Text(label)})}}}
 @Composable private fun Adjust(label:String,value:Float,range:ClosedFloatingPointRange<Float>,onChange:(Float)->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text(label,style=MaterialTheme.typography.bodySmall,modifier=Modifier.width(68.dp));Slider(value=value.coerceIn(range),onValueChange={vm.beginGesture();onChange(it)},onValueChangeFinished={vm.endGesture()},valueRange=range,modifier=Modifier.weight(1f));Text("${value.roundToInt()}",style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(32.dp))}}
 @Composable private fun DevicePanel(s:ProjectState){
  val device=DeviceCatalog.find(s.deviceId)
  Label("模型")
  Choices(DeviceCatalog.choices.map{it.compactTitle},device.compactTitle){title->
   val selected=DeviceCatalog.choices.first{it.compactTitle==title}
   vm.update{it.copy(deviceId=selected.id,stateId=selected.defaultState,colorwayId=ColorwayCatalog.defaultId(selected.id),portrait=false,cropX=0f,cropY=0f)}
  }
  Label("官方配色")
  val colorway=ColorwayCatalog.find(s.deviceId,s.colorwayId)
  Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   ColorwayCatalog.choices(s.deviceId).forEach{choice->
    FilterChip(selected=choice.id==colorway.id,onClick={vm.update{it.copy(colorwayId=choice.id)}},modifier=Modifier.heightIn(min=48.dp).semantics{contentDescription="qf-color-${choice.id}"},label={Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(16.dp).background(Color(choice.swatch),RoundedCornerShape(8.dp)).border(1.dp,Color(0xff858b85),RoundedCornerShape(8.dp)));Spacer(Modifier.width(6.dp));Text(choice.title)}})
   }
  }
  Label("屏幕状态")
  if(device.states.size==1)Text(DeviceCatalog.stateLabel(device.defaultState),style=MaterialTheme.typography.bodyMedium)
  else {
   Choices(device.states.map{DeviceCatalog.stateLabel(it)},DeviceCatalog.stateLabel(s.stateId)){label->val state=device.states.first{DeviceCatalog.stateLabel(it)==label};vm.update{it.copy(stateId=state,portrait=false)}}
   if(s.stateId=="unfolded")Choices(listOf("横向构图","竖向构图"),if(s.portrait)"竖向构图"else"横向构图"){v->vm.update{it.copy(portrait=v.startsWith("竖"))}}
  }
  Label("截图适配")
  Choices(listOf("完整显示","填满屏幕"),if(s.fitMode=="fit")"完整显示"else"填满屏幕"){v->vm.update{it.copy(fitMode=if(v=="完整显示")"fit"else"fill",cropX=0f,cropY=0f)}}
  if(s.fitMode=="fill"){
   Adjust("裁切横移",s.cropX*100,-100f..100f){v->vm.update(false){it.copy(cropX=v/100)}}
   Adjust("裁切纵移",s.cropY*100,-100f..100f){v->vm.update(false){it.copy(cropY=v/100)}}
  }
 }
 @Composable private fun AnglePanel(s:ProjectState){Label("摄影角度");Choices(listOf("正面","左前","右前","俯视","横放"),""){v->vm.update{anglePreset(it,v)}};val e=s.pose.euler();listOf("左右" to (-60f..60f),"俯仰" to (-45f..45f),"旋转" to (-180f..180f)).forEachIndexed{i,p->Adjust(p.first,e[i],p.second){v->val n=e.copyOf();n[i]=v;vm.update(false){it.copy(pose=Quaternion.euler(n[0],n[1],n[2]))}}};Adjust("透视",s.fov,20f..65f){v->vm.update(false){it.copy(fov=v)}};Choices(listOf("透视相机","正交相机"),if(s.orthographic)"正交相机"else"透视相机"){v->vm.update{it.copy(orthographic=v.startsWith("正"))}};TextButton(onClick={vm.update{anglePreset(it,"正面")}}){Text("正面清晰 · 适配当前画布")}}
 @Composable private fun LightingPanel(s:ProjectState){Label("摄影棚");val map=mapOf("柔光白棚" to "soft","冷灰产品照" to "cool","深色边缘光" to "rim");Choices(map.keys.toList(),map.entries.firstOrNull{it.value==s.lighting}?.key?:"柔光白棚"){v->vm.update{it.copy(lighting=map.getValue(v),background=if(v=="深色边缘光")"gradient"else it.background,colorA=if(v=="深色边缘光")0xff26303a.toInt()else if(v=="冷灰产品照")0xffc0cbd4.toInt()else 0xffe9ede8.toInt(),colorB=if(v=="深色边缘光")0xff111820.toInt()else 0xffa4b5b0.toInt())}};Adjust("灯光方位",s.lightDirection,-180f..180f){v->vm.update(false){it.copy(lightDirection=v)}};Adjust("灯光强度",s.lightIntensity*100,10f..200f){v->vm.update(false){it.copy(lightIntensity=v/100)}};Adjust("环境方向",s.environmentRotation,-180f..180f){v->vm.update(false){it.copy(environmentRotation=v)}};Adjust("玻璃反射",s.reflection*100,0f..100f){v->vm.update(false){it.copy(reflection=v/100)}};Adjust("阴影强度",s.shadow*100,0f..100f){v->vm.update(false){it.copy(shadow=v/100)}};Adjust("阴影柔化",s.softness*100,0f..100f){v->vm.update(false){it.copy(softness=v/100)}};Choices(listOf("浮空","落地"),if(s.grounded)"落地"else"浮空"){v->vm.update{it.copy(grounded=v=="落地")}}}
 @Composable private fun BackgroundPanel(s:ProjectState){Label("背景");Choices(listOf("双色渐变","透明","纯白","浅灰","深灰"),when(s.background){"transparent"->"透明";"gradient"->"双色渐变";else->if(s.colorA==0xffffffff.toInt())"纯白"else if(s.colorA==0xff252b31.toInt())"深灰"else"浅灰"}){v->vm.update{it.copy(background=when(v){"透明"->"transparent";"双色渐变"->"gradient";else->"solid"},colorA=when(v){"纯白"->0xffffffff.toInt();"深灰"->0xff252b31.toInt();else->0xffe9ede8.toInt()},colorB=0xffa9bdb5.toInt())}};var hex by remember(s.colorA){mutableStateOf("%06X".format(s.colorA and 0xffffff))};OutlinedTextField(value=hex,onValueChange={v->hex=v.take(6);if(hex.length==6)runCatching{(0xff000000 or hex.toLong(16)).toInt()}.onSuccess{c->vm.update{it.copy(colorA=c,background="solid")}}},label={Text("自定义颜色 · HEX")},singleLine=true,modifier=Modifier.fillMaxWidth().padding(top=12.dp));Text("透明 PNG 可叠放在任意底色上；关闭阴影可获得干净抠图。",style=MaterialTheme.typography.bodySmall,color=Color(0xff5c6b61),modifier=Modifier.padding(top=12.dp))}
 @Composable private fun CanvasPanel(s:ProjectState){Label("默认参数");FilledTonalButton(onClick={vm.saveDefaultSettings()},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp).semantics{contentDescription="qf-save-defaults"}){Text("设为默认参数")};Text("保存当前机型、配色、角度、光影与画布；不包含截图。",style=MaterialTheme.typography.bodySmall);Label("画布比例");val ratio="${s.canvasWidth}:${s.canvasHeight}";Choices(listOf("1:1","4:5","9:16","16:9"),ratio){v->val p=v.split(":");vm.update{it.copy(canvasWidth=p[0].toInt(),canvasHeight=p[1].toInt())}};var customW by remember{mutableStateOf(s.canvasWidth.toString())};var customH by remember{mutableStateOf(s.canvasHeight.toString())};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(customW,{v->customW=v.filter{it.isDigit()}.take(4)},Modifier.weight(1f),label={Text("宽")},singleLine=true);OutlinedTextField(customH,{v->customH=v.filter{it.isDigit()}.take(4)},Modifier.weight(1f),label={Text("高")},singleLine=true)};TextButton(onClick={val w=customW.toIntOrNull();val h=customH.toIntOrNull();if(w!=null&&h!=null&&w>0&&h>0)vm.update{it.copy(canvasWidth=w,canvasHeight=h)}}){Text("应用自定义比例")};Label("PNG 长边");Choices(listOf("2048","2560","3840"),s.exportLongEdge.toString()){v->vm.update{it.copy(exportLongEdge=v.toInt())}};val(w,h)=s.outputSize();Text("输出 $w × $h 像素 · sRGB",style=MaterialTheme.typography.bodySmall,color=Color(0xff52665a));Text("原图仅存储在设备私有目录，不会上传。草稿自动保存。",style=MaterialTheme.typography.labelSmall,color=Color(0xff637368),modifier=Modifier.padding(top=12.dp))}
}
@Composable private fun QingTheme(content:@Composable ()->Unit){MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xff245b44),onPrimary=Color.White,surface=Color(0xfffafbf8),surfaceContainer=Color(0xffeef1eb),onSurface=Color(0xff202a24),outline=Color(0xffa9b5aa),secondaryContainer=Color(0xffe0eadd),onSecondaryContainer=Color(0xff254633))){CompositionLocalProvider(LocalContentColor provides Color(0xff202a24),content=content)}}
