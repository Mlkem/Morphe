package ca.mlkem.niagara.restore;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.WindowManager;
import java.net.URI;
import java.net.URISyntaxException;

/** Pass a user-shared sign-in link back to Niagara's original membership handler. */
public final class RestoreMembershipActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        Intent shared = getIntent();
        String link = null;
        if (Intent.ACTION_SEND.equals(shared.getAction()) && "text/plain".equals(shared.getType())) {
            CharSequence text = shared.getCharSequenceExtra(Intent.EXTRA_TEXT);
            link = signInLink(text);
        }
        // Never show, persist or log the email address or one-time code.
        final String target = link;
        AlertDialog.Builder prompt = new AlertDialog.Builder(this)
                .setTitle("Restore Niagara Pro")
                .setOnCancelListener(dialog -> finish());
        if (target == null) {
            prompt.setMessage("Share the Sign In link from your Niagara membership email. If your email app wraps the link, copy it and enter /applink in Niagara search.")
                    .setPositiveButton("Close", (dialog, which) -> finish());
        } else {
            prompt.setMessage("Continue with this link if you requested a membership email from Niagara on this device.")
                    .setNegativeButton("Cancel", (dialog, which) -> finish())
                    .setPositiveButton("Continue in Niagara", (dialog, which) -> {
                        Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(target))
                                .setClassName(getPackageName(), "bitpit.launcher.ui.HomeActivity");
                        startActivity(open);
                        finish();
                    });
        }
        prompt.show();
    }

    static String signInLink(CharSequence shared) {
        if (shared == null || shared.length() > 8192) return null;
        String link = shared.toString().trim();
        try {
            URI uri = new URI(link);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || !"/app-link/sign-in".equals(uri.getRawPath())
                    || !("niagaralauncher.app".equalsIgnoreCase(uri.getHost()) || "niagaralauncher.com".equalsIgnoreCase(uri.getHost())))
                return null;
            Uri parsed = Uri.parse(link);
            for (String key : new String[] {"email", "code"}) {
                java.util.List<String> values = parsed.getQueryParameters(key);
                if (values.size() != 1 || values.get(0).isEmpty()) return null;
            }
            return link;
        } catch (URISyntaxException e) { return null; }
    }
}
