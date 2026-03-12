# DuplicateFinder

Android application to detect and remove duplicate images from your device gallery.

## Features

- **Exact Duplicate Detection** - Finds identical images using MD5 hash comparison
- **Similar Image Detection** - Finds visually similar images using perceptual hashing (pHash)
- **Quality Review** - Scores image quality (sharpness, detail density, blockiness) and lets you batch-send low-quality photos to trash
- **Smart Grouping** - Groups duplicates with similarity scores
- **Trash System** - Deleted images go to trash with 30-day recovery period
- **Filters** - Filter by folder, date, size, and match type
- **Space Savings** - Shows potential storage savings for each duplicate group
- **Dark Mode** - Full dark theme support
- **Privacy First** - 100% offline, no data leaves your device

## Screenshots

| Home | Scan | Duplicates | Trash |
|------|------|------------|-------|
| Dashboard with stats | Scanning progress | Duplicate groups | Recoverable items |

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 1.9 |
| UI | Jetpack Compose + Material 3 |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt |
| Database | Room |
| Preferences | DataStore |
| Images | Coil |
| Async | Coroutines + Flow |
| Background | WorkManager |

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    PRESENTATION LAYER                        │
│         Screens (Compose) → ViewModels → UI States          │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      DOMAIN LAYER                            │
│            Use Cases → Models → Repository Interfaces        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       DATA LAYER                             │
│          Repositories → Room DB → MediaStore                 │
└─────────────────────────────────────────────────────────────┘
```

## Project Structure

```
app/src/main/java/com/duplicatefinder/
├── di/                     # Hilt modules
├── data/
│   ├── local/
│   │   ├── db/             # Room database, DAOs, entities
│   │   └── datastore/      # User preferences
│   ├── media/              # MediaStore data source
│   └── repository/         # Repository implementations
├── domain/
│   ├── model/              # Domain models
│   ├── repository/         # Repository interfaces
│   └── usecase/            # Business logic
├── presentation/
│   ├── navigation/         # Navigation graph
│   ├── screens/            # UI screens with ViewModels
│   ├── components/         # Reusable UI components
│   └── theme/              # Material 3 theming
├── util/
│   ├── hash/               # MD5, pHash, dHash calculators
│   ├── image/              # Image processing utilities
│   └── extension/          # Kotlin extensions
└── worker/                 # WorkManager for trash cleanup
```

## How It Works

### Duplicate Detection Algorithm

1. **Scan** - Read all images from MediaStore API
2. **Group by Size** - Images with same size are potential exact duplicates
3. **MD5 Hash** - Calculate MD5 for exact byte-by-byte comparison
4. **Perceptual Hash** - Calculate pHash for visual similarity:
   - Resize to 32x32
   - Convert to grayscale
   - Apply DCT (Discrete Cosine Transform)
   - Generate 64-bit binary hash
5. **Compare** - Use Hamming distance to find similar images (>90% threshold)
6. **Group** - Create duplicate groups sorted by potential space savings

### Quality Review Flow

1. **Load selected folders** from Home
2. **Analyze thumbnails** to extract sharpness, detail density, and blockiness
3. **Score quality** from 0 to 100 using weighted metrics
4. **Cache quality metrics** in Room to avoid recalculating unchanged images
5. **Review and batch apply** decisions (Keep / Mark for Trash)

### Trash System

- Deleted images are moved to app's internal `.trash/` folder
- Metadata stored in Room database (original path, deletion date, expiry)
- WorkManager runs daily to clean expired items (30 days default)
- Users can restore or permanently delete from trash

## Requirements

### App Requirements

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Device Baseline (Validated)**: Samsung Galaxy S23 Ultra
- **Intended Devices**: S23 Ultra-class devices and newer (or equivalent/high-end hardware)
- **Performance Note**: Older devices may run, but the app is optimized and documented for S23 Ultra and above

### Development Requirements

- **JDK**: 17
- **Gradle Wrapper**: Included (`gradlew`, `gradlew.bat`)
- **Android SDK packages**:
  - `platform-tools`
  - `platforms;android-34`
  - `build-tools;34.0.0`
- **Optional**: Android Studio (recommended for emulator + debugging)

## Permissions

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

**Note**: No INTERNET permission - the app is 100% offline.

## Setup

### 1) Clone

```bash
git clone https://github.com/jhanva/DuplicateFinder.git
cd DuplicateFinder
```

### 2) Configure Android SDK Path

Create a `local.properties` file in project root:

```properties
sdk.dir=C:\\Users\\YOUR_USER\\AppData\\Local\\Android\\Sdk
```

If you use a custom/local SDK folder, point `sdk.dir` to that location.

### 3) Verify Tooling

Windows:

```powershell
.\gradlew.bat --version
```

macOS/Linux:

```bash
./gradlew --version
```

## Build

### Build Debug APK

Windows:

```powershell
.\gradlew.bat assembleDebug
```

macOS/Linux:

```bash
./gradlew assembleDebug
```

APK output:

`app/build/outputs/apk/debug/app-debug.apk`

Debug package name:

`com.duplicatefinder.debug`

### Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you install by opening the APK directly on device, debug uses a separate package
(`com.duplicatefinder.debug`) so it can coexist with production/release installs.

## Testing

### Unit Tests (JVM)

Windows:

```powershell
.\gradlew.bat testDebugUnitTest
```

macOS/Linux:

```bash
./gradlew testDebugUnitTest
```

HTML report:

`app/build/reports/tests/testDebugUnitTest/index.html`

### Full Unit Test Suite

Windows:

```powershell
.\gradlew.bat test
```

macOS/Linux:

```bash
./gradlew test
```

### Instrumentation Tests (Device/Emulator Required)

```bash
adb devices
./gradlew connectedDebugAndroidTest
```

Windows equivalent:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

## Configuration

Settings available in the app:

| Setting | Default | Description |
|---------|---------|-------------|
| Similarity Threshold | 90% | Minimum similarity to consider as duplicate |
| Auto-delete Days | 30 | Days before trash items are permanently deleted |
| Dark Mode | System | Theme preference |

## Privacy & Security

- **No Internet Access** - App cannot connect to any server
- **No Analytics** - No tracking or telemetry
- **No Ads** - No advertising SDKs
- **Local Processing** - All hashing done on-device
- **Open Source** - Full code transparency

## License

MIT License

## Contributing

This repository uses a direct-to-`main` workflow.

- Keep `main` releasable
- Use Conventional Commits for commits and optional PR titles
- Run the relevant checks before pushing
- Bump `versionName` and `versionCode` only for release/versioning work

The full repository workflow, versioning rules, and merge policy live in `AGENTS.md`.

## Author

**jhanva** - [GitHub](https://github.com/jhanva)
