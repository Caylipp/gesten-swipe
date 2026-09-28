# Gesten-Swipe

Android-App: In TikTok, Instagram Reels, YouTube Shorts & Co. per Kopf-, Augen- oder Mundgeste swipen, ganz ohne Hände.
Konzept und Bewertung: [concept.md](concept.md).

## Was die App kann

| Geste | Standard-Aktion |
|---|---|
| Kurz nicken (Kopf runter und zurück) | Nächstes Video (Swipe hoch) |
| Kopf kurz hoch | Vorheriges Video (Swipe runter) |
| Doppelt blinzeln | Like (Doppeltipp) |
| Mund öffnen | Pause / Weiter (Tipp) |
| Kopf links / rechts | frei belegbar (Standard: aus) |

Unter „Einstellungen“ lässt sich jede Geste frei zuordnen. Zur Auswahl stehen Nächstes, Vorheriges, Like, Pause, Zurück und „Keine Aktion“. Dort stellst du auch Ziel-Apps, Empfindlichkeit, Cooldown und Bildrate ein.

- **Kalibrierung** (6 Schritte, etwa 25 Sekunden): Sie ermittelt pro Nutzer die Richtung und Stärke der Kopfbewegungen sowie die Schwellwerte für Blinzeln und Mund.
- **Testbereich**: ein Fake-Videofeed im TikTok-Stil, mit dem man die Gesten ohne echte App ausprobieren kann.
- **Live-Anzeige**: Kamera-Status, fps, Gesicht erkannt, Auslenkung von Kopf, Augen und Mund relativ zur Schwelle sowie die zuletzt erkannte Geste.
- **Akku**: Die Kamera läuft nur mit etwa 320×240 und 15 fps, und nur solange eine Ziel-App im Vordergrund und der Bildschirm an ist. Über die Benachrichtigung lässt sich die Erkennung pausieren oder beenden.

## Architektur

```
CameraX (Frontkamera, 320×240, ~15 fps)
  → FaceTracker (MediaPipe Face Landmarker: Kopfpose + Blendshapes)
  → GestureDetector (Zustandsautomat: Grundhaltung, Hysterese, Cooldown)
  → Gesture → Zuordnung aus den Einstellungen → SwipeAction
  → SwipeAccessibilityService.dispatchGesture() in der Vordergrund-App
```

| Datei | Aufgabe |
|---|---|
| `gesture/GestureDetector.kt` | Reine Kotlin-Logik ohne Android-Abhängigkeit, unit-getestet |
| `gesture/Calibration.kt` | Berechnet Schwellwerte und Vorzeichen aus den Kalibrier-Messungen |
| `gesture/HeadPose.kt` | Rechnet die MediaPipe-Transformationsmatrix in Pitch/Yaw/Roll um |
| `engine/CameraGestureService.kt` | Foreground-Service (Typ `camera`) mit CameraX und MediaPipe; bindet die Kamera nur in Ziel-Apps |
| `engine/FaceTracker.kt` | MediaPipe im `LIVE_STREAM`-Modus |
| `access/SwipeAccessibilityService.kt` | Führt Swipes und Tipps aus und meldet die Vordergrund-App |
| `ui/*` | Compose-Oberfläche: Start, Einstellungen, Kalibrierung, Testbereich |

### Robustheit der Erkennung

- Kopfgesten werden relativ zu einer **Grundhaltung** gemessen, die im Ruhezustand langsam mitwandert. Wer den Kopf länger als 3 s ausgelenkt hält, bekommt diese Haltung als neue Grundhaltung.
- **Hysterese**: Eine Geste beginnt oberhalb der Schwelle und endet erst unterhalb der halben Schwelle.
- Es zählt nur eine **kurze** Bewegung (60–1500 ms) mit Rückkehr. Dauerhaftes Runterschauen löst nichts aus.
- Ein **Cooldown** (Standard 900 ms) verhindert, dass das Zurückschwingen nach einem Nicken als „Kopf hoch“ gewertet wird.
- Blinzeln wird während Kopfbewegungen ignoriert, weil Runterschauen die Blink-Werte verfälscht. Einzelnes Blinzeln löst nichts aus, und Augen, die länger als 500 ms geschlossen sind, zählen nicht als Blinzeln.
- Ohne erkanntes Gesicht passiert nichts. Nach 2 s ohne Gesicht wird die Grundhaltung neu bestimmt.

## Bauen und Installieren

Voraussetzungen: JDK 17 und das Android SDK (Platform 35). Den SDK-Pfad trägst du in `local.properties` ein (`sdk.dir=…`).

```bash
./gradlew testDebugUnitTest     # Unit-Tests (Detektor, Kalibrierung, Kopfpose)
./gradlew assembleDebug         # APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Das MediaPipe-Modell liegt in `app/src/main/assets/face_landmarker.task` (float16, v1). Bei Bedarf lässt es sich so neu laden:

```bash
curl -L -o app/src/main/assets/face_landmarker.task \
  https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task
```

## Einrichtung auf dem Handy

1. App öffnen und Kamera sowie Benachrichtigungen erlauben.
2. Unter „Bedienungshilfe öffnen“ den Dienst **Gesten-Swipe** aktivieren.
   Ist der Schalter ausgegraut (Android 13+, bei Installation per APK): *App-Info → ⋮ → „Eingeschränkte Einstellungen zulassen“*, danach erneut versuchen.
3. **Kalibrieren**.
4. **Gestensteuerung starten**, dann im Testbereich ausprobieren und anschließend TikTok öffnen.

## Bekannte Grenzen und nächste Schritte

- Nur Android. iOS erlaubt keine Touch-Injection in fremde Apps (siehe Konzept).
- Doppelblinzeln braucht mindestens 15 fps. Bei sehr schnellem Blinzeln kann ein Lidschlag zwischen zwei Frames fallen.
- Die Swipe- und Tipp-Koordinaten sind generisch (Bildschirmmitte). Falls eine App anders reagiert, lassen sie sich in `SwipeAccessibilityService` pro App anpassen.
- Ideen: Overlay mit Kamera-Vorschau und Gestenfeedback, Quick-Settings-Kachel zum An- und Ausschalten, Lächeln oder Augenbrauen als weitere Gesten, Statistik zu Fehlauslösungen.
- Play Store: Die Accessibility-Nutzung muss begründet werden (Barrierefreiheit, freihändige Bedienung). Die Begründung steht bereits in `accessibility_description`.
