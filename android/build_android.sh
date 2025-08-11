#!/bin/bash

# Build script for Torch Android app

echo "Building Torch Android Converter..."

# Check if Android SDK is available
if [ -z "$ANDROID_HOME" ]; then
    echo "Error: ANDROID_HOME environment variable not set"
    echo "Please set ANDROID_HOME to your Android SDK path"
    exit 1
fi

# Check if we're in the right directory
if [ ! -f "settings.gradle" ]; then
    echo "Error: Please run this script from the android/ directory"
    exit 1
fi

# Clean previous builds
echo "Cleaning previous builds..."
./gradlew clean

# Build the app
echo "Building APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "Build successful!"
    echo "APK location: app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "To install on device:"
    echo "adb install app/build/outputs/apk/debug/app-debug.apk"
else
    echo "Build failed!"
    exit 1
fi