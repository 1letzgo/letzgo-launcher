// Launcher3/modules/appfunctions (Workspace-AppFunctions)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
}

val src = rootProject.file("aosp/Launcher3/modules/appfunctions")

android {
    namespace = "com.android.launcher3.appfunctions"
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(listOf(src.resolve("src").path))
        kotlin.setSrcDirs(listOf(src.resolve("src").path))
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    api(libs.androidx.appfunctions)
    api(libs.androidx.appfunctions.service)
    ksp(libs.androidx.appfunctions.compiler)
    implementation(libs.androidx.annotation)
}
