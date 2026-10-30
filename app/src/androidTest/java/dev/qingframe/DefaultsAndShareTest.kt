package dev.qingframe

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.qingframe.model.*
import dev.qingframe.testprobe.SyntheticShareProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class DefaultsAndShareTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun isolated(block:(Context,String,File)->Unit) {
        val dir=File(target.cacheDir,"defaults-share-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(target){override fun getFilesDir():File=dir}
        try { block(context,"defaults-share-store",dir) } finally { dir.deleteRecursively() }
    }
    private fun scene()=ProjectState(deviceId="xiaomi-18-fold",stateId="unfolded",portrait=true,
        colorwayId="xiye-red",imagePath="source-private-should-never-enter-defaults.img",imageWidth=1117,imageHeight=2341,
        pose=Quaternion.euler(24f,-13f,37f),orthographic=true,zoom=1.25f,offsetX=.2f,offsetY=-.3f,
        fitMode="fill",cropX=.4f,cropY=-.2f,canvasWidth=9,canvasHeight=16,exportLongEdge=2560,
        lighting="cool",environmentRotation=123f,reflection=.18f,shadow=.3f,softness=.8f,
        background="gradient",colorA=0xff123456.toInt(),colorB=0xffabcdef.toInt()).safe()
    private fun configuration(state:ProjectState):String {
        val json=JSONObject(StateJson.encode(state))
        for(key in listOf("imagePath","imageWidth","imageHeight"))json.remove(key)
        return json.toString()
    }
    @Test fun defaultsStripSourceMetadataAndKeepPublicDemoDimensions() = isolated { context,session,dir ->
        val original=scene();val store=DefaultSettingsStore(context,session);assertNull(store.loadOrNull());store.save(original)
        val file=File(dir,"test-projects/$session/default-render-settings.json");assertTrue(file.isFile)
        val text=file.readText();val raw=JSONObject(text).getJSONObject("settings")
        for(key in listOf("imagePath","imageWidth","imageHeight"))assertFalse("No private $key in defaults",raw.has(key))
        assertFalse(text.contains(original.imagePath))
        val loaded=requireNotNull(DefaultSettingsStore(context,session).loadOrNull())
        assertEquals("",loaded.imagePath);assertEquals(1672,loaded.imageWidth);assertEquals(2364,loaded.imageHeight)
        assertEquals(configuration(original),configuration(loaded))
    }
    @Test fun defaultUpdateSurvivesNewStoreAndAppliesAllSettingsToNewImage() = isolated { context,session,_ ->
        val store=DefaultSettingsStore(context,session);store.save(scene())
        val updated=scene().copy(colorwayId="ceramic-special",zoom=1.1f,shadow=.7f).safe();store.save(updated)
        val reopened=DefaultSettingsStore(context,session)
        assertEquals(configuration(updated),configuration(requireNotNull(reopened.loadOrNull())))
        val base=ProjectState(deviceId="redmi-k90-pro-max",stateId="portrait",colorwayId="black").safe()
        val applied=reopened.applyToImage(base,"source-new-original.img",1200,2608)
        assertEquals("source-new-original.img",applied.imagePath);assertEquals(1200,applied.imageWidth);assertEquals(2608,applied.imageHeight)
        assertEquals(configuration(updated),configuration(applied))
    }
    @Test fun oldEditorDraftWinsOverSavedDefaults() = isolated { context,session,_ ->
        val draft=scene().copy(colorwayId="black",zoom=.8f).safe()
        ProjectStore(context,session).save(draft)
        DefaultSettingsStore(context,session).save(scene().copy(colorwayId="ceramic-special",zoom=1.7f).safe())
        assertEquals(StateJson.encode(draft),StateJson.encode(ProjectStore(context,session).load()))
    }
    @Test fun missingOrCorruptDefaultsDoNotReplaceIncomingConfiguration() = isolated { context,session,dir ->
        val base=scene();val store=DefaultSettingsStore(context,session)
        val incoming=store.applyToImage(base,"source-new.img",1168,1712)
        assertEquals(configuration(base),configuration(incoming));assertEquals("source-new.img",incoming.imagePath)
        val file=File(dir,"test-projects/$session/default-render-settings.json");file.parentFile!!.mkdirs();file.writeText("{broken")
        assertNull(DefaultSettingsStore(context,session).loadOrNull())
    }
    @Test fun syntheticProviderRejectsInvalidUrisAndWritesWithoutReadingAnyUserSource() {
        val provider=SyntheticShareProvider()
        for(text in listOf("content://dev.qingframe.test.fixture/missing","content://dev.qingframe.test.fixture/outer?file=private","content://another.authority/outer","file:///private.png")) {
            assertThrows(IllegalArgumentException::class.java){provider.getType(Uri.parse(text))}
            assertThrows(FileNotFoundException::class.java){provider.openFile(Uri.parse(text),"r")}
        }
        assertThrows(FileNotFoundException::class.java){provider.openFile(Uri.parse("content://dev.qingframe.test.fixture/outer"),"w")}
    }
}
