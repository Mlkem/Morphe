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
public final class VerifyNiagaraMpp {
    private static final String EXTENSION = "Lca/mlkem/niagara/";
    private static final String UPDATES = "Lca/mlkem/updates/";
    private static final String HOOK = "Lca/mlkem/niagara/AutoFolders;->";
    private static final String PRO_CONSTRUCTOR = "Lb/orMU53BLykeD;-><init>(ZZZ)V";
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
            ((ReferenceInstruction) instruction).getReference().toString().startsWith(HOOK);
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
    // The native database lambda uses a switch for both reads and writes. Verify
    // the exact branch called by NativeBridge.persisted before accepting an APK.
    private static boolean readsSavedFeed(Method query) {
        Map<Integer, Instruction> code = new HashMap<>();
        int address = 0;
        for (Instruction i : instructions(query)) {
            code.put(address, i);
            address += i.getCodeUnits();
        }
        for (Map.Entry<Integer, Instruction> entry : code.entrySet()) {
            if (entry.getValue().getOpcode() != Opcode.PACKED_SWITCH) continue;
            int start = entry.getKey();
            SwitchPayload cases = (SwitchPayload)code.get(start + ((OffsetInstruction)entry.getValue()).getCodeOffset());
            for (SwitchElement item : cases.getSwitchElements()) {
                if (item.getKey() != 6) continue;
                Instruction first = code.get(start + item.getOffset());
                if (!(first instanceof ReferenceInstruction ref) ||
                    !(ref.getReference() instanceof com.android.tools.smali.dexlib2.iface.reference.StringReference str)) return false;
                return str.getString().trim().replaceAll("\\s+", " ").equals("SELECT * FROM Feed WHERE id = (?) AND type = (?)");
            }
        }
        return false;
    }
    private static int verifyHooks(Map<String, Method> before, Map<String, Method> after, boolean auto, boolean pro) {
        int count = 0;
        int proOverrides = 0;
        boolean build38 = before.containsKey("Lb/P7sI5PMvmtbovH;->Idx11cNZ15(Ljava/util/Set;Ljava/util/Set;Ljava/util/Set;)V");
        String controller = build38 ? "Lb/P7sI5PMvmtbovH;" : "Lb/uXEn98dBZyHFNcs4SNt;";
        String changed = build38 ? "Idx11cNZ15" : "QkO0iYBXnHfGs";
        String feedChanged = build38 ? "dAqlsNOBOcwBdqab2iL3pe" : "i31xkztVRqgXM6VmkhJG3j1ad1OM";
        String settings = build38 ? "Lb/Il9nLZSTco0tqHmWDubcE1UH7VJH;" : "Lb/rkhpNkl4D1;";
        String readyField = build38 ? "VJE51uPIoecJiKg0wuKHpmyliYfu:Lb/Qqn68ODSFrckn;"
                                    : "uP3MjWikAhbpaogvi8rIfV8OZg4pk:Lb/naks8ZxpaWirE;";
        Set<String> expected = Set.of(controller+"-><init>", controller+"->"+changed, controller+"->"+feedChanged,
            "Lbitpit/launcher/ui/HomeActivity;->onResume", settings+"->"+changed);
        if (auto) {
            String query = build38 ? "Lb/B9ZYK85o3JvPks7of8tnbXtcon3IV;" : "Lb/qBlsmBnzmgtZe0gU3u2b;";
            check(before.values().stream().anyMatch(m -> m.getDefiningClass().equals(query) &&
                m.getImplementation() != null && readsSavedFeed(m)), "Folder persistence check reads the saved feed");
        }
        Set<String> found = new HashSet<>();
        check(after.keySet().containsAll(before.keySet()), "Original methods missing");
        for (Method old : before.values()) {
            if (old.getImplementation() == null) continue;
            Method updated = after.get(old.toString());
            List<Instruction> a = instructions(old), b = instructions(updated);
            if (pro && old.toString().equals(PRO_CONSTRUCTOR)) {
                check(a.size()==5 && b.size()==6, "One Pro constructor instruction added");
                Instruction override=b.get(1);
                check(override.getOpcode()==Opcode.CONST_4 &&
                    ((OneRegisterInstruction)override).getRegisterA()==1 &&
                    ((NarrowLiteralInstruction)override).getNarrowLiteral()==1, "Pro parameter forced true");
                verifyProFlags(updated);
                b.remove(1);
                proOverrides++;
            }
            List<Instruction> kept = new ArrayList<>();
            for (Instruction i : b) {
                if (isHook(i)) {
                    count++;
                    check(found.add(old.getDefiningClass()+"->"+old.getName()), "Duplicate hook");
                    if (i instanceof RegisterRangeInstruction) {
                        RegisterRangeInstruction r = (RegisterRangeInstruction)i;
                        check(r.getRegisterCount()==1 && r.getStartRegister()<updated.getImplementation().getRegisterCount(), "Valid hook register");
                        String ref=((ReferenceInstruction)i).getReference().toString();
                        int p0=updated.getImplementation().getRegisterCount()-updated.getParameterTypes().size()-1;
                        if (ref.contains("->attach(")) {
                            Instruction previous = kept.get(kept.size()-1);
                            check(previous.getOpcode()==Opcode.IPUT_OBJECT && previous instanceof TwoRegisterInstruction &&
                                ((ReferenceInstruction)previous).getReference().toString().equals(controller+"->"+readyField),
                                "Attach runs at the last initialization, before R8 reuses p0");
                            check(r.getStartRegister()==((TwoRegisterInstruction)previous).getRegisterB(), "Attach uses the live controller receiver");
                        } else check(r.getStartRegister()==(ref.contains("->settings(")?3:p0), "Correct hook argument");
                    }
                } else kept.add(i);
            }
            check(a.size()==kept.size(), "Original instruction count changed: "+old);
            check(old.getImplementation().getRegisterCount()==updated.getImplementation().getRegisterCount(), "Registers changed");
            for(int j=0;j<a.size();j++) check(instruction(a.get(j)).equals(instruction(kept.get(j))), "Original instruction changed: "+old+" #"+j);
        }
        check(count==(auto?5:0) && found.equals(auto?expected:Collections.emptySet()), "Expected auto-folder hooks: "+found);
        check(proOverrides==(pro?1:0), "Exactly one local Pro override when selected");
        for(String added:after.keySet()) if(!before.containsKey(added))
            check(added.startsWith(EXTENSION)||added.startsWith(UPDATES), "Unexpected class/stub packaged: "+added);
        String bridge="Lca/mlkem/niagara/NativeContinuation;-><init>(Lca/mlkem/niagara/NativeBridge$Done;)V";
        check(after.containsKey(bridge)==auto, "Auto-folder extension only included when selected");
        if (auto && build38) {
            String callback = "Mp48tfHH45TRNN7q3taBNtTIbX(Ljava/lang/Object;)Ljava/lang/Object;";
            check(after.containsKey(EXTENSION+"NativeContinuation;->"+callback), "Host continuation override");
            String read = "Lb/FdG6xMzPSaymcSpktNphRSSpiuQd;->jWvXewWdTvbuXsq9m9mUy(Lb/oF4VPGelezQL5RhSUQALi0;ZZLb/Gt2e7OqBn9CvfhW;)Ljava/lang/Object;";
            String context = "Lb/T3aZigmfOFNniS;->tmvaFiZ3YIQF7MAEfYHiQR52ffC()Lb/IdZUlMXZV6Pp17XJ;";
            check(before.containsKey(read) && before.containsKey(context), "Native database and coroutine contracts");
            Set<String> extensionStrings = new HashSet<>();
            for (Method m : after.values()) {
                if (!m.getDefiningClass().startsWith(EXTENSION) || m.getImplementation() == null) continue;
                for (Instruction i : instructions(m)) {
                    if (i instanceof ReferenceInstruction ref && ref.getReference() instanceof com.android.tools.smali.dexlib2.iface.reference.StringReference str)
                        extensionStrings.add(str.getString());
                }
            }
            check(extensionStrings.contains("jWvXewWdTvbuXsq9m9mUy") && extensionStrings.contains("tmvaFiZ3YIQF7MAEfYHiQR52ffC"), "Injected folder bindings");
            for (Instruction i : instructions(after.get(bridge))) {
                if (!(i instanceof ReferenceInstruction ref)) continue;
                String target = ref.getReference().toString();
                if (target.startsWith("Lb/") && target.contains("->")) {
                    check(before.containsKey(target), "Continuation invokes a missing native method: "+target);
                }
            }
        }
        return count;
    }
    /** Execute the tiny constructor's boolean operations for all original input combinations. */
    private static void verifyProFlags(Method constructor) {
        for(int bits=0;bits<8;bits++) {
            int[] registers={0,bits&1,(bits>>1)&1,(bits>>2)&1};
            Map<String,Integer> fields=new HashMap<>();
            for(Instruction i:instructions(constructor)) {
                if(i.getOpcode()==Opcode.CONST_4)
                    registers[((OneRegisterInstruction)i).getRegisterA()]=((NarrowLiteralInstruction)i).getNarrowLiteral();
                else if(i.getOpcode()==Opcode.IPUT_BOOLEAN) {
                    TwoRegisterInstruction store=(TwoRegisterInstruction)i;
                    check(store.getRegisterB()==0,"Stores target the entitlement object");
                    fields.put(((ReferenceInstruction)i).getReference().toString(),registers[store.getRegisterA()]);
                } else check(i.getOpcode()==Opcode.INVOKE_DIRECT || i.getOpcode()==Opcode.RETURN_VOID,"Unexpected constructor operation");
            }
            check(fields.size()==3 && fields.get("Lb/orMU53BLykeD;->QkO0iYBXnHfGs:Z")==1,"Pro enabled");
            check(fields.get("Lb/orMU53BLykeD;->C07CQRD69zoXzbLCDIVboCy:Z")==((bits>>1)&1),"Second flag preserved");
            check(fields.get("Lb/orMU53BLykeD;->R22npmuNnAlm:Z")==((bits>>2)&1),"Third flag preserved");
        }
    }
    private static int verifyResources(Path before, Path after) throws Exception {
        int count = 0;
        try (ZipFile a = new ZipFile(before.toFile()); ZipFile b = new ZipFile(after.toFile())) {
            for (ZipEntry e : Collections.list(a.entries())) {
                if (e.getName().matches("classes[0-9]*\\.dex") || e.getName().startsWith("META-INF/")
                    || e.getName().equals("AndroidManifest.xml") || e.getName().equals("resources.arsc")) continue;
                check(Arrays.equals(a.getInputStream(e).readAllBytes(), b.getInputStream(b.getEntry(e.getName())).readAllBytes()), "resource changed: " + e.getName());
                count++;
            }
        }
        return count;
    }
    public static void main(String[] args) throws Exception {
        Path input = Path.of(args[0]), mpp = Path.of(args[1]), root = Path.of(args[2]);
        Files.createDirectories(root);
        String mode=args.length>3?args[3]:"auto";
        check(Set.of("auto","pro","both","restore","folders-restore").contains(mode),"Known patch selection");
        boolean auto=Set.of("auto", "both", "folders-restore").contains(mode);
        boolean pro=Set.of("pro", "both").contains(mode);
        boolean restore=Set.of("restore", "folders-restore").contains(mode);
        Set<String> selectedNames=new HashSet<>();
        if(restore)selectedNames.add("Restore Niagara Pro membership");
        if(auto)selectedNames.add("Automatic app folders");
        if(pro)selectedNames.add("Enable Niagara Pro");
        PatchLoader.Jar loader = PatchKt.loadPatchesFromJar(Set.of(mpp.toFile()));
        Set<Patch<?>> selected=new HashSet<>();
        loader.stream().filter(p -> selectedNames.contains(p.getName())).forEach(selected::add);
        check(selected.size()==selectedNames.size(),"Selected Niagara patches present");
        for(Patch<?> patch:selected) {
            check(patch instanceof BytecodePatch && !patch.getDefault(), "opt-in bytecode patch");
            check(patch.getCompatibility().size() == 1 && patch.getCompatibility().get(0).getPackageName().equals("bitpit.launcher"), "package restriction");
        }
        PatcherConfig config = new PatcherConfig(input.toFile(), root.resolve("temp").toFile(),
            Set.of(), BytecodeMode.STRIP_FAST, NoOpDexVerifier.INSTANCE,
            root.resolve("workspace").toFile(), "2.1.0", true);
        Path output = root.resolve("niagara-patched-unsigned.apk");
        try (Patcher patcher = new Patcher(config)) {
            patcher.plusAssign(selected);
            List<PatchResult> events = new ArrayList<>();
            FlowCollector<PatchResult> collector = (event, continuation) -> {
                events.add(event);
                if (event.getException() != null) throw new IllegalStateException("Patch failed", event.getException());
                return Unit.INSTANCE;
            };
            BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,
                (scope, continuation) -> patcher.invoke().collect(collector, continuation));
            for(String name:selectedNames) check(events.stream().anyMatch(e ->
                name.equals(e.getPatch().getName())), "Niagara patch executes: "+name);
            PatcherResult result = patcher.get();
            check(!result.getDexFiles().isEmpty(), "modified DEX emitted");
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            ApkUtils.INSTANCE.applyTo(result, output.toFile());
        }
        Map<String, Method> before = methods(input), after = methods(output);
        check(after.containsKey(EXTENSION+"restore/RestoreMembershipActivity;->onCreate(Landroid/os/Bundle;)V")==restore,
            "Membership share activity included only when selected");
        int hooks = verifyHooks(before, after, auto, pro), resources = verifyResources(input, output);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        String report = "{\n  \"input\": \"" + input.getFileName() + "\",\n  \"input_sha256\": \"" + hash + "\",\n"
            + "  \"patcher\": \"2.1.0\",\n  \"original_methods\": " + before.size() + ",\n"
            + "  \"output_methods\": " + after.size() + ",\n  \"verified_native_hooks\": " + hooks + ",\n"
            + "  \"selection\": \"" + mode + "\",\n  \"verified_local_pro_overrides\": " + (pro?1:0) + ",\n"
            + "  \"entitlement_flag_combinations_verified\": " + (pro?8:0) + ",\n"
            + "  \"original_hooked_bodies_preserved\": true,\n  \"unrelated_method_bodies_preserved\": true,\n"
            + "  \"manifest_and_resource_table\": \"rebuilt for startup-update provider\",\n  \"unchanged_other_resource_entries\": " + resources + ",\n"
            + "  \"all_dex_methods_structurally_valid\": true,\n  \"phone_tested\": false\n}\n";
        Files.writeString(root.resolve("validation.json"), report);
        System.out.print(report);
        System.out.println("OUTPUT " + output);
    }
}
