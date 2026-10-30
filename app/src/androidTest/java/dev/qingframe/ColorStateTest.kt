package dev.qingframe

import android.content.Context
import android.content.ContextWrapper
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
class ColorStateTest {
    private val target get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val devices = listOf("xiaomi-18-fold", "redmi-k90-pro-max")
    private fun state(device: String, color: String) = ProjectState(
        deviceId=device, stateId=if(device=="xiaomi-18-fold") "unfolded" else "portrait",
        portrait=device=="xiaomi-18-fold",colorwayId=color,
        imagePath="source-color-state.img",imageWidth=1672,imageHeight=2364,
        pose=Quaternion.euler(24f,-13f,37f),orthographic=true,
        zoom=1.25f,offsetX=.2f,offsetY=-.3f,fitMode="fill",cropX=.4f,cropY=-.2f,
        canvasWidth=9,canvasHeight=16,lighting="cool",reflection=.18f,shadow=.3f
    ).safe()
    private fun assertSameState(expected: ProjectState, actual: ProjectState) {
        val a=JSONObject(StateJson.encode(expected));val b=JSONObject(StateJson.encode(actual))
        for(key in a.keys()) if(key!="pose") assertEquals("field $key",a.get(key).toString(),b.get(key).toString())
        expected.pose.matrix().zip(actual.pose.matrix()).forEachIndexed { i,(x,y) -> assertEquals("pose $i",x,y,.00001f) }
    }
    @Test fun everyCatalogColorwaySurvivesJsonRoundtrip() {
        for(device in devices) {
            val colors=ColorwayCatalog.choices(device)
            assertTrue("Nonempty official color choices for $device",colors.isNotEmpty())
            assertEquals("Unique color ids for $device",colors.size,colors.map{it.id}.toSet().size)
            for(color in colors) {
                assertTrue(color.id.isNotBlank());assertTrue(color.title.isNotBlank())
                val original=state(device,color.id)
                assertEquals(color.id,original.colorwayId)
                assertSameState(original,StateJson.decode(StateJson.encode(original)))
            }
        }
    }
    @Test fun oldDraftWithoutColorUsesThatDevicesDefault() {
        for(device in devices) {
            val original=state(device,ColorwayCatalog.choices(device).last().id)
            val raw=JSONObject(StateJson.encode(original)).apply{remove("colorwayId")}
            val decoded=StateJson.decode(raw.toString())
            val default=ColorwayCatalog.find(device,"").id
            assertTrue(ColorwayCatalog.choices(device).any{it.id==default})
            assertSameState(original.copy(colorwayId=default),decoded)
        }
    }
    @Test fun unknownAndForeignColorIdsFallBackToDeviceDefault() {
        for(device in devices) {
            val default=ColorwayCatalog.find(device,"").id
            val foreign=devices.filter{it!=device}.flatMap{ColorwayCatalog.choices(it)}
                .map{it.id}.filter{other->ColorwayCatalog.choices(device).none{it.id==other}}
            for(invalid in listOf("unknown-color-state-test")+foreign) {
                val baseline=state(device,default)
                val raw=JSONObject(StateJson.encode(baseline)).put("colorwayId",invalid)
                assertSameState(baseline,StateJson.decode(raw.toString()))
                assertSameState(baseline,baseline.copy(colorwayId=invalid).safe())
            }
        }
    }
    @Test fun saveAndRestoreColorPreservesSourcePoseAndEditingFields() {
        val dir=File(target.cacheDir,"color-state-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(target){override fun getFilesDir():File=dir}
        try {
            val store=ProjectStore(context)
            val source=byteArrayOf(0x51,0x46,0x43,0x4f,0x4c,0x4f,0x52)
            store.imageFile("source-color-state.img").writeBytes(source)
            for(device in devices) for(color in ColorwayCatalog.choices(device)) {
                val original=state(device,color.id);store.save(original)
                assertSameState(original,ProjectStore(context).load())
                assertArrayEquals("Color change never rewrites source bytes",source,store.imageFile(original.imagePath).readBytes())
            }
        } finally { dir.deleteRecursively() }
    }
}
