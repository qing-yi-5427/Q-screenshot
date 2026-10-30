package dev.qingframe.testprobe;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.OpenableColumns;
import android.util.Log;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

/** Grant-only original assets; no arbitrary file paths or gallery sources. */
public final class SyntheticShareProvider extends ContentProvider {
    @Override public boolean onCreate(){return true;}
    private void validate(Uri uri) throws FileNotFoundException {
        if(!"content".equals(uri.getScheme())||!"dev.qingframe.test.fixture".equals(uri.getAuthority())||
            !("/outer".equals(uri.getPath())||"/redmi".equals(uri.getPath()))||uri.getQuery()!=null||uri.getFragment()!=null)
            throw new FileNotFoundException("Only original outer/redmi fixtures exported");
    }
    private byte[] bytes(Uri uri) throws Exception {
        try(InputStream input=getContext().createPackageContext("dev.qingframe",0).getAssets().open("/redmi".equals(uri.getPath())?"fixtures/redmi.png":"fixtures/outer.png");ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[16384];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);return output.toByteArray();
        }
    }
    @Override public String getType(Uri uri){try{validate(uri);return "image/png";}catch(FileNotFoundException failure){throw new IllegalArgumentException(failure);}}
    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){
        try {
            validate(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;
            Object[] values=new Object[columns.length];int size=bytes(uri).length;
            for(int i=0;i<columns.length;i++){
                if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))values[i]="qingframe-original-"+uri.getLastPathSegment()+".png";
                else if(OpenableColumns.SIZE.equals(columns[i]))values[i]=size;
                else throw new IllegalArgumentException("Unsupported column");
            }
            MatrixCursor cursor=new MatrixCursor(columns);cursor.addRow(values);return cursor;
        }catch(Exception failure){throw new IllegalArgumentException(failure);}
    }
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException {
        validate(uri);if(!"r".equals(mode))throw new FileNotFoundException("Read-only fixture");
        try {
            byte[] data=bytes(uri);ShareProbeActivity.event(getContext(),"share-probe-events.jsonl",new JSONObject().put("event","OPEN")
                .put("providerUid",Process.myUid()).put("callerUid",Binder.getCallingUid()).put("fixture",uri.getLastPathSegment()).put("bytes",data.length));
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createPipe();
            new Thread(()->{try(ParcelFileDescriptor.AutoCloseOutputStream stream=new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])){stream.write(data);}catch(Exception error){Log.w("QF_SHARE_PROBE","Receiver closed original pipe",error);}},"qf-original-fixture").start();
            return pipe[0];
        }catch(Exception failure){FileNotFoundException error=new FileNotFoundException("Original fixture unavailable");error.initCause(failure);throw error;}
    }
    @Override public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException("Read-only fixture");}
    @Override public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException("Read-only fixture");}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException("Read-only fixture");}
}
