# Gesture Swipe

[Deutsche Version](README.de.md) · Status: frozen, see [STATUS.md](STATUS.md)

An Android app that lets you scroll short-video feeds (TikTok, Reels, Shorts, …) **hands-free with head, eye and mouth gestures**, using the front camera and on-device face tracking.

| Gesture | Default action |
|---|---|
| Quick nod | next video |
| Head up | previous video |
| Double blink | like |
| Open mouth | pause / play |
| Head left / right | configurable |

## Architecture

```
CameraX (front camera, 320×240, ~15 fps)
  → FaceTracker (MediaPipe Face Landmarker: head pose + blendshapes)
  → GestureDetector (state machine: baseline, hysteresis, cooldown)
  → gesture → user mapping → SwipeAccessibilityService.dispatchGesture()
```

- `GestureDetector.kt` is pure Kotlin without Android dependencies and is unit-tested, as are calibration and head-pose math.
- **Calibration** (6 steps, ~25 s) learns direction and strength of each user's head movements and blink/mouth thresholds.
- **Robust detection**: drifting baseline posture, hysteresis (start above threshold, end below half), only short movements with return (60–1500 ms) count, cooldown against the rebound of a nod, blinks ignored during head motion.
- **Battery-aware**: the camera only runs at low resolution while a target app is in the foreground and the screen is on.
- Jetpack Compose UI with settings, calibration, live debug view and a fake video feed for testing.

## Build

JDK 17, Android SDK 35.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # APK in app/build/outputs/apk/debug/
```
