package ca.mlkem.gps;

import app.morphe.patcher.patch.*;
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.BuilderInstruction;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.*;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import com.android.tools.smali.dexlib2.immutable.reference.*;
import java.util.*;
import kotlin.Unit;

/** Fresh patch for the original 3.36/336 release; no APK code is bundled. */
public final class GpsEmulatorProPatch {
    private static final String APP = "Lcom/rosteam/gpsemulator/App;";
    private static final String MAIN = "Lcom/rosteam/gpsemulator/MainActivity;";
    private static final String SETTINGS = "Lcom/rosteam/gpsemulator/SettingsFragment;";
    private static final String PREFS = "Landroid/content/SharedPreferences;";
    private static final String EDITOR = "Landroid/content/SharedPreferences$Editor;";
    private GpsEmulatorProPatch() {}

    public static final BytecodePatch ENABLE_PRO = PatchKt.bytecodePatch(
        "Enable GPS Emulator Pro",
        "GPS Emulator 3.36 (336): enables the app's shared Pro setting and 1,000 " +
        "favourites at startup, and prevents billing callbacks from resetting Pro " +
        "settings. Built against the original APKMirror APKM. Phone testing pending.",
        false,
        builder -> {
            builder.compatibleWith(new Compatibility(
                "com.rosteam.gpsemulator", "GPS Emulator", "Enable Pro on original 3.36",
                ApkFileType.APKM, (Integer)null, null,
                Collections.singletonList(new AppTarget("3.36", Collections.emptyMap(),
                    true, 29, "Version code 336; phone testing pending")), FormFactor.MOBILE));
            builder.execute(context -> {
                if (!"com.rosteam.gpsemulator".equals(context.getPackageMetadata().getPackageName()) ||
                    !"3.36".equals(context.getPackageMetadata().getVersionName()) ||
                    !"336".equals(context.getPackageMetadata().getVersionCode()))
                    throw new IllegalArgumentException("Requires original GPS Emulator 3.36 (336).");

                MutableMethod startup = method(context, APP, "onCreate");
                MutableMethod mainReset = method(context, MAIN, "deshabilitarPRO");
                MutableMethod settingsReset = method(context, SETTINGS, "deshabilitarPRO");
                MutableMethodImplementation impl = startup.getImplementation();
                List<? extends Instruction> ins = impl.getInstructions();
                int insertion = -1;
                for (int i=0; i<ins.size(); i++) {
                    Instruction item = ins.get(i);
                    if (item.getOpcode()==Opcode.IPUT_OBJECT && ref(item).equals(APP+"->preferences:"+PREFS)) {
                        TwoRegisterInstruction put = (TwoRegisterInstruction)item;
                        if (insertion!=-1 || put.getRegisterA()!=0 || put.getRegisterB()!=8)
                            throw new IllegalArgumentException("Unexpected Application preference initialization");
                        insertion=i+1;
                    }
                }
                if (insertion<0 || impl.getRegisterCount()!=9)
                    throw new IllegalArgumentException("Application startup fingerprint did not match");
                // Fail before making any edits if targets are different from the original.
                if (!ref(ins.get(insertion)).equals("noads") ||
                    ins.get(insertion).getOpcode()!=Opcode.CONST_STRING ||
                    ((OneRegisterInstruction)ins.get(insertion)).getRegisterA()!=1)
                    throw new IllegalArgumentException("Already patched or unsupported startup layout; use the original APKM");
                for (MutableMethod reset: Arrays.asList(mainReset, settingsReset)) {
                    Set<String> strings = new HashSet<>();
                    for (Instruction item:reset.getImplementation().getInstructions()) strings.add(ref(item));
                    if (!strings.containsAll(Arrays.asList("noads", "launchonstop", "startlastlocation",
                            "hidenotif", "randomize", "numerofavoritos", "decimal_places")))
                        throw new IllegalArgumentException("Pro reset fingerprint did not match: "+reset);
                }

                // v0 remains the original SharedPreferences. v3-v5 are not yet live.
                // Commit before the original first Pro read and before any ad SDK setup.
                List<BuilderInstruction> added = Arrays.asList(
                    invoke(PREFS, "edit", Collections.emptyList(), EDITOR, 1, 0, 0, 0),
                    new BuilderInstruction11x(Opcode.MOVE_RESULT_OBJECT, 3),
                    new BuilderInstruction21c(Opcode.CONST_STRING, 4, new ImmutableStringReference("noads")),
                    new BuilderInstruction11n(Opcode.CONST_4, 5, 1),
                    invoke(EDITOR, "putBoolean", Arrays.asList("Ljava/lang/String;", "Z"), EDITOR, 3, 3, 4, 5),
                    new BuilderInstruction21c(Opcode.CONST_STRING, 4, new ImmutableStringReference("numerofavoritos")),
                    new BuilderInstruction21s(Opcode.CONST_16, 5, 1000),
                    invoke(EDITOR, "putInt", Arrays.asList("Ljava/lang/String;", "I"), EDITOR, 3, 3, 4, 5),
                    invoke(EDITOR, "commit", Collections.emptyList(), "Z", 1, 3, 0, 0)
                );
                for (int i=0; i<added.size(); i++) impl.addInstruction(insertion+i, added.get(i));
                // Early return retains the original method bodies/debug tables, but bypasses
                // their reset side effects, including the user's selected Pro settings.
                mainReset.getImplementation().addInstruction(0, new BuilderInstruction10x(Opcode.RETURN_VOID));
                settingsReset.getImplementation().addInstruction(0, new BuilderInstruction10x(Opcode.RETURN_VOID));
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });

    private static BuilderInstruction invoke(String owner,String name,List<String> params,
            String result,int count,int c,int d,int e) {
        return new BuilderInstruction35c(Opcode.INVOKE_INTERFACE,count,c,d,e,0,0,
            new ImmutableMethodReference(owner,name,params,result));
    }
    private static String ref(Instruction ins) {
        return ins instanceof ReferenceInstruction ? ((ReferenceInstruction)ins).getReference().toString() : "";
    }
    private static MutableMethod method(BytecodePatchContext context,String type,String name) {
        List<MutableMethod> matches=new ArrayList<>();
        for(MutableMethod m:context.mutableClassDefBy(type).getMethods())
            if(m.getName().equals(name) && m.getReturnType().equals("V") &&
                m.getParameterTypes().isEmpty() && m.getImplementation()!=null) matches.add(m);
        if(matches.size()!=1) throw new IllegalArgumentException("Expected one method: "+type+"->"+name);
        return matches.get(0);
    }
}
