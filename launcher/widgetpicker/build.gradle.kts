// Launcher3/modules/widgetpicker (Compose-Widget-Auswahl)
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val src = rootProject.file("aosp/Launcher3/modules/widgetpicker")

android {
    namespace = "com.android.launcher3.widgetpicker"
    buildFeatures.compose = true
    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(listOf(src.resolve("src").path))
        kotlin.setSrcDirs(listOf(src.resolve("src").path))
        res.setSrcDirs(listOf(src.resolve("res").path))
        manifest.srcFile(src.resolve("AndroidManifest.xml"))
    }
}

dependencies {
    implementation(libs.androidx.core)
    implementation(libs.androidx.window)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
