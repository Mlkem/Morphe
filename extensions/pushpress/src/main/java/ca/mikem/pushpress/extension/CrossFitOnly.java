package ca.mikem.pushpress.extension;

import android.app.Activity;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.lang.reflect.Modifier;

/**
 * Automatically defaults the visible workout-category dropdown to CrossFit Class.
 * Screenshot-verified UI: the CLOSED field displays a class name (e.g.
 * "Small Group/Teams"), not a "Class type" label. The OPEN control is a
 * bottom sheet whose first row is "CrossFit Class".
 * Flutter's accessibility bridge is consulted by ID, not a text-search API.
 * Class selection is verified by observing the closed field after tapping.
 * All operations run on the app's main thread and affect Workouts only.
 */
@SuppressWarnings({"unused", "JavaReflectionMemberAccess"})
public final class CrossFitOnly {
    private static final String TAG = "PushPressCrossFitOnly";
    private static final String CROSS_FIT_CLASS = "crossfit class";
    // These are the actual five rows shown in the 782 Fitness Workouts dropdown.
    private static final String[] WORKOUT_CLASSES = {
            "crossfit class", "express", "hyrox training club",
            "small group/teams", "prime time class"
    };
    private static final long INSTALL_RETRY_MS = 250L;
    private static final long IDLE_SCAN_MS = 650L;
    private static final long ACTIVE_SCAN_MS = 200L;
    private static final long MENU_WAIT_MS = 4_500L;
    private static final long VERIFY_WAIT_MS = 1_800L;
    private static final long RETRY_PAUSE_MS = 900L;
    private static final long SCREEN_EXIT_MS = 1_500L;
    private static final int MAX_AUTO_OPEN_ATTEMPTS = 4;

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> activityRef = new WeakReference<>(null);
    private static WeakReference<View> flutterViewRef = new WeakReference<>(null);
    private static AccessibilityNodeProvider provider;
    private static Field flutterAccessibilityBridgeField;
    private static long lastSemanticsRequestAt;
    private static long lastDiagnosticAt;
    private static long installStartedAt;
    private static int missingFlutterViewPolls;
    private static int lastNodeCount;
    private static String lastRecognizedClass = "";
    private static String lastSemanticsProblem = "";
    private static boolean installed;
    private static boolean semanticsEnabled;
    private static boolean selectedThisVisit;
    private static boolean openingMenu;
    private static boolean verifyingSelection;
    private static long selectionRequestedAt;
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
        flutterAccessibilityBridgeField = null;
        lastNodeCount = 0;
        lastRecognizedClass = "";
        lastSemanticsProblem = "";
        lastSemanticsRequestAt = 0L;
        lastDiagnosticAt = 0L;
        semanticsEnabled = false;
        installed = true;
        installStartedAt = SystemClock.uptimeMillis();
        missingFlutterViewPolls = 0;
        Log.i(TAG, "PushPress CrossFit diagnostic: constructor hook entered");
        resetVisit();
        MAIN.removeCallbacks(INSTALL);
        MAIN.removeCallbacks(SCAN);
        MAIN.post(INSTALL);

        // Only report when the automatic selection did not complete.
        // Report actual stage, rather than only saying that Flutter is present.
        MAIN.postDelayed(() -> {
            Activity live = activityRef.get();
            if (!isActivityUsable(live) || selectedThisVisit) return;
            View flutter = flutterViewRef.get();
            String status = flutter == null ? "view missing"
                    : provider == null ? "bridge missing"
                    : lastNodeCount == 0 ? "semantics empty"
                    : lastRecognizedClass.isEmpty() ? "selector not found"
                    : "selection not completed (" + lastRecognizedClass + ")";
            if (!lastSemanticsProblem.isEmpty()) status += "; " + lastSemanticsProblem;
            Log.w(TAG, "Stage diagnosis: " + status);
            Toast.makeText(live, "CrossFit diagnostic: " + status, Toast.LENGTH_LONG).show();
        }, 8_500L);
    }

    private static final Runnable INSTALL = new Runnable() {
        @Override public void run() {
            Activity activity = activityRef.get();
            if (!isActivityUsable(activity)) return;
            View root = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
            View flutterView = findFlutterView(root);
            if (flutterView == null || !flutterView.isAttachedToWindow()) {
                missingFlutterViewPolls++;
                if (missingFlutterViewPolls % 16 == 0) {
                    Log.w(TAG, "No attached FlutterView after " +
                            (SystemClock.uptimeMillis() - installStartedAt) +
                            " ms; root=" + (root == null ? "null" : root.getClass().getName()));
                }
                MAIN.postDelayed(this, INSTALL_RETRY_MS);
                return;
            }
            flutterViewRef = new WeakReference<>(flutterView);
            enableFlutterSemantics(flutterView);
            provider = resolveFlutterAccessibilityBridge(flutterView);
            Log.i(TAG, "Watching Workouts category dropdown (bridge=" +
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
            lastNodeCount = nodes.size();
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

            // Finish an in-flight bottom-sheet selection before inspecting the closed field.
            // The menu's CrossFit row lives in the lower half of the screen.
            if (openingMenu) {
                SemanticNode target = findCrossFitMenuItem(nodes, flutterView);
                if (target != null && activate(flutterView, target)) {
                    openingMenu = false;
                    verifyingSelection = true;
                    selectionRequestedAt = now;
                    Log.i(TAG, "Tapped CrossFit Class in the bottom sheet; verifying field");
                } else if (now - menuOpenedAt >= MENU_WAIT_MS) {
                    openingMenu = false;
                    nextAttemptAt = now + RETRY_PAUSE_MS;
                    Log.w(TAG, "CrossFit Class option not exposed in bottom sheet");
                }
                MAIN.postDelayed(this, ACTIVE_SCAN_MS);
                return;
            }

            // The real 3.44 screen has NO 'Class type' caption. Detect the
            // selected class name itself above the weekday/date strip.
            SemanticNode classField = findWorkoutsClassField(nodes, flutterView);
            if (classField == null) {
                if (lastClassTypeSeenAt > 0L && now - lastClassTypeSeenAt > SCREEN_EXIT_MS) {
                    resetVisit();
                }
                MAIN.postDelayed(this, IDLE_SCAN_MS);
                return;
            }
            lastClassTypeSeenAt = now;
            lastRecognizedClass = !classField.text.isEmpty()
                    ? classField.text : classField.description;

            if (isExactClass(classField, CROSS_FIT_CLASS)) {
                selectedThisVisit = true;
                verifyingSelection = false;
                openingMenu = false;
            }

            // Do not mark success merely because a tap was dispatched:
            // only the CLOSED dropdown displaying CrossFit Class confirms it.
            if (verifyingSelection) {
                if (now - selectionRequestedAt >= VERIFY_WAIT_MS) {
                    verifyingSelection = false;
                    nextAttemptAt = now + RETRY_PAUSE_MS;
                    Log.w(TAG, "CrossFit tap was not reflected in selected class; retrying");
                } else {
                    MAIN.postDelayed(this, ACTIVE_SCAN_MS);
                    return;
                }
            }

            // Once CrossFit is visible, do not keep interfering with manual changes.
            if (!selectedThisVisit && !openingMenu && openAttempts < MAX_AUTO_OPEN_ATTEMPTS
                    && now >= nextAttemptAt) {
                openAttempts++;
                if (activate(flutterView, classField)) {
                    openingMenu = true;
                    menuOpenedAt = now;
                    Log.i(TAG, "Opened workout category menu; attempt " + openAttempts);
                } else {
                    nextAttemptAt = now + RETRY_PAUSE_MS;
                    Log.w(TAG, "Could not open workout category dropdown");
                }
            }
            MAIN.postDelayed(this, selectedThisVisit ? IDLE_SCAN_MS : ACTIVE_SCAN_MS);
        }
    };

    private static void resetVisit() {
        selectedThisVisit = false;
        openingMenu = false;
        verifyingSelection = false;
        selectionRequestedAt = 0L;
        menuOpenedAt = 0L;
        lastClassTypeSeenAt = 0L;
        nextAttemptAt = 0L;
        openAttempts = 0;
    }

    /**
     * The production Flutter embedding is R8-minified (the older app calls
     * FlutterView Mf.A). Never rely on the private field being literally named
     * 'accessibilityBridge': discover it by its AccessibilityNodeProvider type.
     */
    private static AccessibilityNodeProvider resolveFlutterAccessibilityBridge(View flutterView) {
        if (flutterView == null) return null;
        try {
            AccessibilityNodeProvider publicProvider = flutterView.getAccessibilityNodeProvider();
            if (publicProvider != null) return publicProvider;

            if (flutterAccessibilityBridgeField != null) {
                Object value = flutterAccessibilityBridgeField.get(flutterView);
                if (value instanceof AccessibilityNodeProvider) {
                    return (AccessibilityNodeProvider) value;
                }
            }

            for (Class<?> type = flutterView.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    // R8 may rename the type and field, but cannot remove inheritance.
                    if (!AccessibilityNodeProvider.class.isAssignableFrom(field.getType())) {
                        continue;
                    }
                    try {
                        field.setAccessible(true);
                        Object value = field.get(flutterView);
                        if (value instanceof AccessibilityNodeProvider) {
                            flutterAccessibilityBridgeField = field;
                            return (AccessibilityNodeProvider) value;
                        }
                    } catch (Throwable ignored) {
                        // Keep looking at other fields even when a private one is blocked.
                    }
                }
            }
            lastSemanticsProblem = "no provider-typed view field";
        } catch (Throwable error) {
            Log.w(TAG, "Could not obtain Flutter accessibility bridge", error);
            lastSemanticsProblem = "bridge reflection error";
        }
        return null;
    }

    /**
     * Flutter's Android accessibility bridge stores semantics in a Map<Integer,
     * SemanticsNode>. Its field name is also obfuscated in production. Collect
     * virtual IDs from candidate integer-keyed Maps and ask the provider for
     * actual AccessibilityNodeInfo objects. Unrelated map keys are harmless.
     */
    private static List<SemanticNode> readVisibleSemantics(View flutterView) {
        AccessibilityNodeProvider activeProvider = provider;
        if (activeProvider == null) return Collections.emptyList();
        try {
            Rect screen = new Rect();
            if (!flutterView.getGlobalVisibleRect(screen)) return Collections.emptyList();

            Set<Integer> ids = new HashSet<>();
            int mapsSeen = 0;
            for (Class<?> type = activeProvider.getClass(); type != null;
                 type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) ||
                            !Map.class.isAssignableFrom(field.getType())) continue;
                    try {
                        field.setAccessible(true);
                        Object raw = field.get(activeProvider);
                        if (!(raw instanceof Map)) continue;
                        mapsSeen++;
                        for (Object id : ((Map<?, ?>) raw).keySet()) {
                            if (id instanceof Integer) ids.add((Integer) id);
                        }
                    } catch (Throwable ignored) {
                        // Skip fields that are inaccessible; don't fail the entire scan.
                    }
                }
            }

            List<SemanticNode> result = new ArrayList<>(Math.min(ids.size(), 256));
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
                } catch (Throwable ignored) {
                    // Some virtual semantics IDs are not accessible from Android.
                } finally {
                    recycle(info);
                }
            }
            if (result.isEmpty()) {
                lastSemanticsProblem = mapsSeen == 0 ? "no semantics map"
                        : ids.isEmpty() ? "semantics map empty" : "nodes not exposed";
            } else {
                lastSemanticsProblem = "";
            }
            return result;
        } catch (Throwable error) {
            Log.w(TAG, "Unable to inspect Flutter semantics", error);
            lastSemanticsProblem = "semantics inspection error";
            return Collections.emptyList();
        }
    }

    /**
     * On the supplied screen the closed category dropdown is between the gym
     * header and weekday strip (roughly 11%-25% of the Flutter viewport).
     * Match actual class values rather than looking for a non-existent caption.
     * Geometric bounds keep the same words inside workout cards and sheets out.
     */
    private static SemanticNode findWorkoutsClassField(List<SemanticNode> nodes, View view) {
        Rect visible = new Rect();
        if (!view.getGlobalVisibleRect(visible)) return null;
        int start = visible.top + (visible.height() * 8 / 100);
        int finish = visible.top + (visible.height() * 37 / 100);
        SemanticNode best = null;
        int bestScore = Integer.MIN_VALUE;
        for (SemanticNode node : nodes) {
            if (!isKnownClass(node)) continue;
            int cy = node.rect.centerY();
            if (cy < start || cy > finish) continue;
            int score = (node.clickable ? 10000 : 0)
                    + Math.min(node.rect.width(), visible.width())
                    - Math.abs(cy - (visible.top + visible.height() * 16 / 100));
            if (score > bestScore) {
                bestScore = score;
                best = node;
            }
        }
        return best;
    }

    /** CrossFit Class appears as the first option inside the screenshot's bottom sheet. */
    private static SemanticNode findCrossFitMenuItem(List<SemanticNode> nodes, View view) {
        Rect visible = new Rect();
        if (!view.getGlobalVisibleRect(visible)) return null;
        int sheetTop = visible.top + (visible.height() * 48 / 100);
        SemanticNode best = null;
        for (SemanticNode node : nodes) {
            if (!isExactClass(node, CROSS_FIT_CLASS) || node.rect.centerY() < sheetTop) continue;
            if (best == null || (node.clickable && !best.clickable) ||
                    (node.clickable == best.clickable && node.rect.top < best.rect.top)) {
                best = node;
            }
        }
        return best;
    }

    private static boolean isKnownClass(SemanticNode node) {
        for (String className : WORKOUT_CLASSES) {
            if (isExactClass(node, className)) return true;
        }
        return false;
    }

    private static boolean isExactClass(SemanticNode node, String className) {
        return matchesClass(node.text, className) || matchesClass(node.description, className);
    }

    private static boolean matchesClass(String value, String className) {
        if (value == null || value.isEmpty()) return false;
        // Flutter can append hints (button, selected, double-tap), sometimes
        // separated by spaces instead of commas. Screen bounds filter the
        // controls, so matching the entire category name as a phrase is safe.
        return value.equals(className) || value.startsWith(className + ",")
                || value.startsWith(className + " ")
                || value.endsWith(", " + className)
                || value.contains(", " + className + ",");
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
            if (engine == null) {
                lastSemanticsProblem = "engine not attached";
                return;
            }

            // Prefer the official AccessibilityChannel API. Its implementation
            // calls FlutterJNI.setSemanticsEnabled(true) and retains the bridge.
            try {
                Method getChannel = engine.getClass().getMethod("getAccessibilityChannel");
                Object channel = getChannel.invoke(engine);
                if (channel != null) {
                    channel.getClass().getMethod("onAndroidAccessibilityEnabled").invoke(channel);
                    semanticsEnabled = true;
                    return;
                }
            } catch (Throwable ignored) {
                // Some Flutter embeddings obfuscate this public API.
            }

            // Fallback: locate the JNI object by the *method* it implements.
            // Field names and implementation class names may both be minified.
            for (Class<?> type = engine.getClass(); type != null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers())) continue;
                    try {
                        field.setAccessible(true);
                        Object candidate = field.get(engine);
                        if (candidate == null) continue;
                        Method setSemantics = candidate.getClass().getMethod(
                                "setSemanticsEnabled", boolean.class);
                        setSemantics.invoke(candidate, true);
                        semanticsEnabled = true;
                        return;
                    } catch (NoSuchMethodException ignored) {
                        // Not FlutterJNI.
                    } catch (Throwable ignored) {
                        // Reflection failure on this field; try another.
                    }
                }
            }
            lastSemanticsProblem = "semantics API unavailable";
        } catch (Throwable error) {
            Log.w(TAG, "Unable to enable Flutter semantics", error);
            lastSemanticsProblem = "enable semantics error";
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
