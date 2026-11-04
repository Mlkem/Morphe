extension { name = "extensions/gmail-accounts.mpe" }
android { namespace = "ca.mlkem.gmail.extension" }
configurations.configureEach { exclude(group = "org.jetbrains.kotlin") }
