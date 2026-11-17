package ca.mlkem.niagara;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

/** Event-driven sorter. Only native, persisted folder entries may be auto-hidden. */
public final class AutoFolders {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static NativeBridge bridge;
    private static Context context;
    private static SharedPreferences prefs;
    private static boolean scanning, running, scanAgain, includeExisting;
    private static final ArrayDeque<Job> queue = new ArrayDeque<>();
    private static final Set<String> queued = new HashSet<>();
    private static String status = "Ready. New installations are sorted automatically.";
    private static final Runnable SCAN = AutoFolders::scan;
    private AutoFolders() {}
    static final class Job {
        final Object app; final String key; final int category;
        Job(Object item, String identity, int group) { app = item; key = identity; category = group; }
    }
    public static void init(Context value) {
        if (context != null) return;
        context = value.getApplicationContext();
        prefs = context.getSharedPreferences("mlkem_auto_folders", Context.MODE_PRIVATE);
        if (!prefs.contains("since")) prefs.edit().putLong("since", System.currentTimeMillis()).commit();
    }
    public static void attach(Object owner) {
        MAIN.post(() -> {
            try {
                bridge = new NativeBridge(owner);
                Object factory = NativeBridge.call(NativeBridge.field(owner, "wU9F7hXH2h8KnHbCWy3Orm8APDQD"), "QkO0iYBXnHfGs");
                init((Context) NativeBridge.field(factory, "QkO0iYBXnHfGs"));
                requestScan();
            } catch (Throwable error) { error(error); }
        });
    }
    public static void resume(Activity activity) { init(activity); requestScan(); }
    public static void changed() { requestScan(); }
    public static void settings(Object builder) {
        try { Activity activity = (Activity) NativeBridge.field(builder, "i31xkztVRqgXM6VmkhJG3j1ad1OM"); init(activity); NativeBridge.settings(activity, builder); }
        catch (Throwable failure) { error(failure); }
    }
    private static void requestScan() {
        MAIN.post(() -> { MAIN.removeCallbacks(SCAN); MAIN.postDelayed(SCAN, 700); });
    }
    static void error(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) cause = cause.getCause();
        status = "Sorting paused for an item: " + cause.getClass().getSimpleName() + ". Its icon stays visible; reopen Home to retry.";
        Log.e("NiagaraAutoFolders", "Auto folders operation failed", failure);
    }
    private static boolean enabled(int category) { return prefs.getBoolean("enabled", true) && prefs.getBoolean("category." + Categories.IDS[category], true); }
    private static void scan() {
        if (bridge == null || prefs == null) return;
        if (running || scanning) { scanAgain = true; return; }
        try {
            if (!bridge.ready()) {
                status = "Finish Niagara's initial setup to start automatic sorting.";
                return;
            }
            recoverRemovedFolders();
            List<Object> apps = bridge.apps();
            Set<Object> hidden = new HashSet<>(bridge.hidden()), favorites = new HashSet<>(bridge.favorites());
            boolean existing = includeExisting; includeExisting = false;
            long since = existing ? 0 : prefs.getLong("since", Long.MAX_VALUE);
            scanning = true;
            IO.execute(() -> {
                List<Job> jobs = new ArrayList<>();
                for (Object app : apps) {
                    try {
                        if (!Process.myUserHandle().equals(NativeBridge.user(app))) continue;
                        String pkg = NativeBridge.component(app).getPackageName();
                        if (pkg.equals(context.getPackageName())) continue;
                        PackageInfo info = context.getPackageManager().getPackageInfo(pkg, 0);
                        ApplicationInfo ai = info.applicationInfo;
                        if (ai == null) continue;
                        String key = NativeBridge.component(app).flattenToString() + "@" + info.firstInstallTime;
                        int category = Categories.classify(ai.category, ai.flags, pkg, NativeBridge.label(app));
                        if (Categories.eligible(info.firstInstallTime, since, prefs.getBoolean("enabled", true),
                                prefs.getBoolean("category." + Categories.IDS[category], true),
                                !existing && prefs.contains("done." + key), hidden.contains(app), favorites.contains(app), true))
                            jobs.add(new Job(app, key, category));
                    } catch (android.content.pm.PackageManager.NameNotFoundException removed) {
                        // Uninstalled between the native snapshot and PackageManager query.
                    } catch (Throwable failure) { error(failure); }
                }
                MAIN.post(() -> {
                    scanning = false;
                    for (Job job : jobs) if (queued.add(job.key)) queue.add(job);
                    next();
                });
            });
        } catch (Throwable failure) { scanning = false; error(failure); }
    }
    private static void next() {
        if (running) return;
        Job job = queue.poll();
        if (job == null) {
            if (scanAgain) { scanAgain = false; requestScan(); }
            return;
        }
        running = true;
        try {
            if (!enabled(job.category) || bridge.hidden().contains(job.app) || bridge.favorites().contains(job.app)) { finish(job); return; }
            String title = Categories.NAMES[job.category];
            Object folder = bridge.findFolder(prefs.getInt("folder." + Categories.IDS[job.category], 0), title);
            if (folder != null) place(job, folder);
            else bridge.createFolder(title, (created, failure) -> MAIN.post(() -> {
                if (failure != null || created == null) { error(failure != null ? failure : new IllegalStateException("Folder creation returned no folder")); finish(job); }
                else place(job, created);
            }));
        } catch (Throwable failure) { error(failure); finish(job); }
    }
    private static void place(Job job, Object folder) {
        try {
            if (!enabled(job.category)) { finish(job); return; }
            prefs.edit().putInt("folder." + Categories.IDS[job.category], NativeBridge.id(folder)).apply();
            // Favorites works with basic pop-up folders, and guarantees access even
            // when Pro app-list folders are unavailable. Reuse an existing folder.
            Collection<Object> favorites = bridge.favorites();
            Collection<Object> contents = bridge.contents(folder);
            if (contents == null) throw new IllegalStateException("Folder has no native feed");
            favorites.add(folder);
            contents.add(job.app);
            awaitSaved(job, folder, contents, favorites, 0);
        } catch (Throwable failure) { error(failure); finish(job); }
    }
    private static void awaitSaved(Job job, Object folder, Collection<Object> contents, Collection<Object> favorites, int attempt) {
        IO.execute(() -> {
            boolean saved = false; Throwable error = null;
            try { saved = bridge.persisted(contents, job.app) && bridge.persisted(favorites, folder); }
            catch (Throwable failure) { error = failure; }
            final boolean persisted = saved; final Throwable failure = error;
            MAIN.post(() -> {
                try {
                    if (!enabled(job.category) || !contents.contains(job.app) || !favorites.contains(folder)) { finish(job); return; }
                    if (failure != null) { error(failure); finish(job); return; }
                    if (!persisted) {
                        if (attempt < 20) MAIN.postDelayed(() -> awaitSaved(job, folder, contents, favorites, attempt + 1), 250);
                        else { error(new IllegalStateException("Folder save was not confirmed")); finish(job); }
                        return;
                    }
                    // Write ownership first. If killed before hiding, recovery can
                    // safely remove this entry. Never claim manually hidden apps.
                    if (!bridge.hidden().contains(job.app) && !bridge.favorites().contains(job.app)) {
                        String record = NativeBridge.id(job.app) + ":" + NativeBridge.id(folder) + ":" + job.category;
                        if (!prefs.edit().putString("managed." + job.key, record).commit()) throw new IllegalStateException("Could not save undo record");
                        bridge.hidden().add(job.app);
                    }
                    prefs.edit().putBoolean("done." + job.key, true).apply();
                    status = "Sorted " + NativeBridge.label(job.app) + " into " + Categories.NAMES[job.category] + ".";
                    finish(job);
                } catch (Throwable problem) { error(problem); finish(job); }
            });
        });
    }
    private static void finish(Job job) { queued.remove(job.key); running = false; MAIN.post(AutoFolders::next); }
    /** Undo only owned hides. Deleting a folder never leaves its apps stranded. */
    private static void recoverRemovedFolders() throws Exception {
        Map<Integer, Object> apps = new HashMap<>(), folders = new HashMap<>();
        for (Object app : bridge.apps()) apps.put(NativeBridge.id(app), app);
        for (Object folder : bridge.folders()) folders.put(NativeBridge.id(folder), folder);
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            if (!entry.getKey().startsWith("managed.")) continue;
            String[] record = String.valueOf(entry.getValue()).split(":");
            if (record.length != 3) continue;
            Object app = apps.get(Integer.parseInt(record[0])), folder = folders.get(Integer.parseInt(record[1]));
            int category = Integer.parseInt(record[2]);
            if (app == null) { prefs.edit().remove(entry.getKey()).apply(); continue; }
            // Check component identity as well as the native row id (id reuse).
            if (!entry.getKey().substring(8).startsWith(NativeBridge.component(app).flattenToString() + "@")) {
                prefs.edit().remove(entry.getKey()).apply(); continue;
            }
            boolean restore = !enabled(category) || folder == null || !bridge.contents(folder).contains(app) || !bridge.favorites().contains(folder);
            if (restore) { bridge.hidden().remove(app); prefs.edit().remove(entry.getKey()).apply(); }
        }
    }
    private static void restoreAll() {
        if (bridge == null) return;
        try {
            Map<Integer, Object> apps = new HashMap<>();
            for (Object app : bridge.apps()) apps.put(NativeBridge.id(app), app);
            for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                if (!entry.getKey().startsWith("managed.")) continue;
                String[] record = String.valueOf(entry.getValue()).split(":");
                Object app = apps.get(Integer.parseInt(record[0]));
                if (app != null && entry.getKey().substring(8).startsWith(NativeBridge.component(app).flattenToString() + "@")) bridge.hidden().remove(app);
                prefs.edit().remove(entry.getKey()).apply();
            }
            status = "Restored icons hidden by Auto folders. Folder contents were kept.";
        } catch (Throwable failure) { error(failure); }
    }
    public static void showSettings(Activity activity) {
        init(activity);
        LinearLayout body = new LinearLayout(activity); body.setOrientation(LinearLayout.VERTICAL);
        int padding = (int)(20 * activity.getResources().getDisplayMetrics().density);
        body.setPadding(padding, padding / 2, padding, padding / 2);
        TextView explanation = new TextView(activity);
        explanation.setText("New apps go into native folders in Favorites. Their separate icons are hidden only after the folder is saved. Favorites, manually hidden apps, and other profiles are preserved.\n\nCategories use Android's app metadata. Apps without a category go into Other Apps. Switching a category off restores icons hidden by that category.\n");
        body.addView(explanation);
        Switch master = addSwitch(activity, body, "Automatic sorting", "enabled");
        for (int i = 0; i < Categories.IDS.length; i++) addSwitch(activity, body, Categories.NAMES[i], "category." + Categories.IDS[i]);
        Button sort = new Button(activity); sort.setText("Sort existing apps now");
        sort.setOnClickListener(v -> { includeExisting = true; requestScan(); Toast.makeText(activity, "Sorting enabled categories; favorites stay as they are.", Toast.LENGTH_LONG).show(); }); body.addView(sort);
        Button restore = new Button(activity); restore.setText("Restore automatically hidden icons");
        restore.setOnClickListener(v -> { master.setChecked(false); restoreAll(); Toast.makeText(activity, "Icons restored. Automatic sorting is now off.", Toast.LENGTH_LONG).show(); }); body.addView(restore);
        TextView state = new TextView(activity); state.setText(bridge == null ? "Return to the Niagara home screen once to start sorting." : status); body.addView(state);
        ScrollView scroll = new ScrollView(activity); scroll.addView(body);
        new AlertDialog.Builder(activity).setTitle("Auto folders").setView(scroll).setPositiveButton("Done", null).show();
    }
    private static Switch addSwitch(Activity activity, LinearLayout parent, String label, String key) {
        Switch toggle = new Switch(activity); toggle.setText(label); toggle.setMinHeight((int)(48 * activity.getResources().getDisplayMetrics().density));
        toggle.setChecked(prefs.getBoolean(key, true));
        toggle.setOnCheckedChangeListener((view, enabled) -> {
            prefs.edit().putBoolean(key, enabled).apply();
            if (!enabled && bridge != null) try { recoverRemovedFolders(); } catch (Throwable failure) { error(failure); }
            requestScan();
        }); parent.addView(toggle); return toggle;
    }
}
