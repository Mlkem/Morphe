import app.morphe.patcher.*;
import app.morphe.patcher.apk.ApkUtils;
import app.morphe.patcher.dex.*;
import app.morphe.patcher.patch.*;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.dexbacked.DexBackedDexFile;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.flow.FlowCollector;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Run with input APK, collection MPP and a temporary output directory. No device required. */
public final class VerifyManagerMpp {
    private static final String RESOLVER = "Lapp/morphe/manager/domain/manager/DownloadUrlResolver;";
    private static final String EXTENSION = "Lca/mlkem/manager/ApkMirrorUrls;";
    // The official Android Gradle build also emits this empty resource class;
    // the offline Java/D8 verification build does not generate Android resources.
    private static final String RESOURCE_CONSTRUCTOR = "Lca/mlkem/manager/extension/R;-><init>()V";
    private static final String HOOK = EXTENSION + "->forApp(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";

    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    private static Map<String, Method> methods(Path apk) throws Exception {
        Map<String, Method> result = new TreeMap<>();
        try (ZipFile z = new ZipFile(apk.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (!e.getName().matches("classes[0-9]*\\.dex")) continue;
                DexBackedDexFile dex = DexBackedDexFile.fromInputStream(Opcodes.getDefault(),
                    new BufferedInputStream(z.getInputStream(e)));
                for (ClassDef c : dex.getClasses()) for (Method m : c.getMethods()) {
                    check(result.put(m.toString(), m) == null, "duplicate method " + m);
                    if (m.getImplementation() != null) new MutableMethodImplementation(m.getImplementation());
                }
            }
        }
        return result;
    }
    private static List<Instruction> instructions(Method method) {
        List<Instruction> result = new ArrayList<>();
        method.getImplementation().getInstructions().forEach(result::add);
        return result;
    }
    private static boolean isHook(Instruction instruction) {
        return instruction instanceof ReferenceInstruction &&
            HOOK.equals(((ReferenceInstruction) instruction).getReference().toString());
    }
    private static String instruction(Instruction i) {
        StringBuilder b = new StringBuilder(i.getOpcode().toString());
        if (i instanceof OneRegisterInstruction) b.append(" A=").append(((OneRegisterInstruction)i).getRegisterA());
        if (i instanceof TwoRegisterInstruction) b.append(" B=").append(((TwoRegisterInstruction)i).getRegisterB());
        if (i instanceof ThreeRegisterInstruction) b.append(" C=").append(((ThreeRegisterInstruction)i).getRegisterC());
        if (i instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction x = (FiveRegisterInstruction)i;
            b.append(" regs=").append(x.getRegisterCount()).append(':').append(x.getRegisterC()).append(',')
                .append(x.getRegisterD()).append(',').append(x.getRegisterE()).append(',')
                .append(x.getRegisterF()).append(',').append(x.getRegisterG());
        }
        if (i instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction x = (RegisterRangeInstruction)i;
            b.append(" range=").append(x.getStartRegister()).append(':').append(x.getRegisterCount());
        }
        if (i instanceof WideLiteralInstruction) b.append(" literal=").append(((WideLiteralInstruction)i).getWideLiteral());
        if (i instanceof OffsetInstruction) b.append(" offset=").append(((OffsetInstruction)i).getCodeOffset());
        if (i instanceof ReferenceInstruction) b.append(" ref=").append(((ReferenceInstruction)i).getReference());
        return b.toString();
    }
    private static int verifyHooks(Map<String, Method> before, Map<String, Method> after) {
        int count = 0;
        for (Method old : before.values()) {
            if (!old.getDefiningClass().equals(RESOLVER) ||
                !Set.of("apiSearchUrl", "webSearchUrl", "resolve").contains(old.getName())) continue;
            Method updated = after.get(old.toString());
            List<Instruction> a = instructions(old), b = instructions(updated);
            check(b.size() >= 4 && isHook(b.get(0)), "hook missing: " + old);
            check(b.get(0).getOpcode() == Opcode.INVOKE_STATIC_RANGE, "hook opcode");
            RegisterRangeInstruction call = (RegisterRangeInstruction)b.get(0);
            int expected = updated.getImplementation().getRegisterCount() - updated.getParameterTypes().size();
            check(call.getStartRegister() == expected && call.getRegisterCount() == 2, "hook must receive p1/p2");
            check(expected >= 2, "v0 must not alias this/parameters");
            check(b.get(1).getOpcode() == Opcode.MOVE_RESULT_OBJECT && ((OneRegisterInstruction)b.get(1)).getRegisterA() == 0, "hook result");
            check(b.get(2).getOpcode() == Opcode.IF_EQZ && ((OneRegisterInstruction)b.get(2)).getRegisterA() == 0, "null falls back");
            check(((OffsetInstruction)b.get(2)).getCodeOffset() == b.get(2).getCodeUnits() + b.get(3).getCodeUnits(), "branch lands on original body");
            check(b.get(3).getOpcode() == Opcode.RETURN_OBJECT && ((OneRegisterInstruction)b.get(3)).getRegisterA() == 0, "early return");
            int skip = isHook(a.get(0)) ? 0 : 4;
            check(a.size() + skip == b.size(), "no duplicated hook or lost instructions");
            check(old.getImplementation().getRegisterCount() == updated.getImplementation().getRegisterCount(), "register count preserved");
            for (int j = 0; j < a.size(); j++) check(instruction(a.get(j)).equals(instruction(b.get(j + skip))), "original instruction changed: " + old + " #" + j);
            check(old.getImplementation().getTryBlocks().isEmpty() && updated.getImplementation().getTryBlocks().isEmpty(), "resolver try blocks");
            count++;
        }
        check(count == 3, "expected three resolver entry points");
        check(after.values().stream().anyMatch(m -> m.getDefiningClass().equals(EXTENSION) && m.getName().equals("forApp")), "extension missing");
        Set<String> added = new HashSet<>(after.keySet());
        added.removeAll(before.keySet());
        check(added.stream().allMatch(m -> m.startsWith(EXTENSION + "->") || m.equals(RESOURCE_CONSTRUCTOR)), "unexpected added app methods");
        if (added.contains(RESOURCE_CONSTRUCTOR)) {
            List<Instruction> stub = instructions(after.get(RESOURCE_CONSTRUCTOR));
            check(stub.size() == 2 && stub.get(0).getOpcode() == Opcode.INVOKE_DIRECT
                && ((ReferenceInstruction)stub.get(0)).getReference().toString().equals("Ljava/lang/Object;-><init>()V")
                && stub.get(1).getOpcode() == Opcode.RETURN_VOID, "resource class must be empty constructor boilerplate");
        }
        check(after.keySet().containsAll(before.keySet()), "original methods missing");
        return count;
    }
    private static int verifyResources(Path before, Path after) throws Exception {
        int count = 0;
        try (ZipFile a = new ZipFile(before.toFile()); ZipFile b = new ZipFile(after.toFile())) {
            for (ZipEntry e : Collections.list(a.entries())) {
                if (e.getName().matches("classes[0-9]*\\.dex") || e.getName().startsWith("META-INF/")) continue;
                check(Arrays.equals(a.getInputStream(e).readAllBytes(), b.getInputStream(b.getEntry(e.getName())).readAllBytes()), "resource changed: " + e.getName());
                count++;
            }
        }
        return count;
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), mpp = Path.of(args[1]), root = Path.of(args[2]);
        Files.createDirectories(root);
        PatchLoader.Jar loader = PatchKt.loadPatchesFromJar(Set.of(mpp.toFile()));
        Patch<?> patch = loader.stream().filter(p -> "APKMirror search for collection apps".equals(p.getName()))
            .findFirst().orElseThrow(() -> new AssertionError("Manager patch missing"));
        check(patch instanceof BytecodePatch && !patch.getDefault(), "opt-in bytecode patch");
        check(patch.getCompatibility().size() == 1 && patch.getCompatibility().get(0).getPackageName().equals("app.morphe.manager"), "package restriction");
        PatcherConfig config = new PatcherConfig(input.toFile(), root.resolve("temp").toFile(),
            Set.of(), BytecodeMode.STRIP_FAST, NoOpDexVerifier.INSTANCE,
            root.resolve("workspace").toFile(), "2.1.0", true);
        Path output = root.resolve("manager-patched-unsigned.apk");
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
            check(events.size() == 1, "patch executes");
            PatcherResult result = patcher.get();
            check(!result.getDexFiles().isEmpty(), "modified DEX emitted");
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            ApkUtils.INSTANCE.applyTo(result, output.toFile());
        }
        Map<String, Method> before = methods(input), after = methods(output);
        int hooks = verifyHooks(before, after), resources = verifyResources(input, output);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        String report = "{\n  \"input\": \"" + input.getFileName() + "\",\n  \"input_sha256\": \"" + hash + "\",\n"
            + "  \"patcher\": \"2.1.0\",\n  \"original_methods\": " + before.size() + ",\n"
            + "  \"output_methods\": " + after.size() + ",\n  \"verified_entry_hooks\": " + hooks + ",\n"
            + "  \"original_resolver_bodies_preserved\": true,\n  \"unchanged_resource_entries\": " + resources + ",\n"
            + "  \"all_dex_methods_structurally_valid\": true,\n  \"phone_tested\": false\n}\n";
        Files.writeString(root.resolve("validation.json"), report);
        System.out.print(report);
        System.out.println("OUTPUT " + output);
    }
}
