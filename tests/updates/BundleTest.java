import app.morphe.patcher.patch.*;
import ca.mlkem.updates.StartupUpdatesKt;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** Verify the actual loaded patch graph and final manifest transformation. */
public class BundleTest {
    static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    static void walk(Patch<?> patch,Set<Patch<?>> seen){if(seen.add(patch))for(Patch<?> d:patch.getDependencies())walk(d,seen);}
    public static void main(String[] args)throws Exception{
        Set<Patch<?>> patches=PatchKt.loadPatchesFromJar(Collections.singleton(new File(args[0])));
        int count=0;
        for(Patch<?> p:patches){
            Set<Patch<?>> closure=new HashSet<>();walk(p,closure);
            long providers=closure.stream().filter(d->d == StartupUpdatesKt.getStartupUpdates()).count();
            require(providers==(p.getName().equals("Merge XAPK only")?0:1),"Startup dependency: "+p.getName()+" count="+providers);count++;
        }
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        for(String pkg:new String[]{"ca.walmart.ecommerceapp","com.google.android.gm","app.morphe.android.gm"}){
            String xml="<manifest xmlns:android='http://schemas.android.com/apk/res/android' package='"+pkg+"'><application android:process=':host'><provider android:name='original.Provider' android:authorities='original'/></application></manifest>";
            Document doc=factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            StartupUpdatesKt.installUpdateProvider(doc);StartupUpdatesKt.installUpdateProvider(doc);
            require(doc.getElementsByTagName("provider").getLength()==2,"Duplicate provider");
            require(doc.getElementsByTagName("uses-permission").getLength()==1,"Duplicate permission");
            Element provider=(Element)doc.getElementsByTagName("provider").item(1);
            String ns="http://schemas.android.com/apk/res/android";
            require(provider.getAttributeNS(ns,"authorities").equals(pkg+".mlkem.startupupdates"),"Clone authority");
            require(provider.getAttributeNS(ns,"exported").equals("false"),"Provider exported");
            require(!provider.hasAttributeNS(ns,"process"),"Provider must inherit host process");
        }
        System.out.println("PASS "+count+" patch dependency graphs; manifest idempotence and clone authorities");
    }
}
