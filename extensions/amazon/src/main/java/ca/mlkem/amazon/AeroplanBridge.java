package ca.mlkem.amazon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import java.util.Collections;

/** Entry point injected into the tested Amazon MainActivity resume method. */
public final class AeroplanBridge {
    private static boolean offered;
    private AeroplanBridge() {}

    static void suppressStartupOffer() { offered = true; }

    public static void onResume(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
                NativeReferralStatus.register(activity);
                NativeReferralStatus.attach(activity);
                if (offered) return;
                offered = true;
                publishShortcut(activity);
                ProfileStore.Profile profile;
                try { profile = ProfileStore.load(activity); }
                catch (Exception unavailable) { profile = ProfileStore.empty(); }
                if (profile.configured() && profile.autoOpen) {
                    open(activity, true);
                    return;
                }
                new AlertDialog.Builder(activity)
                        .setTitle("Shop with Aeroplan")
                        .setMessage("Review your cart and referral in one shopping window, or use the existing Amazon app referral.")
                        .setPositiveButton("Review and shop", (dialog, which) -> ReviewActivity.open(activity))
                        .setNeutralButton("Native referral", (dialog, which) -> open(activity, true))
                        .setNegativeButton("Continue in Amazon", null)
                        .show();
            } catch (RuntimeException ignored) {
                // An unavailable shortcut or window must never break Amazon's launch.
            }
        });
    }

    public static void onWalmartStart(Activity activity) {
        if (offered || activity == null || activity.isFinishing()) return;
        offered = true;
        activity.getWindow().getDecorView().post(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            publishShortcut(activity);
            new AlertDialog.Builder(activity).setTitle("Shop with Aeroplan")
                    .setMessage("Open the Walmart.ca eStore offer before adding items to your shopping cart.")
                    .setPositiveButton("Review and shop", (d, w) -> ReviewActivity.open(activity))
                    .setNegativeButton("Continue in Walmart", null).show();
        });
    }

    static void open(Activity activity, boolean start) {
        Intent intent = new Intent(activity, AeroplanActivity.class);
        intent.setAction(start ? AeroplanActivity.ACTION_START : AeroplanActivity.ACTION_SETTINGS);
        activity.startActivity(intent);
    }

    private static void publishShortcut(Activity activity) {
        try {
            ShortcutManager manager = activity.getSystemService(ShortcutManager.class);
            if (manager == null || manager.isRateLimitingActive()) return;
            ShortcutInfo shortcut = new ShortcutInfo.Builder(activity, "aeroplan_helper_settings")
                    .setShortLabel("Aeroplan")
                    .setLongLabel("Aeroplan referral and settings")
                    .setIcon(Icon.createWithResource(activity, android.R.drawable.ic_menu_compass))
                    .setIntent(("ca.walmart.ecommerceapp".equals(activity.getPackageName())
                            ? new Intent(activity, ReviewActivity.class).setAction(Intent.ACTION_VIEW)
                            : new Intent(activity, AeroplanActivity.class).setAction(AeroplanActivity.ACTION_SETTINGS)))
                    .build();
            for (ShortcutInfo existing : manager.getDynamicShortcuts()) {
                if (existing.getId().equals(shortcut.getId())) {
                    manager.updateShortcuts(Collections.singletonList(shortcut));
                    return;
                }
            }
            if (manager.getDynamicShortcuts().size() + manager.getManifestShortcuts().size()
                    < manager.getMaxShortcutCountPerActivity()) {
                manager.addDynamicShortcuts(Collections.singletonList(shortcut));
            }
        } catch (RuntimeException ignored) {
            // Amazon's shortcuts remain untouched if the launcher has no room.
        }
    }
}
