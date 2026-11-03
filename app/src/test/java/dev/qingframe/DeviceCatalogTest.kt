package dev.qingframe
import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
class DeviceCatalogTest {
 @Test fun redmiIsSingleStraightState(){val d=DeviceCatalog.find("redmi-k90-pro-max");assertEquals(listOf("portrait"),d.states);assertEquals("直板 · 主屏",DeviceCatalog.stateLabel(d.defaultState));val s=ProjectState(deviceId=d.id,stateId="unfolded",portrait=true).safe();assertEquals("portrait",s.stateId);assertFalse(s.portrait)}
 @Test fun foldDraftStillKeepsState(){val s=ProjectState(deviceId="mix-fold-4",stateId="unfolded",portrait=true).safe();assertEquals("unfolded",s.stateId);assertTrue(s.portrait);assertEquals("folded",ProjectState().safe().stateId)}
 @Test fun importDoesNotInventAnUnfoldedRedmi(){assertEquals("portrait" to false,DeviceCatalog.recommend("redmi-k90-pro-max",2364,1672));assertEquals("unfolded" to true,DeviceCatalog.recommend("wide-fold",1672,2364))}
 @Test fun bootstrapMetadataRetainsBuiltinAndLegacyDownloadIdentity(){assertTrue(DeviceCatalog.choices.map{it.id}.containsAll(listOf("xiaomi-18-fold","redmi-k90-pro-max")));assertEquals("future-phone",DeviceCatalog.find("future-phone").id)}
 @Test fun legacyModelsResolveBeforeValidation(){
  for(id in DeviceCatalog.legacyDeviceIds){
   val before=ProjectState(deviceId=id,stateId="unfolded",portrait=true,zoom=1.4f,canvasWidth=9,canvasHeight=16)
   val after=before.safe()
   assertEquals("xiaomi-18-fold",after.deviceId);assertEquals("unfolded",after.stateId);assertTrue(after.portrait)
   assertEquals(1.4f,after.zoom,0f);assertEquals(9,after.canvasWidth);assertEquals(16,after.canvasHeight)
   val portrait=before.copy(stateId="portrait",portrait=false).safe();assertEquals("unfolded",portrait.stateId);assertTrue(portrait.portrait)
  }
 }

}
