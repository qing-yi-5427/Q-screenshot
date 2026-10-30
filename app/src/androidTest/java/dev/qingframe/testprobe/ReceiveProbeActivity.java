package dev.qingframe.testprobe;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.UUID;

/** Selected in the real Android chooser. Reads the target FileProvider grant under test APK UID. */
public final class ReceiveProbeActivity extends Activity {
    @SuppressWarnings("deprecation") @Override public void onCreate(Bundle saved){
        super.onCreate(saved);TextView view=new TextView(this);view.setPadding(24,48,24,24);view.setTextSize(20);setContentView(view);
        try {
            Intent incoming=getIntent();Uri uri=incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if(!Intent.ACTION_SEND.equals(incoming.getAction())||!"image/png".equals(incoming.getType())||uri==null||!"dev.qingframe.files".equals(uri.getAuthority()))throw new IllegalArgumentException("Expected QingFrame PNG chooser delivery");
            int targetUid=getPackageManager().getPackageUid("dev.qingframe",0);
            if(Process.myUid()==targetUid)throw new IllegalStateException("Receiver must be another UID");
            boolean grant=checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==PackageManager.PERMISSION_GRANTED;
            if(!grant)throw new SecurityException("No chooser read grant");
            byte[] bytes;
            try(InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()){
                if(input==null)throw new IllegalArgumentException("Missing stream");byte[] buffer=new byte[16384];int count;
                while((count=input.read(buffer))!=-1){if(output.size()+count>64*1024*1024)throw new IllegalArgumentException("PNG too large");output.write(buffer,0,count);}bytes=output.toByteArray();
            }
            if(bytes.length<8||bytes[0]!=(byte)137||bytes[1]!=80||bytes[2]!=78||bytes[3]!=71)throw new IllegalArgumentException("Not PNG");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
            if(options.outWidth<=0||options.outHeight<=0)throw new IllegalArgumentException("Invalid PNG dimensions");
            byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hash=new StringBuilder();for(byte value:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
            File dir=new File(getFilesDir(),"share-received");dir.mkdirs();File copy=new File(dir,"received-"+UUID.randomUUID()+".png");try(FileOutputStream output=new FileOutputStream(copy)){output.write(bytes);}
            JSONObject event=new JSONObject().put("event","RECEIVED").put("receiverUid",Process.myUid()).put("receiverPid",Process.myPid()).put("targetUid",targetUid)
                .put("readGrant",grant).put("uri",uri.toString()).put("bytes",bytes.length).put("sha256",hash.toString()).put("width",options.outWidth).put("height",options.outHeight).put("copy","share-received/"+copy.getName());
            ShareProbeActivity.event(this,"share-receiver-events.jsonl",event);view.setText("QF Test Receiver\nPNG read under another UID\n"+options.outWidth+" × "+options.outHeight+"\nSHA-256 "+hash);
        }catch(Exception failure){
            try{ShareProbeActivity.event(this,"share-receiver-events.jsonl",new JSONObject().put("event","RECEIVE_FAILED").put("receiverUid",Process.myUid()).put("error",failure.toString()));}catch(Exception ignored){}
            view.setText("QF Test Receiver\nFAILED: "+failure);
        }
    }
}
