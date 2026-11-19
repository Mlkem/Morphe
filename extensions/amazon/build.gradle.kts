extension {
    name = "extensions/amazon-aeroplan.mpe"
}

android {
    namespace = "ca.mlkem.amazon.extension"
}

configurations.configureEach {
    exclude(group = "org.jetbrains.kotlin")
}
