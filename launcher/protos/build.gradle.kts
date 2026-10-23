// Launcher3-Logging-Protos (protobuf-lite). Wie Launcher3ResLib in AOSP mit der
// Quickstep-Variante von launcher_atom_extension.proto.
plugins {
    `java-library`
    alias(libs.plugins.protobuf)
}

val launcher = rootProject.file("aosp/Launcher3")

sourceSets.main {
    proto {
        setSrcDirs(listOf(launcher.resolve("protos"), launcher.resolve("quickstep/protos_overrides")))
    }
}

dependencies {
    api(libs.protobuf.javalite)
}

protobuf {
    protoc { artifact = libs.protoc.get().toString() }
    generateProtoTasks {
        all().configureEach {
            builtins.named("java") { option("lite") }
        }
    }
}
