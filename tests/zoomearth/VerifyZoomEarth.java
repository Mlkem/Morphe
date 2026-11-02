import app.morphe.patcher.*;
import app.morphe.patcher.apk.ApkUtils;
import app.morphe.patcher.dex.*;
import app.morphe.patcher.patch.*;
import app.morphe.patcher.resource.ResourceMode;
import com.android.apksig.ApkVerifier;
import com.reandroid.apk.*;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.flow.FlowCollector;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Apply the actual MPP to a user-supplied APK. Never commit inputs, APKs or keys. */
public class VerifyZoomEarth {
    static final String ASSET = "assets/public/assets/js/app.js";
    static final String[] NAMES = {"Enable Zoom Earth Pro features", "Remove Zoom Earth ads",
        "100 Zoom Earth favourites", "Quiet Zoom Earth prompts", "Allow Zoom Earth patched installation",
        "Remove Zoom Earth LiteAPKs promotions"};
    static int assertions;
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        assertions++;
    }
    static byte[] read(Path apk, String entry) throws Exception {
        try (ZipFile zip = new ZipFile(apk.toFile())) { return zip.getInputStream(zip.getEntry(entry)).readAllBytes(); }
    }
    static Map<String,String> hashes(Path apk) throws Exception {
        Map<String,String> result = new TreeMap<>();
        try (ZipFile zip = new ZipFile(apk.toFile())) {
            for (ZipEntry e : Collections.list(zip.entries())) {
                if (e.isDirectory() || e.getName().matches("(?i)META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC)|SIG-[^/]*)")) continue;
                byte[] bytes = zip.getInputStream(e).readAllBytes();
                result.put(e.getName(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
            }
        }
        return result;
    }
    static Path apply(Path input, Path mpp, int mask, Path root, boolean reject) throws Exception {
        Files.createDirectories(root);
        Set<Patch<?>> selected = new LinkedHashSet<>();
        for (Patch<?> p : PatchKt.loadPatchesFromJar(Set.of(mpp.toFile()))) {
            for (int i = 0; i < NAMES.length; i++) if ((mask & (1 << i)) != 0 && NAMES[i].equals(p.getName())) {
                check(p instanceof RawResourcePatch && !p.getDefault(), "Raw resource patch must be opt-in");
                check(p.getCompatibility().get(0).getTargets().get(0).isExperimental(), "Experimental target");
                selected.add(p);
            }
        }
        check(selected.size() == Integer.bitCount(mask), "All requested patches found");
        PatcherConfig config = new PatcherConfig(input.toFile(), root.resolve("temp").toFile(), Set.of(),
            BytecodeMode.STRIP_FAST, NoOpDexVerifier.INSTANCE, root.resolve("workspace").toFile(), "2.1.0", true);
        Path output = root.resolve("zoom-unsigned.apk");
        try (Patcher patcher = new Patcher(config)) {
            patcher.plusAssign(selected);
            check(config.getBytecodeMode$morphe_patcher() == BytecodeMode.NONE, "No DEX processing");
            check(config.getResourceMode$morphe_patcher() == ResourceMode.RAW_ONLY, "No resource recompilation");
            List<PatchResult> events = new ArrayList<>();
            FlowCollector<PatchResult> collector = (event, c) -> { events.add(event); return Unit.INSTANCE; };
            BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE, (scope, c) -> patcher.invoke().collect(collector, c));
            check(events.size() == selected.size(), "Every patch produced a result");
            if (reject) {
                check(events.stream().allMatch(e -> e.getException() != null), "Unsafe input must be rejected");
                return null;
            }
            for (PatchResult e : events) if (e.getException() != null) throw new IllegalStateException(e.getException());
            PatcherResult result = patcher.get();
            check(result.getDexFiles().isEmpty(), "No replacement DEX");
            check(result.getResources().getResourcesApk() == null, "No compiled resources");
            check(result.getResources().getDeleteResources().isEmpty(), "No deleted resources");
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            ApkUtils.INSTANCE.applyTo(result, output.toFile());
        }
        Map<String,String> before = hashes(input), after = hashes(output);
        check(before.keySet().equals(after.keySet()), "No app entries added or removed");
        Set<String> changed = new TreeSet<>();
        for (String name : before.keySet()) if (!before.get(name).equals(after.get(name))) changed.add(name);
        Set<String> expected = new TreeSet<>();
        if ((mask & 15) != 0) expected.add(ASSET);
        if ((mask & 48) != 0) expected.add("classes.dex");
        check(changed.equals(expected), "Only the requested app entries changed: " + changed);
        Files.write(root.resolve("app.js"), read(output, ASSET));
        return output;
    }
    static void alterAsset(Path input, Path output) throws Exception {
        try (ZipFile src = new ZipFile(input.toFile()); ZipOutputStream dst = new ZipOutputStream(Files.newOutputStream(output))) {
            for (ZipEntry e : Collections.list(src.entries())) {
                dst.putNextEntry(new ZipEntry(e.getName())); dst.write(src.getInputStream(e).readAllBytes());
                if (e.getName().equals(ASSET)) dst.write("\n// unknown modification".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                dst.closeEntry();
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path splits = Path.of(args[0]).toAbsolutePath(), mpp = Path.of(args[1]).toAbsolutePath();
        Path root = Path.of(args[2]).toAbsolutePath(); Files.createDirectories(root);
        Path original = root.resolve("original-merged.apk");
        try (ApkBundle bundle = new ApkBundle()) {
            bundle.loadApkDirectory(splits.toFile(), false);
            check(bundle.getApkModuleList().size() == 5, "All five supplied splits are included");
            try (ApkModule merged = bundle.mergeModules(false); var writer = merged.createApkFileWriter(original.toFile())) { writer.write(); }
        }
        Files.write(root.resolve("original.js"), read(original, ASSET));
        int preserved = hashes(original).size() - 1;
        for (int mask = 1; mask < 16; mask++) {
            Path output = apply(original, mpp, mask, root.resolve("mask-" + mask), false);
            apply(output, mpp, mask, root.resolve("reapply-" + mask), true);
            if (mask == 15) {
                Path signed = root.resolve("signature-test.apk");
                ApkUtils.INSTANCE.signApk(output.toFile(), signed.toFile(), "Zoom Earth verification",
                    new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(), "verification-only", "fixture", "verification-only"), 32);
                check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(), "Android signature verifies");
                check(hashes(output).equals(hashes(signed)), "Signing retains all app-entry bytes");
            }
            System.out.println("PASS mask " + mask + ": one asset changed; " + preserved + " entries preserved; reapply rejected");
        }
        // Independent patches must also compose across separate patcher runs.
        Path successive = original;
        for (int mask : new int[]{8, 4, 2, 1}) successive = apply(successive, mpp, mask, root.resolve("successive-" + mask), false);
        check(Arrays.equals(read(successive, ASSET), read(root.resolve("mask-15/zoom-unsigned.apk"), ASSET)), "Patch order is immaterial");
        for (String kind : new String[]{"version", "package", "asset"}) {
            Path wrong = root.resolve("wrong-" + kind + ".apk");
            if (kind.equals("asset")) alterAsset(original, wrong);
            else try (ApkModule apk = ApkModule.loadApkFile(original.toFile())) {
                if (kind.equals("version")) apk.getAndroidManifest().getManifestElement().searchAttributeByResourceId(0x0101021c).setValueAsString("6.3.4");
                else apk.getAndroidManifest().getManifestElement().searchAttributeByName("package").setValueAsString("ca.mlkem.unrelated");
                apk.setApkSignatureBlock(null);
                try (var writer = apk.createApkFileWriter(wrong.toFile())) { writer.write(); }
            }
            apply(wrong, mpp, 15, root.resolve("reject-" + kind), true);
        }
        Files.writeString(root.resolve("verification.json"), "{\"result\":\"passed\",\"combinations\":15,\"assertions\":" + assertions +
            ",\"unchanged_entries_per_combination\":" + preserved + ",\"phone_tested\":false}\n");
        System.out.println("PASS " + assertions + " assertions; 15 combinations; reverse order; rejection cases; APK signing");
    }
}
