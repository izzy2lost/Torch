@echo off
echo Copying Starship assets to Android project...

set STARSHIP_PATH=C:\Users\izzyn\Downloads\Starship-main\Starship-main
set ANDROID_ASSETS=app\src\main\assets\starship

REM Check if Starship directory exists
if not exist "%STARSHIP_PATH%" (
    echo Error: Starship directory not found at %STARSHIP_PATH%
    pause
    exit /b 1
)

REM Create Android assets directory
if exist "%ANDROID_ASSETS%" (
    echo Removing existing assets...
    rmdir /s /q "%ANDROID_ASSETS%"
)
mkdir "%ANDROID_ASSETS%"

REM Copy config.yml
echo Copying config.yml...
copy "%STARSHIP_PATH%\config.yml" "%ANDROID_ASSETS%\config.yml"

REM Copy assets folder
echo Copying assets folder...
if exist "%STARSHIP_PATH%\assets" (
    xcopy "%STARSHIP_PATH%\assets" "%ANDROID_ASSETS%\assets" /E /I /H /Y
)

REM Copy yamls folder
echo Copying yamls folder...
if exist "%STARSHIP_PATH%\yamls" (
    xcopy "%STARSHIP_PATH%\yamls" "%ANDROID_ASSETS%\yamls" /E /I /H /Y
)

REM Copy include/assets folder
echo Copying include/assets folder...
if exist "%STARSHIP_PATH%\include\assets" (
    mkdir "%ANDROID_ASSETS%\include"
    xcopy "%STARSHIP_PATH%\include\assets" "%ANDROID_ASSETS%\include\assets" /E /I /H /Y
)

REM Copy any other important files
echo Copying other important files...
if exist "%STARSHIP_PATH%\*.yml" (
    copy "%STARSHIP_PATH%\*.yml" "%ANDROID_ASSETS%\"
)

echo.
echo Copy completed! Files copied to: %ANDROID_ASSETS%
echo.
echo Listing copied files:
dir /s "%ANDROID_ASSETS%"

pause