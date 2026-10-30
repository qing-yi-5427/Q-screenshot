package dev.qingframe

import android.content.ClipData
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.qingframe.model.ShareViewModel

/** Separate exported receiver: external images never replace the current editor project. */
class ShareActivity:ComponentActivity(){
 private val model:ShareViewModel by viewModels()
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);Log.i("QF_SHARE","ON_CREATE restored=${savedInstanceState!=null}");model.accept(intent)
  setContent{MaterialTheme{val status by model.status.collectAsState()
   LaunchedEffect(status.id,status.phase,status.autoShared){if(status.phase=="ready"&&!status.autoShared)share()}
   Surface(Modifier.fillMaxSize()){Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
    Text("Q-screenshot · 图片套壳",style=MaterialTheme.typography.headlineSmall);Spacer(Modifier.height(20.dp));Text(status.message)
    if(status.busy){CircularProgressIndicator(Modifier.padding(24.dp));TextButton(onClick={model.cancel()}){Text("取消生成")}}
    if(status.output.isNotBlank()&&!status.busy){Button(onClick={share()},modifier=Modifier.padding(top=20.dp)){Text("再次分享 PNG")}}
    if(!status.busy)TextButton(onClick={finish()}){Text("返回")}
   }}
  }}
 }
 override fun onNewIntent(intent:Intent){super.onNewIntent(intent);Log.i("QF_SHARE","ON_NEW_INTENT");setIntent(intent);model.accept(intent,true)}
 private fun share(){try{
  val state=model.status.value;val uri=FileProvider.getUriForFile(this,"$packageName.files",model.outputFile(state.output))
  val send=Intent(Intent.ACTION_SEND).apply{type="image/png";putExtra(Intent.EXTRA_STREAM,uri);clipData=ClipData.newUri(contentResolver,"套壳截图",uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  startActivity(Intent.createChooser(send,"分享套壳截图"));model.chooserStarted()
 }catch(e:Exception){model.chooserFailed(e.message?:"请重试")}}
}
