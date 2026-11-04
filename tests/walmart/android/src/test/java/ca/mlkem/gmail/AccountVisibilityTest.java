package ca.mlkem.gmail;

import android.accounts.Account;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.preference.PreferenceActivity;
import android.view.View;
import java.lang.reflect.Field;
import java.util.*;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.android.controller.ActivityController;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, manifest = Config.NONE)
public class AccountVisibilityTest {
    public static class Inbox extends Activity {
        int recreations;
        @Override protected void onCreate(android.os.Bundle state) {
            super.onCreate(state);
            AccountVisibility.initialize(this);
            AccountVisibility.initialize(this);
        }
        @Override public void recreate() { recreations++; }
    }
    private Activity activity;
    private SharedPreferences prefs;
    private final Account a = new Account("first@example.test", "app.revanced");
    private final Account b = new Account("second@example.test", "app.revanced");
    private final Account otherType = new Account("second@example.test", "ca.mlkem.gmail.pop3");

    @Before public void setUp() throws Exception {
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        prefs = activity.getSharedPreferences("mlkem_account_visibility", 0);
        prefs.edit().clear().commit();
        Field field = AccountVisibility.class.getDeclaredField("revisions");
        field.setAccessible(true); ((Map<?, ?>) field.get(null)).clear();
        AccountVisibility.initialize(activity);
        AccountVisibility.filter(Arrays.asList(a, b), a);
    }

    private void hide(Account... accounts) {
        Set<String> keys = new HashSet<>();
        for (Account account : accounts) keys.add(AccountVisibility.key(account));
        prefs.edit().putStringSet("hidden_accounts", keys).commit();
    }

    @Test public void filtersOnlyRequestedIdentityWithoutMutatingInput() {
        Object unknown = new Object();
        List<Object> all = Collections.unmodifiableList(Arrays.asList(a, b, otherType, unknown));
        hide(b);
        assertEquals(Arrays.asList(a, otherType, unknown), AccountVisibility.filter(all, a));
        assertEquals(4, all.size());
        assertFalse(prefs.getStringSet("hidden_accounts", Collections.emptySet()).toString().contains("example.test"));
    }

    @Test public void selectedHiddenAccountRemainsReachableAndLastAccountSurvives() {
        hide(a, b);
        assertEquals(Collections.singletonList(b), AccountVisibility.filter(Arrays.asList(a, b), b));
        assertEquals(Collections.singletonList(a), AccountVisibility.filter(Arrays.asList(a, b), null));
        assertTrue(AccountVisibility.filter(Collections.emptyList(), null).isEmpty());
    }

    @Test public void cancelDoesNotSaveAndApplyPersistsAcrossActivityRecreation() {
        AccountVisibility.show(activity);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.getListView().isItemChecked(1));
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1), 1, 1);
        assertFalse(dialog.getListView().isItemChecked(1));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(Arrays.asList(a, b), AccountVisibility.filter(Arrays.asList(a, b), a));
        AccountVisibility.show(activity);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(dialog.getListView().isItemChecked(1));
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1), 1, 1);
        assertFalse(dialog.getListView().isItemChecked(1));
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        Activity replacement = Robolectric.buildActivity(Activity.class).setup().get();
        AccountVisibility.initialize(replacement);
        assertEquals(Collections.singletonList(a), AccountVisibility.filter(Arrays.asList(a, b), a));
        assertEquals(1, prefs.getLong("revision", 0));
    }

    @Test public void currentAccountCannotBeUncheckedAndShowAllRecoversEverything() {
        hide(b);
        AccountVisibility.show(activity);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(null, 0, 0);
        assertTrue(dialog.getListView().isItemChecked(0));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(Arrays.asList(a, b), AccountVisibility.filter(Arrays.asList(a, b), a));
    }

    @Test public void headerIsIdempotentAndAvatarProvidesRecoveryEntry() {
        List<PreferenceActivity.Header> headers = new ArrayList<>();
        AccountVisibility.addHeader(headers); AccountVisibility.addHeader(headers);
        assertEquals(1, headers.size());
        assertTrue(AccountVisibility.selectHeader(activity, headers.get(0)));
        ShadowAlertDialog.getLatestAlertDialog().dismiss();
        View avatar = new View(activity); AccountVisibility.attach(avatar);
        assertTrue(avatar.performLongClick());
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
        assertFalse(AccountVisibility.selectHeader(activity, new PreferenceActivity.Header()));
    }

    @Test public void removedAccountsCannotLockOutAndNoAccountsIsExplained() {
        hide(a, b);
        assertEquals(Collections.singletonList(otherType), AccountVisibility.filter(Collections.singletonList(otherType), null));
        AccountVisibility.filter(Collections.emptyList(), null);
        AccountVisibility.show(activity);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertTrue(ShadowAlertDialog.getLatestAlertDialog().isShowing());
        assertEquals(2, prefs.getStringSet("hidden_accounts", Collections.emptySet()).size());
    }

    @Test public void returningToInboxRefreshesOnceThroughAndroidLifecycle() {
        ActivityController<Inbox> controller = Robolectric.buildActivity(Inbox.class).setup();
        Inbox inbox = controller.get();
        controller.pause().stop();
        prefs.edit().putLong("revision", 1).commit();
        controller.restart().start().resume();
        assertEquals(1, inbox.recreations);
        controller.pause().stop().restart().start().resume();
        assertEquals(1, inbox.recreations);
        controller.pause().stop().destroy();
    }

    @Test @Config(sdk = 29) public void lifecycleWorksOnMinimumSupportedAndroid() {
        returningToInboxRefreshesOnceThroughAndroidLifecycle();
    }
}
