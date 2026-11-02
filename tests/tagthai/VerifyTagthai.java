import app.morphe.patcher.*;
import app.morphe.patcher.apk.ApkUtils;
import app.morphe.patcher.dex.*;
import app.morphe.patcher.patch.*;
import com.android.apksig.ApkVerifier;
import com.android.tools.smali.dexlib2.*;
import com.android.tools.smali.dexlib2.dexbacked.*;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.iface.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.iface.reference.*;
import com.reandroid.apk.ApkModule;
import kotlin.Unit;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.flow.FlowCollector;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Real-APK test; inputs and signed outputs remain outside the repository. */
public class VerifyTagthai {
 static final String MAIN="Lcom/thaidigitalplatform/tagthai/MainActivity;";
 static final String GUARD="Ltech/ainu/security/appguard/AinuAppGuard;";
 static final String[] NAMES={"Allow TAGTHAi accessibility apps","Allow TAGTHAi screenshots","Select TAGTHAi text"};
 static final String ACCESS="LQ8/r;->invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;";
 static final String SDK=GUARD+"->onAccessibilityAction(Lkotlin/jvm/functions/Function2;)V";
 static final String UPDATE="Lio/flutter/view/a;->a(Ljava/nio/ByteBuffer;[Ljava/lang/String;[Ljava/nio/ByteBuffer;)V";
 static final String STATE="Lio/flutter/view/b;->onAccessibilityStateChanged(Z)V";
 static final String CTOR="Lio/flutter/view/j;-><init>(Landroid/view/View;LB3/k;Landroid/view/accessibility/AccessibilityManager;Landroid/content/ContentResolver;Lio/flutter/plugin/platform/o;)V";
 static final String PLUGIN="LB9/a;->onMethodCall(LS7/o;LS7/q;)V";
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);}
 static String ref(Instruction i){return i instanceof ReferenceInstruction?((ReferenceInstruction)i).getReference().toString():"";}
 static List<Instruction> instructions(Method m){List<Instruction> r=new ArrayList<>();m.getImplementation().getInstructions().forEach(r::add);return r;}
 static String semantic(Method m){
  StringBuilder b=new StringBuilder();b.append(m.getImplementation().getRegisterCount());
  for(Instruction i:instructions(m)){
   b.append('|').append(i.getOpcode()).append(':').append(ref(i));
   if(i instanceof OneRegisterInstruction)b.append(" A").append(((OneRegisterInstruction)i).getRegisterA());
   if(i instanceof TwoRegisterInstruction)b.append(" B").append(((TwoRegisterInstruction)i).getRegisterB());
   if(i instanceof ThreeRegisterInstruction)b.append(" C").append(((ThreeRegisterInstruction)i).getRegisterC());
   if(i instanceof FiveRegisterInstruction){FiveRegisterInstruction f=(FiveRegisterInstruction)i;b.append(' ').append(f.getRegisterCount()).append('/').append(f.getRegisterC()).append('/').append(f.getRegisterD()).append('/').append(f.getRegisterE()).append('/').append(f.getRegisterF()).append('/').append(f.getRegisterG());}
   if(i instanceof RegisterRangeInstruction){RegisterRangeInstruction r=(RegisterRangeInstruction)i;b.append(' ').append(r.getStartRegister()).append('/').append(r.getRegisterCount());}
   if(i instanceof WideLiteralInstruction)b.append(" lit").append(((WideLiteralInstruction)i).getWideLiteral());
   if(i instanceof OffsetInstruction)b.append(" off").append(((OffsetInstruction)i).getCodeOffset());
  }
  return b.toString();
 }

 static Map<String,Method> read(Path path)throws Exception{
  Map<String,Method> out=new TreeMap<>(); Set<String> classes=new HashSet<>();
  try(ZipFile zip=new ZipFile(path.toFile())){
   for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().matches("classes[0-9]*\\.dex")){
    DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));
    for(ClassDef c:dex.getClasses()){
     if(!classes.add(c.getType()))throw new AssertionError("Duplicate class "+c.getType());
     for(Method m:c.getMethods())if(m.getImplementation()!=null){new MutableMethodImplementation(m.getImplementation());out.put(m.toString(),m);}
    }
   }
  }
  return out;
 }
 static Map<String,String> classLocations(Path path)throws Exception{
  Map<String,String> out=new HashMap<>();
  try(ZipFile zip=new ZipFile(path.toFile())){
   for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().matches("classes[0-9]*\\.dex")){
    DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));
    for(ClassDef c:dex.getClasses())out.put(c.getType(),e.getName());
   }
  }
  return out;
 }
 static Set<Patch<?>> selected(Path mpp,int mask)throws Exception{
  Set<Patch<?>> result=new LinkedHashSet<>();
  for(Patch<?> p:PatchKt.loadPatchesFromJar(Set.of(mpp.toFile())))for(int i=0;i<3;i++)
   if((mask&(1<<i))!=0 && NAMES[i].equals(p.getName()))result.add(p);
  check(result.size()==Integer.bitCount(mask),"MPP exposes the requested independent patches");
  return result;
 }
 static Path apply(Path input,Path mpp,int mask,Path root,boolean reject)throws Exception{
  Files.createDirectories(root);
  Set<Patch<?>> patches=selected(mpp,mask);
  for(Patch<?> patch:patches){
   check(!patch.getDefault(),"Patch is opt-in: "+patch.getName());
   check(patch.getCompatibility().get(0).getTargets().get(0).isExperimental(),"Target is experimental");
  }
  PatcherConfig config=new PatcherConfig(input.toFile(),root.resolve("temp").toFile(),Set.of(),BytecodeMode.STRIP_FAST,NoOpDexVerifier.INSTANCE,root.resolve("workspace").toFile(),"2.1.0",true);
  Path output=root.resolve("tagthai-unsigned.apk");
  try(Patcher p=new Patcher(config)){
   p.plusAssign(patches);List<PatchResult> events=new ArrayList<>();
   FlowCollector<PatchResult> collector=(event,c)->{events.add(event);return Unit.INSTANCE;};
   BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,(scope,c)->p.invoke().collect(collector,c));
   check(events.size()==patches.size(),"Every selected patch produced a result");
   if(reject){check(events.stream().allMatch(e->e.getException()!=null),"Modified or wrong-version input rejected");return null;}
   for(PatchResult e:events)if(e.getException()!=null)throw new IllegalStateException("Patch failed",e.getException());
   PatcherResult result=p.get();check(!result.getDexFiles().isEmpty(),"Morphe generated patched DEX");
   Files.copy(input,output,StandardCopyOption.REPLACE_EXISTING);ApkUtils.INSTANCE.applyTo(result,output.toFile());
  }
  return output;
 }
 static String hash(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 public static void main(String[] args)throws Exception{
  Path input=Path.of(args[0]),mpp=Path.of(args[1]),root=Path.of(args[2]);int mask=Integer.parseInt(args[3]);
  Map<String,Method> before=read(input);
  Path output=apply(input,mpp,mask,root,false);
  Map<String,Method> after=read(output);
  check(after.keySet().containsAll(before.keySet()),"All original methods retained");
  Set<String> changed=new TreeSet<>();
  for(String key:before.keySet())if(!hash(semantic(before.get(key))).equals(hash(semantic(after.get(key)))))changed.add(key);
  Set<String> expected=new TreeSet<>();
  if((mask&1)!=0)expected.add(ACCESS);
  if((mask&2)!=0)expected.addAll(Set.of(MAIN+"->k()V",MAIN+"->onWindowFocusChanged(Z)V",PLUGIN));
  if((mask&4)!=0)expected.addAll(Set.of(UPDATE,CTOR));
  check(changed.equals(expected),"Exactly the selected methods changed: "+changed);
  long added=after.keySet().stream().filter(k->!before.containsKey(k)).count();
  check(((mask&4)!=0)==(added>0),"Text extension is present only when selected");
  for(String key:after.keySet())if(!before.containsKey(key))check(key.startsWith("Lca/mlkem/tagthai/"),"Only the text extension adds methods");
  check(semantic(before.get(SDK)).equals(semantic(after.get(SDK))),"Original AppGuard call and callback timing are preserved");
  check(semantic(before.get(STATE)).equals(semantic(after.get(STATE))),"Original Flutter state listener is unchanged; no forced startup semantics");
  Map<String,String> oldLocations=classLocations(input),newLocations=classLocations(output);
  for(String type:Set.of(GUARD,"Ltech/ainu/security/appguard/enum/AccessibilityResult;","Lio/flutter/view/b;"))
   check(Objects.equals(oldLocations.get(type),newLocations.get(type)),"Read-only validation did not relocate original class: "+type);
  if((mask&1)!=0){
   List<Instruction> code=instructions(after.get(ACCESS));
   check(code.get(2).getOpcode()==Opcode.PACKED_SWITCH && code.get(3).getOpcode()==Opcode.CHECK_CAST && code.get(4).getOpcode()==Opcode.CHECK_CAST,"Shared callback dispatch precedes accessibility edit");
   check(ref(code.get(5)).contains("->DISABLE:") && ((OneRegisterInstruction)code.get(5)).getRegisterA()==9,"Only accessibility result argument is normalized");
   check(ref(code.get(6)).equals("Ljava/util/ArrayList;") && ((OneRegisterInstruction)code.get(6)).getRegisterA()==10,"Accessibility app list receives a new empty array list");
   check(code.get(8).getOpcode()==Opcode.SGET,"Original callback body follows injected arguments");
   // Resolve the packed-switch payload: its file-error branch must bypass the edit.
   int address=0, switchAddress=0; Map<Integer,Instruction> byAddress=new HashMap<>();
   for(Instruction i:code){byAddress.put(address,i);if(i==code.get(2))switchAddress=address;address+=i.getCodeUnits();}
   SwitchPayload payload=(SwitchPayload)byAddress.get(switchAddress+((OffsetInstruction)code.get(2)).getCodeOffset());
   for(SwitchElement e:payload.getSwitchElements())if(e.getKey()==0){
    Instruction branch=byAddress.get(switchAddress+e.getOffset());
    check(branch.getOpcode()==Opcode.MOVE_OBJECT && ((TwoRegisterInstruction)branch).getRegisterA()==1 && ((TwoRegisterInstruction)branch).getRegisterB()==9,"File-error callback branch bypasses accessibility normalization");
   }
  }
  if((mask&2)!=0)for(String key:Set.of(MAIN+"->k()V",MAIN+"->onWindowFocusChanged(Z)V",PLUGIN)){
   check(instructions(after.get(key)).stream().noneMatch(i->ref(i).equals("Landroid/view/Window;->setFlags(II)V")||ref(i).equals("Landroid/view/Window;->addFlags(I)V")),"Secure-window activation removed at "+key);
  }
  if((mask&4)!=0){
   List<Instruction> c=instructions(after.get(CTOR));
   check(ref(c.get(c.size()-2)).equals("Lca/mlkem/tagthai/TextSelection;->attach(Landroid/view/View;Landroid/view/accessibility/AccessibilityNodeProvider;Ljava/util/Map;Landroid/view/accessibility/AccessibilityManager$AccessibilityStateChangeListener;)V"),"Passive text hook attaches after bridge initialization");
   FiveRegisterInstruction call=(FiveRegisterInstruction)c.get(c.size()-2);
   check(call.getRegisterCount()==4 && call.getRegisterC()==0 && call.getRegisterD()==6 && call.getRegisterE()==1 && call.getRegisterF()==2,"Bridge hook receives View, provider, map and original listener");
   List<Instruction> u=instructions(after.get(UPDATE));
   check(ref(u.get(u.size()-2)).equals("Lca/mlkem/tagthai/TextSelection;->onSemanticsUpdated(Landroid/view/accessibility/AccessibilityNodeProvider;)V"),"Completed semantics frame notifies on-demand capture");
   check(u.get(u.size()-5).getOpcode()==Opcode.MOVE_OBJECT_FROM16 && ((TwoRegisterInstruction)u.get(u.size()-5)).getRegisterB()==18,"High-register delegate receiver moved safely");
  }
  try(ApkModule apk=ApkModule.loadApkFile(output.toFile())){check(apk.getVersionCode()==4251001 && apk.getPackageName().equals("com.thaidigitalplatform.tagthai"),"Package and version code preserved");}
  int resources=0;
  try(ZipFile a=new ZipFile(input.toFile());ZipFile b=new ZipFile(output.toFile())){
   for(ZipEntry e:Collections.list(a.entries())){String n=e.getName();if(e.isDirectory()||n.matches("classes[0-9]*\\.dex")||n.startsWith("META-INF/"))continue;
    if(b.getEntry(n)==null || !Arrays.equals(a.getInputStream(e).readAllBytes(),b.getInputStream(b.getEntry(n)).readAllBytes()))throw new AssertionError("Unexpected resource/native change: "+n);resources++;
   }
  }
  check(true,"All "+resources+" resource/native files preserved");
  if(mask==7){
   Path signed=root.resolve("tagthai-signature-test.apk");
   ApkUtils.INSTANCE.signApk(output.toFile(),signed.toFile(),"TAGTHAi verification",new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(),"verification-only","fixture","verification-only"),28);
   check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(),"Patched APK passes Android signature verification");
  }
  apply(output,mpp,mask,root.resolve("reapply"),true);
  if(mask==7){
   Path wrongVersion=root.resolve("wrong-version.apk");
   try(ApkModule apk=ApkModule.loadApkFile(input.toFile())){
    apk.getAndroidManifest().getManifestElement().searchAttributeByResourceId(0x0101021c).setValueAsString("4.25.2");
    apk.setApkSignatureBlock(null);
    try(com.reandroid.archive.writer.ApkFileWriter writer=apk.createApkFileWriter(wrongVersion.toFile())){writer.write();}
   }
   apply(wrongVersion,mpp,mask,root.resolve("wrong-version-check"),true);
  }
  if(args.length>4)apply(Path.of(args[4]),mpp,mask,root.resolve("wrong-package"),true);
  if(args.length>5)apply(Path.of(args[5]),mpp,mask,root.resolve("legacy-dev7"),true);
  Files.writeString(root.resolve("verification-result.json"),"{\"result\":\"passed\",\"mask\":"+mask+",\"changed_methods\":"+changed.size()+",\"original_methods\":"+before.size()+",\"added_methods\":"+added+",\"resources_preserved\":"+resources+",\"phone_tested\":false}\n");
 }
}
