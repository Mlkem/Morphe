package ca.mlkem.amazon;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.*;
import android.text.InputType;
import android.view.View;

import android.webkit.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.DateFormat;
import java.util.*;
import org.json.*;

/** Read-only points helper. The member controls sign-in, shopping and checkout in the website. */
public final class ReviewActivity extends Activity {
    private ReferralVisit visit;
    private boolean walmart, resumed, inspecting;
    private TextView banner;
    private Button activation, retry;
    private WalmartHandoff handoff;
    private FrameLayout frame;
    private final List<WebView> tabs = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String script, summary = "";
    private JSONObject snapshot, liveRules, lastCart;
    private WebView committedView;
    private String failedUrl;
    private String rate = "";
    private OrderLog orders;
    private int generation;
    private final Runnable tick = new Runnable() {
        public void run() {
            WebView view = current();
            if (pendingHandoff() && view != null) {
                String next = failedUrl == null ? handoff.autoContinue(view.getUrl(), SystemClock.elapsedRealtime()) : null;
                if (next != null) view.loadUrl(next);
                draw();
            }
            inspect();
            if (resumed) handler.postDelayed(this, 2200);
        }
    };
    static void open(Activity from) { from.startActivity(new Intent(from, ReviewActivity.class)); }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        walmart = "ca.walmart.ecommerceapp".equals(getPackageName());
        visit = new ReferralVisit(walmart);
        orders = new OrderLog(this,walmart);
        try (InputStream in = getAssets().open("mlkem_aeroplan_review.js");
             BufferedReader reader = new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))) {
            StringBuilder source = new StringBuilder();
            for (String line; (line = reader.readLine()) != null;) source.append(line).append('\n');
            script = source.toString();
        } catch (IOException e) { script = null; }
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setFitsSystemWindows(true);
        LinearLayout bar = new LinearLayout(this); root.addView(bar);
        activation = action(bar, "Activate", v -> {
            if (pendingHandoff()) {
                if (handoff.currentClick() != null) {
                    openReferralInBrowser(handoff.currentClick());
                    return;
                }
                String next = handoff.continueVisit();
                if (next != null) { current().loadUrl(next); draw(); }
                else new AlertDialog.Builder(this).setTitle("Open the eStore in your browser?")
                    .setMessage("Start a new Walmart referral there and finish shopping in that browser.")
                    .setNegativeButton("Stay here", null).setPositiveButton("Open eStore", (d,w) -> external(visit.offer)).show();
                return;
            }
            if (current() == null || visit.arrived == 0) startOffer();
            else new AlertDialog.Builder(this).setTitle("Start a new referral?")
                .setMessage("Items already in your cart may need to be added again.")
                .setNegativeButton("Keep shopping",null).setPositiveButton("Activate", (d,w)->startOffer()).show();
        });
        action(bar,"Review",v -> review()); action(bar,"Orders",v -> orders());
        banner = new TextView(this); banner.setTextSize(14); banner.setTextColor(Color.WHITE);
        banner.setPadding(dp(12),dp(8),dp(12),dp(8)); banner.setOnClickListener(v -> review()); root.addView(banner);
        retry = new Button(this);
        retry.setAllCaps(false); retry.setText("Retry referral in Walmart");
        retry.setOnClickListener(v -> {
            if (!pendingHandoff() || current() == null) return;
            String target = handoff.retryClick(SystemClock.elapsedRealtime());
            if (target != null) { failedUrl = null; summary = ""; current().loadUrl(target); draw(); }
        });
        root.addView(retry);
        frame = new FrameLayout(this); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1)); setContentView(root);
        startOffer();
    }
    private Button action(LinearLayout bar, String name, View.OnClickListener click) {
        Button b=new Button(this); b.setText(name); b.setAllCaps(false); b.setOnClickListener(click);
        bar.addView(b,new LinearLayout.LayoutParams(0,-2,1)); return b;
    }
    private int dp(int value) { return Math.round(getResources().getDisplayMetrics().density*value); }
    private void startOffer() {
        destroyTabs(); visit = new ReferralVisit(walmart); handoff = walmart ? new WalmartHandoff() : null; liveRules=null; lastCart=null; rate="";
        WebView view = newTab(); view.loadUrl(visit.offer); draw();
    }
    private boolean pendingHandoff() { return handoff != null && visit.sawRedirect && visit.arrived == 0; }
    private void observe(String url) {
        if (pendingHandoff() && visit.offerPage(url)) {
            visit.sawRedirect = false;
            handoff = new WalmartHandoff();
        }
        visit.observe(url,System.currentTimeMillis());
        if (pendingHandoff()) handoff.observe(url, SystemClock.elapsedRealtime());
        if (visit.arrived != 0) handoff = null;
    }
    private WebView current() { return tabs.isEmpty() ? null : tabs.get(tabs.size()-1); }
    private void invalidatePage() { generation++; inspecting=false; summary=""; snapshot=null; }
    private WebView newTab() {
        invalidatePage();
        WebView view = new WebView(this);
        WebSettings s=view.getSettings(); s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(true); s.setJavaScriptCanOpenWindowsAutomatically(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,true);
        view.setSaveEnabled(false);
        view.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r) {
                if (!r.isForMainFrame()) return false;
                String url = r.getUrl().toString();
                if (SessionRules.isHttps(url)) {
                    if (v == current() && pendingHandoff() && WalmartHandoff.rakuten(url)) {
                        handoff.observe(url, SystemClock.elapsedRealtime());
                        draw();
                    }
                    return false;
                }
                if (v == current() && pendingHandoff()) {
                    String next = WalmartHandoff.browserDestination(url);
                    if (next != null) v.loadUrl(next);
                    else if (handoff.currentClick() != null && (url.startsWith("intent:") || url.startsWith("walmart:"))) {
                        handoff.recordClickError(handoff.currentClick(), "Rakuten tried to open an app without a Walmart web address.");
                        draw();
                    }
                }
                return true;
            }
            @Override public void onPageStarted(WebView v,String url,Bitmap icon) {
                if (v!=current()) return;
                invalidatePage(); committedView=null; failedUrl=null;
                if (!visit.store(url)) observe(url);
                else if (pendingHandoff()) handoff.observe(url, SystemClock.elapsedRealtime());
                draw();
            }
            @Override public void doUpdateVisitedHistory(WebView v,String url,boolean reload) {
                if (v==current()) { invalidatePage(); if (!visit.store(url) || committedView==v) observe(url); draw(); }
            }
            @Override public void onPageFinished(WebView v,String url) {
                if (v==current() && url.equals(v.getUrl()) && !url.equals(failedUrl)) { committedView=v; observe(url); draw(); inspect(); }
            }
            private void fail(WebView v,String url) {
                if (v!=current()) return;
                failedUrl=url; committedView=null; invalidatePage();
                if (visit.arrived!=0) visit.interrupted=true;
                summary="Page could not be verified"; draw();
            }
            @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError error) {
                if (!r.isForMainFrame() || v != current()) return;
                String url = r.getUrl().toString();
                int code = error.getErrorCode();
                if (pendingHandoff() && WalmartHandoff.rakuten(url)) {
                    handoff.observe(url, SystemClock.elapsedRealtime());
                    String reason;
                    if (code == ERROR_HOST_LOOKUP) reason = "The phone couldn’t resolve the Rakuten domain.";
                    else if (code == ERROR_TIMEOUT) reason = "The connection to Rakuten timed out.";
                    else if (String.valueOf(error.getDescription()).contains("ERR_BLOCKED_BY_CLIENT")) reason = "The Rakuten request was blocked.";
                    else reason = "The connection to Rakuten failed.";
                    handoff.recordClickError(url, reason);
                }
                fail(v,url);
                if (pendingHandoff() && (code == ERROR_HOST_LOOKUP || code == ERROR_CONNECT || code == ERROR_TIMEOUT)) {
                    String next = handoff.relayFailed(url);
                    if (next != null) { v.loadUrl(next); draw(); }
                }
            }
            @Override public void onReceivedHttpError(WebView v,WebResourceRequest r,WebResourceResponse response) {
                if (!r.isForMainFrame() || v != current()) return;
                String url = r.getUrl().toString();
                if (pendingHandoff() && WalmartHandoff.rakuten(url)) {
                    handoff.observe(url, SystemClock.elapsedRealtime());
                    handoff.recordClickError(url, "Rakuten returned HTTP " + response.getStatusCode() + ".");
                }
                fail(v,url);
            }
            @Override public void onReceivedSslError(WebView v,SslErrorHandler h,SslError e) {
                h.cancel();
                if (v == current() && pendingHandoff() && e.getUrl().equals(handoff.currentClick())) {
                    handoff.recordClickError(e.getUrl(), "Android couldn’t verify the secure connection to Rakuten.");
                    fail(v,e.getUrl());
                } else if (e.getUrl().equals(v.getUrl())) fail(v,e.getUrl());
            }
        });
        view.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onCreateWindow(WebView parent,boolean dialog,boolean gesture,Message result) {
                if (!gesture || parent!=current() || tabs.size()>=4) return false;
                WebView child=newTab();
                ((WebView.WebViewTransport)result.obj).setWebView(child); result.sendToTarget(); return true;
            }
            @Override public void onCloseWindow(WebView window) { if (window==current() && tabs.size()>1) closeTab(); }
        });
        tabs.add(view); frame.removeAllViews(); frame.addView(view); return view;
    }
    private void draw() {
        long now = SystemClock.elapsedRealtime();
        String click = pendingHandoff() ? handoff.currentClick() : null;
        String problem = pendingHandoff() ? handoff.clickProblem(now) : null;
        activation.setText(pendingHandoff() ? (click == null && handoff.canContinue() ? "Continue" : "Browser") : "Activate");
        retry.setVisibility(problem == null ? View.GONE : View.VISIBLE);
        banner.setBackgroundColor(visit.replaced || visit.interrupted ? 0xff883b2b : visit.arrived>0 && visit.sawRedirect ? 0xff165544 : 0xff38475a);
        String host=current()==null ? "" : SessionRules.host(current().getUrl());
        String hint = pendingHandoff() ? (handoff.canContinue() ? "\nIf Walmart doesn’t open, tap Continue." :
            "\nIf Walmart doesn’t open, tap Browser to restart at the eStore.") : "";
        if (click != null) {
            host = SessionRules.host(click);
            hint = problem == null ? "\nOpening Walmart through Rakuten…" :
                "\n" + problem + " If you use AdGuard or filtered DNS, check this host in its log. Retry here or tap Browser for the same referral.";
        }
        banner.setText(visit.status(System.currentTimeMillis()) + hint + (rate.isEmpty()?"":" · "+rate) + (host.isEmpty()?"":"\n"+host) + (summary.isEmpty()?"":"\n"+summary));
    }
    private void inspect() {
        WebView v=current();
        if (v==null || committedView!=v || !resumed) return;
        String url=v.getUrl();
        boolean offer = visit.offerPage(url);
        if (!visit.store(url) && !offer) return;
        visit.observe(url,System.currentTimeMillis());
        if (script==null) { summary="Cart review unavailable · check the official offer"; draw(); return; }
        if (inspecting) return;
        inspecting=true; final int turn=generation;
        String query = offer ? OfferTerms.SCRIPT :
            script+"("+JSONObject.quote(visit.slug)+","+(liveRules==null?"null":liveRules.toString())+")";
        v.evaluateJavascript(query, result -> {
            if (turn!=generation || v!=current()) return;
            inspecting=false;
            if (!url.equals(v.getUrl()) || !resumed) return;
            try {
                if (offer) {
                    liveRules = new JSONObject(result);
                    rate = liveRules.optString("rate");
                    draw();
                    return;
                }
                snapshot=new JSONObject(result);
                switch(snapshot.optString("kind")) {
                    case "order":
                        String id=snapshot.optString("id");
                        if (orders.valid(id)) { orders.save(id,false); summary="Order saved on this device"; }
                        else summary="Order number unavailable · save it in Orders";
                        break;
                    case "product": summary=label(snapshot)+" · "+snapshot.optString("group"); break;
                    case "cart": case "checkout":
                        JSONArray rows=snapshot.getJSONArray("rows");
                        if ("cart".equals(snapshot.optString("kind"))) {
                            lastCart=rows.length()==0 ? null : snapshot;
                        } else if (rows.length()==0 && lastCart!=null) {
                            snapshot=new JSONObject(lastCart.toString());
                            snapshot.put("kind","checkout"); snapshot.put("previousCart",true); snapshot.put("whole",false);
                            rows=snapshot.getJSONArray("rows");
                        }
                        int flags=0,unknown=0;
                        for (int i=0;i<rows.length();i++) {
                            String state=rows.getJSONObject(i).optString("state");
                            if ("flag".equals(state)) flags++;
                            if ("unknown".equals(state)) unknown++;
                        }
                        summary=rows.length()==0 ? "Cart contents unavailable · tap Review" :
                            (snapshot.optBoolean("previousCart")?"Last cart review · ":"")+flags+" flagged · "+unknown+" unknown · "+rows.length()+" lines reviewed"+
                                (snapshot.optBoolean("whole")?"":" · coverage unverified");
                        break;
                    default: summary="";
                }
            } catch (Exception e) { snapshot=null; summary="Review unavailable · check the current offer"; }
            draw();
        });
    }
    private String label(JSONObject row) {
        switch(row.optString("state")) {
            case "flag": return "May not earn";
            case "listed": return "Category listed by the eStore";
            case "review": return "Check offer conditions";
            default: return "Eligibility unknown";
        }
    }
    private void review() {
        StringBuilder message=new StringBuilder(visit.status(System.currentTimeMillis()));
        message.append("\n\nOnly Aeroplan can confirm credit. Activate before adding items and finish in this window.");
        if (snapshot!=null && snapshot.has("rows")) {
            Map<String,List<String>> groups=new LinkedHashMap<>();
            JSONArray rows=snapshot.optJSONArray("rows");
            for (int i=0;rows!=null && i<rows.length();i++) {
                JSONObject row=rows.optJSONObject(i); if(row==null) continue;
                String key=label(row)+" · "+row.optString("group");
                if (!groups.containsKey(key)) groups.put(key,new ArrayList<>());
                groups.get(key).add(row.optString("title"));
            }
            for (Map.Entry<String,List<String>> g:groups.entrySet()) {
                message.append("\n\n").append(g.getKey()).append(" (").append(g.getValue().size()).append(")");
                for (String item:g.getValue()) message.append("\n• ").append(item);
            }
            if (snapshot.optBoolean("previousCart")) message.append("\n\nCheckout hides its items. These are from your last cart review. Return to the cart to verify changes.");
            else if (!snapshot.optBoolean("whole")) message.append("\n\nSome cart lines may be missing. Expand the full item list before checkout.");
        } else if (!summary.isEmpty()) message.append("\n\n").append(summary);
        else message.append("\n\nOpen a product, cart or checkout page to review the retailer’s categories.");
        message.append("\n\nThe helper compares categories with explicit lists on the current offer and dated November warnings. Coupons, pre-existing items and other conditions may affect credit.");
        new AlertDialog.Builder(this).setTitle(visit.merchant+" cart review").setMessage(message)
            .setPositiveButton("Close",null).setNeutralButton("Offer terms",(d,w)->external(visit.offer)).show();
    }

    private void openReferralInBrowser(String url) {
        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            // Prefer the default handler for generic web addresses when Android exposes it.
            Intent probe = new Intent(Intent.ACTION_VIEW, Uri.parse("https://browser.invalid/")).addCategory(Intent.CATEGORY_BROWSABLE);
            android.content.pm.ResolveInfo browser = getPackageManager().resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
            if (browser != null && browser.activityInfo != null && !"android".equals(browser.activityInfo.packageName))
                open.setPackage(browser.activityInfo.packageName);
            startActivity(open);
        } catch (ActivityNotFoundException | SecurityException e) {
            if (open.getPackage() != null) {
                try { open.setPackage(null); startActivity(open); return; }
                catch (ActivityNotFoundException | SecurityException ignored) { }
            }
            notice("Couldn’t open a browser. You can retry the referral here.");
        }
    }

    private void external(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch(ActivityNotFoundException e) { Toast.makeText(this,"Open the official eStore in your browser.",Toast.LENGTH_LONG).show(); }
    }
    private void orders() {
        try {
            JSONArray list=orders.read(); String[] names=new String[list.length()];
            for(int i=0;i<names.length;i++) {
                JSONObject row=list.getJSONObject(i);
                names[i]=row.getString("id")+" · "+DateFormat.getDateInstance().format(new Date(row.getLong("time")))+"\n"+
                    (row.optBoolean("matched")?"History match marked by you":"Saved · points unconfirmed");
            }
            AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Recent "+visit.merchant+" orders");
            if (names.length==0) b.setMessage("Order references from supported confirmation pages appear here. You can also save one yourself.");
            else b.setItems(names,(d,w)->orderDetail(list.optJSONObject(w)));
            b.setPositiveButton("Done",null).setNeutralButton("Add order",(d,w)->addOrder())
                .setNegativeButton("eStore history",(d,w)->external(ReferralVisit.PORTAL+"/en-CA/order-history")).show();
        } catch(Exception e){ notice("Could not read the local order history."); }
    }
    private void orderDetail(JSONObject row) {
        String id=row.optString("id");
        new AlertDialog.Builder(this).setTitle(id)
            .setMessage("Compare this reference with your official eStore history. Mark a match only if the same order appears there.")
            .setPositiveButton("I found this order",(d,w)->save(id,true))
            .setNeutralButton("eStore history",(d,w)->external(ReferralVisit.PORTAL+"/en-CA/order-history"))
            .setNegativeButton("Clear all…",(d,w)->new AlertDialog.Builder(this).setTitle("Clear all saved orders?")
                .setNegativeButton("Cancel",null).setPositiveButton("Clear",(x,y)->{orders.clear();orders();}).show()).show();
    }
    private void addOrder() {
        EditText id=new EditText(this); id.setSingleLine(); id.setSaveEnabled(false);
        id.setInputType(InputType.TYPE_CLASS_TEXT); id.setHint(walmart?"Walmart order number":"123-1234567-1234567");
        new AlertDialog.Builder(this).setTitle("Save order number").setView(id).setNegativeButton("Cancel",null)
            .setPositiveButton("Save",(d,w)->save(id.getText().toString().trim(),false)).show();
    }
    private void save(String id,boolean matched) {
        if (!orders.valid(id)) {notice("Check the order number format.");return;}
        try {orders.save(id,matched);orders();} catch(Exception e){notice("Could not save this order.");}
    }
    private void notice(String message) {Toast.makeText(this,message,Toast.LENGTH_LONG).show();}
    private void closeTab() {
        invalidatePage(); WebView v=tabs.remove(tabs.size()-1);frame.removeAllViews();v.stopLoading();v.destroy();
        if(current()!=null){frame.addView(current()); committedView=current();} draw();
    }
    private void destroyTabs() {
        invalidatePage(); if(frame!=null)frame.removeAllViews();
        for(WebView v:tabs){v.stopLoading();v.destroy();} tabs.clear();
    }
    @Override public void onBackPressed() {
        WebView v=current(); if(v!=null && v.canGoBack())v.goBack();else if(tabs.size()>1)closeTab();else super.onBackPressed();
    }
    @Override protected void onResume(){super.onResume();resumed=true;handler.post(tick);}
    @Override protected void onPause(){resumed=false;handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onDestroy(){destroyTabs();super.onDestroy();}
}
