package dev.qingframe
import dev.qingframe.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
class LibraryPolicyTest {
 @Test fun downloadsArePinnedToThisPublicRepository(){
  assertTrue(LibraryPolicy.packageUrl("https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-2026-11/redmi-k90-1.zip"))
  for(url in listOf("http://github.com/qing-yi-5427/Q-screenshot/releases/download/models-a/p.zip","https://github.com/other/Q-screenshot/releases/download/models-a/p.zip","https://github.com/qing-yi-5427/Q-screenshot/releases/download/v1/p.zip","https://user@github.com/qing-yi-5427/Q-screenshot/releases/download/models-a/p.zip","https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-a/../p.zip","https://github.com/qing-yi-5427/Q-screenshot/releases/download/models-a/p.zip?private=1"))assertFalse(url,LibraryPolicy.packageUrl(url))
  assertTrue(LibraryPolicy.redirectUrl("https://release-assets.githubusercontent.com/public/path?signature=x"));assertFalse(LibraryPolicy.redirectUrl("https://uploads.github.com/private"));assertFalse(LibraryPolicy.redirectUrl("https://github.com.evil.org/path"))
 }
 @Test fun archivePathsCannotEscape(){for(name in listOf("../x","/x","devices/../x","devices/./x","devices\\x","C:/x","devices//x"))assertFalse(name,LibraryPolicy.entry(name));assertTrue(LibraryPolicy.entry("devices/redmi-k90-pro-max/portrait.glb"))}
 @Test fun centralDirectoryRejectsUnixSymlink(){
  val file=Files.createTempFile("model-test",".zip").toFile()
  try{
   ZipOutputStream(file.outputStream()).use{it.putNextEntry(ZipEntry("package.json"));it.write("{}".toByteArray());it.closeEntry()};LibraryPolicy.rejectLinks(file)
   val bytes=file.readBytes();val pos=(0..bytes.size-4).first{bytes[it]==0x50.toByte()&&bytes[it+1]==0x4b.toByte()&&bytes[it+2]==1.toByte()&&bytes[it+3]==2.toByte()}
   RandomAccessFile(file,"rw").use{it.seek((pos+5).toLong());it.write(3);it.seek((pos+38).toLong());it.write(byteArrayOf(0,0,0xff.toByte(),0xa1.toByte()))}
   assertThrows(IllegalArgumentException::class.java){LibraryPolicy.rejectLinks(file)}
  }finally{file.delete()}
 }
 @Test fun missingPinnedVersionDoesNotBorrowNewerStates(){val previous=DeviceCatalog.find("redmi-k90-pro-max");try{DeviceCatalog.register(previous.copy(states=listOf("main"),defaultState="main",modelVersion="2.0"));val before=ProjectState(deviceId=previous.id,modelVersion="1.0",stateId="portrait",colorwayId="old-white");val after=before.safe();assertEquals("portrait",after.stateId);assertEquals("1.0",after.modelVersion);assertEquals("old-white",after.colorwayId);assertTrue(ColorwayCatalog.choices(before.deviceId,before.modelVersion).isEmpty())}finally{DeviceCatalog.register(previous)}}
 @Test fun unknownDraftIdentityIsPreservedWithoutInventingFoldStates(){val s=ProjectState(deviceId="xiaomi-future-ultra",stateId="main-display",colorwayId="titanium",canvasWidth=7,canvasHeight=9).safe();assertEquals("xiaomi-future-ultra",s.deviceId);assertEquals("main-display",s.stateId);assertEquals("titanium",s.colorwayId);assertEquals(7,s.canvasWidth)}
}
