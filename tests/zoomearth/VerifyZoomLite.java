import app.morphe.patcher.apk.ApkUtils;
import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Real supplied working APK required. No APK, extracted app code or key is committed. */
public class VerifyZoomLite {
    static void check(boolean ok, String message) { VerifyZoomEarth.check(ok, message); }
    static final String FREE = "const Nt=Object.freeze({FREE:\"free\",PRO:\"pro\"})";
    static final String LITE = "const Nt=Object.freeze({FREE:\"pro\",PRO:\"pro\"})";
    static void verifyDex(byte[] original, byte[] patched) throws Exception {
        check(original.length == patched.length, "DEX byte length retained");
        var dex = VerifyZoomStartup.dex(original);
        DexBackedMethod onCreate = null;
        for (var c : dex.getClasses()) for (var m : c.getMethods())
            if (m.toString().equals("Lcom/neave/zoomearth/MainActivity;->onCreate(Landroid/os/Bundle;)V")) onCreate = m;
        check(onCreate != null, "Original activity found");
        var instructions = onCreate.getImplementation().getInstructions().iterator();
        var first = (DexBackedInstruction)instructions.next();
        var second = (DexBackedInstruction)instructions.next();
        check(((ReferenceInstruction)first).getReference().toString().equals("LXpk8a;->StartGame(Landroid/content/Context;)V"), "First injected call");
        check(((ReferenceInstruction)second).getReference().toString().equals("Lcom/zx/LB;->ll(Landroid/app/Activity;)V"), "Second injected call");
        int start = first.instructionStart, end = second.instructionStart + second.getCodeUnits() * 2;
        check(end - start == 12, "Only twelve instruction bytes eligible");
        for (int i = 0; i < original.length; i++) {
            if (i >= 8 && i < 32) continue;
            check(i >= start && i < end ? patched[i] == 0 : patched[i] == original[i],
                "Restored activity/startup code retained, byte " + i);
        }
        byte[] signature = MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(patched, 32, patched.length));
        check(Arrays.equals(signature, Arrays.copyOfRange(patched, 12, 32)), "DEX signature valid");
        Adler32 checksum = new Adler32(); checksum.update(patched, 12, patched.length - 12);
        check(ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).getInt(8) == (int)checksum.getValue(), "DEX checksum valid");
        for (var c : VerifyZoomStartup.dex(patched).getClasses()) for (var m : c.getMethods())
            if (m.getImplementation() != null) new com.android.tools.smali.dexlib2.builder.MutableMethodImplementation(m.getImplementation());
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), mpp = Path.of(args[1]), root = Path.of(args[2]), originalChecks = Path.of(args[3]);
        Files.createDirectories(root);
        byte[] originalDex = VerifyZoomEarth.read(input, "classes.dex"), expectedDex = null;
        String originalJs = new String(VerifyZoomEarth.read(input, VerifyZoomEarth.ASSET), java.nio.charset.StandardCharsets.UTF_8);
        check(originalJs.contains(LITE), "Working APK tier change identified");
        Files.writeString(root.resolve("original.js"), originalJs);
        Path combined = null;
        for (int cleanup = 0; cleanup < 2; cleanup++) for (int mask = 0; mask < 16; mask++) {
            if (cleanup == 0 && mask == 0) continue;
            int selection = mask | (cleanup == 1 ? 32 : 0);
            Path out = VerifyZoomEarth.apply(input, mpp, selection,
                root.resolve(cleanup == 1 ? "cleanup-mask-" + mask : "mask-" + mask), false);
            String previous = Files.readString(mask == 0 ? originalChecks.resolve("original.js") : originalChecks.resolve("mask-" + mask + "/app.js"));
            String expected = (mask & 1) == 0 ? previous.replace(FREE, LITE) : previous;
            check(Arrays.equals(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8), VerifyZoomEarth.read(out, VerifyZoomEarth.ASSET)),
                "Feature bytes match verified behavior; distinct free tier restored with Pro");
            if (cleanup == 1) {
                byte[] current = VerifyZoomEarth.read(out, "classes.dex");
                if (expectedDex == null) {
                    verifyDex(originalDex, current); expectedDex = current;
                    Files.write(root.resolve("patched-classes.dex"), current);
                } else check(Arrays.equals(expectedDex, current), "Identical cleanup with every feature combination");
                if (mask == 15) combined = out;
            }
            System.out.println("PASS LiteAPKs cleanup=" + cleanup + " features=" + mask);
        }
        // Reverse feature order and a separate cleanup run produce the same result.
        Path successive = input;
        for (int mask : new int[]{8, 4, 2, 1, 32})
            successive = VerifyZoomEarth.apply(successive, mpp, mask, root.resolve("successive-" + mask), false);
        check(VerifyZoomEarth.hashes(successive).equals(VerifyZoomEarth.hashes(combined)), "Order-independent app-entry output");
        VerifyZoomEarth.apply(combined, mpp, 47, root.resolve("reapply"), true);
        VerifyZoomEarth.apply(input, mpp, 16, root.resolve("reject-original-startup"), true);
        VerifyZoomEarth.apply(originalChecks.resolve("original-merged.apk"), mpp, 32, root.resolve("reject-original-input"), true);
        Path unknown = root.resolve("unknown-js.apk"); VerifyZoomEarth.alterAsset(input, unknown);
        VerifyZoomEarth.apply(unknown, mpp, 15, root.resolve("reject-unknown-js"), true);
        Path unknownDex = root.resolve("unknown-dex.apk"); VerifyZoomStartup.alteredDex(input, unknownDex);
        VerifyZoomEarth.apply(unknownDex, mpp, 32, root.resolve("reject-unknown-dex"), true);
        Path signed = root.resolve("signature-test.apk");
        ApkUtils.INSTANCE.signApk(combined.toFile(), signed.toFile(), "Zoom Earth Lite verification",
            new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(), "verification-only", "fixture", "verification-only"), 26);
        check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(), "APK signing verifies");
        check(VerifyZoomEarth.hashes(signed).equals(VerifyZoomEarth.hashes(combined)), "Signing preserves app entries");
        Files.writeString(root.resolve("verification.json"), "{\"result\":\"passed\",\"combinations\":31,\"removed_startup_calls\":2,\"instruction_bytes_replaced\":12,\"other_dex_bytes_unchanged_except_checksums\":true,\"all_other_app_entries_unchanged\":true,\"reverse_order\":\"passed\",\"rejections\":\"passed\",\"signature\":\"passed\",\"phone_tested\":false}\n");
        System.out.println("PASS working-base compatibility, exact DEX preservation, input guards and signing");
    }
}
