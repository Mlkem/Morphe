import app.morphe.patcher.apk.ApkUtils;
import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Binary layout and real-APK checks; does not claim a successful phone launch. */
public class VerifyZoomStartup {
    static final String SIGNATURE = "Lcom/pairip/SignatureCheck;->verifyIntegrity(Landroid/content/Context;)V";
    static final String LICENSING = "Lcom/pairip/licensecheck/LicenseClient;->initializeLicenseCheck()V";
    static void check(boolean ok, String message) { VerifyZoomEarth.check(ok, message); }
    static DexBackedDexFile dex(byte[] bytes) throws Exception {
        return DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new ByteArrayInputStream(bytes));
    }
    static void verifyDex(byte[] original, byte[] patched) throws Exception {
        check(original.length == patched.length, "Original DEX size retained");
        Set<String> changed = new TreeSet<>();
        boolean[] allowed = new boolean[original.length];
        Arrays.fill(allowed, 8, 32, true); // DEX checksum and signature only.
        Map<String,DexBackedMethod> after = new HashMap<>();
        for (var c : dex(patched).getClasses()) for (var m : c.getMethods()) {
            after.put(m.toString(), m);
            if (m.getImplementation() != null) new MutableMethodImplementation(m.getImplementation());
        }
        int count = 0;
        for (var c : dex(original).getClasses()) for (var m : c.getMethods()) {
            var out = after.get(m.toString()); count++;
            check(out != null && m.getMethodIndex() == out.getMethodIndex(), "Method identity/index retained");
            var impl = m.getImplementation();
            if (impl == null) continue;
            var output = out.getImplementation();
            int start = ((DexBackedInstruction)impl.getInstructions().iterator().next()).instructionStart;
            int units = 0; for (var i : impl.getInstructions()) units += i.getCodeUnits();
            check(((DexBackedInstruction)output.getInstructions().iterator().next()).instructionStart == start, "Code offsets retained");
            check(output.getRegisterCount() == impl.getRegisterCount(), "Register counts retained");
            if (!Arrays.equals(Arrays.copyOfRange(original,start,start+2*units), Arrays.copyOfRange(patched,start,start+2*units))) {
                changed.add(m.toString());
                check(m.getReturnType().equals("V"), "Only void methods stubbed");
                for (var i : output.getInstructions()) check(i.getOpcode() == Opcode.RETURN_VOID, "Every executable/handler entry returns");
                Arrays.fill(allowed, start, start + 2 * units, true);
            }
        }
        check(count == after.size(), "No methods added or removed");
        check(changed.equals(Set.of(SIGNATURE, LICENSING)), "Exactly the two known startup methods changed");
        for (int i = 0; i < original.length; i++) if (!allowed[i]) check(original[i] == patched[i], "Unexpected byte change at " + i);
        byte[] signature = MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(patched,32,patched.length));
        check(Arrays.equals(signature, Arrays.copyOfRange(patched,12,32)), "DEX SHA-1 signature valid");
        Adler32 checksum = new Adler32(); checksum.update(patched,12,patched.length-12);
        check(ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).getInt(8) == (int)checksum.getValue(), "DEX Adler32 valid");
    }
    static void alteredDex(Path input, Path output) throws Exception {
        try (ZipFile src = new ZipFile(input.toFile()); ZipOutputStream dst = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipEntry e : Collections.list(src.entries())) {
                dst.putNextEntry(new ZipEntry(e.getName())); byte[] bytes = src.getInputStream(e).readAllBytes();
                if (e.getName().equals("classes.dex")) bytes[bytes.length-1] ^= 1;
                dst.write(bytes); dst.closeEntry();
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path original = Path.of(args[0]), mpp = Path.of(args[1]), root = Path.of(args[2]);
        Path previous = Path.of(args[3]); Files.createDirectories(root);
        byte[] originalDex = VerifyZoomEarth.read(original, "classes.dex"), expected = null;
        Files.write(root.resolve("original.js"), VerifyZoomEarth.read(original, VerifyZoomEarth.ASSET));
        for (int mask = 0; mask < 16; mask++) {
            Path output = VerifyZoomEarth.apply(original,mpp,mask|16,root.resolve("mask-"+mask),false);
            byte[] bytes = VerifyZoomEarth.read(output,"classes.dex");
            if (expected == null) { verifyDex(originalDex,bytes); expected = bytes; Files.write(root.resolve("patched-classes.dex"),bytes); }
            else check(Arrays.equals(expected,bytes), "Same DEX change with every feature combination");
            byte[] js = Files.readAllBytes(mask == 0 ? previous.resolve("original.js") : previous.resolve("mask-"+mask+"/app.js"));
            check(Arrays.equals(js,VerifyZoomEarth.read(output,VerifyZoomEarth.ASSET)), "Prior tested JavaScript behavior retained");
            if (mask == 15) {
                Path signed = root.resolve("signature-test.apk");
                ApkUtils.INSTANCE.signApk(output.toFile(),signed.toFile(),"Zoom Earth startup verification",
                    new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(),"verification-only","fixture","verification-only"),32);
                check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(),"Android signing verifies");
                VerifyZoomEarth.apply(output,mpp,31,root.resolve("reapply"),true);
            }
            System.out.println("PASS startup + feature mask "+mask);
        }
        for (String kind : new String[]{"package","version"})
            VerifyZoomEarth.apply(previous.resolve("wrong-"+kind+".apk"),mpp,16,root.resolve("wrong-"+kind),true);
        Path wrong = root.resolve("modified-dex.apk"); alteredDex(original,wrong);
        VerifyZoomEarth.apply(wrong,mpp,16,root.resolve("modified-dex"),true);
        // Applying the startup fix to a dev.9 JavaScript-only output is compatible;
        // user instructions still start from the original archive for a full repatch.
        Path priorOutput = VerifyZoomEarth.apply(previous.resolve("mask-15/zoom-unsigned.apk"),mpp,16,root.resolve("after-dev9"),false);
        check(Arrays.equals(expected,VerifyZoomEarth.read(priorOutput,"classes.dex")), "Startup patch composes after dev.9 resources");
        Files.writeString(root.resolve("verification.json"),"{\"result\":\"passed\",\"startup_combinations\":16,\"changed_methods\":2,\"dex_size_unchanged\":true,\"dex_offsets_retained\":true,\"all_other_dex_bytes_retained_except_header_checksums\":true,\"phone_tested\":false}\n");
        System.out.println("PASS startup checks: all combinations, DEX layout, input rejection, signatures");
    }
}
