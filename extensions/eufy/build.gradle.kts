extension {
    name = "extensions/eufy-camera-only.mpe"
}

android {
    namespace = "ca.mlkem.eufy.extension"
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin")
}
