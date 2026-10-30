package dev.qingframe.testprobe;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/** Pure Java: standalone test-APK processes cannot borrow target APK Kotlin stdlib. */
public final class ShareProbeActivity extends Activity {
    static void event(android.content.Context context, String file, JSONObject event) throws Exception {
        try (FileOutputStream stream = new FileOutputStream(new File(context.getFilesDir(), file), true)) {
            stream.write((event.toString()+"\n").getBytes(StandardCharsets.UTF_8));
        }
        Log.i("QF_SHARE_PROBE", event.toString());
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        try {
            String session=getIntent().getStringExtra("test_session");
            if(session==null)session="defaults-share-probe";
            if(!session.matches("defaults-share-[A-Za-z0-9-]{1,32}"))throw new IllegalArgumentException("Isolated session required");
            String fixture=getIntent().getStringExtra("probe_fixture");
            if(fixture==null)fixture="outer";
            if(!fixture.equals("outer")&&!fixture.equals("redmi")&&!fixture.equals("missing"))throw new IllegalArgumentException("Only original fixture URIs allowed");
            int targetUid=getPackageManager().getPackageUid("dev.qingframe",0);
            if(Process.myUid()==targetUid)throw new IllegalStateException("Sender must be another UID");
            boolean hot=getIntent().getBooleanExtra("probe_hot",false);
            boolean noGrant=getIntent().getBooleanExtra("probe_no_grant",false);
            Uri uri=Uri.parse("content://dev.qingframe.test.fixture/"+fixture);
            JSONObject event=new JSONObject().put("event","SEND").put("senderPackage",getPackageName()).put("senderUid",Process.myUid())
                .put("senderPid",Process.myPid()).put("targetUid",targetUid).put("session",session).put("fixture",fixture).put("hotFlags",hot).put("readGrant",!noGrant);
            event(this,"share-probe-events.jsonl",event);
            Intent send=new Intent(Intent.ACTION_SEND).setClassName("dev.qingframe","dev.qingframe.ShareActivity").setType("image/png");
            send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newRawUri("QingFrame original synthetic fixture",uri));
            if(!noGrant)send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if(hot)send.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
            send.putExtra("test_session",session);startActivity(send);
        } catch(Exception failure) { Log.e("QF_SHARE_PROBE","Explicit share launch failed",failure);throw new RuntimeException(failure); }
        finally { finish(); }
    }
}
