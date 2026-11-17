package ca.mlkem.niagara;

import java.util.Locale;

/** Local-only classification. Unknown metadata has an honest Other destination. */
public final class Categories {
    public static final String[] IDS = {"games", "music", "video", "photos", "social", "news", "maps", "productivity", "accessibility", "other"};
    public static final String[] NAMES = {"Games", "Music & Audio", "Video", "Photography", "Social", "News & Reading", "Maps & Travel", "Productivity", "Accessibility", "Other Apps"};
    private Categories() {}
    public static int classify(int androidCategory, int flags, String packageName, String label) {
        // Android CATEGORY_GAME..CATEGORY_ACCESSIBILITY are 0..8. FLAG_IS_GAME
        // is retained as a fallback for older games without appCategory metadata.
        if (androidCategory >= 0 && androidCategory <= 8) return androidCategory;
        if ((flags & 0x02000000) != 0) return 0;
        String name = label == null ? "" : label.trim().toLowerCase(Locale.ROOT);
        String pkg = packageName == null ? "" : packageName.toLowerCase(Locale.ROOT);
        if (name.equals("truckful") || pkg.endsWith(".truckful")) return 0;
        return 9;
    }
    public static boolean eligible(long firstInstall, long since, boolean enabled, boolean categoryEnabled,
                                   boolean alreadyHandled, boolean hidden, boolean favorite, boolean personalProfile) {
        return firstInstall >= since && enabled && categoryEnabled && !alreadyHandled && !hidden && !favorite && personalProfile;
    }
}
