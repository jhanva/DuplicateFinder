# DuplicateFinder

Android application to detect and remove duplicate images from your device gallery.

## Features

- **Exact Duplicate Detection** - Finds identical images using MD5 hash comparison
- **Similar Image Detection** - Finds visually similar images using perceptual hashing (pHash)
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

### Trash System

- Deleted images are moved to app's internal `.trash/` folder
- Metadata stored in Room database (original path, deletion date, expiry)
- WorkManager runs daily to clean expired items (30 days default)
- Users can restore or permanently delete from trash

## Requirements

- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)
- **Recommended**: Samsung S23 Ultra or similar high-end device

## Permissions

```xml
<uses-permission android:name="android.permission.READ_MEDIA_IMAGES" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

**Note**: No INTERNET permission - the app is 100% offline.

## Build

### Debug APK

```bash
# Clone repository
git clone https://github.com/jhanva/DuplicateFinder.git
cd DuplicateFinder

# Build debug APK
./gradlew assembleDebug

# APK location
# app/build/outputs/apk/debug/app-debug.apk
```

### Install on Device

```bash
# Connect device with USB debugging enabled
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Run Tests

```bash
./gradlew test
```

## Configuration

Settings available in the app:

| Setting | Default | Description |
|---------|---------|-------------|
| Similarity Threshold | 90% | Minimum similarity to consider as duplicate |
| Auto-delete Days | 30 | Days before trash items are permanently deleted |
| Dark Mode | System | Theme preference |

## Privacy & Security

- ✅ **No Internet Access** - App cannot connect to any server
- ✅ **No Analytics** - No tracking or telemetry
- ✅ **No Ads** - No advertising SDKs
- ✅ **Local Processing** - All hashing done on-device
- ✅ **Open Source** - Full code transparency

## License

MIT License

## Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## Author

**jhanva** - [GitHub](https://github.com/jhanva)
