extension { name = "extensions/startup-updates.mpe" }
android { namespace = "ca.mlkem.updates.extension" }
configurations.configureEach { exclude(group = "org.jetbrains.kotlin") }
