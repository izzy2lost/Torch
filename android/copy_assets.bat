@echo off
echo Copying configuration assets to Android project...

set STARSHIP_PATH=C:\Users\izzyn\Downloads\Starship-main\Starship-main
set SPAGHETTI_PATH=C:\Users\izzyn\Downloads\spaghetti-kart-main\spaghetti-kart-main
set ANDROID_ASSETS=app\src\main\assets

echo.
echo === Copying Starship (Star Fox 64) Assets ===
set TARGET_DIR=%ANDROID_ASSETS%\starship

REM Check if Starship directory exists
if not exist "%STARSHIP_PATH%" (
    echo Warning: Starship directory not found at %STARSHIP_PATH%
    echo Skipping Starship assets...
) else (
    REM Create/clean Starship assets directory
    if exist "%TARGET_DIR%" (
        echo Removing existing Starship assets...
        rmdir /s /q "%TARGET_DIR%"
    )
    mkdir "%TARGET_DIR%"

    REM Copy Starship files
    echo Copying Starship config.yml...
    copy "%STARSHIP_PATH%\config.yml" "%TARGET_DIR%\config.yml"

    echo Copying Starship assets folder...
    if exist "%STARSHIP_PATH%\assets" (
        xcopy "%STARSHIP_PATH%\assets" "%TARGET_DIR%\assets" /E /I /H /Y
    )

    echo Copying Starship yamls folder...
    if exist "%STARSHIP_PATH%\yamls" (
        xcopy "%STARSHIP_PATH%\yamls" "%TARGET_DIR%\yamls" /E /I /H /Y
    )

    echo Copying Starship include/assets folder...
    if exist "%STARSHIP_PATH%\include\assets" (
        mkdir "%TARGET_DIR%\include"
        xcopy "%STARSHIP_PATH%\include\assets" "%TARGET_DIR%\include\assets" /E /I /H /Y
    )

    echo Starship assets copied successfully!
)

echo.
echo === Copying Spaghetti Kart (Mario Kart 64) Assets ===
set TARGET_DIR=%ANDROID_ASSETS%\spaghetti

REM Check if Spaghetti Kart directory exists
if not exist "%SPAGHETTI_PATH%" (
    echo Warning: Spaghetti Kart directory not found at %SPAGHETTI_PATH%
    echo Skipping Spaghetti Kart assets...
) else (
    REM Create/clean Spaghetti assets directory
    if exist "%TARGET_DIR%" (
        echo Removing existing Spaghetti assets...
        rmdir /s /q "%TARGET_DIR%"
    )
    mkdir "%TARGET_DIR%"

    REM Copy Spaghetti Kart files
    echo Copying Spaghetti config.yml...
    copy "%SPAGHETTI_PATH%\config.yml" "%TARGET_DIR%\config.yml"

    echo Copying Spaghetti assets folder...
    if exist "%SPAGHETTI_PATH%\assets" (
        xcopy "%SPAGHETTI_PATH%\assets" "%TARGET_DIR%\assets" /E /I /H /Y
    )

    echo Copying Spaghetti yamls folder...
    if exist "%SPAGHETTI_PATH%\yamls" (
        xcopy "%SPAGHETTI_PATH%\yamls" "%TARGET_DIR%\yamls" /E /I /H /Y
    )

    echo Copying Spaghetti include folder...
    if exist "%SPAGHETTI_PATH%\include" (
        xcopy "%SPAGHETTI_PATH%\include" "%TARGET_DIR%\include" /E /I /H /Y
    )

    echo Spaghetti Kart assets copied successfully!
)

echo.
echo === Copy Summary ===
echo Assets copied to: %ANDROID_ASSETS%
echo.
echo Directory structure:
dir /s "%ANDROID_ASSETS%"

pause