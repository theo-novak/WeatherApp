@echo off
rem Simple Weather: build the APK and install it on the connected phone.
rem Run from anywhere; it always works on its own folder.
cd /d "%~dp0"
echo === Building APK (first build downloads libraries; later ones are fast) ===
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo BUILD FAILED - scroll up for the error.
    exit /b 1
)
echo.
echo === Installing on phone ===
adb install -r app\build\outputs\apk\debug\app-debug.apk
if errorlevel 1 (
    echo INSTALL FAILED - is the phone plugged in and USB debugging on?
    exit /b 1
)
echo.
echo Done. Look for "Simple Weather" on your phone.