# letzgo-launcher

Ein schlanker Android Launcher auf Basis von AOSP Launcher3: **Bedienlogik wie iOS 26, Gestaltung
immer Material 3** (dynamische Farben, Tonflächen, M3-Komponenten).

Status: im Aufbau. Launcher3 aus AOSP (ohne Quickstep) baut als eigenständige Gradle-App;
Meilensteine M1 bis M3 des [Umbau-Plans](docs/ios26-umbau.md) sind umgesetzt (iOS-Raster, kein
App-Drawer – alle Apps stehen in der App-Mediathek, der Homescreen ist eine Auswahl, Bearbeitungsmodus mit Wackeln, Ordner mit 3 × 3-Raster,
Zahlen-Badges, App-Mediathek, Suche, Heute-Ansicht).

## Bauen

Voraussetzungen: JDK 21 oder neuer (getestet mit JDK 25), Android SDK mit Plattform 37.1
(lädt AGP bei akzeptierter Lizenz selbst nach).

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Danach in den Android-Einstellungen „letzgo“ als Standard-Startbildschirm wählen.

## Releases und Updates

Fertige APKs liegen als [GitHub-Releases](https://github.com/1letzgo/letzgo-launcher/releases)
(Tag `launcher-android-<versionCode>`). Release-Builds prüfen beim Start höchstens alle sechs Stunden
auf eine neue Version, laden sie in der App (Prüfsumme gegen den GitHub-Digest) und übergeben sie
dem Paketinstaller; in den Launcher-Einstellungen gibt es zusätzlich „Nach Updates suchen“.

```sh
tools/release.sh "Was ist neu"
```

- `versionCode` = Anzahl Commits, `versionName` steht in `gradle.properties` (`letzgoVersionName`).
- Release-APKs sind mit dem Debug-Key dieses Macs (`~/.android/debug.keystore`) signiert. Der Key
  darf sich nicht ändern, sonst lehnt Android die Updates ab.

## Aufbau

| Verzeichnis | Inhalt |
|---|---|
| `aosp/` | AOSP-Snapshot (git subtree vom Branch `aosp/<tag>`), mit wenigen markierten Anpassungen |
| `app/` | App-Modul: baut Launcher3 aus `aosp/Launcher3` (`src`, `src_no_quickstep`, …), eigenes Manifest, eigener Code unter `de.letzgo.launcher`, eigene Ressourcen in `app/src/main/res` (überschreiben die von Launcher3) |
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
`build/generated/res` und bildet diese Verweise auf öffentliche Farben ab.

Eigene Ressourcen der App liegen in `app/src/main/res`. Sie werden nach den generierten
Launcher3-Ressourcen registriert und haben deshalb Vorrang; so lassen sich Launcher3-Ressourcen
(z. B. `xml/device_profiles.xml`, Dimensionen, Themes) gezielt ersetzen.

### Änderungen an `aosp/`

- Plattform-Flags auf `de.letzgo.launcher.fwflags.*` umgebogen (7 Dateien)
- App-Lock-APIs (nicht im öffentlichen SDK) über `de.letzgo.launcher.compat.AppLockCompat`
- Keine Änderung, aber zu wissen: Wischen nach unten nutzt `StatusBarManager.expandNotificationsPanel()`
  bzw. `expandSettingsPanel()` per Reflection (nicht im öffentlichen SDK, Stand Android 16/17 erlaubt;
  `de.letzgo.launcher.system.SystemPanels`)
- `BuildConfig.ALL_APPS_ENABLED` blendet „Apps list“ im Homescreen-Menü aus
  (`popup/WorkspaceLongPressOptions.kt`)
- `BuildConfig.SPRING_LOADED_SHRINK`: beim Ziehen bleibt der Homescreen in voller Größe
  (`states/SpringLoadedState.java`); stattdessen blättert der Bildschirmrand beim Ziehen zur
  Nachbarseite (`Workspace.java`)
- `BuildConfig.FOLDER_PREVIEW_GRID`: Ordner-Vorschau als 3 × 3-Raster, Ordner immer mit vollen
  Seiten (`folder/ClippedFolderIconLayoutRule.java`, `folder/FolderGridOrganizer.java`)
- `BuildConfig.WIDGETS_ALIGN_TO_ICONS`: Widgets auf dem Homescreen enden bündig mit den Icons
  (`deviceprofile/WorkspaceProfileNonResponsiveFactory.kt`)
- `BuildConfig.WIDGETS_IOS_SIZES`: Widgets auf dem Homescreen nur klein/mittel/groß, ohne freies
  Skalieren (`widget/LauncherAppWidgetProviderInfo.java`)
- `BuildConfig.FOLDER_ROUNDED_SQUARE`: Ordner als abgerundetes Quadrat statt Kreis
  (`graphics/ThemeManager.kt`)
- `BuildConfig.FOLDER_CATEGORY_NAMES`: neue Ordner nach Mediathek-Kategorie benennen
  (`folder/FolderNameProvider.java`)
- `BuildConfig.NUMERIC_BADGES`: rote Zahlen-Badges statt Punkten (`BubbleTextView.java`,
  `folder/FolderIcon.java`; Zeichnen in `iconloaderlib/.../DotRenderer.java`)

### AOSP aktualisieren

1. In `aosp-tag.txt` den Tag eintragen (leer = neuester) und pushen; der Workflow
   `import-aosp.yml` legt den Snapshot auf dem Branch `aosp/<tag>` ab.
2. `git fetch && git subtree merge --prefix=aosp --squash origin/aosp/<tag>`
