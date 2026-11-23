package ca.mlkem.amazon;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.http.SslError;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** The eStore is visible for sign-in; its Amazon destination opens in the native app. */
public final class AeroplanActivity extends Activity {
    public static final String ACTION_START = "ca.mlkem.amazon.START";
    public static final String ACTION_SETTINGS = "ca.mlkem.amazon.SETTINGS";
    private static final int INK = Color.rgb(26, 36, 43);
    private static final int ACCENT = Color.rgb(174, 25, 39);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<WebView> windows = new ArrayList<>();
    private FrameLayout content;
    private TextView status;
    private Button fillButton;
    private ProfileStore.Profile profile = ProfileStore.empty();
    private boolean settingsVisible, resumed, loadingProfileFailed;
    private boolean handoffAttempted;
    private String pendingAmazonUrl;
    private int fillAttempts;

    private final Runnable fillPoll = new Runnable() {
        @Override public void run() {
            WebView view = current();
            if (!resumed || settingsVisible || handoffAttempted || view == null || !SessionRules.canAutofill(view.getUrl())) return;
            if (++fillAttempts > 120) return;
            fill(view, false);
            handler.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        // Do not save account fields or referral URLs in Android's activity-state bundle.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        loadProfile();
        buildChrome();
        if (state != null) {
            showHome("The previous referral window was closed. Start a new eStore visit before shopping.");
        } else if (ACTION_START.equals(getIntent().getAction())) {
            startSession();
        } else {
            showSettings();
        }
    }

    private void loadProfile() {
        try { profile = ProfileStore.load(this); loadingProfileFailed = false; }
        catch (Exception unavailable) { profile = ProfileStore.empty(); loadingProfileFailed = true; }
    }

    private void buildChrome() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(4), dp(8), dp(4));
        Button back = button("Back", v -> back());
        bar.addView(back, new LinearLayout.LayoutParams(dp(70), dp(48)));
        TextView title = text("Aeroplan referral", 17);
        title.setGravity(Gravity.CENTER);
        bar.addView(title, new LinearLayout.LayoutParams(0, dp(48), 1));
        Button settings = button("Settings", v -> showSettings());
        bar.addView(settings, new LinearLayout.LayoutParams(dp(92), dp(48)));
        root.addView(bar);
        status = text("", 12);
        status.setBackgroundColor(Color.rgb(242, 245, 247));
        status.setPadding(dp(16), dp(9), dp(16), dp(9));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        fillButton = button("Fill saved details", v -> {
            WebView view = current();
            if (view != null) fill(view, true);
        });
        fillButton.setVisibility(View.GONE);
        root.addView(fillButton, new LinearLayout.LayoutParams(-1, dp(44)));
        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        root.addView(button("Aeroplan cart and orders", v -> ReviewActivity.open(this)));
        root.addView(button("Return to Amazon app", v -> leave()), new LinearLayout.LayoutParams(-1, dp(48)));
        setContentView(root);
    }

    private LinearLayout page() {
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout body = new LinearLayout(this);
        body.setPadding(dp(24), dp(24), dp(24), dp(24));
        body.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(body, new ScrollView.LayoutParams(-1, -2));
        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        return body;
    }

    private void paragraph(LinearLayout body, String value) {
        TextView text = text(value, 15);
        text.setPadding(0, dp(8), 0, dp(14));
        body.addView(text);
    }

    private void showHome(String note) {
        settingsVisible = true;
        handler.removeCallbacks(fillPoll);
        fillButton.setVisibility(View.GONE);
        status.setText("Ready to start a new eStore visit");
        LinearLayout body = page();
        body.addView(text("Start with the eStore", 25));
        paragraph(body, note);
        paragraph(body, "1. Open the eStore and review the eligible Amazon categories.\n\n"
                + "2. Tap Sign in to earn. Review your details, tap Continue, then Shop & earn.\n\n"
                + "3. The referral opens the Amazon app automatically. Shop and check out there. Items already in your cart do not qualify.");
        body.addView(button("Start a new session", v -> confirmNewSession()));
        if (current() != null && !handoffAttempted) body.addView(button("Return to eStore", v -> showWindow(current())));
        body.addView(button("Edit Aeroplan settings", v -> showSettings()));
    }

    private void showSettings() {
        settingsVisible = true;
        handler.removeCallbacks(fillPoll);
        fillButton.setVisibility(View.GONE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        status.setText("Saved on this device · sent only to the official eStore form");
        LinearLayout body = page();
        body.addView(text("Aeroplan settings", 25));
        paragraph(body, "Enter the account that should earn the points. Your details are encrypted on this device and are never included in the patch.");
        if (loadingProfileFailed) paragraph(body, "Saved details could not be opened. Enter them again or clear the saved profile.");
        body.addView(text("Aeroplan number", 14));
        EditText number = new EditText(this);
        number.setInputType(InputType.TYPE_CLASS_NUMBER);
        number.setSingleLine(true);
        number.setHint("9-digit Aeroplan number");
        number.setText(profile.number);
        number.setSaveEnabled(false);
        number.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        number.setContentDescription("Aeroplan number");
        body.addView(number, new LinearLayout.LayoutParams(-1, dp(56)));
        body.addView(text("Last name", 14));
        EditText lastName = new EditText(this);
        lastName.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
        lastName.setSingleLine(true);
        lastName.setHint("Last name on your Aeroplan account");
        lastName.setText(profile.lastName);
        lastName.setSaveEnabled(false);
        lastName.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        lastName.setContentDescription("Last name");
        body.addView(lastName, new LinearLayout.LayoutParams(-1, dp(56)));
        CheckBox automatic = new CheckBox(this);
        automatic.setText("Open the eStore automatically at Amazon startup");
        automatic.setTextColor(INK);
        automatic.setChecked(profile.autoOpen);
        automatic.setPadding(0, dp(16), 0, dp(12));
        body.addView(automatic);
        paragraph(body, "Review your account and tap Shop & earn on the eStore. Shopping then opens in the Amazon app. This helper cannot verify or guarantee points after the handoff.");
        body.addView(button("Save settings", v -> {
            String normalized = SessionRules.normalizeNumber(number.getText().toString());
            String familyName = lastName.getText().toString().trim();
            if (!SessionRules.validNumber(normalized)) {
                number.setError("Enter a 9-digit Aeroplan number"); number.requestFocus(); return;
            }
            if (!SessionRules.validLastName(familyName)) {
                lastName.setError("Enter your last name"); lastName.requestFocus(); return;
            }
            ProfileStore.Profile replacement = new ProfileStore.Profile(normalized, familyName, automatic.isChecked());
            try {
                ProfileStore.save(this, replacement);
                profile = replacement;
                loadingProfileFailed = false;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                showHome("Settings saved. If the eStore is already signed in, verify that it shows the intended Aeroplan account before continuing.");
            } catch (Exception error) {
                toast("Could not save securely. Your details have not been saved.");
            }
        }));
        if (current() != null && !handoffAttempted) body.addView(button("Return to eStore", v -> showWindow(current())));
        body.addView(button(profile.configured() ? "Start a new eStore session" : "Sign in on the eStore", v -> confirmNewSession()));
        body.addView(button("Clear saved details", v -> new AlertDialog.Builder(this)
                .setTitle("Clear saved Aeroplan details?")
                .setMessage("This removes the helper's saved profile and closes its eStore window. Website sign-ins are managed separately by the eStore and Amazon.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> {
                    try {
                        ProfileStore.clear(this);
                        profile = ProfileStore.empty(); loadingProfileFailed = false;
                        destroyWindows(); showSettings(); toast("Saved details cleared");
                    } catch (Exception error) { toast("Could not completely clear the saved profile. Try again."); }
                }).show()));
        paragraph(body, "For another visit, long-press the Amazon app icon and choose Aeroplan if your launcher has room for the shortcut. The startup prompt also returns after Amazon fully restarts.");
    }

    private void confirmNewSession() {
        if (current() == null) { startSession(); return; }
        new AlertDialog.Builder(this).setTitle("Start a new session?")
                .setMessage("This restarts the eStore referral. Items already in your cart are not made eligible by starting again.")
                .setNegativeButton("Keep current", null)
                .setPositiveButton("Start new", (dialog, which) -> startSession()).show();
    }

    private void startSession() {
        NativeReferralStatus.beginVisit();
        destroyWindows();
        handoffAttempted = false;
        pendingAmazonUrl = null;
        WebView view = createWebView();
        windows.add(view);
        showWindow(view);
        view.loadUrl(SessionRules.ESTORE_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWebView() {
        WebView view = new WebView(this);
        view.setSaveEnabled(false);
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSupportMultipleWindows(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSafeBrowsingEnabled(true);
        // Preserve the eStore sign-in and referral redirect session until native handoff.
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView source, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return handleNavigation(source, request.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView source, String url) {
                return handleNavigation(source, url);
            }
            @Override public void onPageStarted(WebView source, String url, android.graphics.Bitmap icon) {
                // Redirects and window.open navigations can also arrive here.
                if (handoffAttempted || NativeAmazon.destination(url) != null) {
                    source.stopLoading();
                    handleNavigation(source, url);
                    return;
                }
                if (source == current()) {
                    handler.removeCallbacks(fillPoll);
                    fillAttempts = 0;
                    updateStatus(url);
                }
            }
            @Override public void onPageFinished(WebView source, String url) {
                if (handoffAttempted || NativeAmazon.destination(url) != null) {
                    source.stopLoading();
                    handleNavigation(source, url);
                    return;
                }
                CookieManager.getInstance().flush();
                if (source == current()) {
                    updateStatus(url);
                    if (SessionRules.canAutofill(url)) restartFillPoll();
                }
            }
            @Override public void onReceivedSslError(WebView source, SslErrorHandler ssl, SslError error) {
                ssl.cancel();
                if (!handoffAttempted && source == current()) status.setText("Connection could not be verified. This page was blocked.");
            }
            @Override public void onReceivedError(WebView source, WebResourceRequest request, WebResourceError error) {
                if (!handoffAttempted && request.isForMainFrame() && source == current()) {
                    handler.removeCallbacks(fillPoll);
                    status.setText("Page could not load. Use Back or start a new session from Settings.");
                }
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView source, boolean dialog, boolean userGesture, Message result) {
                if (!userGesture || settingsVisible || handoffAttempted || isFinishing()
                        || !windows.contains(source) || windows.size() >= 5) return false;
                WebView child = createWebView();
                windows.add(child);
                showWindow(child);
                ((WebView.WebViewTransport) result.obj).setWebView(child);
                result.sendToTarget();
                return true;
            }
            @Override public void onCloseWindow(WebView target) {
                if (!handoffAttempted && !isFinishing()) closeWindow(target);
            }
        });
        return view;
    }

    private boolean handleNavigation(WebView source, String url) {
        if (handoffAttempted || isFinishing() || isDestroyed() || !windows.contains(source)) return true;
        String destination = NativeAmazon.destination(url);
        if (destination != null) {
            openAmazon(destination);
            return true;
        }
        if ("about:blank".equals(url)) return false; // Empty popup before its referral destination arrives.
        if (SessionRules.isHttps(url)) return false;
        toast("This link could not be opened. Continue through the eStore or return to Amazon.");
        return true;
    }

    private void openAmazon(String destination) {
        if (handoffAttempted || isFinishing() || isDestroyed()) return;
        handoffAttempted = true;
        pendingAmazonUrl = destination;
        handler.removeCallbacks(fillPoll);
        fillButton.setVisibility(View.GONE);
        for (WebView view : windows) view.stopLoading();
        CookieManager.getInstance().flush();
        status.setText("Opening the referral in the Amazon app…");
        if (NativeAmazon.open(this, destination)) {
            finish();
            return;
        }
        settingsVisible = true;
        status.setText("Amazon could not open the referral link");
        LinearLayout body = page();
        body.addView(text("Open Amazon to continue", 25));
        paragraph(body, "The referral has not been handed to Amazon. Try opening it again, or restart the eStore visit. Returning to Amazon with the button below skips this link.");
        body.addView(button("Try opening Amazon again", v -> {
            String retry = pendingAmazonUrl;
            handoffAttempted = false;
            openAmazon(retry);
        }));
        body.addView(button("Restart eStore referral", v -> startSession()));
    }

    private void showWindow(WebView view) {
        if (handoffAttempted) { showHome("Start a new eStore visit to try the referral again."); return; }
        if (view == null) { showHome("Start a fresh visit through the official eStore."); return; }
        settingsVisible = false;
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
        content.removeAllViews();
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        updateStatus(view.getUrl());
        restartFillPoll();
    }

    private void updateStatus(String url) {
        if (settingsVisible) return;
        boolean portal = SessionRules.canAutofill(url);
        fillButton.setVisibility(portal ? View.VISIBLE : View.GONE);
        if (portal) status.setText("aeroplan.rewardops.com · Review the account and offer, then continue through the eStore.");
        else status.setText(SessionRules.host(url).isEmpty() ? "Opening eStore referral…" : SessionRules.host(url));
    }

    private void fill(WebView view, boolean manual) {
        if (handoffAttempted || !profile.configured() || !SessionRules.canAutofill(view.getUrl())) return;
        // A second origin check runs inside the document, covering navigation races.
        view.evaluateJavascript(AutofillScript.create(profile.number, profile.lastName, manual), result -> {
            if (handoffAttempted || view != current() || settingsVisible || !SessionRules.canAutofill(view.getUrl())) return;
            if ("\"filled\"".equals(result)) {
                status.setText("Details filled on aeroplan.rewardops.com. Review them and tap Continue.");
            } else if (manual && "\"waiting\"".equals(result)) {
                toast("Tap Sign in to earn on the eStore first.");
            } else if (manual && "\"user-entered\"".equals(result)) {
                toast("The form already contains details. Review or edit them on the page.");
            }
        });
    }

    private void restartFillPoll() {
        handler.removeCallbacks(fillPoll);
        fillAttempts = 0;
        if (resumed && !settingsVisible && !handoffAttempted) handler.post(fillPoll);
    }

    private WebView current() { return windows.isEmpty() ? null : windows.get(windows.size() - 1); }

    private void closeWindow(WebView view) {
        if (!windows.remove(view)) return;
        if (view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
        view.stopLoading(); view.destroy();
        showWindow(current());
    }

    private void destroyWindows() {
        handler.removeCallbacks(fillPoll);
        if (content != null) content.removeAllViews();
        for (WebView view : windows) { view.stopLoading(); view.destroy(); }
        windows.clear();
    }

    private void back() {
        WebView view = current();
        if (settingsVisible && view != null && !handoffAttempted) { showWindow(view); return; }
        if (!settingsVisible && view != null && view.canGoBack()) { view.goBack(); return; }
        if (!settingsVisible && windows.size() > 1) { closeWindow(view); return; }
        leave();
    }

    private void leave() {
        AeroplanBridge.suppressStartupOffer();
        if (isTaskRoot() && !NativeAmazon.openHome(this)) toast("Open Amazon from your launcher to continue.");
        finish();
    }

    @Override public void onBackPressed() { back(); }
    @Override protected void onResume() {
        super.onResume(); resumed = true;
        for (WebView view : windows) view.onResume();
        restartFillPoll();
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(fillPoll);
        for (WebView view : windows) view.onPause();
        super.onPause();
    }
    @Override protected void onDestroy() { destroyWindows(); super.onDestroy(); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(INK); return text;
    }
    private Button button(String label, View.OnClickListener action) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false);
        button.setTextColor(ACCENT); button.setOnClickListener(action); return button;
    }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); }
}
