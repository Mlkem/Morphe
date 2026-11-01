import app.morphe.patcher.*;
import app.morphe.patcher.apk.ApkUtils;
import app.morphe.patcher.dex.*;
import app.morphe.patcher.patch.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.iface.*;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.flow.FlowCollector;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

public class VerifyOpenWithMpp {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        System.out.println("PASS " + message);
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), mpp = Path.of(args[1]), root = Path.of(args[2]);
        Files.createDirectories(root);
        try (ZipFile z = new ZipFile(mpp.toFile())) {
            check(z.getEntry("classes.dex") != null, "MPP contains Android patch DEX");
            DexBackedDexFile patchDex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(),
                new BufferedInputStream(z.getInputStream(z.getEntry("classes.dex"))));
            boolean found = false;
            for (ClassDef c : patchDex.getClasses()) {
                if (c.getType().equals("Lca/mlkem/openwith/OpenWithProPatch;")) found = true;
                for (Method m : c.getMethods()) if (m.getImplementation() != null)
                    new MutableMethodImplementation(m.getImplementation());
            }
            check(found, "Android DEX contains the patch entry class and valid method structures");
        }
        PatchLoader.Jar loader = PatchKt.loadPatchesFromJar(Set.of(mpp.toFile()));
        check(!loader.isEmpty(), "Morphe loads the patch bundle");
        Patch<?> patch = loader.stream().filter(p -> "Enable Pro file-type handling".equals(p.getName()))
            .findFirst().orElseThrow(() -> new AssertionError("Open with patch missing"));
        check(patch instanceof BytecodePatch && !patch.getDefault(), "Patch is opt-in bytecode patch");
        check(patch.getCompatibility().size() == 1 &&
            patch.getCompatibility().get(0).getPackageName().equals("com.openwith.app"),
            "Patch package restriction is correct");
        check(patch.getCompatibility().get(0).getTargets().get(0).getVersion().equals("1.2.1"),
            "Patch version restriction is correct");
        PatcherConfig config = new PatcherConfig(input.toFile(), root.resolve("temp").toFile(),
            Set.of(), BytecodeMode.STRIP_FAST, NoOpDexVerifier.INSTANCE,
            root.resolve("workspace").toFile(), "2.1.0", true);
        Path output = root.resolve("openwith-patched-unsigned.apk");
        try (Patcher patcher = new Patcher(config)) {
            patcher.plusAssign(Set.of(patch));
            List<PatchResult> events = new ArrayList<>();
            FlowCollector<PatchResult> collector = (event, continuation) -> {
                events.add(event);
                if (event.getException() != null) throw new IllegalStateException("Patch failed", event.getException());
                return Unit.INSTANCE;
            };
            BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> patcher.invoke().collect(collector, continuation));
            check(events.size() == 1, "Selected patch executes successfully");
            PatcherResult result = patcher.get();
            check(!result.getDexFiles().isEmpty(), "Patcher produces modified app DEX");
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            ApkUtils.INSTANCE.applyTo(result, output.toFile());
        }
        int classes = 0, methods = 0;
        try (ZipFile z = new ZipFile(output.toFile())) {
            for (ZipEntry entry : Collections.list(z.entries())) {
                if (!entry.getName().matches("classes[0-9]*\\.dex")) continue;
                DexBackedDexFile dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(),
                    new BufferedInputStream(z.getInputStream(entry)));
                for (ClassDef c : dex.getClasses()) {
                    classes++;
                    for (Method m : c.getMethods()) if (m.getImplementation() != null) {
                        new MutableMethodImplementation(m.getImplementation()); methods++;
                    }
                }
            }
        }
        check(methods > 20000, "Rebuilt app method structures valid: " + classes + " classes, " + methods + " methods");
        System.out.println("OUTPUT " + output);
        System.out.println("Android import/install/runtime testing remains pending.");
    }
}
