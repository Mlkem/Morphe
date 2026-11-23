package ca.mlkem.niagara.restore;

import android.app.AlertDialog;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, manifest=Config.NONE)
public class RestoreMembershipTest {
    private static final String LINK = "https://niagaralauncher.app/app-link/sign-in?email=test%2Balias%40example.invalid&code=A%2Bb%3D";

    @Test public void onlyForwardsOriginalLinkAfterConfirmation() {
        shadowOf(RuntimeEnvironment.getApplication()).checkActivities(false);
        Intent share = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, LINK);
        RestoreMembershipActivity activity = Robolectric.buildActivity(RestoreMembershipActivity.class, share).setup().get();
        assertNull(shadowOf(activity).getNextStartedActivity());
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        Intent result = shadowOf(activity).getNextStartedActivity();
        assertEquals(Intent.ACTION_VIEW,result.getAction());
        assertEquals(LINK,result.getDataString());
        assertEquals(activity.getPackageName(),result.getComponent().getPackageName());
        assertEquals("bitpit.launcher.ui.HomeActivity",result.getComponent().getClassName());
        assertNull(result.getExtras());
        assertTrue(activity.isFinishing());
    }
    @Test public void onlyTheTwoOfficialSignInRoutesAreAccepted() {
        assertEquals(LINK, RestoreMembershipActivity.signInLink("  "+LINK+" "));
        String legacy=LINK.replace("launcher.app","launcher.com");
        assertEquals(legacy,RestoreMembershipActivity.signInLink(legacy));
        for (String bad : new String[]{ LINK.replace("https:","http:"), LINK.replace(".app/",".app.invalid/"),
                LINK.replace(".app/",".app@other.invalid/"), LINK.replace("sign-in","secret-command"),
                LINK+"&code=duplicate", LINK.replace("code=","other="), LINK.replace(".app/",".app:8080/") })
            assertNull(RestoreMembershipActivity.signInLink(bad));
    }
}
