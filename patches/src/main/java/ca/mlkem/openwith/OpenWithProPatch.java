package ca.mlkem.openwith;

import app.morphe.patcher.patch.*;
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod;
import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.builder.MutableMethodImplementation;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10x;
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction11n;
import com.android.tools.smali.dexlib2.iface.instruction.*;
import java.util.*;
import kotlin.Unit;

/** Reproduces the three tested edits without bundling or replacing the target APK. */
public final class OpenWithProPatch {
    private OpenWithProPatch() {}
    private static final String LICENSE_CALL =
        "Lcom/pairip/licensecheck/LicenseClient;->checkLicense(Landroid/content/Context;)V";

    public static final BytecodePatch ENABLE_PRO = PatchKt.bytecodePatch(
        "Enable Pro file-type handling",
        "Open with 1.2.1 (11) local test: enables the shared Pro flag used by " +
        "file-type settings and routing, and removes the two Play installation " +
        "licence startup calls for a re-signed build. Phone testing pending.",
        false,
        builder -> {
            builder.compatibleWith(new Compatibility(
                "com.openwith.app", "Open with", "Pro file-type handling test",
                ApkFileType.APK, (Integer)null, null,
                Collections.singletonList(new AppTarget("1.2.1", Collections.emptyMap(),
                    true, 32, "Version code 11; phone testing pending")), FormFactor.MOBILE));
            builder.execute(context -> {
                if (!"com.openwith.app".equals(context.getPackageMetadata().getPackageName()) ||
                    !"1.2.1".equals(context.getPackageMetadata().getVersionName()) ||
                    !"11".equals(context.getPackageMetadata().getVersionCode())) {
                    throw new IllegalArgumentException("Requires Open with 1.2.1, version code 11.");
                }

                MutableMethod pro = method(context, "Lel0;", "c", "Z", "Landroid/content/Context;");
                MutableMethod app = method(context, "Lcom/pairip/application/Application;",
                    "attachBaseContext", "V", "Landroid/content/Context;");
                MutableMethod provider = method(context, "Lcom/pairip/licensecheck/LicenseContentProvider;",
                    "onCreate", "Z");

                // Check all three targets before changing any instructions.
                MutableMethodImplementation impl = pro.getImplementation();
                List<? extends Instruction> ins = impl.getInstructions();
                boolean keyFound = false;
                int resultIndex = -1;
                for (int i = 0; i < ins.size(); i++) {
                    Instruction item = ins.get(i);
                    if (item instanceof ReferenceInstruction) {
                        String reference = ((ReferenceInstruction)item).getReference().toString();
                        if (reference.equals("pro_purchased")) keyFound = true;
                        if (reference.equals("Landroid/content/SharedPreferences;->getBoolean(Ljava/lang/String;Z)Z")) {
                            if (resultIndex != -1) throw new IllegalArgumentException("Ambiguous Pro reader");
                            resultIndex = i + 1;
                        }
                    }
                }
                if (!keyFound || resultIndex < 0 || resultIndex + 1 >= ins.size())
                    throw new IllegalArgumentException("Pro reader fingerprint did not match");
                Instruction result = ins.get(resultIndex);
                if (!(result instanceof OneRegisterInstruction))
                    throw new IllegalArgumentException("Unexpected Pro result instruction");
                int register = ((OneRegisterInstruction)result).getRegisterA();
                if (result.getOpcode() != Opcode.MOVE_RESULT &&
                    !(result.getOpcode() == Opcode.CONST_4 &&
                      ((NarrowLiteralInstruction)result).getNarrowLiteral() == 1))
                    throw new IllegalArgumentException("Unexpected Pro reader result");
                Instruction ret = ins.get(resultIndex + 1);
                if (ret.getOpcode() != Opcode.RETURN ||
                    ((OneRegisterInstruction)ret).getRegisterA() != register || register > 15)
                    throw new IllegalArgumentException("Unexpected Pro reader return");
                boolean patchApp = checkLicenseCall(app, 0);
                boolean patchProvider = checkLicenseCall(provider, 2);

                impl.replaceInstruction(resultIndex, new BuilderInstruction11n(Opcode.CONST_4, register, 1));
                if (patchApp) omitCall(app, 0);
                if (patchProvider) omitCall(provider, 2);
                return Unit.INSTANCE;
            });
            return Unit.INSTANCE;
        });

    private static MutableMethod method(BytecodePatchContext context, String type,
            String name, String returnType, String... parameters) {
        List<MutableMethod> matches = new ArrayList<>();
        for (MutableMethod m : context.mutableClassDefBy(type).getMethods()) {
            if (!m.getName().equals(name) || !m.getReturnType().equals(returnType) ||
                m.getParameterTypes().size() != parameters.length) continue;
            boolean same = true;
            for (int i = 0; i < parameters.length; i++)
                same &= parameters[i].contentEquals(m.getParameterTypes().get(i));
            if (same) matches.add(m);
        }
        if (matches.size() != 1 || matches.get(0).getImplementation() == null)
            throw new IllegalArgumentException("Expected one method: " + type + "->" + name);
        return matches.get(0);
    }

    private static boolean checkLicenseCall(MutableMethod method, int index) {
        List<? extends Instruction> ins = method.getImplementation().getInstructions();
        if (ins.size() <= index) throw new IllegalArgumentException("Missing licence call: " + method);
        Instruction item = ins.get(index);
        if (item.getOpcode() == Opcode.INVOKE_STATIC && item instanceof ReferenceInstruction &&
            LICENSE_CALL.equals(((ReferenceInstruction)item).getReference().toString())) return true;
        if (ins.size() > index + 2 && item.getOpcode() == Opcode.NOP &&
            ins.get(index + 1).getOpcode() == Opcode.NOP && ins.get(index + 2).getOpcode() == Opcode.NOP)
            return false;
        throw new IllegalArgumentException("Unexpected licence startup fingerprint: " + method);
    }

    private static void omitCall(MutableMethod method, int index) {
        MutableMethodImplementation impl = method.getImplementation();
        impl.replaceInstruction(index, new BuilderInstruction10x(Opcode.NOP));
        impl.addInstruction(index + 1, new BuilderInstruction10x(Opcode.NOP));
        impl.addInstruction(index + 2, new BuilderInstruction10x(Opcode.NOP));
    }
}
