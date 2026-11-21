package ca.mlkem.amazon;

import android.content.Intent;
import android.net.Uri;
import java.net.URISyntaxException;
import java.util.List;

/** In-memory destinations supplied by the official Walmart offer for this visit only. */
final class WalmartHandoff {
    static final long GRACE_MS = 8000;
    private String relay, partner;
    private boolean leftNotice, usedPartner, captured;
    private long received;
    private String clickUrl, clickError;
    private boolean onTrustedRoute, atClick;
    private long clickStarted;

    static boolean portalRoute(String url, boolean notice) {
        if (!SessionRules.isHttps(url) || !"aeroplan.rewardops.com".equalsIgnoreCase(SessionRules.host(url))) return false;
        List<String> path = Uri.parse(url).getPathSegments();
        if (path.size() != 3) return false;
        String merchant = path.get(2);
        if (!("walmart".equalsIgnoreCase(merchant) || "walmart.ca".equalsIgnoreCase(merchant)
                || "walmart canada".equalsIgnoreCase(merchant) || "walmart-canada".equalsIgnoreCase(merchant))) return false;
        return ("en-CA".equalsIgnoreCase(path.get(0)) &&
                (notice ? "affiliate-redirect" : "affiliate-details").equals(path.get(1)))
                || ("fr-CA".equalsIgnoreCase(path.get(0)) &&
                (notice ? "réorienter-sur-les-marques-affiliées" : "détails-sur-les-marques-affiliées").equals(path.get(1)));
    }
    private static String parameter(Uri uri, String name) {
        List<String> values = uri.getQueryParameters(name);
        return values.size() == 1 ? values.get(0) : null;
    }
    private static boolean store(String url) {
        String host = SessionRules.host(url);
        return SessionRules.isHttps(url) && ("www.walmart.ca".equalsIgnoreCase(host) || "walmart.ca".equalsIgnoreCase(host));
    }
    private static boolean partnerLink(String url) {
        return store(url) || (SessionRules.isHttps(url) && ("click.linksynergy.com".equalsIgnoreCase(SessionRules.host(url))
                || "goto.walmart.ca".equalsIgnoreCase(SessionRules.host(url))));
    }
    static boolean rakuten(String url) {
        return SessionRules.isHttps(url) && "click.linksynergy.com".equalsIgnoreCase(SessionRules.host(url));
    }
    void observe(String url, long now) {
        if (!portalRoute(url, true)) {
            if (!captured || !SessionRules.isHttps(url)) return;
            leftNotice = true;
            boolean known = url.equals(partner) || url.equals(relay);
            if (rakuten(url) && (onTrustedRoute || known)) {
                if (!atClick || !url.equals(clickUrl)) {
                    clickUrl = url;
                    clickStarted = now;
                    clickError = null;
                }
                atClick = true;
                usedPartner = true;
                onTrustedRoute = true;
            } else {
                atClick = false;
                onTrustedRoute = known;
            }
            return;
        }
        if (captured) return;
        onTrustedRoute = true;
        captured = true;
        received = now;
        String supplied = parameter(Uri.parse(url), "redirectUrl");
        if (partnerLink(supplied)) {
            relay = supplied;
        } else if (SessionRules.isHttps(supplied) && "clicks.trx-hub.com".equalsIgnoreCase(SessionRules.host(supplied))) {
            Uri wrapper = Uri.parse(supplied);
            List<String> path = wrapper.getPathSegments();
            if (path.size() != 2 || !"xid".equals(path.get(0))) return;
            relay = supplied;
            String target = parameter(wrapper, "q");
            if (portalRoute(parameter(wrapper, "p"), false) && partnerLink(target)) partner = target;
        }
    }
    String currentClick() { return atClick ? clickUrl : null; }
    String retryClick(long now) {
        if (!atClick) return null;
        clickStarted = now;
        clickError = null;
        return clickUrl;
    }
    void recordClickError(String url, String reason) {
        if (atClick && clickUrl.equals(url)) clickError = reason;
    }
    String clickProblem(long now) {
        if (!atClick) return null;
        if (clickError != null) return clickError;
        return now - clickStarted >= GRACE_MS ? "Rakuten hasn’t opened Walmart yet." : null;
    }
    String autoContinue(String current, long now) {
        if (!captured || leftNotice || now - received < GRACE_MS || !portalRoute(current, true)) return null;
        return takeRelay();
    }
    private String takeRelay() {
        leftNotice = true;
        return relay;
    }
    String relayFailed(String url) {
        if (relay == null || !relay.equals(url) || partner == null) return null;
        leftNotice = true;
        return continueVisit();
    }
    boolean canContinue() { return (!leftNotice && relay != null) || (!usedPartner && partner != null); }
    String continueVisit() {
        if (!leftNotice && relay != null) return takeRelay();
        if (usedPartner) return null;
        usedPartner = true;
        return partner;
    }
    static String browserDestination(String url) {
        if (url == null) return null;
        if (url.regionMatches(true, 0, "http://", 0, 7)) {
            String https = "https" + url.substring(4);
            return store(https) ? https : null;
        }
        if (!url.startsWith("intent://")) return null;
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
            String fallback = intent.getStringExtra("browser_fallback_url");
            if (store(fallback)) return fallback;
            return store(intent.getDataString()) ? intent.getDataString() : null;
        } catch (URISyntaxException | IllegalArgumentException e) { return null; }
    }
}
