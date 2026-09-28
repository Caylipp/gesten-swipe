# Projektstatus: eingefroren

**Stand:** 28.09.2026 · Version 0.1.0 (MVP, Debug-Build)

## Wo das Projekt steht

| Bereich | Status |
|---|---|
| Android-App (Kotlin, Compose) | ✅ fertig implementiert, baut ohne Fehler |
| Unit-Tests (Detektor, Kalibrierung, Kopfpose) | ✅ 22/22 grün |
| Android Lint | ✅ 0 Fehler (nur Hinweise auf neuere Library-Versionen) |
| **Test auf echtem Gerät** | ❌ **noch nie gelaufen** |
| Release-Build / Signierung / Play Store | ❌ nicht begonnen |
| iOS | ⛔ bewusst verworfen, siehe unten |

Die Erkennungslogik ist nur mit simulierten Werten getestet. Ob die Gesten mit echten MediaPipe-Daten zuverlässig auslösen, ist noch offen.

## Wiedereinstieg: die ersten Schritte

1. **Toolchain prüfen** (war auf dem Mac per Homebrew installiert):
   ```bash
   export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
   echo "sdk.dir=/opt/homebrew/share/android-commandlinetools" > local.properties
   ./gradlew testDebugUnitTest assembleDebug
   ```
   Fehlt etwas: `brew install openjdk@17 && brew install --cask android-commandlinetools`, danach
   `sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`.
   Der erste Build dauert lange (10–20 min), weil alle Abhängigkeiten geladen werden.
2. **Auf ein Android-Handy installieren:** `adb install -r app/build/outputs/apk/debug/app-debug.apk`
3. **Einrichten:** Kamera erlauben → Bedienungshilfe aktivieren → kalibrieren → Testbereich (siehe README).
4. **Praxistest:** Die Live-Anzeige auf der Startseite zeigt Neigung, Drehung, Augen und Mund im Verhältnis zur Schwelle. Damit lassen sich die Schwellwerte prüfen.

## Offene Punkte und nächste Schritte (nach Priorität)

1. **Gerätetest.** Prüfen, ob die Richtungen stimmen (Pitch/Yaw aus der MediaPipe-Matrix), ob die Schwellwerte passen, wie oft Fehlauslösungen passieren und wie sich Akku und Wärme über 30 Minuten entwickeln.
2. **Swipe in echten Apps prüfen:** TikTok, Instagram, YouTube. Die Koordinaten sind generisch (Bildschirmmitte, Swipe von 72 % nach 28 %) und lassen sich pro App in `access/SwipeAccessibilityService.kt` anpassen.
3. **Doppelblinzeln** bei 15 fps ist grenzwertig. Falls es unzuverlässig ist: fps erhöhen oder auf „Augen lange zu“ bzw. Lächeln umsteigen.
4. **APK verkleinern** (aktuell 60 MB wegen MediaPipe für alle ABIs): `abiFilters` auf `arm64-v8a` beschränken oder ein App Bundle bauen.
5. **Komfort:** Quick-Settings-Kachel zum An- und Ausschalten, Overlay mit Gestenfeedback in der Ziel-App.
6. **Abhängigkeiten aktualisieren.** Bewusst auf einer stabilen Kombination gelassen: AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1, Compose-BOM 2024.12, MediaPipe 0.10.14. Lint meldet neuere Versionen (u. a. AGP 9.x, MediaPipe 1.0.0), ein Upgrade braucht einen eigenen Durchgang.
7. **Release:** Signing-Config, Datenschutzerklärung, Play-Store-Begründung für die Accessibility-Nutzung (Text steht in `accessibility_description`).

## Wichtige Entscheidungen

- **Nur Android.** iOS bietet keine API, um Touches in fremde Apps einzuspeisen, und keine Kamera im Hintergrund. Alternative für iPhone-Nutzer: Apples Schaltersteuerung mit Kopfbewegungen über die Kamera oder Eye Tracking (ab iOS 18). Das eignet sich eher als Anleitung oder TikTok-Content, nicht als App.
- **Zwei Services:** Kamera und Erkennung laufen in einem Foreground-Service (Typ `camera`), die Gesten führt der AccessibilityService aus. Kommuniziert wird über das Singleton `engine/EngineState.kt`, weil beide im selben Prozess laufen.
- **Kalibrierung bestimmt die Vorzeichen.** Die Richtung von „Kopf runter“ in der MediaPipe-Matrix war vorab nicht sicher bekannt, deshalb misst die Kalibrierung sie. Ohne Kalibrierung gilt die Annahme `pitchDownSign = -1`.
- **Erkennungslogik ohne Android-Abhängigkeit** (`gesture/`), damit sie per JVM-Unit-Test prüfbar ist.

## Wo was liegt

Siehe Architekturtabelle in der [README](README.md). Konzept und Marktbewertung: [concept.md](concept.md).
