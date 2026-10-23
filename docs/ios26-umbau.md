# Plan: Umbau zu einem iOS-26-artigen Launcher

Basis: AOSP Launcher3 (android-17.0.0_r1, ohne Quickstep), gebaut über Gradle (`app/`).
Ziel: Bedienlogik und Optik möglichst nah am iOS-26-Homescreen.

## Leitlinien

- **Erweitern statt umschreiben.** Eigener Code liegt in `app/src/main` unter `de.letzgo.launcher`.
  Einstiegspunkt ist eine eigene Activity `LetzgoLauncher extends Launcher`; Launcher3 sieht dafür
  Hooks vor (`createTouchControllers()`, `getDefaultOverlay()`, Dagger-Module in
  `src_no_quickstep/.../dagger`). Patches in `aosp/` nur, wo es keinen Hook gibt – klein halten
  und im Commit begründen, damit spätere AOSP-Updates per `git subtree merge` machbar bleiben.
- **Ressourcen überschreiben, nicht editieren.** Eigene Werte (Grid, Dimensionen, Farben, Layouts)
  in `app/src/main/res`; sie gewinnen gegen die generierten Launcher3-Ressourcen.
- **Feature-Flags nutzen.** Wo Launcher3 Verhalten per aconfig-Flag schaltet, den Wert in
  `flags/overrides.properties` setzen statt Code zu ändern.
- **Kein System-Launcher.** Die App läuft ohne Plattform-Signatur. Damit gibt es Grenzen (siehe
  unten), die das Design berücksichtigen muss.

## Grenzen einer normalen App

| iOS-Verhalten | Möglich? | Umsetzung |
|---|---|---|
| Zoom-Animation beim App-Start | teilweise | `ActivityOptions.makeScaleUpAnimation` vom Icon aus (heute: Clip-Reveal in `ActivityContext.java:523`) |
| App „fliegt“ beim Schließen zurück aufs Icon | nein | braucht `CONTROL_REMOTE_APP_TRANSITION_ANIMATIONS` (Quickstep); System-Standard bleibt |
| App-Umschalter / Gesten | nein | gehört weiter dem System-Launcher (Quickstep) |
| Blur hinter Ordnern, Dock, Mediathek | ja | `RenderEffect`/`RuntimeShader` (API 31/33) auf Launcher-Views; für Hintergrundbild: Window-Blur (`setBackgroundBlurRadius`) oder abgedunkelte Wallpaper-Farben |
| Wischen von oben links = Mitteilungen | ja | Systemleiste bleibt unverändert; Wischen in der Bildschirmmitte → Suche |
| App-Sperre / versteckte Apps | eingeschränkt | Android-17-App-Lock-APIs nicht öffentlich; „versteckt“ = nur in der Mediathek-Kategorie „Ausgeblendet“ |

## Phasen

### 0 – Grundgerüst

- `LetzgoLauncher extends Launcher` + Manifest umstellen; eigenes `LauncherAppComponent` falls
  Dagger-Bindings ersetzt werden müssen (`src_no_quickstep/.../dagger/Modules.kt` als Vorlage).
- Branding: App-Name, Icon, Package-Labels; Einstellungen-Screen (Fragment ersetzen via
  `settings_fragment_name`).
- Entscheidung minSdk: AOSP nennt 31, nutzt aber Farben ab API 34 → **Vorschlag minSdk 34**.
- CI: GitHub Action, die `:app:assembleDebug` baut.

### 1 – Homescreen-Raster und Grundoptik

| Teil | Launcher3 | Änderung |
|---|---|---|
| Raster | `res/xml/device_profiles.xml`, `InvariantDeviceProfile` | eigenes Profil: 4 × 6 Telefon, 6 × 5 Tablet; großzügige Zeilenabstände wie iOS |
| Dock | `Hotseat`, `numHotseatIcons` | 4 Plätze, ohne Beschriftung, Glas-Hintergrund (abgerundete Kapsel) |
| Icons | `IconShape`, `ThemeManager`, iconloader | Squircle-Maske (superelliptisch), größere Icons, keine Schatten |
| Beschriftung | `BubbleTextView` | kleine Schrift, einzeilig, Ellipsis |
| Suchleiste | QSB (`QSB_ON_FIRST_SCREEN`) | entfernen (`buildConfigField` auf `false`) |
| Seitenindikator | `PageIndicatorDots` | Punkte über dem Dock, immer sichtbar. Die „Suchen“-Pille (iOS 26) kommt mit der Suche in Phase 6 |

### 2 – Kein App-Drawer: alle Apps auf dem Homescreen

- Beim Laden und bei Installationen jede startbare App, die nicht auf Homescreen/Dock/Ordner
  liegt, an der nächsten freien Stelle ab der letzten Seite einfügen.
  Ansatz: Model-Task nach `LoaderTask`-Abschluss auf Basis von `AddWorkspaceItemsTask`;
  Installationen über `ItemInstallQueue`/`SessionCommitReceiver` (Pref `pref_add_icon_to_home`
  fest an).
- `ALL_APPS`-Zustand nicht mehr per Geste erreichbar: `createTouchControllers()` ohne
  `AllAppsSwipeController`; „Apps list“ im Optionsmenü (`OptionsPopupView`) entfernen.
- „Vom Homescreen entfernen“ (App bleibt in der Mediathek) vs. „App löschen“ (Deinstallation über
  `REQUEST_DELETE_PACKAGES`). Merker für entfernte Apps in eigener Tabelle/Pref, damit Schritt 1
  sie nicht wieder einfügt.

### 3 – Bearbeitungsmodus („Jiggle“)

- Einstieg: lange auf freie Fläche drücken, oder Kontextmenü → „Home-Bildschirm bearbeiten“, oder
  langes Drücken + Ziehen eines Icons.
- Zustand: auf `EDIT_MODE` (`states/EditModeState.kt`) aufbauen, nicht auf `SPRING_LOADED`
  (der verkleinert die Seiten). Workspace bleibt in Originalgröße.
- Wackel-Animation für `BubbleTextView`, `FolderIcon`, Widgets (Rotation ±1,5°, versetzte Phase);
  Minus-Badge oben links → Dialog „Vom Home-Bildschirm entfernen / App löschen“.
- Obere Leiste mit „+“ (Widget-Galerie), „Bearbeiten“, „Fertig“ statt `DropTargetBar`
  (`DeleteDropTarget`/`SecondaryDropTarget` entfallen im Normalfluss).
- Tippen auf Seitenpunkte → Seitenübersicht zum Ausblenden/Umordnen von Seiten.
- Neue Seite automatisch beim Ziehen an den rechten Rand, leere Seiten verschwinden.

### 4 – Ordner

| Teil | Launcher3 | Änderung |
|---|---|---|
| Ordner-Icon | `FolderIcon`, `PreviewItemManager`, `ClippedFolderIconLayoutRule` | abgerundetes Quadrat mit 3 × 3-Mini-Raster statt runder Vorschau mit 4 Icons |
| Inhalt | `Folder`, `FolderGridOrganizer` | 3 × 3 pro Seite, horizontal blätternd, großer Titel oben |
| Öffnen | `FolderAnimationManager` | Zoom aus dem Icon, Hintergrund weichgezeichnet und abgedunkelt |
| Umbenennen | Folder-Titel | nur im Bearbeitungsmodus |

### 5 – App-Mediathek

- Eigene letzte Seite rechts im `Workspace` (kein `CellLayout`, sondern eigener View; Workspace
  behandelt sie wie eine Zusatzseite).
- Oben Suchfeld; darunter Kategorien als 2 × 2-Kacheln (3 große Icons + Mini-Cluster für den Rest).
  Kategorien: `ApplicationInfo.category` (öffentlich, oft leer) + Heuristik + „Zuletzt hinzugefügt“
  (`firstInstallTime`); „Vorschläge“ optional mit `PACKAGE_USAGE_STATS` (Nutzerfreigabe).
- Herunterwischen bzw. Suche → alphabetische Liste aller Apps; Daten aus `AllAppsStore`.

### 6 – Suche (Spotlight)

- Wischen nach unten auf dem Homescreen oder Tippen auf die „Suchen“-Pille öffnet ein Overlay mit
  Tastatur, Glas-Hintergrund.
- Treffer: Apps (`DefaultAppSearchAlgorithm` aus `allapps/search`), App-Shortcuts, Kontakte und
  Einstellungen später.
- Eigener `TouchController` in `createTouchControllers()`.

### 7 – Mitteilungs-Badges

- Rote Zahlen-Badges statt Punkte: `NotificationListener` liefert die Schlüssel pro Paket,
  `PopupDataProvider`/`DotInfo` zählen; Rendering in `DotRenderer` (iconloader) ersetzen.
- Ordner zeigen die Summe (`FolderDotInfo`).

### 8 – Heute-Ansicht

- Seite links von Seite 1 über `getDefaultOverlay()` (`LauncherOverlayManager`): vertikale Liste
  von Widgets (eigener `AppWidgetHost`), Suchfeld oben.

### 9 – Widgets

- Größen auf iOS-Raster einrasten (klein 2 × 2, mittel 4 × 2, groß 4 × 4), keine freie Größe.
- Widget-Stapel (Smart Stack): eigener View mit mehreren `AppWidgetHostView`, vertikal wischbar.
- Widget-Galerie: vorhandenen Compose-Widget-Picker (`modules/widgetpicker`) umgestalten.

### 10 – Liquid Glass und Bewegung

- Glas-Material als wiederverwendbare Komponente (Blur + Lichtkante per `RuntimeShader`) für
  Dock, Ordner, Menüs, Suchpille, Mediathek-Kacheln.
- Icon-Stile Standard / Dunkel / Getönt / Klar über `ThemeManager` + Monochrom-Icons
  (`MonochromeIconFactory`).
- Federanimationen (`animationlib`, `SpringAnimation`) für Seitenwechsel, Ordner, Jiggle.
- Kontextmenü (`PopupContainerWithArrow`, `SystemShortcut`) im iOS-Stil: Glas, Liste ohne Pfeil,
  Icon wird vergrößert hervorgehoben.

## Reihenfolge

| Meilenstein | Phasen | Ergebnis |
|---|---|---|
| M1 – benutzbar ✅ | 0, 1, 2 | iOS-Raster, alle Apps auf dem Homescreen, kein Drawer |
| M2 – iOS-Bedienung ✅ | 3, 4, 7 | Jiggle-Modus, iOS-Ordner, Zahlen-Badges (offen: Ordner-Optik beim Öffnen, Seitenübersicht) |
| M3 – iOS-Seiten | 5, 6, 8 | Mediathek, Suche, Heute-Ansicht |
| M4 – Feinschliff | 9, 10 | Widget-Stapel, Liquid Glass, Animationen |

## Offene Entscheidungen

1. minSdk 34 (Vorschlag) oder 31 halten?
2. Mediathek-Kategorien: nur Heuristik oder Nutzungsstatistik (Nutzer muss Zugriff erlauben)?
3. Beschriftungen unter Icons immer anzeigen (iOS-Standard) oder abschaltbar?
4. Android-Widgets frei skalierbar lassen oder strikt auf iOS-Größen einrasten?
