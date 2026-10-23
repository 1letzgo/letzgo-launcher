// Launcher3 ohne Quickstep (entspricht dem AOSP-Target "Launcher3"), als normale App gebaut.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val aosp = rootProject.file("aosp")
val launcher = aosp.resolve("Launcher3")

// Quellverzeichnisse aus dem Snapshot, die in AOSP als statische Java-Libs eingebunden werden
// Launcher3-Ressourcen mit öffentlichen Framework-Farben (siehe PublicizeResources)
val publicizeResources by tasks.registering(PublicizeResources::class) {
    inputDir = launcher.resolve("res")
    outputDir = layout.buildDirectory.dir("generated/res/launcher3")
}

val launcherSources = listOf(
    launcher.resolve("src"),
    launcher.resolve("src_no_quickstep"),
    launcher.resolve("src_plugins"),
    launcher.resolve("shared/src"),
    launcher.resolve("dagger/src"),
    launcher.resolve("modules/concurrent/src"),
    aosp.resolve("frameworks_base_sysui_plugins/plugin_core/src"),
    aosp.resolve("frameworks_base_sysui_plugins/plugin_core/annotations/src"),
    aosp.resolve("frameworks_libs_systemui/msdllib/src"),
).map { it.path } + "src/main/java"

android {
    namespace = "com.android.launcher3"

    defaultConfig {
        applicationId = "de.letzgo.launcher"
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        // Entspricht Launcher3/tools/buildconfig.sh
        buildConfigField("boolean", "IS_STUDIO_BUILD", "false")
        buildConfigField("boolean", "QSB_ON_FIRST_SCREEN", "true")
        buildConfigField("boolean", "IS_DEBUG_DEVICE", "false")
        buildConfigField("boolean", "WIDGETS_ENABLED", "true")
        buildConfigField("boolean", "NOTIFICATION_DOTS_ENABLED", "true")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(launcherSources)
        kotlin.setSrcDirs(launcherSources)
        res.setSrcDirs(listOf("src/main/res"))
    }

    buildTypes {
        release {
            // Wie in AOSP: Proguard aus, bis die Keep-Regeln für den Gradle-Build geprüft sind
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
}

androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(publicizeResources, PublicizeResources::outputDir)
}

dependencies {
    implementation(project(":flags"))
    implementation(project(":compat"))
    implementation(project(":systemui:iconloader"))
    implementation(project(":systemui:animation"))
    implementation(project(":systemui:dynamiccolors"))
    implementation(project(":systemui:usertype"))
    implementation(project(":launcher:protos"))
    implementation(project(":launcher:widgetpicker"))
    implementation(project(":launcher:appfunctions"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core)
    implementation(libs.androidx.core.animation)
    implementation(libs.androidx.graphics.shapes)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.dynamicanimation)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.slice.view)
    implementation(libs.androidx.cardview)
    implementation(libs.androidx.window)
    implementation(libs.androidx.lifecycle.common.java8)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.guava)
    compileOnly(libs.jsr305)

    implementation(libs.dagger)
    implementation(libs.javax.inject)
    ksp(libs.dagger.compiler)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
}
