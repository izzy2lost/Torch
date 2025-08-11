@echo off

echo Building Torch Android Converter...

REM Check if Android SDK is available
if "%ANDROID_HOME%"=="" (
    echo Error: ANDROID_HOME environment variable not set
    echo Please set ANDROID_HOME to your Android SDK path
    exit /b 1
)

REM Check if we're in the right directory
if not exist "settings.gradle" (
    echo Error: Please run this script from the android\ directory
    exit /b 1
)

REM Clean previous builds
echo Cleaning previous builds...
call gradlew.bat clean

REM Build the app
echo Building APK...
call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo Build successful!
    echo APK location: app\build\outputs\apk\debug\app-debug.apk
    echo.
    echo To install on device:
    echo adb install app\build\outputs\apk\debug\app-debug.apk
) else (
    echo Build failed!
    exit /b 1
)