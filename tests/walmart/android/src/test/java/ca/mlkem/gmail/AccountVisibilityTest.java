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
    private static final class WrappedAccount {
        final String id, email;
        WrappedAccount(String id, String email) { this.id = id; this.email = email; }
    }
    private static final AccountVisibility.Adapter converter = new AccountVisibility.Adapter() {
        public String id(Object value) { return ((WrappedAccount) value).id; }
        public String email(Object value) { return ((WrappedAccount) value).email; }
    };
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
        // Settings is not the registered inbox: saving there refreshes on return.
        AccountVisibility.initialize(Robolectric.buildActivity(Activity.class).setup().get());
        filter(Arrays.asList(a, b), a);
    }

    private static List<?> filter(List<?> source, Object current) {
        return AccountVisibility.filter(source, current, null);
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
        assertEquals(Arrays.asList(a, otherType, unknown), filter(all, a));
        assertEquals(4, all.size());
        assertFalse(prefs.getStringSet("hidden_accounts", Collections.emptySet()).toString().contains("example.test"));
    }

    @Test public void selectedHiddenAccountRemainsReachableAndLastAccountSurvives() {
        hide(a, b);
        assertEquals(Collections.singletonList(b), filter(Arrays.asList(a, b), b));
        assertEquals(Collections.singletonList(a), filter(Arrays.asList(a, b), null));
        assertTrue(filter(Collections.emptyList(), null).isEmpty());
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
        assertEquals(Arrays.asList(a, b), filter(Arrays.asList(a, b), a));
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
        assertEquals(Collections.singletonList(a), filter(Arrays.asList(a, b), a));
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
        assertEquals(Arrays.asList(a, b), filter(Arrays.asList(a, b), a));
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
        assertEquals(Collections.singletonList(otherType), filter(Collections.singletonList(otherType), null));
        filter(Collections.emptyList(), null);
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

    @Test public void wrappedHostAccountsCanBeHiddenAndRestoredThroughTheDialog() {
        WrappedAccount first = new WrappedAccount("hub:1", "first@example.test");
        WrappedAccount second = new WrappedAccount("hub:2", "second@example.test");
        List<WrappedAccount> source = Arrays.asList(first, second);
        assertEquals(source, AccountVisibility.filter(source, first, converter));
        AccountVisibility.show(activity);
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        assertEquals("second@example.test", dialog.getListView().getAdapter().getItem(1));
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1), 1, 1);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(Collections.singletonList(first), AccountVisibility.filter(source, first, converter));
        AccountVisibility.show(activity);
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(source, AccountVisibility.filter(source, first, converter));
    }

    @Test public void wrapperIdentityProtectsCurrentAndUnknownRowsStayVisible() {
        WrappedAccount first = new WrappedAccount("hub:1", "first@example.test");
        WrappedAccount second = new WrappedAccount("hub:2", "second@example.test");
        Object unknown = new Object();
        hide(AccountVisibility.account(second, converter));
        // Selected objects may be rebuilt; protection uses the host identity, not object equality.
        WrappedAccount selectedAgain = new WrappedAccount("hub:2", "second@example.test");
        List<Object> source = Arrays.asList(first, second, unknown);
        assertEquals(source, AccountVisibility.filter(source, selectedAgain, converter));
        assertEquals(Arrays.asList(first, unknown), AccountVisibility.filter(source, first, converter));
        assertEquals(source, AccountVisibility.filter(source, first, new Object()));
    }

    @Test public void avatarApplyRefreshesAnAlreadyVisibleInbox() {
        Inbox inbox = Robolectric.buildActivity(Inbox.class).setup().get();
        View avatar = new View(inbox);
        AccountVisibility.attach(avatar);
        assertTrue(avatar.performLongClick());
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        dialog.getListView().performItemClick(dialog.getListView().getChildAt(1), 1, 1);
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        assertEquals(1, inbox.recreations);
        assertEquals(Collections.singletonList(a), filter(Arrays.asList(a, b), a));
    }
}
