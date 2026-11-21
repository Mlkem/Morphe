package ca.mlkem.niagara;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.os.UserHandle;
import java.lang.reflect.*;
import java.util.*;

/** Reviewed Niagara 1.16.37 / 1643 ABI. Every write uses native repositories/feeds. */
public final class NativeBridge {
    public interface Done { void complete(Object result, Throwable error); }
    public static final String CONTROLLER = "b.uXEn98dBZyHFNcs4SNt";
    public static final String ITEM = "b.I60ZWJ2JJQswp5ncJqdmpckTw";
    private final Object controller, repository, feeds;
    public NativeBridge(Object owner) throws Exception {
        controller = owner;
        repository = field(owner, "S3wX241M0r7dtme");
        feeds = field(owner, "T6TlZjUmR83SSjbLrA");
    }
    public boolean ready() throws Exception {
        return ((SharedPreferences) field(repository, "F0o2ptD8ZHQLbx1bWQj"))
            .getInt("bitpit.launcher.key.BOOT_STATE", 0) == 1;
    }
    public static Class<?> type(String name) throws Exception { return Class.forName(name, false, NativeBridge.class.getClassLoader()); }
    public static Object field(Object target, String name) throws Exception {
        return target.getClass().getField(name).get(target);
    }
    public static void put(Object target, String name, Object value) throws Exception { target.getClass().getField(name).set(target, value); }
    private static Method method(Class<?> owner, String name, Object[] args) throws Exception {
        Method found = null;
        for (Method m : owner.getMethods()) {
            if (!m.getName().equals(name) || m.getParameterTypes().length != args.length) continue;
            Class<?>[] types = m.getParameterTypes(); boolean fits = true;
            for (int i = 0; i < types.length; i++) {
                Class<?> t = types[i];
                if (t == int.class) t = Integer.class; else if (t == long.class) t = Long.class; else if (t == boolean.class) t = Boolean.class;
                if (args[i] == null ? types[i].isPrimitive() : !t.isInstance(args[i])) { fits = false; break; }
            }
            if (fits) { if (found != null) throw new NoSuchMethodException("Ambiguous " + name); found = m; }
        }
        if (found == null) throw new NoSuchMethodException(owner.getName() + "." + name);
        return found;
    }
    public static Object call(Object target, String name, Object... args) throws Exception { return method(target.getClass(), name, args).invoke(target, args); }
    public static Object callStatic(String owner, String name, Object... args) throws Exception { return method(type(owner), name, args).invoke(null, args); }
    public static Object emptyContext() throws Exception { return type("b.u6kWpMmoNHeAi5BeY9jj").getField("RIsor1V85WbjA").get(null); }
    public static Object unit() throws Exception { return type("b.H6XY97VfBd").getField("QkO0iYBXnHfGs").get(null); }
    private static Object proxy(String iface, InvocationHandler handler) throws Exception {
        return Proxy.newProxyInstance(NativeBridge.class.getClassLoader(), new Class<?>[]{type(iface)}, handler);
    }
    public static Object terminal() throws Exception {
        return proxy("b.UmorjzOdXluhk2", (p, m, a) -> {
            if (m.getName().equals("rNimljjbL3")) return emptyContext();
            if (m.getName().equals("DXrRefnGncAQzaLktSL")) return null;
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return "AutoFolders continuation";
        });
    }
    @SuppressWarnings("unchecked") public List<Object> apps() throws Exception { return (List<Object>) call(repository, "CGIzpDMt4IosrEXJSXs"); }
    @SuppressWarnings("unchecked") public List<Object> folders() throws Exception {
        List<Object> result = new ArrayList<>();
        for (Object x : (List<Object>) call(repository, "jKmfyue9uxluWGPwaMZ"))
            if (((Number) field(x, "cZSAj6Yk6k2hoQSAAr0pYx9S")).intValue() == 3) result.add(x);
        return result;
    }
    public static Context context(Object app) throws Exception { return (Context) field(app, "dumwb6g03n1L1jCMzP9"); }
    public static Object identity(Object item) throws Exception { return field(item, "T6TlZjUmR83SSjbLrA"); }
    public static int id(Object item) throws Exception { return ((Number) field(identity(item), "QkO0iYBXnHfGs")).intValue(); }
    public static String label(Object item) throws Exception { return (String) field(item, "S3wX241M0r7dtme"); }
    public static ComponentName component(Object app) throws Exception { return (ComponentName) field(field(app, "BLDdEWAqgmSgtBKrBHAE"), "QkO0iYBXnHfGs"); }
    public static UserHandle user(Object app) throws Exception { return (UserHandle) field(field(app, "BLDdEWAqgmSgtBKrBHAE"), "C07CQRD69zoXzbLCDIVboCy"); }
    @SuppressWarnings("unchecked") public Collection<Object> hidden() throws Exception { return (Collection<Object>) field(feeds, "Oi8EEimW6PCnY9wobOiEmnRL"); }
    @SuppressWarnings("unchecked") public Collection<Object> favorites() throws Exception { return (Collection<Object>) field(feeds, "F0zsLmDyNP46xTEokim0"); }
    @SuppressWarnings("unchecked") public Collection<Object> contents(Object folder) throws Exception { return (Collection<Object>) call(feeds, "DXrRefnGncAQzaLktSL", folder); }
    public Object findFolder(int id, String title) throws Exception {
        Object named = null;
        for (Object folder : folders()) {
            if (id > 0 && id(folder) == id) return folder;
            if (label(folder).equalsIgnoreCase(title) && named == null) named = folder;
        }
        return named;
    }
    public void createFolder(String title, Done done) throws Exception {
        Object factory = call(field(controller, "wU9F7hXH2h8KnHbCWy3Orm8APDQD"), "QkO0iYBXnHfGs");
        Object normalizer = field(factory, "CGIzpDMt4IosrEXJSXs");
        Object normalized = call(normalizer, "QkO0iYBXnHfGs", title);
        Object section = call(normalizer, "C07CQRD69zoXzbLCDIVboCy", title);
        long serial = ((Number) field(field(factory, "kHCf7TXmMVX5vxaPS"), "XsWJYGAdKRv0cKLyNiHqk")).longValue();
        Constructor<?> ctor = type("b.oQcyEm0ip6kPEdWWSQwBH1uEgiSl").getConstructor(String.class, String.class, long.class,
            String.class, String.class, String.class, String.class, type("b.mRNWiSrsK565ZtCunmotpr"), String.class,
            int.class, byte[].class, long.class, int.class, int.class, int.class, String.class, String.class, int.class, int.class);
        Object entity = ctor.newInstance("", "", serial, "", "", "", title, normalized, section, 2, null,
            System.currentTimeMillis(), 3, 0, 0, null, null, 0, 3805440);
        put(entity, "XxI9Vaz7yJV4E1n", 0xff4b8076);
        Object action = proxy("b.vWXwYhWM2HZN", (p, m, a) -> {
            if (m.getName().equals("QkO0iYBXnHfGs")) return call(a[0], "kHCf7TXmMVX5vxaPS", entity);
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            return "AutoFolders create";
        });
        NativeContinuation continuation = new NativeContinuation(done);
        Object result = call(repository, "C07CQRD69zoXzbLCDIVboCy", 12, action, continuation);
        Object suspended = type("b.bU2em0LRuS4u53QWXuuK").getField("RIsor1V85WbjA").get(null);
        if (result != suspended) continuation.deliver(result);
    }
    /** Read through Niagara's DAO after its asynchronous feed save has committed. */
    public boolean persisted(Collection<Object> feed, Object item) throws Exception {
        Object dao = field(feeds, "T6TlZjUmR83SSjbLrA");
        Object query = type("b.qBlsmBnzmgtZe0gU3u2b").getConstructor(int.class, int.class, int.class)
            .newInstance(field(feed, "T6TlZjUmR83SSjbLrA"), field(feed, "S3wX241M0r7dtme"), 6);
        Object rows = callStatic("b.qsNmvj5VLDnsPcq4Ge", "kHCf7TXmMVX5vxaPS", field(dao, "QkO0iYBXnHfGs"), true, false, query);
        Object iid = identity(item);
        for (Object row : (List<?>) rows) if (iid.equals(field(row, "R22npmuNnAlm"))) return true;
        return false;
    }
    public static void settings(Activity activity, Object builder) {
        try {
            Object row = type("b.SVteKkH3agn3HfStY").getConstructor(String.class).newInstance("mlkem_auto_folders");
            // Use Niagara's own Folders drawable in the native preference icon slot.
            int folderIcon = activity.getResources().getIdentifier("ic_ip_act_folder", "drawable", activity.getPackageName());
            if (folderIcon != 0) put(row, "CGIzpDMt4IosrEXJSXs", folderIcon);
            put(row, "R22npmuNnAlm", "Auto folders");
            put(row, "DXrRefnGncAQzaLktSL", "Sort new apps into folders and hide their separate icons");
            put(row, "zb6WeGAkeFk01GxkRZTwjyHAD", proxy("b.fkQ3p3udaQCrDLpFpQUM2PoTI", (p, m, a) -> {
                // The setting exposes a single two-argument click callback on both releases.
                if (m.getDeclaringClass() != Object.class && m.getParameterTypes().length == 2 && m.getReturnType() == boolean.class) {
                    AutoFolders.showSettings(activity);
                    return true;
                }
                if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                if (m.getName().equals("equals")) return p == a[0];
                return "AutoFolders settings";
            }));
            call(builder, "QkO0iYBXnHfGs", row);
        } catch (Throwable error) { AutoFolders.error(error); }
    }
}
