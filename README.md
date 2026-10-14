# GPS Map Camera 📷📍

A modern, native Android camera application built with **Jetpack Compose**, **CameraX**, and **Kotlin** that captures full-resolution photos with a live date/time, GPS coordinates, accuracy, altitude, and reverse-geocoded address stamped directly onto the saved image.

---

## ✨ Features

- **High-Resolution CameraX Viewfinder**:
  - Live preview with auto-focus and tap-to-focus metering.
  - Smooth pinch-to-zoom support (1x to 8x).
  - Front and rear camera switching.
  - Flash modes: Auto, On, and Off.
  - Orientation-aware capture preserving natural image orientation.
- **Precision Location Stamping**:
  - Fresh high-accuracy GPS fix acquired before capture.
  - Live GPS pill indicator showing lock status and accuracy (`±4m Locked`, `Acquiring...`, `Offline`).
  - Never mislabels stale location data as fresh.
  - Reverse geocoding resolves readable street, locality, city, and country addresses (with graceful offline fallback).
- **Customizable Watermark Engine**:
  - Scalable vector rendering adapted to any camera resolution.
  - **Styles**: Frosted Glass Badge, High-Contrast Dark Card, and Clean Drop-Shadow Outline.
  - **Positions**: Bottom-Left, Bottom-Right, Top-Left, Top-Right.
  - **Toggles**: Toggle Date/Time, Coordinates, Address, Accuracy, Altitude, or custom project title.
  - **Coordinate Formats**: Decimal Degrees (`37.774900° N, 122.419400° W`) and Degrees-Minutes-Seconds (DMS).
- **EXIF Metadata Injection**:
  - Writes standard GPS tags (`TAG_GPS_LATITUDE`, `TAG_GPS_LONGITUDE`, `TAG_GPS_ALTITUDE`) and timestamps into the saved JPEG.
- **In-App Gallery & Sharing**:
  - Scoped-storage gallery browsing photos taken with the app.
  - Full-resolution photo preview with detailed metadata inspector sheet.
  - Native Android system share sheet.
- **100% On-Device Privacy**:
  - Zero cloud servers, zero analytics, zero advertising SDKs.
  - Foreground location access only—never tracks background location.
  - Photos stored exclusively on the local device.

---

## 🛠️ Tech Stack & Requirements

- **Language**: Kotlin 2.0.0
- **UI Framework**: Jetpack Compose (Material 3)
- **Camera**: AndroidX CameraX 1.3.4
- **Location**: Google Play Services Location 21.3.0 & Android LocationManager
- **Image Loading**: Coil 2.7.0
- **Build System**: Gradle 8.8 / AGP 8.5.2
- **Java**: OpenJDK 17
- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)

---

## 🚀 Building & Testing Locally

### Prerequisites
1. OpenJDK 17 installed and `JAVA_HOME` configured.
2. Android SDK Command-line Tools and Platform 34/35 installed with `ANDROID_HOME` set.

### Build Debug APK
```bash
# On Linux/macOS:
export JAVA_HOME="/path/to/jdk17"
export ANDROID_HOME="/path/to/android-sdk"
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## 📲 Installing the APK

1. Transfer `app-debug.apk` to your Android device via USB, Google Drive, or direct download.
2. Open the file on your device.
3. If prompted, allow **Install from unknown sources** for your file manager or browser.
4. Launch **GPS Map Camera** and grant Camera and Location permissions when prompted.

---

## 🔒 Permissions & Privacy Model

GPS Map Camera requests only the minimum essential runtime permissions:

| Permission | Purpose |
| :--- | :--- |
| `android.permission.CAMERA` | View live camera preview and capture photos with CameraX. |
| `android.permission.ACCESS_FINE_LOCATION` | Obtain precise GPS coordinates and altitude for the photo stamp. |
| `android.permission.ACCESS_COARSE_LOCATION` | Fallback approximate network location when GPS is unavailable. |

> **Privacy Guarantee**:
> - **No Background Location**: Location is only accessed in the foreground while the camera is active.
> - **No Third-Party Trackers**: No analytics or telemetry libraries are included.
> - **Modern Scoped Storage**: Images are saved directly into `Pictures/GPS Map Camera` via MediaStore. No broad filesystem access is requested.

---

## 🤖 GitHub Actions CI/CD & Releases

The repository includes an automated workflow at `.github/workflows/android.yml`:
1. **On Every Push / PR**: Runs `./gradlew testDebugUnitTest` and compiles `./gradlew assembleDebug`, uploading the APK as a GitHub Actions workflow artifact.
2. **On Release Tags (`v*`)**: Whenever a tag like `v1.0.0` is pushed, the workflow automatically generates a **GitHub Release** and attaches the compiled APK as a downloadable asset.

---

## 📄 License
This project is open-source under the Apache 2.0 License.
