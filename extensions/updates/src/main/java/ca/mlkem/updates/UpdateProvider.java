package ca.mlkem.updates;

import android.app.Application;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;

/** Non-exported initialization only. Exposes no data and starts no background service. */
public final class UpdateProvider extends ContentProvider {
    @Override public boolean onCreate(){
        if(Build.VERSION.SDK_INT>=26)try{
            Context context=getContext();Context app=context==null?null:context.getApplicationContext();
            if(app instanceof Application)StartupUpdates.install((Application)app);
        }catch(Exception | LinkageError ignored){} // The host must still open if checking is unavailable.
        return true;
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String o){return null;}
    @Override public String getType(Uri u){return null;}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
