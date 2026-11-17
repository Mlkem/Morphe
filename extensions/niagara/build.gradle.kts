extension { name = "extensions/niagara-auto-folders.mpe" }
android { namespace = "ca.mlkem.niagara.extension" }
configurations.configureEach { exclude(group = "org.jetbrains.kotlin") }
// Only signatures, kept outside main sources so the app's classes are never replaced.
val compileNativeSignatures by tasks.registering(JavaCompile::class) {
    source(fileTree("src/compileOnly/java"))
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("native-signatures"))
    options.release.set(8)
}
val nativeSignatures = files(compileNativeSignatures.flatMap { it.destinationDirectory })
dependencies { compileOnly(nativeSignatures) }
tasks.named("preBuild") { dependsOn(compileNativeSignatures) }
