package ca.mlkem.amazon;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.net.Uri;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/** Shows what this process observed, with no stored URLs, tags or account information. */
final class NativeReferralStatus implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = "ca.mlkem.amazon.native_referral_status";
    private static Application registered;
    private enum Status { UNSEEN, WAITING, SENT, FAILED }
    private static Status status = Status.UNSEEN;
    private static long sentAt;
    private static AmazonReferral.Tag sentTag = AmazonReferral.Tag.ABSENT;

    static void beginVisit() { status = Status.WAITING; }
    static void linkSent(Uri destination) {
        sentAt = System.currentTimeMillis();
        sentTag = AmazonReferral.tag(destination);
        status = Status.SENT;
    }
    static void launchFailed() { status = Status.FAILED; }

    static void register(Activity activity) {
        Application app = activity.getApplication();
        if (registered != app) {
            app.registerActivityLifecycleCallbacks(new NativeReferralStatus());
            registered = app;
        }
    }

    private static String label() {
        switch (status) {
            case WAITING: return "Waiting for link";
            case SENT:
                long minutes = Math.max(0, (System.currentTimeMillis() - sentAt) / DateUtils.MINUTE_IN_MILLIS);
                return "Sent to Amazon · " + (minutes == 0 ? "just now" : minutes < 60 ? minutes + " min ago" : minutes / 60 + " h ago");
            case FAILED: return "Link failed";
            default: return "No referral seen";
        }
    }

    private static String details() {
        switch (status) {
            case SENT:
                CharSequence when = DateUtils.getRelativeTimeSpanString(sentAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
                return "The eStore shopping flow sent a link to the Amazon app " + when
                    + ". " + sentTag.description + ". Amazon has not confirmed ongoing tracking or points for an order.";
            case FAILED: return "Amazon could not open the referral link. Start a new eStore visit to try again.";
            case WAITING: return "The eStore visit started. The helper is waiting for a link to be sent to Amazon.";
            default: return "No eStore link has been handed to the Amazon app during this session.";
        }
    }

    private static final class Chip extends TextView implements Runnable {
        Chip(Activity activity) { super(activity); }
        @Override public void run() {
            removeCallbacks(this);
            String label = label();
            setText("Aeroplan · " + label);
            setContentDescription("Aeroplan: " + label + ". Open referral details.");
            if (status == Status.SENT && isAttachedToWindow() && getWindowVisibility() == View.VISIBLE)
                postDelayed(this, DateUtils.MINUTE_IN_MILLIS);
        }
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == View.VISIBLE) run(); else removeCallbacks(this);
        }
        @Override protected void onDetachedFromWindow() {
            removeCallbacks(this);
            super.onDetachedFromWindow();
        }
    }

    static void attach(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof FrameLayout)) return;
        FrameLayout frame = (FrameLayout) decor;
        Chip chip = frame.findViewWithTag(TAG);
        if (chip == null) {
            float density = activity.getResources().getDisplayMetrics().density;
            chip = new Chip(activity);
            chip.setTag(TAG);
            chip.setTextSize(13);
            chip.setMaxWidth(Math.round(activity.getResources().getDisplayMetrics().widthPixels * .8f));
            chip.setMaxLines(2);
            chip.setTextColor(Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(Math.round(42 * density));
            chip.setPadding(Math.round(12 * density), 0, Math.round(12 * density), 0);
            chip.setFocusable(true);
            GradientDrawable pill = new GradientDrawable();
            pill.setCornerRadius(24 * density);
            pill.setColor(0xff234956);
            chip.setBackground(pill);
            chip.setElevation(6 * density);
            chip.setOnClickListener(v -> new AlertDialog.Builder(activity)
                .setTitle("Aeroplan referral status")
                .setMessage(details() + "\n\nWeb cart review starts a separate eStore visit to check products and cart timing.")
                .setNegativeButton("Close", null)
                .setNeutralButton("Start referral", (d, w) -> AeroplanBridge.open(activity, true))
                .setPositiveButton("Web cart review", (d, w) -> ReviewActivity.open(activity)).show());
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            final int margin = Math.round(8 * density);
            final int top = Math.round(56 * density);
            lp.setMarginEnd(margin);
            lp.topMargin = top;
            chip.setOnApplyWindowInsetsListener((v, insets) -> {
                FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) v.getLayoutParams();
                int insetEnd = v.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? insets.getSystemWindowInsetLeft() : insets.getSystemWindowInsetRight();
                int end = margin + insetEnd;
                int offset = top + insets.getSystemWindowInsetTop();
                if (p.topMargin != offset || p.getMarginEnd() != end) {
                    p.topMargin = offset;
                    p.setMarginEnd(end);
                    v.setLayoutParams(p);
                }
                return insets;
            });
            frame.addView(chip, lp);
            chip.requestApplyInsets();
        }
        chip.bringToFront();
        chip.run();
    }

    @Override public void onActivityResumed(Activity activity) {
        String name = activity.getClass().getName();
        if (shoppingScreen(name)) attach(activity);
    }

    static boolean shoppingScreen(String name) {
        return (name.startsWith("com.amazon.mShop.") && !name.equals(NativeAmazon.DEEP_LINK_ACTIVITY))
                || name.equals("com.amazon.mobile.mash.embeddedbrowser.EmbeddedBrowserActivity");
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) {}
    @Override public void onActivityStarted(Activity activity) {}
    @Override public void onActivityPaused(Activity activity) {}
    @Override public void onActivityStopped(Activity activity) {}
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) {}
    @Override public void onActivityDestroyed(Activity activity) {}
}
