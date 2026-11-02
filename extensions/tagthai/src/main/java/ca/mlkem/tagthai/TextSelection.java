package ca.mlkem.tagthai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** In-app, on-demand text selection. No service, storage or network permissions. */
public final class TextSelection {
    static final String BUTTON_TAG = "mlkem.tagthai.select-text";
    static final int MAX_NODES = 4096;
    static final int MAX_TEXT = 100000;
    static final long CAPTURE_TIMEOUT_MS = 2000;
    private static final Map<AccessibilityNodeProvider, WeakReference<Source>> SOURCES = new WeakHashMap<>();
    private TextSelection() {}

    public static void attach(View host, AccessibilityNodeProvider provider, Map<?, ?> nodes,
            AccessibilityManager.AccessibilityStateChangeListener listener) {
        if (host == null || provider == null || nodes == null || listener == null) return;
        Source source = new Source(host, provider, nodes, listener);
        SOURCES.put(provider, new WeakReference<>(source));
        host.addOnAttachStateChangeListener(source);
        host.post(() -> source.mount(host));
    }

    public static void onSemanticsUpdated(AccessibilityNodeProvider provider) {
        WeakReference<Source> reference = SOURCES.get(provider);
        Source source = reference == null ? null : reference.get();
        if (source != null) source.onFrame();
    }

    static Activity activity(Context context) {
        Set<Context> seen = new HashSet<>();
        while (context instanceof ContextWrapper && seen.add(context)) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    static final class Source implements View.OnAttachStateChangeListener {
        final WeakReference<View> host;
        final WeakReference<AccessibilityNodeProvider> provider;
        final WeakReference<Map<?, ?>> nodes;
        final WeakReference<AccessibilityManager.AccessibilityStateChangeListener> listener;
        Button button;
        AlertDialog dialog;
        boolean pending, changedSemantics;
        int request;
        Runnable timeout;

        Source(View view, AccessibilityNodeProvider bridge, Map<?, ?> semantics,
                AccessibilityManager.AccessibilityStateChangeListener stateListener) {
            host = new WeakReference<>(view);
            provider = new WeakReference<>(bridge);
            nodes = new WeakReference<>(semantics);
            listener = new WeakReference<>(stateListener);
        }

        void mount(View view) {
            Activity a = activity(view.getContext());
            if (!view.isAttachedToWindow() || a == null || a.isFinishing() || a.isDestroyed() || button != null) return;
            View content = a.findViewById(android.R.id.content);
            if (!(content instanceof FrameLayout) || content.findViewWithTag(BUTTON_TAG) != null) return;
            FrameLayout parent = (FrameLayout) content;
            Button chip = new Button(a);
            chip.setTag(BUTTON_TAG);
            chip.setText("Select text");
            chip.setContentDescription("Select visible text. Drag to move.");
            chip.setAllCaps(false);
            chip.setMinHeight(dp(a, 48));
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
            layout.setMargins(dp(a, 12), dp(a, 56), dp(a, 12), dp(a, 12));
            parent.addView(chip, layout);
            chip.setElevation(dp(a, 8));
            chip.setOnClickListener(v -> capture());
            chip.setOnTouchListener(new Drag(parent));
            button = chip;
        }

        boolean systemAccessibilityEnabled() {
            View view = host.get();
            AccessibilityManager manager = view == null ? null :
                (AccessibilityManager) view.getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
            return manager != null && manager.isEnabled();
        }

        void capture() {
            View view = host.get();
            AccessibilityManager.AccessibilityStateChangeListener state = listener.get();
            if (pending || dialog != null || view == null || !view.isAttachedToWindow() || state == null) return;
            pending = true;
            final int ticket = ++request;
            if (button != null) { button.setEnabled(false); button.setText("Reading text…"); }
            timeout = () -> finish(ticket, false);
            view.postDelayed(timeout, CAPTURE_TIMEOUT_MS);
            if (systemAccessibilityEnabled()) {
                view.post(() -> finish(ticket, true));
                return;
            }
            // This is the only place we activate semantics, after an explicit tap.
            // Wait for the delegate's next complete update, not a timer or old cache.
            changedSemantics = true;
            try { state.onAccessibilityStateChanged(true); }
            catch (RuntimeException unavailable) { finish(ticket, false); }
        }

        void onFrame() {
            View view = host.get();
            if (!pending || view == null) return;
            final int ticket = request;
            view.post(() -> finish(ticket, true));
        }

        void restore() {
            if (!changedSemantics) return;
            changedSemantics = false;
            AccessibilityManager.AccessibilityStateChangeListener state = listener.get();
            if (state != null) {
                try { state.onAccessibilityStateChanged(systemAccessibilityEnabled()); }
                catch (RuntimeException disposed) { /* The original listener guards disposed bridges. */ }
            }
        }

        void finish(int ticket, boolean fresh) {
            if (!pending || ticket != request) return;
            pending = false;
            View view = host.get();
            if (view != null && timeout != null) view.removeCallbacks(timeout);
            timeout = null;
            if (button != null) { button.setEnabled(true); button.setText("Select text"); }
            AccessibilityNodeProvider bridge = provider.get();
            Map<?, ?> semantics = nodes.get();
            Rect viewport = new Rect();
            String text = "";
            try {
                if (fresh && view != null && view.isAttachedToWindow() && bridge != null && semantics != null && view.getGlobalVisibleRect(viewport)) {
                    int[] rootLocation = new int[2];
                    view.getRootView().getLocationOnScreen(rootLocation);
                    viewport.offset(rootLocation[0], rootLocation[1]);
                    text = collect(bridge, semantics, viewport);
                }
            } finally { restore(); }
            Activity a = view == null ? null : activity(view.getContext());
            if (a == null || a.isFinishing() || a.isDestroyed() || !view.isAttachedToWindow()) return;
            if (text.isEmpty()) {
                Toast.makeText(a, "No selectable text on this screen. Try again after it loads.", Toast.LENGTH_LONG).show();
                return;
            }
            show(a, text);
        }

        void show(Activity a, String text) {
            TextView body = new TextView(a);
            body.setText(text);
            body.setTextSize(18);
            body.setTextIsSelectable(true);
            body.setPadding(dp(a, 20), dp(a, 12), dp(a, 20), dp(a, 20));
            ScrollView scroll = new ScrollView(a);
            scroll.addView(body);
            dialog = new AlertDialog.Builder(a).setTitle("Select text").setView(scroll)
                .setPositiveButton("Done", null)
                .setNeutralButton("Copy all", (d, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("TAGTHAi text", text));
                }).create();
            dialog.setOnDismissListener(d -> dialog = null);
            if ((a.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0)
                dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            dialog.show();
        }

        public void onViewAttachedToWindow(View view) { view.post(() -> mount(view)); }
        public void onViewDetachedFromWindow(View view) {
            pending = false;
            request++;
            if (timeout != null) view.removeCallbacks(timeout);
            timeout = null;
            restore();
            if (dialog != null) { dialog.dismiss(); dialog = null; }
            if (button != null && button.getParent() instanceof ViewGroup) ((ViewGroup) button.getParent()).removeView(button);
            button = null;
        }
    }

    static String collect(AccessibilityNodeProvider provider, Map<?, ?> nodes, Rect viewport) {
        List<Integer> ids = new ArrayList<>();
        // The bridge and the button run on the main thread; snapshot IDs because
        // requesting a node can update bridge state. Never follow Binder children.
        for (Object key : nodes.keySet()) if (key instanceof Integer) ids.add((Integer) key);
        Collections.sort(ids);
        List<Line> lines = new ArrayList<>();
        Field flags, parent;
        try {
            if (ids.isEmpty()) return "";
            Class<?> nodeType = nodes.get(ids.get(0)).getClass();
            flags = nodeType.getDeclaredField("c");
            parent = nodeType.getDeclaredField("O");
            flags.setAccessible(true);
            parent.setAccessible(true);
        } catch (ReflectiveOperationException | RuntimeException unsupported) { return ""; }
        for (int index = 0; index < ids.size() && index < MAX_NODES; index++) {
            AccessibilityNodeInfo info = null;
            try {
                if (!visibleBranch(nodes.get(ids.get(index)), flags, parent)) continue;
                info = provider.createAccessibilityNodeInfo(ids.get(index));
                if (info == null || info.isPassword() || !info.isVisibleToUser()) continue;
                Rect bounds = new Rect();
                info.getBoundsInScreen(bounds);
                if (!Rect.intersects(bounds, viewport)) continue;
                String text = clean(info.getText());
                String description = clean(info.getContentDescription());
                if (!text.isEmpty()) lines.add(new Line(bounds, text));
                if (!description.isEmpty() && !description.equals(text)) lines.add(new Line(bounds, description));
            } catch (RuntimeException unavailable) {
                // A stale node should not crash the app or prevent other text.
            } finally { if (info != null) info.recycle(); }
        }
        Collections.sort(lines, Comparator.comparingInt((Line l) -> l.bounds.top)
            .thenComparingInt(l -> l.bounds.left));
        StringBuilder result = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (Line line : lines) {
            // Deduplicate repeated provider labels without dropping identical
            // words that appear at distinct positions on the screen.
            String key = line.bounds.toShortString() + '\n' + line.text;
            if (!seen.add(key)) continue;
            if (result.length() > 0) {
                if (result.length() + 2 >= MAX_TEXT) break;
                result.append("\n\n");
            }
            int remaining = MAX_TEXT - result.length();
            if (remaining <= 0) break;
            result.append(line.text, 0, Math.min(line.text.length(), remaining));
            if (result.length() >= MAX_TEXT) break;
        }
        return result.toString();
    }

    private static boolean visibleBranch(Object node, Field flags, Field parent) {
        // TAGTHAi 4.25.1's Flutter node fields are fingerprinted by the patch.
        // Flutter's provider marks even hidden nodes visible when queried by ID;
        // follow parents to exclude hidden routes and obscured descendants too.
        try {
            for (int depth = 0; node != null && depth < 128; depth++) {
                if ((flags.getInt(node) & 0x2000) != 0) return false; // IS_HIDDEN
                node = parent.get(node);
            }
            return node == null;
        } catch (IllegalAccessException | RuntimeException unavailable) { return false; }
    }

    private static String clean(CharSequence text) { return text == null ? "" : text.toString().trim(); }
    private static int dp(Context context, int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static final class Line {
        final Rect bounds; final String text;
        Line(Rect bounds, String text) { this.bounds = bounds; this.text = text; }
    }

    private static final class Drag implements View.OnTouchListener {
        final ViewGroup parent;
        final int slop;
        float downX, downY, startX, startY;
        boolean dragging;
        Drag(ViewGroup parent) { this.parent = parent; slop = ViewConfiguration.get(parent.getContext()).getScaledTouchSlop(); }
        public boolean onTouch(View view, MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getRawX(); downY = event.getRawY();
                    startX = view.getX(); startY = view.getY(); dragging = false;
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX, dy = event.getRawY() - downY;
                    if (dx * dx + dy * dy > slop * slop) dragging = true;
                    if (dragging) {
                        view.setX(Math.max(0, Math.min(parent.getWidth() - view.getWidth(), startX + dx)));
                        view.setY(Math.max(0, Math.min(parent.getHeight() - view.getHeight(), startY + dy)));
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) view.performClick();
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    dragging = false;
                    return true;
                default: return false;
            }
        }
    }
}
