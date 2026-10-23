// frameworks/libs/systemui/animationlib
plugins {
    alias(libs.plugins.android.library)
}

val src = rootProject.file("aosp/frameworks_libs_systemui/animationlib")

android {
    namespace = "com.android.app.animation"
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(listOf(src.resolve("src").path))
        kotlin.setSrcDirs(listOf(src.resolve("src").path))
        res.setSrcDirs(listOf(src.resolve("res").path))
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.annotation)
}
