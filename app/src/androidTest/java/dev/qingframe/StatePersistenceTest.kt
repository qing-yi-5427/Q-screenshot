package dev.qingframe

import android.content.Context
import android.content.ContextWrapper
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StatePersistenceTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(block: (ProjectStore, File) -> Unit) {
        val dir = File(target.cacheDir, "persistence-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(target) { override fun getFilesDir(): File = dir }
        try { block(ProjectStore(context), dir) } finally { dir.deleteRecursively() }
    }
    private fun assertSameState(expected: ProjectState, actual: ProjectState) {
        val a=JSONObject(StateJson.encode(expected));val b=JSONObject(StateJson.encode(actual))
        for(key in a.keys()) {
            if(key=="pose") continue
            assertEquals("field $key",a.get(key).toString(),b.get(key).toString())
        }
        expected.pose.matrix().zip(actual.pose.matrix()).forEachIndexed { i,(x,y) -> assertEquals("pose $i",x,y,0.00001f) }
    }
    @Test fun roundtripPreservesEverySerializedField() {
        val state=ProjectState(deviceId="xiaomi-18-fold",stateId="unfolded",portrait=true,
            imagePath="source-test.img",imageWidth=1672,imageHeight=2364,
            pose=Quaternion.euler(28f,-17f,32f),zoom=1.25f,offsetX=.2f,offsetY=-.3f,
            fitMode="fill",cropX=.4f,cropY=-.5f,fov=42f,orthographic=true,
            lighting="dark",lightDirection=80f,lightIntensity=1.4f,environmentRotation=100f,
            reflection=.3f,shadow=.7f,softness=.8f,grounded=true,background="solid",
            colorA=0xff124578.toInt(),colorB=0xffabcdef.toInt(),canvasWidth=2160,canvasHeight=3840,exportLongEdge=3840)
        assertSameState(state,StateJson.decode(StateJson.encode(state)))
    }
    @Test fun legacyMissingFieldsUseDefaults() {
        val defaults=ProjectState()
        assertSameState(defaults,StateJson.decode("{}"))
        val migrated=StateJson.decode("""{"schemaVersion":0,"stateId":"unfolded","zoom":1.5}""")
        assertEquals("unfolded",migrated.stateId);assertEquals(1.5f,migrated.zoom,0f)
        assertEquals(defaults.background,migrated.background);assertEquals(defaults.exportLongEdge,migrated.exportLongEdge)
    }
    @Test fun futureVersionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { StateJson.decode("""{"schemaVersion":999}""") }
    }
    @Test fun pathsCannotEscapeProject() = isolated { store,_ ->
        for(path in listOf("../private.png","/absolute.png","nested/../../private.png")) {
            assertEquals("",StateJson.decode(JSONObject().put("imagePath",path).toString()).imagePath)
            assertThrows(IllegalArgumentException::class.java) { store.imageFile(path) }
        }
        assertEquals("source-safe.img",StateJson.decode("""{"imagePath":"source-safe.img"}""").imagePath)
    }
    @Test fun saveReplacesDraftAndIgnoresUncommittedTemp() = isolated { store,dir ->
        val first=ProjectState(zoom=1.2f);store.save(first);assertSameState(first,store.load())
        val root=File(dir,"project");val draft=File(root,"draft.json")
        assertTrue(draft.isFile);assertFalse(File(root,"draft.tmp").exists())
        File(root,"draft.tmp").writeText("truncated uncommitted write")
        assertSameState(first,store.load())
        val second=first.copy(stateId="unfolded",offsetX=.4f);store.save(second)
        assertSameState(second,store.load());assertFalse(File(root,"draft.tmp").exists())
        assertEquals(StateJson.encode(second),draft.readText())
    }
    @Test fun fixturesDecodeAtTheirSpecifiedDimensions() {
        for((name,size) in listOf("outer" to (1168 to 1712),"inner-landscape" to (2364 to 1672),"inner-portrait" to (1672 to 2364))) {
            val bitmap=target.assets.open("fixtures/$name.png").use { BitmapFactory.decodeStream(it) }
            assertNotNull(name,bitmap);assertEquals(size.first,bitmap!!.width);assertEquals(size.second,bitmap.height);bitmap.recycle()
        }
    }
    @Test fun corruptImportCannotOverwriteSourceOrSavedDraft() = isolated { store,dir ->
        val valid=File(dir,"valid.png");target.assets.open("fixtures/outer.png").use { input -> valid.outputStream().use { input.copyTo(it) } }
        val (path,w,h)=store.import(Uri.fromFile(valid));assertEquals(1168,w);assertEquals(1712,h)
        val original=store.imageFile(path).readBytes();val state=ProjectState(imagePath=path,imageWidth=w,imageHeight=h,zoom=1.4f)
        store.save(state);val draft=File(dir,"project/draft.json").readText()
        val bad=File(dir,"broken.png").apply { writeText("This is not an image") }
        assertThrows(Exception::class.java) { store.import(Uri.fromFile(bad)) }
        assertEquals(draft,File(dir,"project/draft.json").readText());assertArrayEquals(original,store.imageFile(path).readBytes())
        assertFalse(File(dir,"project/import.tmp").exists());assertSameState(state,store.load())
        val decoded=store.decode(store.load(),512);assertTrue(decoded.width>0);assertTrue(decoded.height<=512);decoded.recycle()
    }
    @Test fun missingSourceNeverExportsDemoSilently() = isolated { store,_ ->
        val error=assertThrows(IllegalStateException::class.java){store.decode(ProjectState(imagePath="source-missing.img"),512)}
        assertTrue(error.message!!.contains("重新导入"))
        val demo=store.decode(ProjectState(),512);assertTrue(demo.width>0);demo.recycle()
    }
    @Test fun pruningPreservesHistoryAndOtherPrivateFiles() = isolated { store,dir ->
        val a=ProjectState(imagePath="source-a.img");val b=a.copy(imagePath="source-b.img")
        val history=History<ProjectState>();history.push(a,b);val undone=history.undo(b)
        for(name in listOf("source-a.img","source-b.img","source-orphan.img","keep.png"))store.imageFile(name).writeText("private test")
        val outside=File(dir,"outside.img").apply{writeText("outside project")}
        store.pruneSources(history.referencedStates(undone).map{it.imagePath}.toSet())
        assertTrue(store.imageFile("source-a.img").exists());assertTrue(store.imageFile("source-b.img").exists())
        assertFalse(store.imageFile("source-orphan.img").exists());assertTrue(store.imageFile("keep.png").exists());assertTrue(outside.exists())
    }
    @Test fun undoCancelsObsoleteAutosave() {
        val dir=File(target.cacheDir,"vm-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(target){override fun getFilesDir()=dir}
        val application=object:android.app.Application(){init{attachBaseContext(context)};override fun getFilesDir()=dir}
        lateinit var vm:EditorViewModel
        val holder=androidx.lifecycle.ViewModelStore()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync{
                vm=EditorViewModel(application);holder.put("test",vm)
                vm.update{it.copy(zoom=1.8f)};vm.undo()
            }
            Thread.sleep(800)
            assertEquals(1f,vm.store.load().zoom,0f)
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{holder.clear()};dir.deleteRecursively()}
    }
    @Test fun importingAfterFillResetsToExplicitFit() {
        val dir=File(target.cacheDir,"vm-import-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(target){override fun getFilesDir()=dir}
        val application=object:android.app.Application(){init{attachBaseContext(context)};override fun getFilesDir()=dir}
        val input=File(dir,"new.png");target.assets.open("fixtures/outer.png").use{source->input.outputStream().use{source.copyTo(it)}}
        lateinit var vm:EditorViewModel;val holder=androidx.lifecycle.ViewModelStore()
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync{vm=EditorViewModel(application);holder.put("test",vm);vm.update{it.copy(fitMode="fill")};vm.import(Uri.fromFile(input))}
            val deadline=System.currentTimeMillis()+5000;while(vm.importBusy.value&&System.currentTimeMillis()<deadline)Thread.sleep(20)
            assertFalse(vm.importBusy.value);assertEquals("fit",vm.state.value.fitMode);assertTrue(vm.message.value.contains("完整显示"))
        }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync{holder.clear()};dir.deleteRecursively()}
    }

    @Test fun redmiDraftRoundtripKeepsStraightDevice(){
        val state=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",imageWidth=1200,imageHeight=2608)
        assertSameState(state,StateJson.decode(StateJson.encode(state)))
    }
    @Test fun isolatedSessionCannotReplaceExistingUserDraft() = isolated { production,dir ->
        val current=ProjectState(imagePath="source-user.img",zoom=1.7f);production.save(current)
        val before=File(dir,"project/draft.json").readBytes()
        val context=object:ContextWrapper(target){override fun getFilesDir()=dir}
        val test=ProjectStore(context,"redmi-test");test.save(ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait"))
        assertArrayEquals(before,File(dir,"project/draft.json").readBytes());assertSameState(current,production.load())
        assertTrue(File(dir,"test-projects/redmi-test/draft.json").isFile)
        assertThrows(IllegalArgumentException::class.java){ProjectStore(context,"../project")}
    }

    @Test fun refreshedDefaultDoesNotChangeExistingCanvas() = isolated { store,_ ->
        val fresh=store.load()
        assertEquals("xiaomi-18-fold",fresh.deviceId)
        assertTrue(fresh.canvasWidth.toFloat()/fresh.canvasHeight in .78f.. .84f)
        val old=ProjectState(canvasWidth=4,canvasHeight=5,zoom=.8f)
        store.save(old);assertSameState(old,store.load())
        val fitted=store.automaticCanvas(ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait"))
        assertEquals(4,fitted.canvasWidth);assertEquals(5,fitted.canvasHeight)
    }

    @Test fun removedModelsMigrateOnceWithoutLosingDraftOrSource() {
        for(id in DeviceCatalog.legacyDeviceIds) for(state in listOf("folded","unfolded","portrait")) isolated { store,dir ->
            val before=ProjectState(deviceId=id,stateId=state,portrait=state=="unfolded",imagePath="source-kept.img",imageWidth=1672,imageHeight=2364,pose=Quaternion.euler(38f,-17f,26f),zoom=1.6f,offsetX=.2f,offsetY=-.3f,canvasWidth=4,canvasHeight=5,environmentRotation=72f,reflection=.4f,fitMode="fill",cropX=.2f)
            val source=store.imageFile(before.imagePath).apply{writeBytes(byteArrayOf(1,2,3,4))}
            val original=StateJson.encode(before);File(dir,"project/draft.json").writeText(original)
            val after=store.load()
            val expected=before.copy(deviceId="xiaomi-18-fold",stateId=if(state=="portrait")"unfolded"else state,portrait=before.portrait||state=="portrait")
            assertSameState(expected,after);assertNotNull(store.migrationNotice)
            assertArrayEquals(byteArrayOf(1,2,3,4),source.readBytes())
            assertEquals(original,File(dir,"project/draft-before-model-update.json").readText())
            assertEquals("xiaomi-18-fold",JSONObject(File(dir,"project/draft.json").readText()).getString("deviceId"))
            assertSameState(expected,store.load());assertNull(store.migrationNotice)
        }
    }

    @Test fun migrationSaveFailureKeepsOriginalAndDecodedState() = isolated { store,dir ->
        val old=ProjectState(deviceId="mix-fold-4",stateId="unfolded",portrait=true,imagePath="source-kept.img",zoom=1.7f,canvasWidth=9,canvasHeight=16)
        val text=StateJson.encode(old);val draft=File(dir,"project/draft.json").apply{writeText(text)}
        File(dir,"project/draft.tmp").mkdir()
        val decoded=store.load();assertSameState(old.copy(deviceId="xiaomi-18-fold"),decoded)
        assertEquals(text,draft.readText());assertTrue(store.migrationNotice!!.contains("保存失败"))
    }
    @Test fun redmiDraftDoesNotTriggerMigration() = isolated { store,dir ->
        val old=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",imagePath="source-redmi.img",zoom=1.2f,canvasWidth=543,canvasHeight=1000)
        store.save(old);assertSameState(old,store.load());assertNull(store.migrationNotice)
        assertFalse(File(dir,"project/draft-before-model-update.json").exists())
    }

    @Test fun rearPoseIsBackedUpAndCorrectedOnce() = isolated { store,dir ->
        val old=ProjectState(deviceId="xiaomi-18-fold",stateId="unfolded",portrait=true,imagePath="source-front.img",pose=Quaternion.euler(155f,12f,-6f),zoom=1.4f,offsetX=.2f,canvasWidth=9,canvasHeight=16,lighting="cool",shadow=.3f)
        val raw=StateJson.encode(old);val draft=File(dir,"project/draft.json").apply{writeText(raw)}
        store.imageFile(old.imagePath).writeBytes(byteArrayOf(8,7,6))
        val loaded=store.load();assertSameState(old.copy(pose=Quaternion()),loaded)
        assertTrue(store.migrationNotice!!.contains("旋转"));assertEquals(raw,File(dir,"project/draft-before-front-rotation.json").readText())
        assertArrayEquals(byteArrayOf(8,7,6),store.imageFile(old.imagePath).readBytes())
        val persisted=draft.readText();assertTrue(FrontRotation.allowed(StateJson.decode(persisted)))
        assertSameState(loaded,store.load());assertNull(store.migrationNotice)
    }
    @Test fun permittedPoseDoesNotRewriteDraft() = isolated { store,dir ->
        val old=ProjectState(pose=Quaternion.euler(-30f,20f,60f),imagePath="source-kept.img",zoom=1.3f,canvasWidth=4,canvasHeight=5)
        store.save(old);val f=File(dir,"project/draft.json");val before=f.readBytes()
        assertSameState(old,store.load());assertArrayEquals(before,f.readBytes());assertNull(store.migrationNotice)
        assertFalse(File(dir,"project/draft-before-front-rotation.json").exists())
    }

}
