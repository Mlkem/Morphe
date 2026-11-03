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

/** Verifies real MPP output, including coexistence and executed mode paths. */
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
        var code = code(m); int result=0;
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
                case INVOKE_VIRTUAL -> {
                    check(((ReferenceInstruction)i).getReference().toString().equals("Lpra;->d()Z"),"Only temporary-mode query");
                    result=fields.get("Lpra;->f:I")!=0?1:0;
                }
                case MOVE_RESULT -> r[((OneRegisterInstruction)i).getRegisterA()]=result;
                case IF_EQZ -> { if ((Integer)r[((OneRegisterInstruction)i).getRegisterA()]==0) next=pc+((OffsetInstruction)i).getCodeOffset(); }
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
    static Map<String,Method> methods(Path apk) throws Exception {
        Map<String,Method> result=new TreeMap<>();Set<String> classes=new HashSet<>();
        try(ZipFile z=new ZipFile(apk.toFile())) {
            for(var e:Collections.list(z.entries()))if(e.getName().matches("classes[0-9]*\\.dex")) {
                var d=dex(z.getInputStream(e).readAllBytes());
                for(var c:d.getClasses()) {
                    check(classes.add(c.getType()),"Unique class "+c.getType());
                    for(var m:c.getMethods())result.put(m.toString(),m);
                }
            }
        }
        return result;
    }
    static String body(Method m) {
        var impl=m.getImplementation();if(impl==null)return "no code";
        var s=new StringBuilder().append(impl.getRegisterCount());
        for(var i:impl.getInstructions()) {
            s.append('|').append(i.getOpcode());
            if(i instanceof OneRegisterInstruction a)s.append(" a").append(a.getRegisterA());
            if(i instanceof TwoRegisterInstruction a)s.append(" b").append(a.getRegisterB());
            if(i instanceof ThreeRegisterInstruction a)s.append(" c").append(a.getRegisterC());
            if(i instanceof FiveRegisterInstruction a)s.append(" regs").append(a.getRegisterCount()).append(',').append(a.getRegisterC()).append(',').append(a.getRegisterD()).append(',').append(a.getRegisterE()).append(',').append(a.getRegisterF()).append(',').append(a.getRegisterG());
            if(i instanceof RegisterRangeInstruction a)s.append(" range").append(a.getStartRegister()).append(',').append(a.getRegisterCount());
            if(i instanceof ReferenceInstruction a)s.append(" ref").append(a.getReference());
            if(i instanceof DualReferenceInstruction a)s.append(" ref2").append(a.getReference2());
            if(i instanceof WideLiteralInstruction a)s.append(" literal").append(a.getWideLiteral());
            if(i instanceof OffsetInstruction a)s.append(" offset").append(a.getCodeOffset());
            if(i instanceof com.android.tools.smali.dexlib2.iface.instruction.formats.ArrayPayload a)s.append(" array").append(a.getElementWidth()).append(a.getArrayElements());
            if(i instanceof SwitchPayload a)for(var e:a.getSwitchElements())s.append(" case").append(e.getKey()).append(':').append(e.getOffset());
        }
        for(var t:impl.getTryBlocks()) {
            s.append(" try").append(t.getStartCodeAddress()).append(':').append(t.getCodeUnitCount());
            for(var h:t.getExceptionHandlers())s.append(':').append(h.getExceptionType()).append(':').append(h.getHandlerCodeAddress());
        }
        return s.toString();
    }
    public static void main(String[] args) throws Exception {
        Path input=Path.of(args[0]), output=Path.of(args[1]);
        var before=hashes(input);var after=hashes(output);
        before.keySet().removeIf(n->n.matches("classes[0-9]*\\.dex"));
        after.keySet().removeIf(n->n.matches("classes[0-9]*\\.dex"));
        check(before.equals(after),"All non-DEX application files retained");
        var originals=methods(input);var patched=methods(output);
        check(originals.keySet().equals(patched.keySet()),"All method identities retained");
        Set<String> changes=Set.of("Lpra;->a()I","Lpqw;->aq()Z","Lpqw;->ae()Z","Lpqw;->ag(IZZ)Z");
        for(String key:originals.keySet())if(!changes.contains(key))
            check(body(originals.get(key)).equals(body(patched.get(key))),"Unchanged method "+key);
        Method resolve=patched.get("Lpra;->a()I");
        Method support=patched.get("Lpqw;->aq()Z");
        Method auto=patched.get("Lpqw;->ae()Z");
        Method setter=patched.get("Lpqw;->ag(IZZ)Z");
        for(int stored=0;stored<=6;stored++)for(int temporary=0;temporary<=6;temporary++) {
            Object[] r={null,"this"};
            int effective=temporary!=0?temporary:stored;
            int value=execute(resolve,r,Map.of("Lpra;->c:I",stored,"Lpra;->f:I",temporary),0,-1);
            check(value==(effective==3?1:effective),"Saved/temporary modes "+stored+"/"+temporary);
            check(value!=3,"Floating never resolves");
        }
        for(int requested:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,5,6,255,Integer.MAX_VALUE}) {
            Object[] r=new Object[15];Arrays.fill(r,"untouched");r[12]=requested;
            int value=execute(setter,r,Map.of(),0,4);
            check(value==(requested==3?1:requested),"Requested mode "+requested);
            for(int i=0;i<r.length;i++)if(i!=0 && i!=12)check("untouched".equals(r[i]),"Other register preserved");
        }
        for(Method m:List.of(support,auto)) {
            Object[] r=new Object[m.getImplementation().getRegisterCount()];r[r.length-1]="this";
            check(execute(m,r,Map.of(),0,-1)==0,"Capability/automatic entry disabled");
        }
        List<ClassProvider> providers=new ArrayList<>();Set<String> classes=new HashSet<>();int methods=0;
        for(String name:hashes(output).keySet())if(name.matches("classes[0-9]*\\.dex")) {
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
        System.out.println("PASS: all resources, native libraries and every other method retained.");
        System.out.println(assertions+" assertions; Android/device runtime testing remains pending.");
    }
}
