pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "letzgo-launcher"

// Generierte aconfig-Flags und Stubs für Plattform-Code, der nicht im Snapshot liegt
include(":flags", ":compat")
// Bibliotheken aus frameworks/libs/systemui (aosp/frameworks_libs_systemui)
include(
    ":systemui:iconloader",
    ":systemui:animation",
    ":systemui:dynamiccolors",
    ":systemui:usertype",
)
// Launcher3-Module und App
include(":launcher:protos", ":launcher:widgetpicker", ":launcher:appfunctions", ":app")
