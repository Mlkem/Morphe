package ca.mikem.pushpress.extension;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Defaults Workouts -> Class type to the API-provided CrossFit class on each visit.
 * Flutter's AccessibilityBridge does not implement findAccessibilityNodeInfosByText,
 * so we enumerate its semantics IDs and request node information for each ID.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class CrossFitOnly {
    private static final String TAG = "PushPressCrossFitOnly";
    private static final String CLASS_TYPE_TEXT = "class type";
    private static final String CROSS_FIT_CLASS = "crossfit class";
    private static final String CROSS_FIT_SHORT = "crossfit";
    private static final long INSTALL_RETRY_MS = 250L;
    private static final long IDLE_SCAN_MS = 650L;
    private static final long ACTIVE_SCAN_MS = 200L;
    private static final long MENU_WAIT_MS = 4_500L;
    private static final long RETRY_PAUSE_MS = 900L;
    private static final long SCREEN_EXIT_MS = 1_500L;
    private static final int MAX_AUTO_OPEN_ATTEMPTS = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static WeakReference<View> flutterViewRef = new WeakReference<>(null);
    private static AccessibilityNodeProvider provider;
    private static Field semanticsTreeField;
    private static Field flutterAccessibilityBridgeField;
    private static long lastSemanticsRequestAt;
    private static long lastDiagnosticAt;
    private static boolean installed;
    private static boolean semanticsEnabled;
    private static boolean selectedThisVisit;
    private static boolean openingMenu;
    private static long menuOpenedAt;
    private static long lastClassTypeSeenAt;
    private static long nextAttemptAt;
    private static int openAttempts;

    private CrossFitOnly() { }

    public static synchronized void install(Activity activity) {
        if (activity == null) return;
        if (installed && activityRef.get() == activity) return;

        // An Activity can be recreated without killing the app process.
        activityRef = new WeakReference<>(activity);
        flutterViewRef = new WeakReference<>(null);
        provider = null;
        semanticsTreeField = null;
        flutterAccessibilityBridgeField = null;
        lastSemanticsRequestAt = 0L;
        lastDiagnosticAt = 0L;
        semanticsEnabled = false;
        installed = true;
        resetVisit();
        MAIN.removeCallbacks(INSTALL);
        MAIN.removeCallbacks(SCAN);
        MAIN.post(INSTALL);
    }

    private static final Runnable INSTALL = new Runnable() {
        @Override public void run() {
            Activity activity = activityRef.get();
            if (!isActivityUsable(activity)) return;
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            View flutterView = findFlutterView(root);
            if (flutterView == null || !flutterView.isAttachedToWindow()) {
                MAIN.postDelayed(this, INSTALL_RETRY_MS);
                return;
            }
            flutterViewRef = new WeakReference<>(flutterView);
            enableFlutterSemantics(flutterView);
            provider = resolveFlutterAccessibilityBridge(flutterView);
            Log.i(TAG, "Watching Workouts/Class type for CrossFit default (bridge=" +
                    (provider != null) + ")");
            MAIN.post(SCAN);
        }
    };

    private static final Runnable SCAN = new Runnable() {
        @Override public void run() {
            Activity activity = activityRef.get();
            View flutterView = flutterViewRef.get();
            if (!isActivityUsable(activity) || flutterView == null) return;
            if (!flutterView.isAttachedToWindow()) {
                MAIN.postDelayed(INSTALL, INSTALL_RETRY_MS);
                return;
            }
            if (!semanticsEnabled) enableFlutterSemantics(flutterView);
            if (provider == null) provider = resolveFlutterAccessibilityBridge(flutterView);

            long now = SystemClock.uptimeMillis();
            List<SemanticNode> nodes = readVisibleSemantics(flutterView);
            if (nodes.isEmpty() && now - lastSemanticsRequestAt >= 3_000L) {
                // Request Flutter semantics without changing Android accessibility settings.
                enableFlutterSemantics(flutterView);
                lastSemanticsRequestAt = now;
            }
            if (nodes.isEmpty() && now - lastDiagnosticAt >= 6_000L) {
                Log.w(TAG, "No visible Flutter semantics (bridge=" + (provider != null) +
                        ", requested=" + semanticsEnabled + ")");
                lastDiagnosticAt = now;
            }

            // Flutter dropdown routes may hide the underlying Class type label from semantics.
            // Finish an in-flight selection before checking whether the label is still visible.
            if (openingMenu) {
                SemanticNode target = findCrossFit(nodes);
                if (target != null && activate(flutterView, target)) {
                    selectedThisVisit = true;
                    openingMenu = false;
                    Log.i(TAG, "Requested CrossFit class selection");
                } else if (now - menuOpenedAt >= MENU_WAIT_MS) {
                    openingMenu = false;
                    nextAttemptAt = now + RETRY_PAUSE_MS;
                    Log.w(TAG, "CrossFit option not exposed; will retry if the field is visible");
                }
                MAIN.postDelayed(this, openingMenu ? ACTIVE_SCAN_MS : IDLE_SCAN_MS);
                return;
            }

            SemanticNode classTypeLabel = findClassType(nodes);
            if (classTypeLabel == null) {
                if (lastClassTypeSeenAt > 0L && now - lastClassTypeSeenAt > SCREEN_EXIT_MS) {
                    resetVisit();
                }
                MAIN.postDelayed(this, IDLE_SCAN_MS);
                return;
            }
            lastClassTypeSeenAt = now;

            // A previously selected CrossFit value appears alongside the closed field.
            if (!selectedThisVisit && isCrossFitValueNearField(nodes, classTypeLabel, flutterView)) {
                selectedThisVisit = true;
                Log.i(TAG, "CrossFit class already selected");
            }

            // Only default once per Workouts visit. Users can still change it afterward.
            if (!selectedThisVisit && openAttempts < MAX_AUTO_OPEN_ATTEMPTS
                    && now >= nextAttemptAt) {
                SemanticNode field = chooseClassTypeField(nodes, classTypeLabel, flutterView);
                openAttempts++;
                if (field != null && activate(flutterView, field)) {
                    openingMenu = true;
                    menuOpenedAt = now;
                } else {
                    nextAttemptAt = now + RETRY_PAUSE_MS;
                }
            }
            MAIN.postDelayed(this, selectedThisVisit ? IDLE_SCAN_MS : ACTIVE_SCAN_MS);
        }
    };

    private static void resetVisit() {
        selectedThisVisit = false;
        openingMenu = false;
        menuOpenedAt = 0L;
        lastClassTypeSeenAt = 0L;
        nextAttemptAt = 0L;
        openAttempts = 0;
    }

    /**
     * FlutterView returns a null public provider when Android accessibility is off.
     * The bridge still exists as a FlutterView field; read it directly instead.
     */
    private static AccessibilityNodeProvider resolveFlutterAccessibilityBridge(View flutterView) {
        if (flutterView == null) return null;
        try {
            AccessibilityNodeProvider publicProvider = flutterView.getAccessibilityNodeProvider();
            if (publicProvider != null) return publicProvider;
            if (flutterAccessibilityBridgeField == null) {
                Class<?> type = flutterView.getClass();
                while (type != null) {
                    try {
                        Field field = type.getDeclaredField("accessibilityBridge");
                        field.setAccessible(true);
                        flutterAccessibilityBridgeField = field;
                        break;
                    } catch (NoSuchFieldException ignored) {
                        type = type.getSuperclass();
                    }
                }
            }
            if (flutterAccessibilityBridgeField == null) return null;
            Object raw = flutterAccessibilityBridgeField.get(flutterView);
            return raw instanceof AccessibilityNodeProvider
                    ? (AccessibilityNodeProvider) raw : null;
        } catch (Throwable error) {
            Log.w(TAG, "Could not obtain Flutter's private accessibility bridge", error);
            return null;
        }
    }

    private static List<SemanticNode> readVisibleSemantics(View flutterView) {
        AccessibilityNodeProvider activeProvider = provider;
        if (activeProvider == null) return Collections.emptyList();
        try {
            if (semanticsTreeField == null) {
                Class<?> type = activeProvider.getClass();
                while (type != null && semanticsTreeField == null) {
                    try {
                        semanticsTreeField = type.getDeclaredField("flutterSemanticsTree");
                        semanticsTreeField.setAccessible(true);
                    } catch (NoSuchFieldException missing) {
                        type = type.getSuperclass();
                    }
                }
                if (semanticsTreeField == null) return Collections.emptyList();
            }
            Object raw = semanticsTreeField.get(activeProvider);
            if (!(raw instanceof Map)) return Collections.emptyList();
            Map<?, ?> semantics = (Map<?, ?>) raw;
            // Snapshot IDs before calling back into Flutter. All access is on the main thread.
            List<Integer> ids = new ArrayList<>(semantics.size());
            for (Object id : semantics.keySet()) {
                if (id instanceof Integer) ids.add((Integer) id);
            }
            Rect screen = new Rect();
            if (!flutterView.getGlobalVisibleRect(screen)) return Collections.emptyList();
            List<SemanticNode> result = new ArrayList<>(ids.size());
            for (int id : ids) {
                AccessibilityNodeInfo info = null;
                try {
                    info = activeProvider.createAccessibilityNodeInfo(id);
                    if (info == null) continue;
                    Rect rect = new Rect();
                    info.getBoundsInScreen(rect);
                    if (rect.isEmpty() || !Rect.intersects(rect, screen)) continue;
                    String text = normalize(info.getText());
                    String description = normalize(info.getContentDescription());
                    if (text.isEmpty() && description.isEmpty() && !info.isClickable()) continue;
                    result.add(new SemanticNode(id, text, description, rect, info.isClickable()));
                } finally {
                    recycle(info);
                }
            }
            return result;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to inspect Flutter semantics", error);
            semanticsTreeField = null;
            return Collections.emptyList();
        }
    }

    private static SemanticNode findClassType(List<SemanticNode> nodes) {
        for (SemanticNode node : nodes) {
            if (node.text.contains(CLASS_TYPE_TEXT) || node.description.contains(CLASS_TYPE_TEXT)) {
                return node;
            }
        }
        return null;
    }

    private static SemanticNode findCrossFit(List<SemanticNode> nodes) {
        for (SemanticNode node : nodes) {
            if (isCrossFit(node.text) || isCrossFit(node.description)) return node;
        }
        return null;
    }

    private static boolean isCrossFit(String value) {
        return value.equals(CROSS_FIT_CLASS) || value.equals(CROSS_FIT_SHORT)
                || value.startsWith(CROSS_FIT_CLASS + ",")
                || value.startsWith(CROSS_FIT_SHORT + ",");
    }

    private static boolean isCrossFitValueNearField(
            List<SemanticNode> nodes, SemanticNode label, View flutterView) {
        float density = flutterView.getResources().getDisplayMetrics().density;
        int maxDistance = (int) (140f * density);
        for (SemanticNode node : nodes) {
            if (!isCrossFit(node.text) && !isCrossFit(node.description)) continue;
            if (node.rect.top >= label.rect.top - maxDistance
                    && node.rect.top <= label.rect.bottom + maxDistance
                    && horizontalOverlap(node.rect, label.rect) > 0) return true;
        }
        return false;
    }

    private static SemanticNode chooseClassTypeField(
            List<SemanticNode> nodes, SemanticNode label, View flutterView) {
        // Best case: a tappable semantics node includes the Class type caption.
        for (SemanticNode node : nodes) {
            if (node.clickable && (node.text.contains(CLASS_TYPE_TEXT)
                    || node.description.contains(CLASS_TYPE_TEXT))) return node;
        }
        // Otherwise the caption and dropdown have separate semantics nodes.
        float density = flutterView.getResources().getDisplayMetrics().density;
        int maxDistance = (int) (105f * density);
        SemanticNode closest = null;
        int bestDistance = Integer.MAX_VALUE;
        for (SemanticNode node : nodes) {
            if (!node.clickable || node == label || node.rect.isEmpty()) continue;
            int verticalDistance = node.rect.top - label.rect.bottom;
            if (verticalDistance < -(int) (16 * density) || verticalDistance > maxDistance) continue;
            if (horizontalOverlap(node.rect, label.rect) <= 0) continue;
            int distance = Math.abs(verticalDistance);
            if (distance < bestDistance) {
                closest = node;
                bestDistance = distance;
            }
        }
        // Final fallback: tapping a combined Flutter field's text label can open it.
        return closest != null ? closest : label;
    }

    private static int horizontalOverlap(Rect a, Rect b) {
        return Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
    }

    private static boolean activate(View flutterView, SemanticNode node) {
        if (node == null) return false;
        try {
            if (node.clickable && provider != null
                    && provider.performAction(node.id, AccessibilityNodeInfo.ACTION_CLICK, null)) {
                return true;
            }
        } catch (Throwable error) {
            Log.w(TAG, "Flutter semantics click unavailable; trying touch", error);
        }
        return tapScreenRect(flutterView, node.rect);
    }

    private static boolean tapScreenRect(View flutterView, Rect screenRect) {
        if (flutterView == null || screenRect == null || screenRect.isEmpty()) return false;
        int[] location = new int[2];
        flutterView.getLocationOnScreen(location);
        float x = screenRect.exactCenterX() - location[0];
        float y = screenRect.exactCenterY() - location[1];
        if (x < 0 || y < 0 || x >= flutterView.getWidth() || y >= flutterView.getHeight()) {
            return false;
        }
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(
                downTime, downTime, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(
                downTime, downTime + 35L, MotionEvent.ACTION_UP, x, y, 0);
        try {
            flutterView.dispatchTouchEvent(down);
            flutterView.dispatchTouchEvent(up);
            return true;
        } finally {
            down.recycle();
            up.recycle();
        }
    }

    private static View findFlutterView(View view) {
        if (view == null) return null;
        Class<?> type = view.getClass();
        if ("Mf.A".equals(type.getName()) || hasPublicNoArgMethod(type, "getAttachedFlutterEngine")) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findFlutterView(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static boolean hasPublicNoArgMethod(Class<?> type, String name) {
        try {
            type.getMethod(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void enableFlutterSemantics(View flutterView) {
        try {
            Method getEngine = flutterView.getClass().getMethod("getAttachedFlutterEngine");
            Object engine = getEngine.invoke(flutterView);
            if (engine == null) return;
            Field flutterJniField = null;
            for (Field field : engine.getClass().getDeclaredFields()) {
                if ("io.flutter.embedding.engine.FlutterJNI".equals(field.getType().getName())) {
                    flutterJniField = field;
                    break;
                }
            }
            if (flutterJniField == null) return;
            flutterJniField.setAccessible(true);
            Object flutterJni = flutterJniField.get(engine);
            if (flutterJni == null) return;
            Method setSemanticsEnabled =
                    flutterJni.getClass().getMethod("setSemanticsEnabled", boolean.class);
            setSemanticsEnabled.invoke(flutterJni, true);
            semanticsEnabled = true;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to enable Flutter semantics yet", error);
        }
    }

    private static boolean isActivityUsable(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1
                || !activity.isDestroyed());
    }

    private static String normalize(CharSequence text) {
        return text == null ? "" : text.toString().trim().replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT);
    }

    private static void recycle(AccessibilityNodeInfo node) {
        if (node != null && Build.VERSION.SDK_INT < 33) {
            try { node.recycle(); } catch (Throwable ignored) { }
        }
    }

    private static final class SemanticNode {
        final int id;
        final String text;
        final String description;
        final Rect rect;
        final boolean clickable;

        SemanticNode(int id, String text, String description, Rect rect, boolean clickable) {
            this.id = id;
            this.text = text;
            this.description = description;
            this.rect = rect;
            this.clickable = clickable;
        }
    }
}
