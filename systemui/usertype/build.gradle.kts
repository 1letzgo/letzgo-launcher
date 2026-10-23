// frameworks/libs/systemui/usertypelib
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.parcelize)
}

val src = rootProject.file("aosp/frameworks_libs_systemui/usertypelib")

android {
    namespace = "com.android.users"
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(listOf(src.resolve("src").path))
        kotlin.setSrcDirs(listOf(src.resolve("src").path))
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}
