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
                case CONST_WIDE_16 -> r[((OneRegisterInstruction)i).getRegisterA()]=((WideLiteralInstruction)i).getWideLiteral();
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
                case IF_EQ -> {
                    var two=(TwoRegisterInstruction)i;
                    if (Objects.equals(r[two.getRegisterA()],r[two.getRegisterB()])) next=pc+((OffsetInstruction)i).getCodeOffset();
                }
                case IF_NE -> {
                    var two=(TwoRegisterInstruction)i;
                    if (!Objects.equals(r[two.getRegisterA()],r[two.getRegisterB()])) next=pc+((OffsetInstruction)i).getCodeOffset();
                }
                case RETURN -> { return (Integer)r[((OneRegisterInstruction)i).getRegisterA()]; }
                case RETURN_WIDE -> {
                    long value=(Long)r[((OneRegisterInstruction)i).getRegisterA()];
                    check(value==0L,"Shortcut provider returns the empty 64-bit state");
                    return Math.toIntExact(value);
                }
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
    record Action(String type, List<Integer> entries) {}
    record Key(List<Action> actions) {}
    static boolean zero(Object value) { return value==null || Integer.valueOf(0).equals(value); }
    /** Executes the actual action-lookup DEX against merged/cached menu fixtures. */
    static Action lookup(Method m, Key key, String type) {
        Object[] r=new Object[m.getImplementation().getRegisterCount()];
        r[r.length-2]=key;r[r.length-1]=type;
        var code=code(m);int pc=0;Object result=null;
        for(int step=0;step<1000;step++) {
            Instruction i=code.get(pc);check(i!=null,"Popup instruction boundary "+m+" pc="+pc+" key="+key+" type="+type);
            int next=pc+i.getCodeUnits();
            int a=i instanceof OneRegisterInstruction x?x.getRegisterA():-1;
            int b=i instanceof TwoRegisterInstruction x?x.getRegisterB():-1;
            switch(i.getOpcode()) {
                case CONST_4, CONST_16 -> r[a]=((NarrowLiteralInstruction)i).getNarrowLiteral();
                case MOVE_OBJECT -> r[a]=r[b];
                case CHECK_CAST, NOP -> {}
                case IGET_OBJECT -> {
                    String field=((ReferenceInstruction)i).getReference().toString();
                    if(field.equals("Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->h:Lwns;"))r[a]=((Key)r[b]).actions();
                    else if(field.equals("Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->e:Lqae;"))r[a]=((Action)r[b]).type();
                    else if(field.equals("Lcom/google/android/libraries/inputmethod/metadata/ActionDef;->f:Lwns;"))r[a]=((Action)r[b]).entries();
                    else throw new AssertionError("Unexpected popup field "+field);
                }
                case IGET -> {
                    check(((ReferenceInstruction)i).getReference().toString().equals("Lqbc;->c:I"),"Reads popup keycode");
                    r[a]=(Integer)r[b];
                }
                case SGET_OBJECT -> {
                    String field=((ReferenceInstruction)i).getReference().toString();
                    check(field.equals("Lqae;->a:Lqae;")||field.equals("Lqae;->b:Lqae;"),"Known action type");
                    r[a]=field.contains("->a:")?"PRESS":"LONG_PRESS";
                }
                case INVOKE_INTERFACE -> {
                    String call=((ReferenceInstruction)i).getReference().toString();
                    Object receiver=r[((FiveRegisterInstruction)i).getRegisterC()];
                    switch(call) {
                        case "Ljava/lang/Iterable;->iterator()Ljava/util/Iterator;" -> result=((Iterable<?>)receiver).iterator();
                        case "Ljava/util/Iterator;->hasNext()Z" -> result=((Iterator<?>)receiver).hasNext()?1:0;
                        case "Ljava/util/Iterator;->next()Ljava/lang/Object;" -> result=((Iterator<?>)receiver).next();
                        default -> throw new AssertionError("Unexpected popup call "+call);
                    }
                }
                case MOVE_RESULT, MOVE_RESULT_OBJECT -> r[a]=result;
                case IF_EQZ -> {if(zero(r[a]))next=pc+((OffsetInstruction)i).getCodeOffset();}
                case IF_NEZ -> {if(!zero(r[a]))next=pc+((OffsetInstruction)i).getCodeOffset();}
                case IF_NE -> {if(!Objects.equals(r[a],r[b]))next=pc+((OffsetInstruction)i).getCodeOffset();}
                case GOTO, GOTO_16, GOTO_32 -> next=pc+((OffsetInstruction)i).getCodeOffset();
                case RETURN_OBJECT -> {return zero(r[a])?null:(Action)r[a];}
                default -> throw new AssertionError("Unexpected popup instruction "+i.getOpcode());
            }
            pc=next;
        }
        throw new AssertionError("Popup lookup did not terminate");
    }
    public static void main(String[] args) throws Exception {
        Path input=Path.of(args[0]), output=Path.of(args[1]);
        var before=hashes(input);var after=hashes(output);
        before.keySet().removeIf(n->n.matches("classes[0-9]*\\.dex"));
        after.keySet().removeIf(n->n.matches("classes[0-9]*\\.dex"));
        check(before.equals(after),"All non-DEX application files retained");
        var originals=methods(input);var patched=methods(output);
        check(originals.keySet().equals(patched.keySet()),"All method identities retained");
        Set<String> changes=Set.of("Lpra;->a()I","Lpqw;->aq()Z","Lpqw;->ae()Z","Lpqw;->ag(IZZ)Z","Lpqw;->at(Z)Z",
            "Lpqw;->fV(Lpev;Landroid/view/inputmethod/EditorInfo;)J",
            "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->g(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;",
            "Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->h(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;");
        for(String key:originals.keySet())if(!changes.contains(key))
            check(body(originals.get(key)).equals(body(patched.get(key))),"Unchanged method "+key);
        Method resolve=patched.get("Lpra;->a()I");
        Method support=patched.get("Lpqw;->aq()Z");
        Method auto=patched.get("Lpqw;->ae()Z");
        Method setter=patched.get("Lpqw;->ag(IZZ)Z");
        Method oneHanded=patched.get("Lpqw;->at(Z)Z");
        Method shortcutState=patched.get("Lpqw;->fV(Lpev;Landroid/view/inputmethod/EditorInfo;)J");
        for(int stored=0;stored<=6;stored++)for(int temporary=0;temporary<=6;temporary++) {
            Object[] r={null,"this"};
            int effective=temporary!=0?temporary:stored;
            int value=execute(resolve,r,Map.of("Lpra;->c:I",stored,"Lpra;->f:I",temporary),0,-1);
            check(value==((effective==2 || effective==3)?1:effective),"Saved/temporary modes "+stored+"/"+temporary);
            check(value!=2 && value!=3,"One-handed and floating never resolve");
        }
        for(int requested:new int[]{Integer.MIN_VALUE,-1,0,1,2,3,4,5,6,255,Integer.MAX_VALUE}) {
            Object[] r=new Object[15];Arrays.fill(r,"untouched");r[12]=requested;
            int value=execute(setter,r,Map.of(),0,7);
            check(value==((requested==2 || requested==3)?1:requested),"Requested mode "+requested);
            for(int i=0;i<r.length;i++)if(i!=0 && i!=12)check("untouched".equals(r[i]),"Other register preserved");
        }
        for(Method m:List.of(support,auto)) {
            Object[] r=new Object[m.getImplementation().getRegisterCount()];r[r.length-1]="this";
            check(execute(m,r,Map.of(),0,-1)==0,"Capability/automatic entry disabled");
        }
        for(int enabled=0;enabled<=1;enabled++) {
            Object[] r={null,"this",enabled};
            check(execute(oneHanded,r,Map.of(),0,-1)==0,"One-handed eligibility disabled for "+enabled);
        }
        for(Object keyboard:new Object[]{null,"keyboard"})for(Object editor:new Object[]{null,"editor"}) {
            check(execute(shortcutState,new Object[]{"this",keyboard,editor},Map.of(),0,-1)==0,
                "One-handed shortcut hidden for every keyboard/editor combination");
        }
        List<Method> popupMethods=new ArrayList<>();int popupCases=0;
        for(String name:List.of("g","h")) {
            String signature="Lcom/google/android/libraries/inputmethod/metadata/SoftKeyDef;->"+name+"(Lqae;)Lcom/google/android/libraries/inputmethod/metadata/ActionDef;";
            Method original=originals.get(signature), filtered=patched.get(signature);popupMethods.add(filtered);
            for(List<Integer> entries:List.of(List.<Integer>of(),List.of(-10057),List.of(-10057,-10018),
                    List.of(-10018,-10057),List.of(66,-10057,-10018),List.of(-10057,-10057),
                    List.of(66),List.of(-10018),List.of(97,98,99))) {
                for(boolean hasPress:new boolean[]{false,true}) {
                    Action press=new Action("PRESS",List.of(66));Action longPress=new Action("LONG_PRESS",entries);
                    var actions=new ArrayList<Action>();if(hasPress)actions.add(press);actions.add(longPress);
                    Key key=new Key(actions);
                    for(String type:new String[]{null,"PRESS","LONG_PRESS","SLIDE_UP"}) {
                        Action baselineAction=lookup(original,key,type);
                        Action expected=baselineAction!=null && baselineAction.type().equals("LONG_PRESS") && baselineAction.entries().contains(-10057)?null:baselineAction;
                        Action actual=lookup(filtered,key,type);
                        check(actual==expected,"Merged popup filtering: "+name+" entries="+entries+" press="+hasPress+" query="+type+" expected="+expected+" actual="+actual);
                        if(type!=null && type.equals("LONG_PRESS"))check(baselineAction==longPress,"Baseline exposes the reported popup");
                        popupCases++;
                    }
                }
            }
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
        for(Method m:List.of(resolve,support,auto,setter,oneHanded,shortcutState)) {
            var analysis=new MethodAnalyzer(classpath,m,null,false);
            check(analysis.getAnalysisException()==null,"Register/type analysis: "+m+" "+analysis.getAnalysisException());
        }
        for(Method m:popupMethods) {
            var analysis=new MethodAnalyzer(classpath,m,null,false);
            check(analysis.getAnalysisException()==null,"Popup register/type analysis: "+m+" "+analysis.getAnalysisException());
        }
        check(methods>10000,"All output methods decoded");
        System.out.println("PASS: 49 restored/temporary combinations, 11 requested modes, floating gates and both one-handed eligibility inputs.");
        System.out.println("PASS: long-press shortcut state is empty for all four keyboard/editor combinations.");
        System.out.println("PASS: "+popupCases+" merged popup cases: one-handed popups removed; normal taps and other actions preserved.");
        System.out.println("PASS: 8 changed methods pass register/type analysis; "+methods+" methods decoded.");
        System.out.println("PASS: all resources, native libraries and every other method retained.");
        System.out.println(assertions+" assertions; Android/device runtime testing remains pending.");
    }
}
