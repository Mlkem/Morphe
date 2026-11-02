package ca.mlkem.tagthai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowLooper;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class TextSelectionTest {
    // Match the verified Flutter semantics fields, including a hidden ancestor.
    static final class Node { int c; Node O; Node(int flags, Node parent) { c=flags; O=parent; } }
    static final class Provider extends AccessibilityNodeProvider {
        final Map<Integer, AccessibilityNodeInfo> info = new HashMap<>();
        final Map<Integer, Node> nodes = new HashMap<>();
        void add(int id, String text, String description, Rect bounds, boolean visible, boolean password, Node node) {
            AccessibilityNodeInfo n = AccessibilityNodeInfo.obtain();
            n.setText(text); n.setContentDescription(description); n.setBoundsInScreen(bounds);
            n.setVisibleToUser(visible); n.setPassword(password);
            info.put(id, n); nodes.put(id, node);
        }
        public AccessibilityNodeInfo createAccessibilityNodeInfo(int id) {
            if (id == 999) throw new IllegalStateException("stale node");
            return info.containsKey(id) ? AccessibilityNodeInfo.obtain(info.get(id)) : null;
        }
    }
    static final Rect SCREEN = new Rect(0, 0, 1080, 1920);
    static Rect at(int y) { return new Rect(10, y, 900, y + 40); }

    @Test public void copiesVisibleThaiAndEnglishInReadingOrder() {
        Provider p = new Provider();
        p.add(1, "Second", "Second", at(100), true, false, new Node(0, null));
        p.add(2, null, "สวัสดี", at(20), true, false, new Node(0, null));
        p.add(3, "Second", null, at(100), true, false, new Node(0, null));
        assertEquals("สวัสดี\n\nSecond", TextSelection.collect(p, p.nodes, SCREEN));
    }

    @Test public void excludesPasswordsOffscreenInvisibleAndHiddenBranches() {
        Provider p = new Provider();
        p.add(1, "visible", null, at(20), true, false, new Node(0, null));
        p.add(2, "password", null, at(80), true, true, new Node(0, null));
        p.add(3, "offscreen", null, at(2200), true, false, new Node(0, null));
        p.add(4, "invisible", null, at(100), false, false, new Node(0, null));
        p.add(5, "hidden", null, at(100), true, false, new Node(0x2000, null));
        p.add(6, "hidden child", null, at(150), true, false, new Node(0, new Node(0x2000, null)));
        Node loop = new Node(0, null); loop.O=loop;
        p.add(7, "bad cycle", null, at(150), true, false, loop);
        p.nodes.put(998, new Node(0, null));
        p.nodes.put(999, new Node(0, null));
        assertEquals("visible", TextSelection.collect(p, p.nodes, SCREEN));
    }

    @Test public void unsupportedNodeLayoutReturnsNoText() {
        Provider p = new Provider();
        p.add(1, "text", null, at(20), true, false, new Node(0, null));
        Map<Integer, Object> unsupported = new HashMap<>(); unsupported.put(1, new Object());
        assertEquals("", TextSelection.collect(p, unsupported, SCREEN));
    }

    @Test public void boundsOutputAndPreservesRepeatedWordsAtDifferentPositions() {
        Provider p = new Provider();
        p.add(1, "Yes", null, at(20), true, false, new Node(0, null));
        p.add(2, "Yes", null, at(100), true, false, new Node(0, null));
        assertEquals("Yes\n\nYes", TextSelection.collect(p, p.nodes, SCREEN));
        p.add(3, String.join("", java.util.Collections.nCopies(120000, "x")), null, at(200), true, false, new Node(0, null));
        assertEquals(TextSelection.MAX_TEXT, TextSelection.collect(p, p.nodes, SCREEN).length());
    }

    @Test public void panelSelectsTextAndCopiesOnlyOnRequestAndCleansUp() {
        Activity a = Robolectric.buildActivity(Activity.class).setup().visible().get();
        FrameLayout host = new FrameLayout(a); a.setContentView(host);
        host.layout(0, 0, 1080, 1920);
        Provider p = new Provider();
        p.add(1, "Hello ไทย", null, at(200), true, false, new Node(0, null));
        TextSelection.attach(host, p, p.nodes);
        ShadowLooper.idleMainLooper();
        Button button = a.findViewById(android.R.id.content).findViewWithTag(TextSelection.BUTTON_TAG);
        assertNotNull(button);
        ClipboardManager clipboard = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.clearPrimaryClip();
        a.getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        button.performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull(dialog); assertTrue(dialog.isShowing());
        assertTrue((dialog.getWindow().getAttributes().flags & android.view.WindowManager.LayoutParams.FLAG_SECURE) != 0);
        assertFalse(clipboard.hasPrimaryClip());
        TextView text = findText(dialog.getWindow().getDecorView(), "Hello ไทย");
        assertNotNull(text); assertTrue(text.isTextSelectable());
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        ShadowLooper.idleMainLooper();
        assertEquals("Hello ไทย", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
        button.performClick();
        AlertDialog second = ShadowAlertDialog.getLatestAlertDialog();
        a.setContentView(new View(a));
        ShadowLooper.idleMainLooper();
        assertFalse(second.isShowing());
        assertNull(a.findViewById(android.R.id.content).findViewWithTag(TextSelection.BUTTON_TAG));
    }

    private static TextView findText(View view, String text) {
        if (view instanceof TextView && text.contentEquals(((TextView) view).getText())) return (TextView) view;
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i=0; i<group.getChildCount(); i++) { TextView result=findText(group.getChildAt(i),text); if(result!=null)return result; }
        }
        return null;
    }
}
