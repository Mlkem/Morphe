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

    public static void onResume(Activity activity) {
        if (offered || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        offered = true;
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            try {
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
                        .setMessage("Start through the eStore before adding items to your cart. "
                                + "Complete your order in the Aeroplan shopping window. Eligible purchases only.")
                        .setPositiveButton("Start shopping", (dialog, which) -> open(activity, true))
                        .setNeutralButton("Settings", (dialog, which) -> open(activity, false))
                        .setNegativeButton("Continue in Amazon", null)
                        .show();
            } catch (RuntimeException ignored) {
                // An unavailable shortcut or window must never break Amazon's launch.
            }
        });
    }

    private static void open(Activity activity, boolean start) {
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
                    .setLongLabel("Aeroplan shopping and settings")
                    .setIcon(Icon.createWithResource(activity, android.R.drawable.ic_menu_compass))
                    .setIntent(new Intent(activity, AeroplanActivity.class).setAction(AeroplanActivity.ACTION_SETTINGS))
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
