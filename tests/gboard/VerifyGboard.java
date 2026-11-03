import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.analysis.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.dexbacked.instruction.DexBackedInstruction;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** Verifies real MPP output, including byte preservation and executed mode paths. */
public class VerifyGboard {
    static int assertions;
    static void check(boolean ok, String why) {
        if (!ok) throw new AssertionError(why);
        assertions++;
    }
    static byte[] read(Path apk, String name) throws Exception {
        try (ZipFile z = new ZipFile(apk.toFile())) { return z.getInputStream(z.getEntry(name)).readAllBytes(); }
    }
    static Map<String,String> hashes(Path apk) throws Exception {
        Map<String,String> result = new TreeMap<>();
        try (ZipFile z = new ZipFile(apk.toFile())) {
            for (ZipEntry e : Collections.list(z.entries())) {
                if (e.isDirectory() || e.getName().matches("(?i)META-INF/(MANIFEST\\.MF|[^/]+\\.(SF|RSA|DSA|EC)|SIG-[^/]*)")) continue;
                result.put(e.getName(), HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(z.getInputStream(e).readAllBytes())));
            }
        }
        return result;
    }
    static DexBackedDexFile dex(byte[] b) throws Exception {
        return DexBackedDexFile.fromInputStream(Opcodes.getDefault(), new ByteArrayInputStream(b));
    }
    static Method method(DexBackedDexFile dex, String descriptor) {
        for (var c : dex.getClasses()) for (var m : c.getMethods()) if (m.toString().equals(descriptor)) return m;
        throw new AssertionError("Missing " + descriptor);
    }
    static NavigableMap<Integer,Instruction> code(Method m) {
        NavigableMap<Integer,Instruction> code = new TreeMap<>(); int offset=0;
        for (Instruction i : m.getImplementation().getInstructions()) { code.put(offset,i); offset+=i.getCodeUnits(); }
        return code;
    }
    static int start(Method m) {
        return ((DexBackedInstruction)m.getImplementation().getInstructions().iterator().next()).instructionStart;
    }
    static int execute(Method m, Object[] r, Map<String,Integer> fields, int pc, int stop) {
        var code = code(m);
        for (int step=0; step<20; step++) {
            if (pc==stop) return (Integer)r[12];
            Instruction i = code.get(pc);
            check(i!=null,"Instruction boundary at "+pc);
            int next=pc+i.getCodeUnits();
            switch(i.getOpcode()) {
                case CONST_4 -> r[((OneRegisterInstruction)i).getRegisterA()]=((NarrowLiteralInstruction)i).getNarrowLiteral();
                case IGET -> {
                    var two=(TwoRegisterInstruction)i;
                    check("this".equals(r[two.getRegisterB()]),"Field access has receiver");
                    String key=((ReferenceInstruction)i).getReference().toString();
                    check(fields.containsKey(key),"Known field "+key);
                    r[two.getRegisterA()]=fields.get(key);
                }
                case IF_NEZ -> { if ((Integer)r[((OneRegisterInstruction)i).getRegisterA()]!=0) next=pc+((OffsetInstruction)i).getCodeOffset(); }
                case IF_NE -> {
                    var two=(TwoRegisterInstruction)i;
                    if (!Objects.equals(r[two.getRegisterA()],r[two.getRegisterB()])) next=pc+((OffsetInstruction)i).getCodeOffset();
                }
                case RETURN -> { return (Integer)r[((OneRegisterInstruction)i).getRegisterA()]; }
                case NOP -> {}
                default -> throw new AssertionError("Unexpected instruction on tested path: "+i.getOpcode());
            }
            pc=next;
        }
        throw new AssertionError("Path did not terminate");
    }
    static void preserved(byte[] a, byte[] b, List<int[]> edits) throws Exception {
        check(a.length==b.length,"DEX length retained");
        for (int i=0;i<a.length;i++) {
            boolean allowed=i>=8 && i<32;
            for (int[] range:edits) allowed|=i>=range[0] && i<range[1];
            if (!allowed && a[i]!=b[i]) throw new AssertionError("Unexpected byte edit at "+i);
        }
        check(Arrays.equals(MessageDigest.getInstance("SHA-1").digest(Arrays.copyOfRange(b,32,b.length)),Arrays.copyOfRange(b,12,32)),"DEX SHA-1");
        Adler32 adler=new Adler32();adler.update(b,12,b.length-12);
        check(ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getInt(8)==(int)adler.getValue(),"DEX checksum");
    }
    public static void main(String[] args) throws Exception {
        Path input=Path.of(args[0]), output=Path.of(args[1]);
        var before=hashes(input);var after=hashes(output);
        check(before.keySet().equals(after.keySet()),"No application files added or removed");
        Set<String> changed=new TreeSet<>();
        for(String name:before.keySet()) if(!before.get(name).equals(after.get(name)))changed.add(name);
        check(changed.equals(Set.of("classes.dex","classes5.dex")),"Only the two targeted DEX files change: "+changed);

        byte[] primary=read(output,"classes.dex"), runtime=read(output,"classes5.dex");
        Method resolve=method(dex(primary),"Lpra;->a()I");
        Method support=method(dex(runtime),"Lpqw;->aq()Z");
        Method auto=method(dex(runtime),"Lpqw;->ae()Z");
        Method setter=method(dex(runtime),"Lpqw;->ag(IZZ)Z");
        for(int stored=0;stored<=6;stored++)for(int temporary=0;temporary<=6;temporary++) {
            Object[] r={null,"this"};
            int effective=temporary!=0?temporary:stored;
            int value=execute(resolve,r,Map.of("Lpra;->c:I",stored,"Lpra;->f:I",temporary),0,-1);
            check(value==(effective==3?1:effective),"Saved/temporary modes "+stored+"/"+temporary);
            check(value!=3,"Floating never resolves");
        }
        for(int requested:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,5,6,255,Integer.MAX_VALUE}) {
            Object[] r=new Object[15];Arrays.fill(r,"untouched");r[12]=requested;
            int value=execute(setter,r,Map.of(),22,27);
            check(value==(requested==3?1:requested),"Requested mode "+requested);
            for(int i=0;i<r.length;i++)if(i!=2 && i!=12)check("untouched".equals(r[i]),"Other register preserved");
        }
        for(Method m:List.of(support,auto)) {
            Object[] r=new Object[m.getImplementation().getRegisterCount()];r[r.length-1]="this";
            check(execute(m,r,Map.of(),0,-1)==0,"Capability/automatic entry disabled");
        }
        preserved(read(input,"classes.dex"),primary,List.of(new int[]{start(resolve),start(resolve)+24}));
        preserved(read(input,"classes5.dex"),runtime,List.of(new int[]{start(support),start(support)+4},
            new int[]{start(auto),start(auto)+4},new int[]{start(setter)+44,start(setter)+54}));

        List<ClassProvider> providers=new ArrayList<>();Set<String> classes=new HashSet<>();int methods=0;
        for(String name:after.keySet())if(name.matches("classes[0-9]*\\.dex")) {
            var d=dex(read(output,name));providers.add(new DexClassProvider(d));
            for(var c:d.getClasses()) {
                check(classes.add(c.getType()),"Unique class "+c.getType());
                for(var m:c.getMethods())if(m.getImplementation()!=null) {
                    // Decode every output instruction, including array payloads.
                    for(var ins:m.getImplementation().getInstructions())ins.getCodeUnits();
                    methods++;
                }
            }
        }
        var classpath=new ClassPath(providers);
        for(Method m:List.of(resolve,support,auto,setter)) {
            var analysis=new MethodAnalyzer(classpath,m,null,false);
            check(analysis.getAnalysisException()==null,"Register/type analysis: "+m+" "+analysis.getAnalysisException());
        }
        check(methods>10000,"All output methods decoded");
        System.out.println("PASS: 49 restored/temporary combinations, 11 requested modes, both false gates.");
        System.out.println("PASS: 4 changed methods pass register/type analysis; "+methods+" methods decoded.");
        System.out.println("PASS: all resources, native libraries, other DEX files and code outside 42 targeted bytes unchanged.");
        System.out.println(assertions+" assertions; Android/device runtime testing remains pending.");
    }
}
