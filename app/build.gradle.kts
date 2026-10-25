// Launcher3 ohne Quickstep (entspricht dem AOSP-Target "Launcher3"), als normale App gebaut.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/** Monoton ohne manuelles Hochzählen: Anzahl Commits (-PletzgoVersionCode=… überschreibt). */
fun gitCommitCount(): Int =
    runCatching {
            providers
                .exec { commandLine("git", "rev-list", "--count", "HEAD") }
                .standardOutput
                .asText
                .get()
                .trim()
                .toInt()
        }
        .getOrDefault(1)

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
        versionCode =
            (project.findProperty("letzgoVersionCode") as String?)?.toIntOrNull() ?: gitCommitCount()
        versionName = project.property("letzgoVersionName") as String

        // Entspricht Launcher3/tools/buildconfig.sh, ohne Suchleiste auf der ersten Seite
        buildConfigField("boolean", "IS_STUDIO_BUILD", "false")
        buildConfigField("boolean", "QSB_ON_FIRST_SCREEN", "false")
        buildConfigField("boolean", "IS_DEBUG_DEVICE", "false")
        buildConfigField("boolean", "WIDGETS_ENABLED", "true")
        buildConfigField("boolean", "NOTIFICATION_DOTS_ENABLED", "true")
        // Eigene Ergänzung: kein App-Drawer (siehe WorkspaceLongPressOptions)
        buildConfigField("boolean", "ALL_APPS_ENABLED", "false")
        // Eigene Ergänzungen für iOS-Verhalten (siehe docs/ios26-umbau.md, Änderungen an aosp/)
        buildConfigField("boolean", "SPRING_LOADED_SHRINK", "false")
        buildConfigField("boolean", "FOLDER_PREVIEW_GRID", "true")
        buildConfigField("boolean", "NUMERIC_BADGES", "true")
        buildConfigField("boolean", "WIDGETS_ALIGN_TO_ICONS", "true")
        buildConfigField("boolean", "FOLDER_CATEGORY_NAMES", "true")
        buildConfigField("boolean", "WIDGETS_IOS_SIZES", "true")
        buildConfigField("boolean", "LETZGO_POPUPS", "true")
        buildConfigField("boolean", "FOLDER_ROUNDED_SQUARE", "true")
        buildConfigField("boolean", "FOLDER_TITLE_ABOVE", "true")
        buildConfigField("boolean", "STABLE_STATUS_BAR_INSET", "true")
        buildConfigField("boolean", "ICON_SIZE_SETTING", "true")
        buildConfigField("boolean", "GRID_MIGRATION_KEEP_SPANS", "true")
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // The launcher is English only: drops the translations of Launcher3, Material and AndroidX too
    androidResources {
        localeFilters += "en"
    }

    (sourceSets.getByName("main") as com.android.build.api.dsl.AndroidSourceSet).apply {
        java.setSrcDirs(launcherSources)
        kotlin.setSrcDirs(launcherSources)
        res.setSrcDirs(emptyList<String>())
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "UPDATE_CHECK", "false")
        }
        release {
            // Prüft beim Start auf neue GitHub-Releases (de.letzgo.launcher.update)
            buildConfigField("boolean", "UPDATE_CHECK", "true")
            // Wie in AOSP: Proguard aus, bis die Keep-Regeln für den Gradle-Build geprüft sind
            isMinifyEnabled = false
            // GitHub-Releases (wie abstand) mit dem Debug-Key dieses Macs (~/.android/debug.keystore).
            // Er darf sich nie ändern, sonst lehnt Android die In-App-Updates ab.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    packaging.resources.excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
}

androidComponents.onVariants { variant ->
    variant.sources.res?.addGeneratedSourceDirectory(publicizeResources, PublicizeResources::outputDir)
    // Eigene Ressourcen nach den Launcher3-Ressourcen registrieren, damit sie Vorrang haben
    variant.sources.res?.addStaticSourceDirectory("src/main/res")
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

    testImplementation(libs.junit)
    // org.json aus android.jar ist im Unit-Test nur ein Stub
    testImplementation(libs.org.json)
}
