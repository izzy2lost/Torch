# Torch Android Converter

A simple Android app that converts N64 ROM files (.z64, .n64, .v64) to O2R format for use with Starship and Spaghetti Cart ports.

## Features

- Simple, user-friendly interface
- File picker for ROM selection
- Converts ROMs to O2R format
- Saves output to Downloads/torch_output/
- Progress indication during conversion

## Building

### Prerequisites

- Android Studio Arctic Fox or later
- Android NDK 25.1.8937393 or later
- CMake 3.22.1 or later
- Minimum SDK: API 24 (Android 7.0)
- Target SDK: API 34 (Android 14)

### Build Steps

1. Open the `android` folder in Android Studio
2. Let Gradle sync the project
3. Build and run on device or emulator

### Native Dependencies

The app builds the original Torch C++ codebase as a native library using:
- CMake for build configuration
- JNI for Java/C++ bridge
- All original Torch dependencies (yaml-cpp, binarytools, n64graphics, etc.)

## Usage

1. Launch the app
2. Tap "Select ROM File" and choose your N64 ROM (.z64, .n64, .v64)
3. Tap "Select Starship/Spaghetti Config Folder" and choose the root directory of either:
   - The Starship repository (for SM64)
   - The Spaghetti Cart repository (for other games)
4. Tap "Convert to O2R" to start the conversion
5. The O2R file will be saved to your Downloads/torch_output/ folder

## Required Files

The app requires the config.yml and asset metadata files from the respective game repositories:

- **For Super Mario 64**: Use the Starship repository
- **For other games**: Use the Spaghetti Cart repository

These repositories contain the necessary YAML files and asset definitions that Torch needs to properly extract and convert the ROM data.

## Supported Games

The app supports the same games as the original Torch:
- Super Mario 64
- Mario Kart 64  
- Star Fox 64
- F-Zero X
- Mario Artist series

## File Structure

```
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/torch/converter/
│   │   │   └── MainActivity.java          # Main Android activity
│   │   ├── cpp/
│   │   │   ├── CMakeLists.txt            # Native build config
│   │   │   └── torch_jni.cpp             # JNI wrapper
│   │   ├── res/                          # Android resources
│   │   └── assets/
│   │       └── config.yml                # Torch config
│   └── build.gradle                      # App build config
├── build.gradle                          # Project build config
└── settings.gradle                       # Project settings
```

## Notes

- The app creates a simplified interface around the core Torch functionality
- ROM files are temporarily copied to internal storage for processing
- Output files are saved to external storage (Downloads folder)
- The native library includes all necessary Torch dependencies