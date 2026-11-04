package ca.mlkem.gmail;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Only the OneGoogle account menu calls filter. Never intercept AccountManager or mail data. */
public final class AccountVisibility {
    private static final int ITEM = 0x6d6c6701;
    private static final String PREFS = "mlkem_account_visibility";
    private static final String HIDDEN = "hidden_accounts";
    private static final Map<Activity, Long> revisions = new WeakHashMap<>();
    private static volatile Context context;
    private static volatile List<Account> accounts = new ArrayList<>();
    private static volatile Account selected;
    private static final Application.ActivityLifecycleCallbacks lifecycle = new Application.ActivityLifecycleCallbacks() {
        @Override public void onActivityCreated(Activity a, Bundle b) {}
        @Override public void onActivityStarted(Activity a) {}
        @Override public void onActivityResumed(Activity a) {}
        @Override public void onActivityPostResumed(Activity a) { resumed(a); }
        @Override public void onActivityPaused(Activity a) {}
        @Override public void onActivityStopped(Activity a) {}
        @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
        @Override public void onActivityDestroyed(Activity a) { revisions.remove(a); }
    };

    private AccountVisibility() {}

    /** Adapter fallback also lets tests exercise wrapped host accounts, not just Android Account. */
    public interface Adapter {
        String id(Object value);
        String email(Object value);
    }

    // Patch-time bridges delegate to the model's own OneGoogle converter (d/f).
    private static String accountId(Object converter, Object value) {
        return converter instanceof Adapter ? ((Adapter) converter).id(value) : null;
    }
    private static String accountEmail(Object converter, Object value) {
        return converter instanceof Adapter ? ((Adapter) converter).email(value) : null;
    }

    static Account account(Object value, Object converter) {
        if (value instanceof Account) return (Account) value;
        if (value == null || converter == null) return null;
        try {
            String id = accountId(converter, value);
            String email = accountEmail(converter, value);
            if (id == null || id.isEmpty() || email == null || email.isEmpty()) return null;
            // Local UI identity only. Never register this synthetic Account with Android.
            return new Account(email, "onegoogle:" + id);
        } catch (RuntimeException unavailable) {
            return null; // Preserve an unknown/unavailable row rather than hiding or crashing it.
        }
    }

    public static void initialize(Activity activity) {
        context = activity.getApplicationContext();
        if (!revisions.containsKey(activity)) activity.registerActivityLifecycleCallbacks(lifecycle);
        revisions.put(activity, revision());
    }

    public static void resumed(Activity activity) {
        Long previous = revisions.put(activity, revision());
        if (previous != null && previous != revision() && !activity.isFinishing()) activity.recreate();
    }

    public static void attach(View avatar) {
        context = avatar.getContext().getApplicationContext();
        avatar.setOnLongClickListener(view -> {
            Context current = view.getContext();
            while (current instanceof android.content.ContextWrapper && !(current instanceof Activity)) {
                Context next = ((android.content.ContextWrapper) current).getBaseContext();
                if (next == current) break;
                current = next;
            }
            if (!(current instanceof Activity)) return false;
            show((Activity) current);
            return true;
        });
    }

    /** Preserve order, identity and unknown account kinds; always keep the selected account. */
    public static List<?> filter(List<?> source, Object current, Object converter) {
        if (source == null) return null;
        Account active = account(current, converter);
        List<Account> known = new ArrayList<>();
        for (Object item : source) {
            Account a = account(item, converter);
            if (a != null && !known.contains(a)) known.add(a);
        }
        accounts = known;
        selected = active;
        if (context == null) return source;
        Set<String> hidden = hidden();
        if (hidden.isEmpty()) return source;
        List<Object> visible = new ArrayList<>();
        for (Object item : source) {
            Account a = account(item, converter);
            if (a == null || a.equals(active) || !hidden.contains(key(a))) visible.add(item);
        }
        // Stale preferences or account removals must never make the switcher empty.
        if (visible.isEmpty() && !source.isEmpty()) visible.add(source.get(0));
        return visible;
    }

    private static SharedPreferences prefs() { return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    private static long revision() { return context == null ? 0 : prefs().getLong("revision", 0); }
    private static Set<String> hidden() { return new HashSet<>(prefs().getStringSet(HIDDEN, new HashSet<>())); }

    static String key(Account account) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                (account.type + "\u0000" + account.name).getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder();
            for (byte b : hash) value.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
            return value.toString();
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static void addMenu(Menu menu) {
        if (menu.findItem(ITEM) == null) menu.add(Menu.NONE, ITEM, Menu.NONE, "Account visibility")
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
    }

    public static boolean selectMenu(Activity activity, MenuItem item) {
        if (item.getItemId() != ITEM) return false;
        show(activity);
        return true;
    }

    public static void addHeader(List<PreferenceActivity.Header> headers) {
        for (PreferenceActivity.Header header : headers) if (header.id == ITEM) return;
        PreferenceActivity.Header header = new PreferenceActivity.Header();
        header.id = ITEM;
        header.title = "Account visibility";
        header.summary = "Choose accounts shown in the account switcher";
        headers.add(0, header);
    }

    public static boolean selectHeader(Activity activity, PreferenceActivity.Header header) {
        if (header.id != ITEM) return false;
        show(activity);
        return true;
    }

    public static void show(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        context = activity.getApplicationContext();
        List<Account> snapshot = new ArrayList<>(accounts);
        if (snapshot.isEmpty()) {
            new AlertDialog.Builder(activity).setTitle("Account visibility")
                .setMessage("Open the inbox and account switcher first, then return here. No accounts have been removed.")
                .setPositiveButton(android.R.string.ok, null).show();
            return;
        }
        Account active = selected;
        Set<String> draft = hidden();
        String[] labels = new String[snapshot.size()];
        boolean[] checked = new boolean[snapshot.size()];
        for (int i = 0; i < snapshot.size(); i++) {
            Account a = snapshot.get(i);
            labels[i] = a.name + (a.equals(active) ? " (current)" : "");
            checked[i] = a.equals(active) || !draft.contains(key(a));
        }
        AlertDialog dialog = new AlertDialog.Builder(activity)
            .setTitle("Accounts to show")
            .setMultiChoiceItems(labels, checked, (d, index, enabled) -> {
                Account a = snapshot.get(index);
                if (!enabled && a.equals(active)) {
                    ((AlertDialog) d).getListView().setItemChecked(index, true);
                    Toast.makeText(activity, "Switch to another account before hiding this one.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (enabled) draft.remove(key(a)); else draft.add(key(a));
            })
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton("Show all", (d, which) -> save(activity, new HashSet<>()))
            .setPositiveButton("Apply", (d, which) -> {
                if (active != null) draft.remove(key(active));
                save(activity, draft);
            }).create();
        dialog.show();
    }

    private static void save(Activity activity, Set<String> hidden) {
        if (hidden.equals(hidden())) return;
        prefs().edit().putStringSet(HIDDEN, new HashSet<>(hidden)).putLong("revision", revision() + 1).apply();
        Toast.makeText(activity, "Saved. Mail sync and notifications stay on.", Toast.LENGTH_LONG).show();
        // A dialog opened from the avatar does not pause/resume its inbox Activity.
        // Refresh after the dialog's dismiss message; settings use the resume callback.
        if (revisions.containsKey(activity)) activity.getWindow().getDecorView().post(() -> {
            if (!activity.isFinishing() && !activity.isDestroyed()) resumed(activity);
        });
    }
}
