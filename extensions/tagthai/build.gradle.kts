extension {
    name = "extensions/tagthai-text.mpe"
}

android {
    namespace = "ca.mlkem.tagthai.extension"
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin")
}
