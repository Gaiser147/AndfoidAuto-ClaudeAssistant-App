# Claude für Android Auto

Private Android-Auto-App: Mikrofon-Button in AA antippen → Automikrofon nimmt auf → Spracherkennung →
Claude antwortet → Antwort kommt über die Autolautsprecher. Die Lenkradtaste bleibt bei Gemini.

Gebaut in Kotlin mit der [Android for Cars App Library](https://developer.android.com/training/cars/apps),
installiert nur privat und in AA über „Unbekannte Quellen“ sichtbar.

## Stand

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup: Android Studio, DHU, AA-Entwicklermodus | ✅ Anleitung unten |
| 1 | Grundgerüst: `CarAppService`, `Session`, `Screen`, Mikrofon-/Stopp-Button, Statusanzeige | ✅ (Demo-Ablauf ohne echte Funktion) |
| 2 | Spracheingabe: `CarAudioRecord` → `SpeechRecognizer` (`EXTRA_AUDIO_SOURCE`, Android 13+) | offen |
| 3 | Claude-Anbindung: Messages API mit Streaming, Systemprompt, Key im Android Keystore | offen |
| 4 | Sprachausgabe: `TextToSpeech` satzweise, Audio-Focus, Stopp | offen |
| 5 | Feinschliff: automatische Rückfrage, Fehlerfälle, Verlauf speichern | offen |
| 6 | Optional: Tool-Use (Navigation, Kalender) | offen |

In Phase 1 spielt der Mikrofon-Button nur die Zustände **Bereit → Hört zu → Denkt nach → Spricht → Bereit**
mit festen Wartezeiten durch, damit sich Oberfläche und Stopp-Button in der Desktop Head Unit testen lassen.

## Phase 0: Setup

### Android Studio und SDK

- Android Studio (aktuelle stabile Version) installieren und das Projekt öffnen. Gradle, AGP 9 und
  Kotlin werden über den Wrapper bzw. den Version-Catalog (`gradle/libs.versions.toml`) geladen.
- Im SDK Manager installieren:
  - **Android SDK Platform 37** (wird zum Kompilieren gebraucht)
  - unter *SDK Tools*: **Android Auto Desktop Head Unit Emulator** (landet in `$ANDROID_HOME/extras/google/auto/`)
- Benötigt JDK 17 oder neuer (das in Android Studio mitgelieferte JBR reicht).

**NixOS:**

- Android Studio aus nixpkgs: `pkgs.android-studio` (z. B. in `environment.systemPackages` oder `home.packages`).
  `adb` kommt mit dem SDK oder über `pkgs.android-tools`.
- Die DHU ist ein dynamisch gelinktes Binary aus dem SDK und startet unter NixOS nicht direkt. Einfachste Wege:
  - `programs.nix-ld.enable = true;` in der NixOS-Konfiguration, dann läuft `desktop-head-unit` direkt, oder
  - einmalig über eine FHS-Umgebung starten, z. B. `steam-run ./desktop-head-unit`
    bzw. eine eigene `buildFHSEnv` mit den fehlenden Bibliotheken (SDL2, libGL, libpulseaudio, …).

### Android Auto auf dem Handy

1. Android-Auto-Einstellungen öffnen (Android 10+: *Einstellungen → Verbundene Geräte → Verbindungseinstellungen → Android Auto*).
2. Ganz unten ca. 10× auf **Version** tippen → Entwicklermodus bestätigen.
3. Drei-Punkte-Menü → **Entwicklereinstellungen** → **Unbekannte Quellen** aktivieren.
4. Zum Testen am PC: Drei-Punkte-Menü → **Head-Unit-Server starten**.

### Bauen und installieren

```bash
./gradlew assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug       # direkt aufs per USB verbundene Handy
./gradlew testDebugUnitTest  # Unit-Tests
```

Alternativ baut die GitHub Action bei jedem Push und hängt die Debug-APK als Artefakt an
(*Actions → Build → claude-auto-debug-apk*).

### In der Desktop Head Unit testen

```bash
adb forward tcp:5277 tcp:5277
$ANDROID_HOME/extras/google/auto/desktop-head-unit
```

Im DHU-Launcher erscheint **Claude Assistent**. Antippen → Mikrofon-Button → Statusanzeige läuft durch,
Stopp bricht ab.

Im echten Auto taucht die App nach der Installation im AA-Launcher auf, solange „Unbekannte Quellen“ aktiv ist.

### Claude-API-Key (erst ab Phase 3 nötig)

In der [Claude Console](https://console.anthropic.com/) einen API-Key anlegen. Die API wird separat
nach Verbrauch abgerechnet, ein claude.ai-Abo deckt sie nicht ab. Der Key kommt **nicht** ins Repo,
sondern wird in Phase 3 in der App eingegeben und mit dem Android Keystore verschlüsselt gespeichert.

## Projektstruktur

```
app/src/main/java/io/github/gaiser147/claudeauto/
├── MainActivity.kt                 Handy-Bildschirm (Hinweise, später Einstellungen)
└── car/
    ├── ClaudeCarAppService.kt      Einstieg für Android Auto, HostValidator
    ├── AssistantSession.kt         eine Verbindung zum Auto (≈ eine Fahrt)
    ├── AssistantScreen.kt          PaneTemplate mit Status und Sprechen-/Stopp-Button
    ├── AssistantStatus.kt          Bereit / Hört zu / Denkt nach / Spricht
    ├── AssistantController.kt      Schnittstelle für einen Gesprächsdurchlauf
    └── DemoAssistantController.kt  Phase-1-Platzhalter, wird ab Phase 2 ersetzt
```

### Designentscheidungen

- **Kategorie IOT, `minCarApiLevel` 5:** AA lässt für Apps aus unbekannten Quellen diese Kategorie zu,
  und `CarAudioRecord` (Phase 2) braucht Car API Level 5.
- **`PaneTemplate` statt `MessageTemplate`:** AA begrenzt die Zahl der Bildschirmwechsel pro Aufgabe.
  Beim `MessageTemplate` zählt jede Textänderung als neuer Bildschirm, beim `PaneTemplate` nur eine
  Änderung von Titel oder Zeilentiteln. Deshalb bleibt der Zeilentitel fest („Status“) und nur der
  Text darunter wechselt.
- **`AssistantController` als Schnittstelle:** Phase 2–4 liefern eine echte Implementierung,
  `AssistantScreen` muss dafür nicht angefasst werden.
- **HostValidator:** Debug-Builds akzeptieren jeden Host (nötig für die DHU), Release-Builds nur die
  signierte Android-Auto-App.
- **minSdk 29 (Android 10):** Die App läuft ab Android 10, die Spracheingabe über das Automikrofon
  wird ab Phase 2 aber Android 13+ voraussetzen.
