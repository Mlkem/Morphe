package ca.mlkem.updates;

import org.json.*;
import java.time.Instant;
import java.util.regex.Pattern;

/** Pure decision logic: app releases and patch compatibility are separate facts. */
public final class UpdatePolicy {
    public static final long DAY=24*60*60*1000L,MAX_CACHE_AGE=7*DAY;
    private static final Pattern HASH=Pattern.compile("[a-f0-9]{64}");
    public static final class Notice {
        public final String title,message,key;public final boolean repatch;
        Notice(String title,String message,String key,boolean repatch){this.title=title;this.message=message;this.key=key;this.repatch=repatch;}
    }
    static long time(String value){try{return Instant.parse(value).toEpochMilli();}catch(Exception e){return 0;}}
    public static Notice evaluate(String json,String packageName,String installed,String revision,long builtAt,long now)throws JSONException {
        JSONObject feed=new JSONObject(json);if(feed.optInt("schema")!=1)return null;
        JSONObject apps=feed.optJSONObject("apps"),app=apps==null?null:apps.optJSONObject(packageName);if(app==null)return null;
        String remoteRevision=app.optString("revision"),name=app.optString("name",packageName);
        if(!HASH.matcher(remoteRevision).matches()||!HASH.matcher(revision).matches()||name.length()>120)return null;
        JSONArray targets=app.optJSONArray("supportedVersions");if(targets==null||targets.length()>100)return null;
        String newest=installed;boolean currentSupported=false;
        for(int i=0;i<targets.length();i++){
            Object raw=targets.opt(i);if(!(raw instanceof String))continue;String candidate=(String)raw;
            currentSupported|=installed.equals(candidate);
            if(Versions.newer(candidate,newest))newest=candidate;
        }
        long published=time(feed.optString("generatedAt"));
        if(published<=0||published>now+5*60*1000)return null;
        boolean newerApp=Versions.newer(newest,installed);
        boolean newPatches=currentSupported&&!remoteRevision.equals(revision)&&published>=builtAt&&published<=now+5*60*1000;
        JSONObject upstream=app.optJSONObject("upstream");String reported="",source="";
        if(upstream!=null&&"ok".equals(upstream.optString("status"))){
            long checked=time(upstream.optString("checkedAt"));String candidate=upstream.optString("version");
            source=upstream.optString("source");
            if(("Google Play".equals(source)||"GitHub".equals(source))&&checked>0&&now>=checked-5*60*1000&&now-checked<=MAX_CACHE_AGE&&Versions.newer(candidate,installed))reported=candidate;
        }
        if(newerApp||newPatches){
            String body=newerApp?"Installed: "+installed+"\nNewly supported: "+newest+"\n\nOpen Morphe to review your patch selection and repatch the original APK.":"Updated patches are available for "+name+" "+installed+".\n\nOpen Morphe to refresh your source and repatch the original APK.";
            if(!reported.isEmpty()&&Versions.newer(reported,newest))body+="\n\n"+source+" also lists "+reported+". Support for that release is not yet listed.";
            return new Notice("Ready to review in Morphe",body,"ready:"+newest+":"+remoteRevision,true);
        }
        if(!reported.isEmpty())return new Notice("New "+name+" release",source+" lists "+reported+"; you have "+installed+".\n\nPatch support for this release is not yet listed. Keep your current app until compatible patches and a matching original APK are available.","upstream:"+reported,false);
        return null;
    }
    public static boolean due(long now,long last){return last<=0||now<last||now-last>=DAY;}
}
