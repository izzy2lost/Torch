@echo off
echo Setting up Android project for Torch Converter...
echo.

REM Check if Android Studio is available
set STUDIO_PATH="C:\Program Files\Android\Android Studio\bin\studio64.exe"
if not exist %STUDIO_PATH% (
    echo Android Studio not found at expected location
    echo Please check if Android Studio is installed
    echo.
    echo Alternative: Open Android Studio manually and import this project
    echo Project location: %CD%
    pause
    exit /b 1
)

echo Found Android Studio!
echo.

REM Check if ANDROID_HOME is set
if "%ANDROID_HOME%"=="" (
    echo Warning: ANDROID_HOME environment variable not set
    echo This may cause build issues
    echo Please set ANDROID_HOME to your Android SDK path
    echo.
)

echo Opening project in Android Studio...
echo Project path: %CD%
echo.

REM Open Android Studio with this project
start "" %STUDIO_PATH% "%CD%"

echo.
echo Instructions:
echo 1. Android Studio should open with this project
echo 2. Let Gradle sync complete (may take a few minutes)
echo 3. If prompted, install any missing SDK components
echo 4. Build the project using Build ^> Make Project
echo 5. Generate APK using Build ^> Build Bundle(s) / APK(s) ^> Build APK(s)
echo.
pause