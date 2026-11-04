package ca.mlkem.gmail

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.*
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation

internal const val ORIGINAL = "com.google.android.gm"
internal const val CLONE = "ca.mlkem.gmail"
internal const val VERSION = "2026.10.26.1012468012.Release"
internal const val HOOK = "Lca/mlkem/gmail/AccountVisibility;"
private const val MAIN = "Lcom/google/android/apps/gmail/libraries/mailactivity/MailActivityGmail;"
private const val OLD_SETTINGS = "Lcom/google/android/gm/preference/GmailPreferenceActivity;"
private const val NEW_SETTINGS = "Lcom/google/android/apps/gmail/featurelibraries/settingsv2/impl/activity/HubPreferenceActivity;"
private const val ACCOUNT = "Lcom/google/android/apps/gmail/libraries/accounts/android/GmailAndroidAccount;"

internal val gmail = Compatibility(
    name = "Gmail", packageName = ORIGINAL, apkFileType = ApkFileType.APKM,
    description = "Original APKMirror arm64 build 66141086. Separate no-root copy requires ReVanced GmsCore. " +
        "Sign-in, sync and phone UI testing are pending.",
    targets = listOf(AppTarget(version = VERSION, minSdk = 29, isExperimental = true))
)

internal fun BytecodePatchContext.requireGmail() {
    check(packageMetadata.packageName == ORIGINAL && packageMetadata.versionName == VERSION &&
        packageMetadata.versionCode == "66141086") { "Requires original Gmail $VERSION (66141086) from APKMirror." }
    check(classDefBy("Lawxw;").methods.none { it.name == "mlkemAllAccounts" }) {
        "Gmail account visibility is already applied. Select the original APKM."
    }
}

internal fun replacement(original: MutableMethod, registers: Int, body: String): MutableMethod =
    MutableMethod(ImmutableMethod(original.definingClass, original.name, original.parameters,
        original.returnType, original.accessFlags, original.annotations, original.hiddenApiRestrictions,
        ImmutableMethodImplementation(registers, emptyList(), emptyList(), emptyList()))).apply {
        addInstructions(0, body.trimIndent())
    }

val gmailAccountVisibility = bytecodePatch(
    name = "Gmail account visibility",
    description = "Adds Account visibility to Gmail settings and a long press on the account avatar. " +
        "Hide or show individual switcher accounts, with Show all recovery. Keeps the current account visible. " +
        "Does not hide mail, All inboxes, notifications or Android accounts. Creates a separate GmsCore copy; phone testing pending.",
    default = false
) {
    compatibleWith(gmail)
    dependsOn(gmailGmsCore)
    extendWith("extensions/gmail-accounts.mpe")
    execute {
        requireGmail()
        fun method(owner: String, name: String, params: List<String>, result: String) =
            Fingerprint(definingClass = owner, name = name, parameters = params, returnType = result).method

        val list = method("Lawxw;", "g", emptyList(), "Lcgzq;")
        check(list.implementation!!.registerCount == 4 && list.instructions.count() == 26 &&
            list.instructions.any { (it as? ReferenceInstruction)?.reference.toString() == "Lawxw;->h:Lcgzq;" }) {
            "Account menu model differs from the reviewed Gmail build."
        }
        check(classDefBy(ACCOUNT).fields.any { it.name == "a" && it.type == "Landroid/accounts/Account;" })
        check(classDefBy("Lcgzq;").methods.any { it.name == "i" && it.parameterTypes == listOf("Ljava/util/Collection;") && it.returnType == "Lcgzq;" })
        val create = method(MAIN, "onCreate", listOf("Landroid/os/Bundle;"), "V")
        val headers = method(OLD_SETTINGS, "onBuildHeaders", listOf("Ljava/util/List;"), "V")
        val click = method(OLD_SETTINGS, "onHeaderClick", listOf("Landroid/preference/PreferenceActivity\$Header;", "I"), "V")
        val menu = method(NEW_SETTINGS, "onPrepareOptionsMenu", listOf("Landroid/view/Menu;"), "Z")
        val selection = method(NEW_SETTINGS, "onOptionsItemSelected", listOf("Landroid/view/MenuItem;"), "Z")
        val avatar = method("Lcom/google/android/libraries/onegoogle/accountmenu/SelectedAccountDisc;", "c", listOf("Lawzb;", "Lhlz;"), "V")
        for (m in listOf(create, headers, click, menu, selection, avatar)) {
            check(m.instructions.none { (it as? ReferenceInstruction)?.reference.toString().startsWith(HOOK) })
        }
        check(create.implementation!!.registerCount == 26 &&
            headers.implementation!!.registerCount == 10 && click.implementation!!.registerCount == 9 &&
            menu.implementation!!.registerCount == 3 && selection.implementation!!.registerCount == 3)

        // Keep the host's complete account model intact. Both legacy and Bento UI consumers
        // receive a filtered immutable list; neither AccountManager nor sync providers change.
        val wrapper = replacement(list, 3, """
            invoke-virtual {p0}, Lawxw;->mlkemAllAccounts()Lcgzq;
            move-result-object v0
            invoke-virtual {p0}, Lawxw;->a()Ljava/lang/Object;
            move-result-object v1
            invoke-static {v0, v1}, $HOOK->filter(Ljava/util/List;Ljava/lang/Object;)Ljava/util/List;
            move-result-object v0
            invoke-static {v0}, Lcgzq;->i(Ljava/util/Collection;)Lcgzq;
            move-result-object v0
            return-object v0
        """)
        list.setName("mlkemAllAccounts")
        mutableClassDefBy("Lawxw;").methods.add(wrapper)
        val bridge = method(HOOK, "account", listOf("Ljava/lang/Object;"), "Landroid/accounts/Account;")
        val hostBridge = replacement(bridge, 2, """
            instance-of v0, p0, $ACCOUNT
            if-eqz v0, :other
            check-cast p0, $ACCOUNT
            iget-object v0, p0, $ACCOUNT->a:Landroid/accounts/Account;
            return-object v0
            :other
            instance-of v0, p0, Landroid/accounts/Account;
            if-eqz v0, :unknown
            check-cast p0, Landroid/accounts/Account;
            return-object p0
            :unknown
            const/4 v0, 0x0
            return-object v0
        """)
        mutableClassDefBy(HOOK).methods.apply { remove(bridge); add(hostBridge) }
        create.addInstruction(0, "invoke-static/range {p0 .. p0}, $HOOK->initialize(Landroid/app/Activity;)V")
        avatar.addInstruction(0, "invoke-static/range {p0 .. p0}, $HOOK->attach(Landroid/view/View;)V")
        // Header code reuses p0, but p1 remains the List through its final return.
        headers.addInstruction(headers.instructions.count() - 1, "invoke-static {p1}, $HOOK->addHeader(Ljava/util/List;)V")
        click.addInstructionsWithLabels(0, """
            invoke-static {p0, p1}, $HOOK->selectHeader(Landroid/app/Activity;Landroid/preference/PreferenceActivity${'$'}Header;)Z
            move-result v0
            if-eqz v0, :stock
            return-void
        """.trimIndent(), ExternalLabel("stock", click.getInstruction(0)))
        menu.addInstruction(0, "invoke-static {p1}, $HOOK->addMenu(Landroid/view/Menu;)V")
        selection.addInstructionsWithLabels(0, """
            invoke-static {p0, p1}, $HOOK->selectMenu(Landroid/app/Activity;Landroid/view/MenuItem;)Z
            move-result v0
            if-eqz v0, :stock
            const/4 v0, 0x1
            return v0
        """.trimIndent(), ExternalLabel("stock", selection.getInstruction(0)))
    }
}
