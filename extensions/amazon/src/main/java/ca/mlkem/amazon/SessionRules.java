package ca.mlkem.amazon;

import java.net.URI;
import java.net.URISyntaxException;

/** Pure validation rules shared by the runtime and its offline tests. */
public final class SessionRules {
    public static final String ESTORE_URL = "https://aeroplan.rewardops.com/en-CA/affiliate-details/amazon";
    private SessionRules() {}

    public static String normalizeNumber(String value) {
        return value == null ? "" : value.replaceAll("[\\s\\u00a0]", "");
    }

    public static boolean validNumber(String value) {
        return normalizeNumber(value).matches("[0-9]{9}");
    }

    public static boolean validLastName(String value) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 100) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    public static boolean isHttps(String url) {
        URI uri = parse(url);
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && uri.getRawUserInfo() == null
                && (uri.getPort() == -1 || uri.getPort() == 443);
    }

    public static boolean canAutofill(String url) {
        URI uri = parse(url);
        return isHttps(url) && "aeroplan.rewardops.com".equalsIgnoreCase(uri.getHost())
                && "/en-CA/affiliate-details/amazon".equals(uri.getPath());
    }

    public static boolean isAmazon(String url) {
        URI uri = parse(url);
        return isHttps(url) && ("amazon.ca".equalsIgnoreCase(uri.getHost())
                || "www.amazon.ca".equalsIgnoreCase(uri.getHost()));
    }

    public static String host(String url) {
        URI uri = parse(url);
        return uri == null || uri.getHost() == null ? "" : uri.getHost();
    }

    private static URI parse(String value) {
        try { return value == null ? null : new URI(value); }
        catch (URISyntaxException e) { return null; }
    }

    /** JSON string encoding; account data is never interpreted as JavaScript. */
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32 || c == '\u2028' || c == '\u2029') out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }
}
