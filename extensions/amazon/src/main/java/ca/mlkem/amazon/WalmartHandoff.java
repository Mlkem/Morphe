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
    private String clickUrl, failure;
    private enum Stage {
        NOTICE("Aeroplan confirmation"), RELAY("Aeroplan redirect service"), RAKUTEN("Rakuten"),
        STORE("Walmart.ca"), OTHER("Retailer redirect");
        final String label;
        Stage(String label) { this.label = label; }
    }
    private Stage stage;
    private long stageStarted;
    private String action = "Following referral";
    private boolean onTrustedRoute, atClick;
    private void enter(Stage next, long now) {
        if (stage != next) {
            stage = next;
            stageStarted = now;
            failure = null;
        }
    }

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
    static boolean store(String url) {
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
                    stageStarted = now;
                    failure = null;
                }
                enter(Stage.RAKUTEN, now);
                atClick = true;
                usedPartner = true;
                onTrustedRoute = true;
            } else {
                atClick = false;
                onTrustedRoute = known || (onTrustedRoute && "goto.walmart.ca".equalsIgnoreCase(SessionRules.host(url)));
                if (store(url)) enter(Stage.STORE, now);
                else if (known) enter(Stage.RELAY, now);
                else enter(Stage.OTHER, now);
            }
            return;
        }
        if (!captured) {
            captured = true;
            onTrustedRoute = true;
            received = now;
            enter(Stage.NOTICE, now);
        }
        // A same-document update can add the destination after the first notice callback.
        if (relay != null || leftNotice) return;
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
    boolean expectsDestination(String url) {
        return captured && url != null && (onTrustedRoute || url.equals(relay) || url.equals(partner));
    }
    String currentClick() { return atClick ? clickUrl : null; }
    String publisher() {
        String link = recoveryLink();
        return rakuten(link) ? parameter(Uri.parse(link), "id") : null;
    }
    String recoveryLink() {
        // Keep the last official click even when the final Walmart document fails to load.
        return clickUrl != null ? clickUrl : relay;
    }
    String retry(long now) {
        String link = recoveryLink();
        if (link == null) return null;
        stageStarted = now;
        failure = null;
        action = "Retried in Walmart";
        return link;
    }
    void recordFailure(String reason) { failure = reason; }
    String problem(long now) {
        if (failure != null) return failure;
        if (stage == null || now - stageStarted < GRACE_MS) return null;
        return stage.label + " hasn’t finished opening Walmart.";
    }
    String progress(long now) {
        if (stage == null) return "";
        long seconds = Math.max(0L, now - stageStarted) / 1000L;
        return stage.label + " · " + seconds + " s · " + action;
    }
    String autoContinue(String current, long now) {
        if (!captured || leftNotice || now - received < GRACE_MS || !portalRoute(current, true)) return null;
        if (relay == null) return null;
        action = "Continued automatically";
        return takeRelay();
    }
    private String takeRelay() {
        leftNotice = true;
        return relay;
    }
    String relayFailed(String url) {
        if (relay == null || !relay.equals(url) || partner == null) return null;
        leftNotice = true;
        String next = continueVisit();
        if (next != null) action = "Tried the supplied partner link";
        return next;
    }
    boolean canContinue() { return (!leftNotice && relay != null) || (!usedPartner && partner != null); }
    String continueVisit() {
        if (!canContinue()) return null;
        action = "Continued in Walmart";
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
