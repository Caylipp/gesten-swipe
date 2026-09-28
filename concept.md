# Konzept: Gesten-Swipe

## Idee
Handy-App, mit der man in Apps wie TikTok, Reels oder Shorts swipen kann, ohne die Hand zu benutzen: per Augen, Kopfbewegung, Blinzeln oder Mundgeste.

## Bewertung
| Kriterium | Einschätzung |
|---|---|
| Machbarkeit Android | Gut. Mit einem AccessibilityService lassen sich Swipes systemweit ausführen (`dispatchGesture`), die Frontkamera läuft über einen Foreground-Service. |
| Machbarkeit iOS | Sehr eingeschränkt. Drittanbieter-Apps dürfen keine Touches in fremde Apps einspeisen. Apple hat Head Tracking bereits eingebaut (Bedienungshilfen → Schaltersteuerung und ab iOS 18 Eye Tracking). |
| Aufwand MVP | Mittel (Android, 3–5 Wochen) |
| Konkurrenz | Es gibt einige Android-Apps (z. B. „Hands-free scroll“, Samsung-Kopfgesten), meist mit schlechter UX. |
| Risiken | Akku- und Wärmeentwicklung durch die dauerhaft laufende Kamera, Play-Store-Prüfung bei Accessibility-Nutzung (muss begründet werden), Fehlauslösungen. |
| Monetarisierung | Einmalkauf oder Abo. Zusätzliche Zielgruppe mit Barrierefreiheitsbedarf (echter Nutzen). |

**Fazit:** Technisch spannend und auf Android realistisch. Für iOS nicht sinnvoll. Die UX hängt an sehr robuster Gestenerkennung.

## Umsetzungsideen
- **Erkennung**: MediaPipe Face Landmarker (on-device, schnell). Er liefert Blendshapes wie `eyeBlinkLeft`, `jawOpen` und `mouthSmile` sowie die Kopfpose.
- **Gesten-Mapping** (konfigurierbar):
  - Kopf kurz nicken bzw. hochneigen → nächstes bzw. vorheriges Video
  - Doppelblinzeln → Like
  - Mund öffnen → Pause
- **Robustheit**: pro Nutzer kalibrieren, Schwellwerte mit Hysterese und Cooldown, nur bei erkanntem Gesicht aktiv.
- **Akku**: niedrige Auflösung (z. B. 320×240) und 15 fps, automatische Pause, wenn keine Ziel-App im Vordergrund ist.
- **Tech**: Kotlin (nativ), CameraX, MediaPipe Tasks, AccessibilityService.
- **MVP**: nur „Nicken = Swipe hoch“ in TikTok auf Android, dann Gesten erweitern.
