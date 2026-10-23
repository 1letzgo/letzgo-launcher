// frameworks/libs/systemui/dynamiccolors (nur Ressourcen), mit öffentlichen Framework-Farben
// (siehe PublicizeResources). Ersatz für entfallene customColor*-Einträge in src/main/res.
plugins {
    alias(libs.plugins.android.library)
}

val src = rootProject.file("aosp/frameworks_libs_systemui/dynamiccolors")

val publicizeResources by tasks.registering(PublicizeResources::class) {
    inputDir = src.resolve("res")
    outputDir = layout.buildDirectory.dir("generated/res/dynamiccolors")
}

android {
    namespace = "com.android.systemui.dynamiccolors"
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}

androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(publicizeResources, PublicizeResources::outputDir)
}
