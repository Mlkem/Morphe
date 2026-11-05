package ca.mlkem.updates;

import android.app.*;
import android.content.*;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.function.LongSupplier;
import javax.net.ssl.HttpsURLConnection;

/** One daily, asynchronous check on a foreground screen. No keyboard/service prompts. */
public final class StartupUpdates implements Application.ActivityLifecycleCallbacks {
    static final String FEED = "https://raw.githubusercontent.com/Mlkem/Morphe/dev/updates/app-feed.json";
    static final String PREFS = "mlkem_startup_updates_v1";
    static final int MAX_BYTES = 128 * 1024;
    interface Fetcher { String fetch() throws Exception; }
    private static StartupUpdates instance;
    private final SharedPreferences prefs;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Executor worker;
    private final Fetcher fetcher;
    private final LongSupplier clock;
    private final String packageName, version, revision;
    private final long builtAt;
    private WeakReference<Activity> current = new WeakReference<>(null);
    private WeakReference<Activity> dialogOwner = new WeakReference<>(null);
    private WeakReference<AlertDialog> dialog = new WeakReference<>(null);
    private boolean busy;
    private UpdatePolicy.Notice pending;

    // Injected dependencies keep network and lifecycle regressions deterministic in tests.
    StartupUpdates(Application app, Executor worker, Fetcher fetcher, LongSupplier clock,
            String packageName, String version, String revision, long builtAt) {
        prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        this.worker = worker;
        this.fetcher = fetcher;
        this.clock = clock;
        this.packageName = packageName;
        this.version = version;
        this.revision = revision;
        this.builtAt = builtAt;
    }

    public static synchronized void install(Application app) {
        if (instance != null || UpdateConfig.packageName().isEmpty()) return;
        Executor worker = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mlkem-update-check");
            thread.setDaemon(true);
            return thread;
        });
        instance = new StartupUpdates(app, worker, StartupUpdates::download,
                System::currentTimeMillis, UpdateConfig.packageName(), UpdateConfig.version(),
                UpdateConfig.revision(), Long.parseLong(UpdateConfig.builtAt()));
        app.registerActivityLifecycleCallbacks(instance);
    }

    void check() {
        Activity activity = current.get();
        if (busy || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        long now = clock.getAsLong();
        boolean due = UpdatePolicy.due(now, prefs.getLong("attempt", 0));
        long fetched = prefs.getLong("fetched", 0);
        if (!due && (fetched <= 0 || fetched > now || now - fetched > UpdatePolicy.MAX_CACHE_AGE)) return;
        busy = true;
        if (due) prefs.edit().putLong("attempt", now).apply();
        worker.execute(() -> {
            UpdatePolicy.Notice result = null;
            try {
                String json = due ? fetcher.fetch() : prefs.getString("feed", "");
                result = UpdatePolicy.evaluate(json, packageName, version, revision, builtAt, clock.getAsLong());
                if (due) prefs.edit().putString("feed", json).putLong("fetched", clock.getAsLong()).apply();
            } catch (Exception ignored) {
                // Offline, malformed and withheld metadata never become an update claim.
            }
            final UpdatePolicy.Notice notice = result;
            main.post(() -> {
                busy = false;
                pending = notice;
                main.postDelayed(this::showPending, 1500);
            });
        });
    }

    static String download() throws IOException {
        HttpsURLConnection connection = (HttpsURLConnection) new URL(FEED).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "mlkemac-update-check/1");
        long deadline = SystemClock.elapsedRealtime() + 15000;
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Feed unavailable");
            if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("Feed too large");
            try (InputStream input = connection.getInputStream(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int size;
                while ((size = input.read(buffer)) != -1) {
                    if (output.size() + size > MAX_BYTES || SystemClock.elapsedRealtime() > deadline)
                        throw new IOException("Feed too large or too slow");
                    output.write(buffer, 0, size);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    void showPending() {
        Activity activity = current.get();
        UpdatePolicy.Notice notice = pending;
        long now = clock.getAsLong();
        AlertDialog visible = dialog.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed() ||
                !activity.hasWindowFocus() || notice == null || (visible != null && visible.isShowing())) return;
        long remindAfter = prefs.getLong("remind_after", 0);
        if (notice.key.equals(prefs.getString("ignored", "")) ||
                (now < remindAfter && remindAfter - now <= UpdatePolicy.DAY)) return;
        try {
            boolean dark = (activity.getResources().getConfiguration().uiMode &
                    Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            AlertDialog alert = new AlertDialog.Builder(activity, dark ?
                    android.R.style.Theme_Material_Dialog_Alert : android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setTitle(notice.title).setMessage(notice.message)
                    .setPositiveButton("Open Morphe", (d, w) -> openManager(activity))
                    .setNegativeButton("Later", (d, w) -> {})
                    .setNeutralButton("Ignore this update", (d, w) ->
                            prefs.edit().putString("ignored", notice.key).apply()).create();
            alert.show();
            prefs.edit().putLong("remind_after", now + UpdatePolicy.DAY).apply();
            dialog = new WeakReference<>(alert);
            dialogOwner = new WeakReference<>(activity);
            pending = null;
        } catch (RuntimeException ignored) {
            // A closing host screen must never be prevented from closing.
        }
    }

    static void openManager(Context context) {
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN).setComponent(
                    new ComponentName("app.morphe.manager", "app.morphe.manager.MainActivity"));
            context.startActivity(intent);
        } catch (RuntimeException unavailable) {
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Mlkem/Morphe/releases")));
            } catch (RuntimeException ignored) {}
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        current = new WeakReference<>(activity);
        main.postDelayed(this::check, 1500);
        main.postDelayed(this::showPending, 2000);
    }
    @Override public void onActivityPaused(Activity activity) {
        if (current.get() == activity) current.clear();
        if (dialogOwner.get() == activity) {
            AlertDialog visible = dialog.get();
            if (visible != null) visible.dismiss();
            dialog.clear();
            dialogOwner.clear();
        }
    }
    @Override public void onActivityDestroyed(Activity activity) { onActivityPaused(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
}
