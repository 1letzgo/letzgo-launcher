// aconfig-Flags als reine Java-Bibliothek: Launcher3 + systemui-shared aus dem Snapshot,
// dazu Stub-Deklarationen aus flags/aconfig für Plattform-Flags.
plugins {
    `java-library`
}

val generateFlags by tasks.registering(GenerateAconfigFlags::class) {
    declarations.from(
        fileTree(rootProject.file("aosp/Launcher3/aconfig")) { include("*.aconfig") },
        fileTree(rootProject.file("aosp/frameworks_libs_systemui/aconfig")) { include("*.aconfig") },
        fileTree("aconfig") { include("*.aconfig") },
    )
    releaseValues = layout.projectDirectory.file("release-values.properties")
    overrides = layout.projectDirectory.file("overrides.properties")
    outputDir = layout.buildDirectory.dir("generated/aconfig")
}

sourceSets.main {
    java.srcDir(generateFlags)
}
