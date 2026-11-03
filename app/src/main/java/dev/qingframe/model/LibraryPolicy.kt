package dev.qingframe.model

import java.net.URI
import java.io.File
import java.io.RandomAccessFile

/** Fixed public repository; neither a catalog nor a ZIP can choose another origin or local path. */
object LibraryPolicy {
 const val INDEX="https://raw.githubusercontent.com/qing-yi-5427/Q-screenshot/main/model-library/catalog.json"
 const val MAX_ZIP=64L*1024*1024
 const val MAX_EXPANDED=128L*1024*1024
 const val MAX_FILES=32
 fun slug(value:String)=value.matches(Regex("[a-z0-9]+(?:-[a-z0-9]+)*"))&&value.length<=80
 fun version(value:String)=value.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))&&!value.contains("..")
 private fun secure(u:URI)=u.scheme=="https"&&u.rawUserInfo==null&&(u.port==-1||u.port==443)&&u.rawFragment==null
 fun packageUrl(value:String):Boolean=runCatching{
  val u=URI(value);secure(u)&&u.host=="github.com"&&u.rawQuery==null&&u.rawPath.matches(Regex("/qing-yi-5427/Q-screenshot/releases/download/models-[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.zip"))&&!u.rawPath.contains("..")
 }.getOrDefault(false)
 fun redirectUrl(value:String):Boolean=runCatching{val u=URI(value);secure(u)&&u.host=="release-assets.githubusercontent.com"}.getOrDefault(false)
 fun entry(name:String):Boolean = name.length<=240&&!name.startsWith('/')&&!name.contains('\\')&&!name.contains(':')&&name.split('/').none{it==".."||it=="."||it.isEmpty()}
 /** ZipEntry has no portable Unix mode API; inspect the central directory before extraction. */
 fun rejectLinks(file:File){
  RandomAccessFile(file,"r").use{f->
   fun u16():Int=java.lang.Short.reverseBytes(f.readShort()).toInt()and 0xffff
   fun u32():Long=java.lang.Integer.reverseBytes(f.readInt()).toLong()and 0xffffffffL
   val start=maxOf(0L,f.length()-65557);var eocd=-1L
   var pos=f.length()-22
   while(pos>=start){f.seek(pos);if(u32()==0x06054b50L){eocd=pos;break};pos--}
   require(eocd>=0){"ZIP 目录损坏"};f.seek(eocd+4);require(u16()==0&&u16()==0){"不支持分卷 ZIP"};val disk=u16();val count=u16();require(count==disk&&count in 1..MAX_FILES){"ZIP 文件数量超限"};val bytes=u32();val offset=u32();val comment=u16();require(eocd+22+comment==f.length()&&offset+bytes==eocd){"ZIP 目录异常"}
   f.seek(offset)
   repeat(count){require(u32()==0x02014b50L){"ZIP 目录损坏"};val made=u16();u16();val flags=u16();val method=u16();require(flags and 1==0&&method in setOf(0,8)){"不支持加密或特殊压缩 ZIP"};f.skipBytes(16);val name=u16();val extra=u16();val note=u16();require(u16()==0){"不支持分卷 ZIP"};u16();val attributes=u32();u32();val mode=((attributes ushr 16).toInt()and 0xf000);require(mode==0||mode==0x8000||mode==0x4000){"ZIP 不允许软链接或特殊文件"};require((made ushr 8)!=3||mode!=0xa000){"ZIP 不允许软链接"};f.skipBytes(name+extra+note)}
   require(f.filePointer==offset+bytes){"ZIP 目录长度不符"}
  }
 }
}
