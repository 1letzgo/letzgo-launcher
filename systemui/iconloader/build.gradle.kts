// frameworks/libs/systemui/iconloaderlib (Variante iconloader_base, nur öffentliche APIs)
plugins {
    alias(libs.plugins.android.library)
}

val src = rootProject.file("aosp/frameworks_libs_systemui/iconloaderlib")

android {
    namespace = "com.android.launcher3.icons"
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(listOf(src.resolve("src").path))
        kotlin.setSrcDirs(listOf(src.resolve("src").path))
        res.setSrcDirs(listOf(src.resolve("res").path))
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    api(project(":flags"))
    api(project(":systemui:usertype"))
    implementation(libs.androidx.core)
}
