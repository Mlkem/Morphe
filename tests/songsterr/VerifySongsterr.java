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
import java.nio.*;
import java.io.ByteArrayInputStream;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Apply the actual MPP to a user-supplied APK. Never commit inputs, APKs or keys. */
public class VerifySongsterr {
    static final String[] NAMES = {"Enable Songsterr premium features"};
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
        Path output = root.resolve("songsterr-unsigned.apk");
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
        check(changed.equals(Set.of("classes.dex")), "Only primary DEX changed: " + changed);
        return output;
    }
    static DexBackedDexFile dex(byte[] bytes) throws Exception {
        return DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new ByteArrayInputStream(bytes));
    }
    static DexBackedMethod resolver(DexBackedDexFile dex) {
        for (var c : dex.getClasses()) for (var m : c.getMethods())
            if (m.toString().equals("Lcom/songsterr/iap/f;->b()Lf2;")) return m;
        throw new AssertionError("Missing access resolver");
    }
    static void verifyDex(byte[] original, byte[] patched) throws Exception {
        check(original.length == patched.length, "DEX byte length retained");
        var method = resolver(dex(patched));
        Map<Integer,Instruction> instructions = new TreeMap<>();
        int offset=0;
        for (var i : method.getImplementation().getInstructions()) {
            instructions.put(offset,i); offset+=i.getCodeUnits();
        }
        // Follow the actual emitted path. It must not read account, network,
        // billing, time, preferences or instance fields before returning Pro.
        Object[] registers = new Object[method.getImplementation().getRegisterCount()];
        int pc=0; Object result=null;
        for (int step=0; step<4; step++) {
            Instruction i=instructions.get(pc);
            check(i!=null, "Reachable instruction boundary");
            if (i.getOpcode()==Opcode.GOTO_16) pc+=((OffsetInstruction)i).getCodeOffset();
            else if (i.getOpcode()==Opcode.SGET_OBJECT) {
                registers[((OneRegisterInstruction)i).getRegisterA()]=((ReferenceInstruction)i).getReference().toString();
                pc+=i.getCodeUnits();
            } else if (i.getOpcode()==Opcode.RETURN_OBJECT) {
                result=registers[((OneRegisterInstruction)i).getRegisterA()]; break;
            } else throw new AssertionError("Unexpected state-dependent instruction: "+i.getOpcode());
        }
        check("Le2;->v:Le2;".equals(result), "Resolver always returns original Pro singleton");
        // Confirm that this exact singleton is initialized with the highest rank.
        int rank=-99; boolean rankVerified=false;
        for(var c:dex(original).getClasses()) if(c.getType().equals("Le2;")) for(var m:c.getMethods())
            for(var i:m.getImplementation().getInstructions()) {
                if(i instanceof NarrowLiteralInstruction n) rank=n.getNarrowLiteral();
                if(i.getOpcode()==Opcode.SPUT_OBJECT && ((ReferenceInstruction)i).getReference().toString().equals(result))
                    rankVerified=rank==9;
            }
        check(rankVerified,"Existing Pro singleton has rank nine");
        int start=((DexBackedInstruction)instructions.get(0)).instructionStart;
        for (int i=0;i<original.length;i++) {
            if(i>=8 && i<32 || i>=start && i<start+4) continue;
            if(original[i]!=patched[i]) throw new AssertionError("Unexpected DEX byte edit: "+i);
        }
        check(!Arrays.equals(Arrays.copyOfRange(original,start,start+4),Arrays.copyOfRange(patched,start,start+4)),"One instruction changed; all other code and data retained");
        check(Arrays.equals(MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(patched,32,patched.length)),Arrays.copyOfRange(patched,12,32)),"Valid DEX SHA-1");
        Adler32 checksum=new Adler32(); checksum.update(patched,12,patched.length-12);
        check(ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).getInt(8)==(int)checksum.getValue(),"Valid DEX Adler32");
    }
    static void alteredDex(Path input,Path output,String name) throws Exception {
        try(ZipFile src=new ZipFile(input.toFile());ZipOutputStream dst=new ZipOutputStream(Files.newOutputStream(output))) {
            for(ZipEntry e:Collections.list(src.entries())) {
                byte[] bytes=src.getInputStream(e).readAllBytes();
                if(e.getName().equals(name)) bytes[bytes.length-1]^=1;
                dst.putNextEntry(new ZipEntry(e.getName()));dst.write(bytes);dst.closeEntry();
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Path splits=Path.of(args[0]).toAbsolutePath(),mpp=Path.of(args[1]).toAbsolutePath(),root=Path.of(args[2]).toAbsolutePath();
        Files.createDirectories(root);
        Path original=root.resolve("original-merged.apk");
        try(ApkBundle bundle=new ApkBundle()) {
            bundle.loadApkDirectory(splits.toFile(),false);
            check(bundle.getApkModuleList().size()==5,"All five supplied splits included");
            try(ApkModule merged=bundle.mergeModules(false);var writer=merged.createApkFileWriter(original.toFile())) {writer.write();}
        }
        Path output=apply(original,mpp,1,root.resolve("patched"),false);
        verifyDex(read(original,"classes.dex"),read(output,"classes.dex"));
        int methods=0;
        for(String name:List.of("classes.dex","classes2.dex","classes3.dex"))
            for(var c:dex(read(output,name)).getClasses()) for(var m:c.getMethods()) if(m.getImplementation()!=null) {
                new com.android.tools.smali.dexlib2.builder.MutableMethodImplementation(m.getImplementation());methods++;
            }
        check(methods>10000,"All output method implementations structurally parsed");
        check(hashes(output).keySet().stream().anyMatch(n->n.startsWith("lib/arm64-v8a/") && n.endsWith(".so")),"Native split preserved");
        apply(output,mpp,1,root.resolve("reapply"),true);
        for(String name:List.of("classes.dex","classes2.dex","classes3.dex")) {
            Path wrong=root.resolve("altered-"+name+".apk");alteredDex(original,wrong,name);
            apply(wrong,mpp,1,root.resolve("reject-"+name),true);
        }
        for(String kind:List.of("package","version","code")) {
            Path wrong=root.resolve("wrong-"+kind+".apk");
            try(ApkModule apk=ApkModule.loadApkFile(original.toFile())) {
                var manifest=apk.getAndroidManifest().getManifestElement();
                if(kind.equals("package")) manifest.searchAttributeByName("package").setValueAsString("ca.mlkem.unrelated");
                else if(kind.equals("version")) manifest.searchAttributeByResourceId(0x0101021c).setValueAsString("7.1.7");
                else manifest.searchAttributeByResourceId(0x0101021b).setData(1);
                apk.setApkSignatureBlock(null);
                try(var writer=apk.createApkFileWriter(wrong.toFile())) {writer.write();}
            }
            apply(wrong,mpp,1,root.resolve("reject-"+kind),true);
        }
        Path signed=root.resolve("signature-test.apk");
        ApkUtils.INSTANCE.signApk(output.toFile(),signed.toFile(),"Songsterr verification",
            new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(),"verification-only","fixture","verification-only"),32);
        check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(),"Android signature verifies");
        check(hashes(output).equals(hashes(signed)),"Signing preserves app entries");
        Files.writeString(root.resolve("verification.json"),"{\"result\":\"passed\",\"input_package\":\"com.songsterr\",\"version\":\"7.1.6\",\"version_code\":2012282388,\"splits\":5,\"methods_parsed\":"+methods+
            ",\"changed_methods\":1,\"instruction_bytes_replaced\":4,\"preserved_app_entries\":"+(hashes(output).size()-1)+
            ",\"resolver_path\":\"existing Pro singleton, rank 9\",\"input_rejections\":7,\"signature\":\"passed\",\"phone_tested\":false,\"server_features_tested\":false}\n");
        System.out.println("PASS: "+methods+" method implementations; one changed instruction; seven input rejections; all other app entries preserved; Android signature valid.");
    }
}
