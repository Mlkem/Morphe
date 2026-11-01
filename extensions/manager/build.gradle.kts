extension {
    name = "extensions/manager-apkmirror.mpe"
}

android {
    namespace = "ca.mlkem.manager.extension"
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin")
}
