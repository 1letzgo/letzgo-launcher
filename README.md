# letzgo-launcher

Ein schlanker Android Launcher auf Basis von AOSP Launcher3, der sich wie der iOS-Homescreen anfühlt
(Ziel: möglichst nah an der Logik von iOS 26).

Status: im Aufbau. Launcher3 aus AOSP (ohne Quickstep) baut als eigenständige Gradle-App.

## Bauen

Voraussetzungen: JDK 21 oder neuer (getestet mit JDK 25), Android SDK mit Plattform 37.1
(lädt AGP bei akzeptierter Lizenz selbst nach).

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Danach in den Android-Einstellungen „letzgo“/Launcher3 als Standard-Startbildschirm wählen.

## Aufbau

| Verzeichnis | Inhalt |
|---|---|
| `aosp/` | AOSP-Snapshot (git subtree vom Branch `aosp/<tag>`), mit wenigen markierten Anpassungen |
| `app/` | App-Modul: baut Launcher3 aus `aosp/Launcher3` (`src`, `src_no_quickstep`, …), eigenes Manifest, eigener Code unter `de.letzgo.launcher` |
| `flags/` | aconfig-Flags als Java-Klassen, erzeugt von `buildSrc/GenerateAconfigFlags.kt`; Werte in `flags/overrides.properties` |
| `compat/` | Stubs für Klassen aus `frameworks/base`, die nicht im Snapshot liegen |
| `systemui/*` | Bibliotheken aus `frameworks/libs/systemui` (iconloader, animation, dynamiccolors, usertype) |
| `launcher/*` | Launcher3-Module: Protos, Widget-Picker, AppFunctions |

### aconfig-Flags

AOSP erzeugt `Flags`-Klassen mit dem Tool `aconfig`. Hier übernimmt das der Gradle-Task
`generateFlags` im Modul `flags`. Die Werte entsprechen dem AOSP-Release-Config (für
android-17.0.0_r1: `cp1a`) und liegen in `flags/release-values.properties`, erzeugt mit

```sh
tools/import_flag_values.py android-17.0.0_r1 cp1a
```

Eigene Abweichungen gehören in `flags/overrides.properties`. Nicht gelistete Flags sind aus.

Plattform-Flags aus `frameworks/base` (z. B. `android.security.Flags`) liegen auf dem Gerät im
Boot-Classpath und sind dort hidden API. Launcher3 referenziert sie deshalb über
`de.letzgo.launcher.fwflags.*`; die Stub-Deklarationen stehen in `flags/aconfig/`.

### Ressourcen

AOSP-Ressourcen verweisen teils auf private Framework-Ressourcen (`@androidprv:`) und auf
nicht-öffentliche `@android:color/system_*`-Farben, deren IDs auf Geräten nicht stabil sind. Der
Task `PublicizeResources` (buildSrc) kopiert die Ressourcen von Launcher3 und dynamiccolors nach
`build/generated/res` und bildet diese Verweise auf öffentliche Farben ab. Eigene Ressourcen der
App liegen in `app/src/main/res`.

### Änderungen an `aosp/`

- Plattform-Flags auf `de.letzgo.launcher.fwflags.*` umgebogen (7 Dateien)
- App-Lock-APIs (nicht im öffentlichen SDK) über `de.letzgo.launcher.compat.AppLockCompat`

### AOSP aktualisieren

1. In `aosp-tag.txt` den Tag eintragen (leer = neuester) und pushen; der Workflow
   `import-aosp.yml` legt den Snapshot auf dem Branch `aosp/<tag>` ab.
2. `git fetch && git subtree merge --prefix=aosp --squash origin/aosp/<tag>`
