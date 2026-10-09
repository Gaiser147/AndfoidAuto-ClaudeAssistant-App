# Claude für Android Auto

Private Android-Auto-App: Mikrofon-Button in AA antippen → Automikrofon nimmt auf → Spracherkennung →
Claude antwortet → Antwort kommt über die Autolautsprecher. Die Lenkradtaste bleibt bei Gemini.

Gebaut in Kotlin mit der [Android for Cars App Library](https://developer.android.com/training/cars/apps),
installiert nur privat und in AA über „Unbekannte Quellen“ sichtbar.

## Stand

| Phase | Inhalt | Status |
|---|---|---|
| 0 | Setup: Android Studio, DHU, AA-Entwicklermodus | ✅ Anleitung unten |
| 1 | Grundgerüst: `CarAppService`, `Session`, `Screen`, Mikrofon-/Stopp-Button, Statusanzeige | ✅ |
| 2 | Spracheingabe: `CarAudioRecord` → `SpeechRecognizer` (`EXTRA_AUDIO_SOURCE`, Android 13+) | ✅ zeigt den erkannten Text an |
| 3 | Claude-Anbindung: Messages API mit Streaming, Systemprompt, Key im Android Keystore | offen |
| 4 | Sprachausgabe: `TextToSpeech` satzweise, Audio-Focus, Stopp | offen |
| 5 | Feinschliff: automatische Rückfrage, Fehlerfälle, Verlauf speichern | offen |
| 6 | Optional: Tool-Use (Navigation, Kalender) | offen |

Aktuell: App in Android Auto öffnen (oder Mikrofon-Button antippen) → die App hört sofort über das Automikrofon zu, zeigt während des Sprechens
Zwischenergebnisse und am Ende den erkannten Text. Ab Phase 3 geht dieser Text an Claude.

### Spracheingabe (Phase 2)

- `CarAudioRecord` liefert 16 kHz, mono, PCM 16 Bit. Ein Hintergrund-Thread schreibt das in eine Pipe,
  deren Leseende per `EXTRA_AUDIO_SOURCE` an den `SpeechRecognizer` geht.
- Die Aufnahme endet, sobald der Erkenner das Sprechende meldet, nach spätestens 15 Sekunden,
  per Stopp-Button oder wenn das Auto das Mikrofon schließt.
- Bevorzugt wird der On-Device-Erkenner auf Deutsch (`de-DE`). Scheitert er, z. B. weil das deutsche
  Offline-Paket fehlt, nutzt die App für den Rest der Fahrt den Standard-Erkenner (meist Google, online).
  Tipp: In den Android-Einstellungen unter *System → Sprache → Spracherkennung auf dem Gerät*
  (Bezeichnung je nach Hersteller) Deutsch herunterladen.
- Während der Aufnahme hält die App exklusiven Audio-Focus, Musik pausiert also.
- Das Mikrofonrecht fragt die App beim ersten Öffnen auf dem Handy an. Fehlt es, kommt die Abfrage beim
  ersten Tippen im Auto auf dem Handy.

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

Im DHU-Launcher erscheint **Claude Assistent**. Antippen → Mikrofon-Button → ins PC-Mikrofon sprechen.
Die DHU leitet das PC-Mikrofon als Automikrofon weiter. Stopp bricht ab.

Im echten Auto taucht die App nach der Installation im AA-Launcher auf, solange „Unbekannte Quellen“ aktiv ist.

### Claude-API-Key (erst ab Phase 3 nötig)

In der [Claude Console](https://console.anthropic.com/) einen API-Key anlegen. Die API wird separat
nach Verbrauch abgerechnet, ein claude.ai-Abo deckt sie nicht ab. Der Key kommt **nicht** ins Repo,
sondern wird in Phase 3 in der App eingegeben und mit dem Android Keystore verschlüsselt gespeichert.

## Projektstruktur

```
app/src/main/java/io/github/gaiser147/claudeauto/
├── MainActivity.kt                 Handy-Bildschirm: Hinweise, Mikrofonrecht
├── car/
│   ├── ClaudeCarAppService.kt      Einstieg für Android Auto, HostValidator
│   ├── AssistantSession.kt         eine Verbindung zum Auto (≈ eine Fahrt), verdrahtet alles
│   ├── AssistantScreen.kt          PaneTemplate mit Status, Text und Sprechen-/Stopp-Button
│   ├── AssistantStatus.kt          Bereit / Hört zu / Denkt nach / Spricht
│   ├── AssistantUiState.kt         Status + erkannter Text / Hinweis
│   ├── AssistantController.kt      Schnittstelle für einen Gesprächsdurchlauf
│   └── VoiceAssistantController.kt Ablauf: Mikrofonrecht → Zuhören → Ergebnis
└── voice/
    ├── SpeechInput.kt              Schnittstelle + Fehlerarten der Spracheingabe
    ├── CarSpeechInput.kt           CarAudioRecord → Pipe → SpeechRecognizer
    ├── MicrophonePermission.kt     RECORD_AUDIO prüfen/anfragen über CarContext
    └── AudioFocus.kt               exklusiver Audio-Focus während der Aufnahme

hook/                               LSPosed-Modul (eigenes APK)
└── src/main/java/.../hook/
    ├── ClaudeAssistHook.kt         hookt Android Auto, leitet die Sprachtaste auf den Standard-Assistenten um
    └── HookInfoActivity.kt         Infobildschirm mit Einrichtungsanleitung

xposedapi/                          Xposed-API-Stubs (compileOnly, nicht im APK; LSPosed liefert die echten zur Laufzeit)
```

## Lenkradtaste umleiten (LSPosed-Modul, optional)

> Nur für ein gerootetes Handy mit Magisk + LSPosed. Eingriff in die Android-Auto-App, daher
> grundsätzlich fragil und an die AA-Version gebunden.

Die Sprach-/Lenkradtaste in Android Auto ist fest mit Google/Gemini verdrahtet; eine normale App
kann sie nicht abfangen. Das Modul `hook/` fängt den Auslöser **im Android-Auto-Prozess** ab,
unterdrückt den Google-Start und löst stattdessen den Standard-Assistenten des Handys aus.

**Warum per Tastendruck-Injektion statt Intent:** Claude ist als VoiceInteractionService eingebunden
(`com.anthropic.claude/.bell.assist.ClaudeVoiceInteractionService`), nicht als Activity. So ein Dienst
lässt sich nicht per Intent starten — ihn ruft nur das System über den Assistenten-Mechanismus auf.
Der Hook bildet deshalb den Assistenten-Tastendruck nach: er injiziert `KEYCODE_ASSIST` (219, Rückfall
`KEYCODE_VOICE_ASSIST` 231) per Root (`su input keyevent …`), exakt wie der Power-Knopf. Android
startet daraufhin den eingestellten Standard-Assistenten (Claude). Welches Paket das ist, steht in
`settings get secure assistant`.

**Voraussetzung, die zuerst feststehen muss:** Claudes Sprachmodus läuft auch, während Android Auto
aktiv ist, und der Ton kommt aus den Autolautsprechern. Mit dem Power-Knopf bei verbundenem AA getestet
und bestätigt (AA behandelt es als Anruf über die Freisprechanlage).

**Gefunden per statischer Analyse von AA 17.7.663654:** Die Taste kommt als Key-Event mit Keycode
`VOICE_ASSIST` an; die obfuskierte Klasse `tfl` startet die Session über Methode `k` (Callback `tfk`,
„Error starting assistant session"). Diese Namen gelten **nur für diese AA-Version** und brechen bei
Updates — dann im LSPosed-Log `[ClaudeAA] Hook fehlgeschlagen` und die Stelle muss neu ermittelt
werden. Automatische Updates für Android Auto im Play Store deshalb abschalten.

**Einrichtung:**
1. `./gradlew :hook:assembleDebug` → `hook/build/outputs/apk/debug/hook-debug.apk` installieren.
2. In LSPosed das Modul aktivieren, Anwendungsbereich **nur** Android Auto.
3. Android Auto zwangsstoppen (oder Handy neu starten).
4. **In Magisk Android Auto Root gewähren** (`com.google.android.projection.gearhead`). Beim ersten
   Tastendruck erscheint die Magisk-Abfrage; mit „Für immer zulassen" bestätigen.
5. Im LSPosed-Log nach `[ClaudeAA]` suchen: `Hook ... gesetzt` heißt, die Stelle wurde gefunden;
   `Auslöser erkannt` und `KEYCODE 219 gesendet` erscheinen beim Druck auf die Taste.

**Zuerst in der Desktop Head Unit testen, nicht während der Fahrt.** Rechtlich: Eingriff in fremde
App auf dem eigenen Gerät; verstößt gegen Googles Nutzungsbedingungen.

### Designentscheidungen

- **Kategorie IOT, `minCarApiLevel` 5:** AA lässt für Apps aus unbekannten Quellen diese Kategorie zu,
  und `CarAudioRecord` (Phase 2) braucht Car API Level 5.
- **`PaneTemplate` statt `MessageTemplate`:** AA begrenzt die Zahl der Bildschirmwechsel pro Aufgabe.
  Beim `MessageTemplate` zählt jede Textänderung als neuer Bildschirm, beim `PaneTemplate` nur eine
  Änderung von Titel oder Zeilentiteln. Deshalb bleibt der Zeilentitel fest („Status“) und nur der
  Text darunter wechselt.
- **Schnittstellen für Mikrofonrecht und Spracheingabe:** `VoiceAssistantController` ist dadurch
  ohne Gerät unit-testbar (`./gradlew testDebugUnitTest`). Phase 3/4 hängen Claude und TTS an dieselbe Stelle.
- **HostValidator:** Debug-Builds akzeptieren jeden Host (nötig für die DHU), Release-Builds nur die
  signierte Android-Auto-App.
- **minSdk 29 (Android 10):** Die App läuft ab Android 10, die Spracheingabe über das Automikrofon
  braucht aber Android 13+. Darunter zeigt die App einen Hinweis statt abzustürzen.
