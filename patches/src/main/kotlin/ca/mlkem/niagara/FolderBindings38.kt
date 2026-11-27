package ca.mlkem.niagara

import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.proxy.mutableTypes.encodedValue.MutableStringEncodedValue
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21c
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction31c
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.immutable.reference.ImmutableStringReference
import com.android.tools.smali.dexlib2.rewriter.DexRewriter
import com.android.tools.smali.dexlib2.rewriter.Rewriter
import com.android.tools.smali.dexlib2.rewriter.RewriterModule
import com.android.tools.smali.dexlib2.rewriter.Rewriters

// Each table maps the original folder extension names to one signed host release.
private val names38 = mapOf(
    "BLDdEWAqgmSgtBKrBHAE" to "QwycTnmqIXeO",
    "C07CQRD69zoXzbLCDIVboCy" to "cOUepaqoCwdpQ3WMzlfE",
    "CGIzpDMt4IosrEXJSXs" to "qQBAU7HuMrVPSgayqWoBLfEecU1",
    "DXrRefnGncAQzaLktSL" to "Q3bnrxmuw0MyHkrTbG4qWka1VHEta",
    "DwJZVMROBU3p2KDNJ9D" to "ibopRHvShRr",
    "F0o2ptD8ZHQLbx1bWQj" to "vLXYqr2VtCdQMDnwb18nZ",
    "F0zsLmDyNP46xTEokim0" to "gZnEFT4olbgmdW9GsJsZ8Rtgt",
    "H6XY97VfBd" to "fIHXZv2QPUgck0PCIrmZ",
    "I60ZWJ2JJQswp5ncJqdmpckTw" to "byrQ92rdKHamID",
    "JEUXUvcuL2jZkaA3lVI" to "riw9K7uUDELOxXxxGV3FkJLWfGF0o",
    "JgVnxRSDpXD" to "XyCsdNkkV1JIZgr415mfPLNRWTSnv",
    "L9Z5UBTfh8XASMAYBb" to "Mp48tfHH45TRNN7q3taBNtTIbX",
    "Oi8EEimW6PCnY9wobOiEmnRL" to "AbYJNFs7IKqvj3nZCx",
    "QkO0iYBXnHfGs" to "Idx11cNZ15",
    "R22npmuNnAlm" to "iSa8x0kNKx4CyO",
    "RIsor1V85WbjA" to "dlnLGgN7V3NHx",
    "S3wX241M0r7dtme" to "y833T8FAZJrMmjtJpqh2fjTSEP",
    "SVteKkH3agn3HfStY" to "wIiLYFStqiVjkwQ2jkReFh5",
    "T6TlZjUmR83SSjbLrA" to "X5qXlcTlmn4J8sVL8OP0",
    "UmorjzOdXluhk2" to "T3aZigmfOFNniS",
    "X8JK6Afqhu3Jnzb1WcuD" to "G2uNuZFGAq330JCbkgSue0Eoc",
    "XsWJYGAdKRv0cKLyNiHqk" to "GjY3bciiZkrikcwma3O1HZQM",
    "XxI9Vaz7yJV4E1n" to "A7HmuGBTC5du5",
    "bU2em0LRuS4u53QWXuuK" to "fcPvWmrMoDP64zObF",
    "cZSAj6Yk6k2hoQSAAr0pYx9S" to "zCM9phr7ANFBSI7Ej2cepNte8R",
    "dumwb6g03n1L1jCMzP9" to "fmAiDqZDHOKD",
    "e7kYwfpmdAKwqffy3DHfqSmX" to "IdZUlMXZV6Pp17XJ",
    "fkQ3p3udaQCrDLpFpQUM2PoTI" to "nYF0Q20VEueMsVhiDAHpC0C2vC",
    "fu2rtCCigf7Xb3nmMK7yTDWm" to "qQBAU7HuMrVPSgayqWoBLfEecU1",
    "i31xkztVRqgXM6VmkhJG3j1ad1OM" to "dAqlsNOBOcwBdqab2iL3pe",
    "jKmfyue9uxluWGPwaMZ" to "w0Of3iHk9u1wXxNMpQ2n",
    "kHCf7TXmMVX5vxaPS" to "yv8z4WHNnu1DEpp3BRv",
    "mRNWiSrsK565ZtCunmotpr" to "Omlv6gqc9Ki1HbdchbC5",
    "n4PP3LbBEB9lzb" to "CLAHPnl0Mfc0",
    "naks8ZxpaWirE" to "Qqn68ODSFrckn",
    "oQcyEm0ip6kPEdWWSQwBH1uEgiSl" to "VxFhRqLQmbe5Bce1TvTMwp8FL",
    "qBlsmBnzmgtZe0gU3u2b" to "B9ZYK85o3JvPks7of8tnbXtcon3IV",
    "qsNmvj5VLDnsPcq4Ge" to "FdG6xMzPSaymcSpktNphRSSpiuQd",
    "rNimljjbL3" to "tmvaFiZ3YIQF7MAEfYHiQR52ffC",
    "rkhpNkl4D1" to "Il9nLZSTco0tqHmWDubcE1UH7VJH",
    "rlRt8GlCrM" to "n6uNXBrqRs",
    "rvL0E3ZQVo9XE" to "qjjbIJb2CWNHP04",
    "u6kWpMmoNHeAi5BeY9jj" to "MLuTVTFYGaFVhG2P3nBbsnJ",
    "uP3MjWikAhbpaogvi8rIfV8OZg4pk" to "VJE51uPIoecJiKg0wuKHpmyliYfu",
    "uXEn98dBZyHFNcs4SNt" to "P7sI5PMvmtbovH",
    "vWXwYhWM2HZN" to "Gt2e7OqBn9CvfhW",
    "wU9F7hXH2h8KnHbCWy3Orm8APDQD" to "rec5jPG3hf0fg",
    "wf4rpUdaEtZBdN8GZLUNU8T" to "lE6UZd757mQ7jLFMxhLt7O",
    "zb6WeGAkeFk01GxkRZTwjyHAD" to "IBBjpPjEftvXVuLDO3",
)
private val names39 = mapOf(
    "BLDdEWAqgmSgtBKrBHAE" to "F5YNzw9h6OT13LiB7X1AWC",
    "C07CQRD69zoXzbLCDIVboCy" to "D1RZGwuH70yIq2Qgjbqo2u",
    "CGIzpDMt4IosrEXJSXs" to "Br3Cev25Q7d8f1Am5qcanyySpy",
    "DXrRefnGncAQzaLktSL" to "ndqyc4oxwA3BYaAMzL",
    "DwJZVMROBU3p2KDNJ9D" to "P5M7op6vNPy7KChvW",
    "F0o2ptD8ZHQLbx1bWQj" to "Q1cCiJmC8HtvN",
    "F0zsLmDyNP46xTEokim0" to "hTzdC1mLeeUGU0lUQZ",
    "H6XY97VfBd" to "ag8WTHopPRdxHO8IS40VfAXdoS",
    "I60ZWJ2JJQswp5ncJqdmpckTw" to "qBmc6XWnRNeBg0",
    "JEUXUvcuL2jZkaA3lVI" to "TU34xUN2nLsTSla",
    "JgVnxRSDpXD" to "ykN5W2LB4l5",
    "L9Z5UBTfh8XASMAYBb" to "XOSX1MBUj1ZIzW",
    "Oi8EEimW6PCnY9wobOiEmnRL" to "e3hBPAjqs7",
    "QkO0iYBXnHfGs" to "uVRhIfvQNmY",
    "R22npmuNnAlm" to "dUxQfoSOduWl7TMAjvjdg9sq",
    "RIsor1V85WbjA" to "sB9vcwJ3leZrkXGPumbHC",
    "S3wX241M0r7dtme" to "hABfcSjH7Gd4VvDKF1oB",
    "SVteKkH3agn3HfStY" to "VmtWu34qpYfM",
    "T6TlZjUmR83SSjbLrA" to "vk4zxoGqdJEEKPKhb9qKOAC",
    "UmorjzOdXluhk2" to "IFLD1qeGGFwZx5YNqHIFKRallE",
    "X8JK6Afqhu3Jnzb1WcuD" to "kjutXp3xoqj9v7l",
    "XsWJYGAdKRv0cKLyNiHqk" to "bJ1YVtXYcrNYx7Q5uBZofSKJaWsd",
    "XxI9Vaz7yJV4E1n" to "swREuCQddweVSpq3500DPrc",
    "bU2em0LRuS4u53QWXuuK" to "xXETgthW7BCQwWUA7fTM8hv2AsgJa",
    "cZSAj6Yk6k2hoQSAAr0pYx9S" to "vp6WQue8jvbOw",
    "dumwb6g03n1L1jCMzP9" to "CVk2RL2cv8XMyvsxtc76fCcn",
    "e7kYwfpmdAKwqffy3DHfqSmX" to "OzzD4kHTZ3GQW9q5krL",
    "fkQ3p3udaQCrDLpFpQUM2PoTI" to "fbYx3zV0ISaqojG",
    "fu2rtCCigf7Xb3nmMK7yTDWm" to "Br3Cev25Q7d8f1Am5qcanyySpy",
    "i31xkztVRqgXM6VmkhJG3j1ad1OM" to "hNAqVksS9ZOZ3L5wJi5pcqXB504JD",
    "jKmfyue9uxluWGPwaMZ" to "l6SF4ujoiXveW",
    "kHCf7TXmMVX5vxaPS" to "WVF07OSSVzFSdKd8iM3HlE",
    "mRNWiSrsK565ZtCunmotpr" to "jyDKCfXQjOyH",
    "n4PP3LbBEB9lzb" to "lVRTZkufxufVJiIBJuEkb7a4KFe0",
    "naks8ZxpaWirE" to "pjQisL2N2t",
    "oQcyEm0ip6kPEdWWSQwBH1uEgiSl" to "c1S0XwGNKCIlOl4D",
    "qBlsmBnzmgtZe0gU3u2b" to "JCqii6JDK9yGAkw",
    "qsNmvj5VLDnsPcq4Ge" to "SXY0ZyEdzqENh",
    "rNimljjbL3" to "GY6UUxPTegIYX",
    "rkhpNkl4D1" to "U7jH06DMrqjf561W7O",
    "rlRt8GlCrM" to "GJSFscRjvehb",
    "rvL0E3ZQVo9XE" to "hIT6lSi579RNkOxS2OdvEktoN",
    "u6kWpMmoNHeAi5BeY9jj" to "fThmJaPMHcDAqONuuPRjr",
    "uP3MjWikAhbpaogvi8rIfV8OZg4pk" to "zPhBZvc6VO2PXx",
    "uXEn98dBZyHFNcs4SNt" to "w4WGCoktdFYv3",
    "vWXwYhWM2HZN" to "fiZMauRwmR6",
    "wU9F7hXH2h8KnHbCWy3Orm8APDQD" to "SpWYC4L2vCogT1eKVu338ljfj",
    "wf4rpUdaEtZBdN8GZLUNU8T" to "rx61KFw4gCDChNH8DbZSl4",
    "zb6WeGAkeFk01GxkRZTwjyHAD" to "iJIAAuVzJo9UdDTFoRcd",
)

private val identifier = Regex("[A-Za-z0-9_]+")

internal class FolderBindings(val names: Map<String, String>, val databaseReader: String) {
    fun name(value: String): String = identifier.replace(value) { names[it.value] ?: it.value }
}

internal val folderBindings = mapOf(
    "1.16.38" to FolderBindings(names38, "jWvXewWdTvbuXsq9m9mUy"),
    "1.16.39" to FolderBindings(names39, "aYPVRbn04J9qv"),
)

// Bind only our injected folder extension to the new host. Its continuation directly
// inherits a host class, while the folder repositories and settings use reflection.
internal fun BytecodePatchContext.bindFolderExtension(bindings: FolderBindings) {
    val pkg = "Lca/mlkem/niagara/"
    val rewrite = DexRewriter(object : RewriterModule() {
        override fun getTypeRewriter(rewriters: Rewriters): Rewriter<String> = Rewriter(bindings::name)
    })
    val classes = mutableListOf<String>()
    classDefForEach { if (it.type.startsWith(pkg)) classes.add(it.type) }
    check(pkg + "NativeContinuation;" in classes)
    for (type in classes) {
        val cls = mutableClassDefBy(type)
        cls.superclass?.let { cls.setSuperClass(bindings.name(it)) }
        for (field in cls.fields) {
            val constant = field.initialValue as? MutableStringEncodedValue ?: continue
            constant.setValue(bindings.name(constant.value))
        }
        for (old in cls.methods.toList()) {
            val method = MutableMethod(rewrite.methodRewriter.rewrite(old))
            if (type == pkg + "NativeContinuation;") method.setName(bindings.name(old.name))
            val instructions = method.implementation?.instructions.orEmpty()
            instructions.forEachIndexed { index, instruction ->
                val ref = (instruction as? ReferenceInstruction)?.reference as? StringReference
                if (ref != null && instruction is OneRegisterInstruction) {
                    // R8 assigned a different name to the four-argument database
                    // reader than it did to other members sharing its old name.
                    val mapped = if (type == pkg + "NativeBridge;" && old.name == "persisted" &&
                        ref.string == "kHCf7TXmMVX5vxaPS") bindings.databaseReader else bindings.name(ref.string)
                    if (mapped != ref.string) {
                        val string = ImmutableStringReference(mapped)
                        val updated = when (instruction.opcode) {
                            Opcode.CONST_STRING -> BuilderInstruction21c(Opcode.CONST_STRING, instruction.registerA, string)
                            Opcode.CONST_STRING_JUMBO -> BuilderInstruction31c(Opcode.CONST_STRING_JUMBO, instruction.registerA, string)
                            else -> error("Unexpected folder extension string instruction")
                        }
                        method.replaceInstruction(index, updated)
                    }
                }
            }
            cls.methods.remove(old)
            cls.methods.add(method)
        }
    }
}
