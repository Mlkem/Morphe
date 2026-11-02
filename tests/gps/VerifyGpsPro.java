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
import java.util.*;
import java.util.zip.*;

public class VerifyGpsPro {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("PASS "+text);}
 static String ref(Instruction i){return i instanceof ReferenceInstruction?((ReferenceInstruction)i).getReference().toString():"";}
 static List<Instruction> instructions(Method m){List<Instruction> r=new ArrayList<>();m.getImplementation().getInstructions().forEach(r::add);return r;}
 static Map<String,Method> appMethods(Path apk,boolean validate)throws Exception{
  Map<String,Method> result=new TreeMap<>();int methods=0,classes=0,dexes=0;
  try(ZipFile zip=new ZipFile(apk.toFile())){
   for(ZipEntry e:Collections.list(zip.entries()))if(e.getName().matches("classes[0-9]*\\.dex")){
    dexes++;
    DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(e)));
    for(ClassDef c:dex.getClasses()){
     classes++;
     for(Method m:c.getMethods())if(m.getImplementation()!=null){
      if(validate)new MutableMethodImplementation(m.getImplementation());
      methods++;
      if(c.getType().startsWith("Lcom/rosteam/gpsemulator/"))result.put(m.toString(),m);
     }
    }
   }
  }
  check(dexes==(validate?10:9),"Expected DEX count (Morphe adds one DEX for changed classes): "+dexes);
  System.out.println("Scanned "+classes+" classes and "+methods+" methods; validate="+validate);
  return result;
 }
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
 static void simulateStartup(Method m){
  Map<String,Object> prefs=new HashMap<>();prefs.put("noads",false);prefs.put("numerofavoritos",10);
  prefs.put("randomize",true);prefs.put("decimal_places","4");
  Object[] r=new Object[9];r[0]=prefs;Object pending=null;boolean active=false,read=false;
  for(Instruction i:instructions(m)){
   String ref=ref(i);
   if(!active){if(i.getOpcode()==Opcode.IPUT_OBJECT && ref.endsWith("->preferences:Landroid/content/SharedPreferences;"))active=true;continue;}
   if(i.getOpcode()==Opcode.CONST_STRING){r[((OneRegisterInstruction)i).getRegisterA()]=((StringReference)((ReferenceInstruction)i).getReference()).getString();}
   else if(i.getOpcode()==Opcode.CONST_4 || i.getOpcode()==Opcode.CONST_16){r[((OneRegisterInstruction)i).getRegisterA()]=((NarrowLiteralInstruction)i).getNarrowLiteral();}
   else if(i.getOpcode()==Opcode.MOVE_RESULT_OBJECT || i.getOpcode()==Opcode.MOVE_RESULT){r[((OneRegisterInstruction)i).getRegisterA()]=pending;if(read){check(Boolean.TRUE.equals(pending),"First original Pro read sees enabled state");break;}}
   else if(i.getOpcode()==Opcode.INVOKE_INTERFACE){
    FiveRegisterInstruction f=(FiveRegisterInstruction)i;
    check(r[f.getRegisterC()]==prefs,"Preference receiver preserved for "+ref);
    if(ref.contains("->edit()"))pending=prefs;
    else if(ref.contains("->putBoolean(")){prefs.put((String)r[f.getRegisterD()],((Integer)r[f.getRegisterE()])!=0);pending=prefs;}
    else if(ref.contains("->putInt(")){prefs.put((String)r[f.getRegisterD()],r[f.getRegisterE()]);pending=prefs;}
    else if(ref.contains("->commit()"))pending=true;
    else if(ref.contains("->getBoolean(")){pending=prefs.get((String)r[f.getRegisterD()]);read=true;}
    else throw new AssertionError("Unexpected startup invoke "+ref);
   }else throw new AssertionError("Unexpected startup opcode "+i.getOpcode());
  }
  check(read,"Startup simulation reached original Pro read");
  check(Boolean.TRUE.equals(prefs.get("noads")) && Integer.valueOf(1000).equals(prefs.get("numerofavoritos")),"Fresh install receives Pro and 1,000 favourites");
  check(Boolean.TRUE.equals(prefs.get("randomize")) && "4".equals(prefs.get("decimal_places")),"Existing user choices preserved");
 }
 static Path apply(Path input,Patch<?> patch,Path root,boolean expectFailure)throws Exception{
  Files.createDirectories(root);
  PatcherConfig config=new PatcherConfig(input.toFile(),root.resolve("temp").toFile(),Set.of(),BytecodeMode.STRIP_FAST,NoOpDexVerifier.INSTANCE,root.resolve("workspace").toFile(),"2.1.0",true);
  Path output=root.resolve("gps-pro-unsigned.apk");
  try(Patcher p=new Patcher(config)){
   p.plusAssign(Set.of(patch));List<PatchResult> events=new ArrayList<>();
   FlowCollector<PatchResult> collector=(event,c)->{events.add(event);return Unit.INSTANCE;};
   BuildersKt.<Unit>runBlocking(EmptyCoroutineContext.INSTANCE,(scope,c)->p.invoke().collect(collector,c));
   check(events.size()==1,"One patch execution result");
   if(expectFailure){check(events.get(0).getException()!=null,"Reapplying to a modified APK is rejected");return null;}
   if(events.get(0).getException()!=null)throw new IllegalStateException("Patch failed",events.get(0).getException());
   PatcherResult result=p.get();check(!result.getDexFiles().isEmpty(),"Morphe generated patched DEX");
   Files.copy(input,output,StandardCopyOption.REPLACE_EXISTING);ApkUtils.INSTANCE.applyTo(result,output.toFile());
  }
  return output;
 }
 public static void main(String[] args)throws Exception{
  Path input=Path.of(args[0]),mpp=Path.of(args[1]),root=Path.of(args[2]);
  PatchLoader.Jar loader=PatchKt.loadPatchesFromJar(Set.of(mpp.toFile()));
  check(!loader.isEmpty(),"Morphe loads the patch bundle");Patch<?> patch=loader.stream().filter(p -> "Enable GPS Emulator Pro".equals(p.getName())).findFirst().orElseThrow(() -> new AssertionError("GPS Emulator patch missing"));
  check(patch instanceof BytecodePatch && !patch.getDefault(),"Pro patch is opt-in");
  check(patch.getCompatibility().get(0).getPackageName().equals("com.rosteam.gpsemulator") && patch.getCompatibility().get(0).getTargets().get(0).getVersion().equals("3.36"),"Compatibility restricted to GPS Emulator 3.36");
  try(ZipFile zip=new ZipFile(mpp.toFile())){
   DexBackedDexFile dex=DexBackedDexFile.fromInputStream(Opcodes.getDefault(),new BufferedInputStream(zip.getInputStream(zip.getEntry("classes.dex"))));
   for(ClassDef c:dex.getClasses())for(Method m:c.getMethods())if(m.getImplementation()!=null)new MutableMethodImplementation(m.getImplementation());
   check(true,"MPP Android DEX is readable");
  }
  Path output=apply(input,patch,root,false);
  Map<String,Method> before=appMethods(input,false),after=appMethods(output,true);
  check(before.keySet().equals(after.keySet()),"All original app methods retained");
  Set<String> changed=new TreeSet<>();for(String key:before.keySet())if(!semantic(before.get(key)).equals(semantic(after.get(key))))changed.add(key);
  Set<String> expected=Set.of("Lcom/rosteam/gpsemulator/App;->onCreate()V","Lcom/rosteam/gpsemulator/MainActivity;->deshabilitarPRO()V","Lcom/rosteam/gpsemulator/SettingsFragment;->deshabilitarPRO()V");
  check(changed.equals(expected),"Only the three intended app methods changed: "+changed);
  simulateStartup(after.get("Lcom/rosteam/gpsemulator/App;->onCreate()V"));
  for(String key:changed)if(key.contains("deshabilitarPRO"))check(instructions(after.get(key)).get(0).getOpcode()==Opcode.RETURN_VOID,"Billing downgrade returns without clearing settings: "+key);
  try(ApkModule apk=ApkModule.loadApkFile(output.toFile())){check(apk.getVersionCode()==336 && apk.getPackageName().equals("com.rosteam.gpsemulator"),"Original package and version code preserved");}
  try(ZipFile a=new ZipFile(input.toFile());ZipFile b=new ZipFile(output.toFile())){
   int retained=0;
   for(ZipEntry e:Collections.list(a.entries())){String n=e.getName();if(e.isDirectory()||n.matches("classes[0-9]*\\.dex")||n.startsWith("META-INF/"))continue;
    if(b.getEntry(n)==null || !Arrays.equals(a.getInputStream(e).readAllBytes(),b.getInputStream(b.getEntry(n)).readAllBytes()))throw new AssertionError("Unexpected resource change: "+n);retained++;
   }check(true,"Resources/native libraries unchanged: "+retained+" entries");
  }
  Path signed=root.resolve("gps-pro-signature-test.apk");
  ApkUtils.INSTANCE.signApk(output.toFile(),signed.toFile(),"GPS Pro verification",new ApkUtils.KeyStoreDetails(root.resolve("verification.keystore").toFile(),"verification-only","fixture","verification-only"),29);
  check(new ApkVerifier.Builder(signed.toFile()).build().verify().isVerified(),"Patched APK signs and passes Android signature verification");
  Patch<?> fresh=PatchKt.loadPatchesFromJar(Set.of(mpp.toFile())).stream().filter(p -> "Enable GPS Emulator Pro".equals(p.getName())).findFirst().orElseThrow();
  apply(output,fresh,root.resolve("reapply"),true);
  Files.writeString(root.resolve("verification-result.json"), "{\"result\":\"passed\",\"scope\":\"desktop\",\"changed_methods\":3,\"phone_tested\":false}\n");
  System.out.println("SUCCESS. Desktop verification passed. Android import/install/launch/feature testing remains pending.");
 }
}
