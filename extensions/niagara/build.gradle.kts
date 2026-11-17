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
// Android's dependency transforms accept a jar, not a raw classes directory.
val nativeSignaturesJar = tasks.register<Jar>("nativeSignaturesJar") {
    from(compileNativeSignatures)
    archiveFileName.set("niagara-native-signatures.jar")
    destinationDirectory.set(layout.buildDirectory.dir("native-signatures-jar"))
}
dependencies { compileOnly(files(nativeSignaturesJar.flatMap { it.archiveFile })) }
tasks.named("preBuild") { dependsOn(nativeSignaturesJar) }
