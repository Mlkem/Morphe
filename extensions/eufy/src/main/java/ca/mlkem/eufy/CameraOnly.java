package ca.mlkem.eufy;

import android.app.Activity;
import android.app.Application;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inspector.WindowInspector;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Version-specific UI IDs confirmed against original 6.1.20 and 6.1.30 APKMs. No network/camera API calls. */
public final class CameraOnly implements Application.ActivityLifecycleCallbacks {
    static final int NAV = 1, PROMOTIONS = 2;
    static final int MAIN_TABS = 0x7f0a1719;
    static final int HOME_BANNER = 0x7f0a0c04, AI_BANNER = 0x7f0a0c03;
    static final int SHOP_IMAGE = 0x7f0a1a01, SHOP_CLOSE = 0x7f0a19ff;
    static final int SOLAR_IMAGE = 0x7f0a1284, SOLAR_CLOSE = 0x7f0a1285, SOLAR_MORE = 0x7f0a1e2b;
    private static final int DISCOVER = 0x7f14082b, SMART = 0x7f1439fa, SHOP = 0x7f1437dd, HOME = 0x7f1439f9;
    // 6.1.30 (32823) remappings are name-verified in the original APK's resource
    // table. Old numeric values now identify *different* views/strings, so the
    // source version selects the correct entry points at patch time.
    static final int MAIN_TABS_32823 = 0x7f0a1739;
    static final int HOME_BANNER_32823 = 0x7f0a0c0b, AI_BANNER_32823 = 0x7f0a0c0a;
    static final int SHOP_IMAGE_32823 = 0x7f0a1a22, SHOP_CLOSE_32823 = 0x7f0a1a20;
    static final int SOLAR_IMAGE_32823 = 0x7f0a1298, SOLAR_CLOSE_32823 = 0x7f0a1299, SOLAR_MORE_32823 = 0x7f0a1e56;
    static final int DISCOVER_32823 = 0x7f140858, SMART_32823 = 0x7f143a42, SHOP_32823 = 0x7f143825, HOME_32823 = 0x7f143a41;
    private static final int BUILD_31181 = 31181, BUILD_32823 = 32823;
    private static final String PACKAGE = "com.oceanwing.battery.cam";
    private static final String BUSINESS_CARD = "com.oceanwing.battery.cam.account.ui.ItemBusinessBannerView";
    private static CameraOnly instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Activity, Observer> active = new IdentityHashMap<>();
    private final WeakHashMap<Activity, Boolean> registered = new WeakHashMap<>();
    private final WeakHashMap<View, HiddenState> hidden = new WeakHashMap<>();
    private int flags;
    private int targetBuild = BUILD_31181;
    private boolean applying;

    public static void enableNavigation(Activity activity) { enable(activity, NAV, BUILD_31181); }
    public static void enablePromotions(Activity activity) { enable(activity, PROMOTIONS, BUILD_31181); }
    public static void enableNavigation32823(Activity activity) { enable(activity, NAV, BUILD_32823); }
    public static void enablePromotions32823(Activity activity) { enable(activity, PROMOTIONS, BUILD_32823); }

    private static void enable(Activity activity, int flag, int versionCode) {
        if (activity == null) return;
        // AppComponentFactory returns an unattached Activity. Registration is
        // explicitly supported before onCreate (API 29); Context APIs are not.
        // The protected loader bypasses shell Application.onCreate entirely on
        // its modern path. Register on the actual Activity on BOTH factory paths.
        if (instance == null) instance = new CameraOnly();
        instance.flags |= flag;
        instance.targetBuild = versionCode;
        if (!instance.registered.containsKey(activity)) {
            activity.registerActivityLifecycleCallbacks(instance);
            instance.registered.put(activity, Boolean.TRUE);
        }
    }

    // Layout callbacks handle dynamic/rebound activity views before painting. A single
    // foreground-only check also discovers separate Dialog/PopupWindow roots,
    // which don't dispatch Activity lifecycle callbacks. No background polling.
    private final Runnable windows = new Runnable() {
        @Override public void run() {
            if (active.isEmpty()) return;
            if (Build.VERSION.SDK_INT >= 29) {
                for (View root : WindowInspector.getGlobalWindowViews()) safeApply(root);
            }
            handler.postDelayed(this, 600);
        }
    };

    private void safeApply(View root) {
        if (applying) return;
        applying = true;
        try {
            if (root != null && PACKAGE.equals(root.getContext().getPackageName())) apply(root);
        } catch (RuntimeException ignored) {
            // A changing/detached UI must never interrupt camera operation.
        } finally {
            applying = false;
        }
    }

    void apply(View root) {
        // A new build reuses the old IDs for unrelated views. Never search the
        // old IDs when running 32823, even if the correct targets are absent.
        boolean modern = targetBuild == BUILD_32823;
        if ((flags & NAV) != 0) {
            View bar = root.findViewById(modern ? MAIN_TABS_32823 : MAIN_TABS);
            if (bar instanceof ViewGroup) {
                Resources res = root.getResources();
                hideTabs((ViewGroup) bar,
                    new String[]{
                        text(res, modern ? DISCOVER_32823 : DISCOVER, "Discover"),
                        text(res, modern ? SMART_32823 : SMART, "Smart"),
                        text(res, modern ? SHOP_32823 : SHOP, "Shop")
                    }, text(res, modern ? HOME_32823 : HOME, "Home"));
            }
        }
        if ((flags & PROMOTIONS) == 0) return;
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 4096) {
            View view = queue.removeFirst();
            int id = view.getId();
            if (id == (modern ? HOME_BANNER_32823 : HOME_BANNER) ||
                id == (modern ? AI_BANNER_32823 : AI_BANNER) ||
                BUSINESS_CARD.equals(view.getClass().getName())) {
                collapse(view);
                continue;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        // Exact multi-view signatures. Only the native close control is invoked;
        // no generic dialog dismissal, Back action or purchase button is used.
        closeOffer(root, modern ? SHOP_IMAGE_32823 : SHOP_IMAGE,
            modern ? SHOP_CLOSE_32823 : SHOP_CLOSE, 0);
        closeOffer(root, modern ? SOLAR_IMAGE_32823 : SOLAR_IMAGE,
            modern ? SOLAR_CLOSE_32823 : SOLAR_CLOSE, modern ? SOLAR_MORE_32823 : SOLAR_MORE);
    }

    private void closeOffer(View root, int marker, int closeId, int secondMarker) {
        View image = root.findViewById(marker), close = root.findViewById(closeId);
        if (image == null || close == null || !(close.getParent() instanceof ViewGroup)) return;
        ViewGroup card = (ViewGroup) close.getParent();
        if (card.findViewById(marker) != image ||
            (secondMarker != 0 && card.findViewById(secondMarker) == null)) return;
        if (close.isShown() && close.isEnabled() && close.hasOnClickListeners()) close.performClick();
    }

    private static String text(Resources res, int id, String fallback) {
        try { return res.getString(id); } catch (Resources.NotFoundException ignored) { return fallback; }
    }

    // Material TabLayout owns a single strip, whose direct children are TabViews.
    // Keep those objects in place so fragment positions/listeners stay aligned.
    void hideTabs(ViewGroup bar, String[] blockedTitles, String homeTitle) {
        if (bar.getChildCount() != 1 || !(bar.getChildAt(0) instanceof ViewGroup)) return;
        ViewGroup strip = (ViewGroup) bar.getChildAt(0);
        View home = null;
        boolean blockedSelected = false;
        for (int i = 0; i < strip.getChildCount(); i++) {
            View tab = strip.getChildAt(i);
            if (containsTitle(tab, new String[]{homeTitle})) home = tab;
            if (containsTitle(tab, blockedTitles) && tab.isSelected()) blockedSelected = true;
        }
        if (blockedSelected && home != null && home.isEnabled()) home.performClick();
        for (int i = 0; i < strip.getChildCount(); i++) {
            View tab = strip.getChildAt(i);
            if (containsTitle(tab, blockedTitles)) collapse(tab);
            else restore(tab); // A reused TabView may now represent Events/Profile.
        }
    }

    private static boolean containsTitle(View root, String[] titles) {
        ArrayDeque<View> queue = new ArrayDeque<>();
        queue.add(root);
        int visited = 0;
        while (!queue.isEmpty() && visited++ < 128) {
            View view = queue.removeFirst();
            if (view instanceof TextView) {
                String label = ((TextView) view).getText().toString().trim();
                for (String title : titles) if (title.equals(label)) return true;
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup) view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
        return false;
    }

    private void collapse(View view) {
        if (!hidden.containsKey(view)) hidden.put(view, new HiddenState(view));
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        boolean changed = false;
        if (lp != null) {
            if (lp.height != 0) { lp.height = 0; changed = true; }
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) lp;
                if (margins.topMargin != 0 || margins.bottomMargin != 0) {
                    margins.topMargin = margins.bottomMargin = 0; changed = true;
                }
            }
            if (lp instanceof LinearLayout.LayoutParams && ((LinearLayout.LayoutParams) lp).weight != 0) {
                ((LinearLayout.LayoutParams) lp).weight = 0; changed = true;
            }
            if (changed) view.setLayoutParams(lp);
        }
        if (view.getVisibility() != View.GONE) view.setVisibility(View.GONE);
        view.setClickable(false);
        view.setFocusable(false);
        view.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
    }

    private void restore(View view) {
        HiddenState state = hidden.remove(view);
        if (state != null) state.restore(view);
    }

    private static final class HiddenState {
        final int height, top, bottom, visibility, accessibility;
        final float weight;
        final boolean clickable, focusable;
        HiddenState(View view) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            height = lp == null ? 0 : lp.height;
            top = lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).topMargin : 0;
            bottom = lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0;
            weight = lp instanceof LinearLayout.LayoutParams ? ((LinearLayout.LayoutParams) lp).weight : 0;
            visibility = view.getVisibility(); accessibility = view.getImportantForAccessibility();
            clickable = view.isClickable(); focusable = view.isFocusable();
        }
        void restore(View view) {
            ViewGroup.LayoutParams lp = view.getLayoutParams();
            if (lp != null) {
                lp.height = height;
                if (lp instanceof ViewGroup.MarginLayoutParams) {
                    ((ViewGroup.MarginLayoutParams) lp).topMargin = top;
                    ((ViewGroup.MarginLayoutParams) lp).bottomMargin = bottom;
                }
                if (lp instanceof LinearLayout.LayoutParams) ((LinearLayout.LayoutParams) lp).weight = weight;
                view.setLayoutParams(lp);
            }
            view.setVisibility(visibility); view.setClickable(clickable); view.setFocusable(focusable);
            view.setImportantForAccessibility(accessibility);
        }
    }

    private final class Observer implements ViewTreeObserver.OnGlobalLayoutListener {
        final View root;
        Observer(View root) { this.root = root; }
        @Override public void onGlobalLayout() { safeApply(root); }
        void detach() {
            if (root.getViewTreeObserver().isAlive()) root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
        }
    }

    @Override public void onActivityResumed(Activity activity) {
        onActivityPaused(activity);
        View root = activity.getWindow().getDecorView();
        Observer observer = new Observer(root);
        active.put(activity, observer);
        root.getViewTreeObserver().addOnGlobalLayoutListener(observer);
        safeApply(root);
        handler.removeCallbacks(windows);
        handler.post(windows);
    }
    @Override public void onActivityPaused(Activity activity) {
        Observer observer = active.remove(activity);
        if (observer != null) observer.detach();
        if (active.isEmpty()) handler.removeCallbacks(windows);
    }
    @Override public void onActivityDestroyed(Activity activity) {
        onActivityPaused(activity);
        registered.remove(activity);
        activity.unregisterActivityLifecycleCallbacks(this);
    }
    @Override public void onActivityStopped(Activity activity) { onActivityPaused(activity); }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}

    // Package-scoped constructor for Android view regression tests.
    CameraOnly() {}
    void setTestFlags(int flags) { this.flags = flags; }
    void setTestBuild(int versionCode) {
        if (versionCode != BUILD_31181 && versionCode != BUILD_32823) throw new IllegalArgumentException("Unsupported test build");
        this.targetBuild = versionCode;
    }
}
