plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.protobuf) apply false
}

// Gemeinsame Einstellungen für alle Android-Module: AOSP-Code braucht Java 21 und
// Kotlin-Default-Methoden ohne Kompatibilitäts-Bridges (entspricht -Xjvm-default=all).
subprojects {
    pluginManager.withPlugin("com.android.base") {
        extensions.configure<com.android.build.api.dsl.CommonExtension>("android") {
            compileSdk { version = release(37) { minorApiLevel = 1 } }
            defaultConfig.minSdk = 34
            compileOptions.sourceCompatibility = JavaVersion.VERSION_21
            compileOptions.targetCompatibility = JavaVersion.VERSION_21
        }
        tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
            compilerOptions {
                jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
                jvmDefault = org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY
            }
        }
    }
    pluginManager.withPlugin("java-library") {
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}
