@echo off
setlocal

cd /d "%~dp0"

echo [1/3] Building release artifacts...
call .\gradlew :app:assembleRelease :app:bundleRelease
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyy-MM-dd\")"') do set "DATE_DIR=%%i"
set "TARGET_DIR=%~dp0releases\%DATE_DIR%"

if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

set "APK_SRC=%~dp0app\build\outputs\apk\release\app-release.apk"
set "AAB_SRC=%~dp0app\build\outputs\bundle\release\app-release.aab"
set "APK_DST=%TARGET_DIR%\LittleClicker.apk"
set "AAB_DST=%TARGET_DIR%\app-release.aab"

if not exist "%APK_SRC%" (
    echo APK not found: %APK_SRC%
    exit /b 1
)

if not exist "%AAB_SRC%" (
    echo AAB not found: %AAB_SRC%
    exit /b 1
)

copy /Y "%APK_SRC%" "%APK_DST%" >nul
copy /Y "%AAB_SRC%" "%AAB_DST%" >nul

echo [2/3] Copied APK to: %APK_DST%
echo [3/3] Copied AAB to: %AAB_DST%
echo Done.

exit /b 0
