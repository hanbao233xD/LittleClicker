@echo off
setlocal

cd /d "%~dp0"

echo [1/4] Building release artifacts...
call .\gradlew :app:assembleRelease :app:bundleRelease
if errorlevel 1 (
    echo Build failed.
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-Date -Format yyyy-MM-dd"') do set "DATE_DIR=%%i"
if "%DATE_DIR%"=="" (
    echo Failed to resolve date directory.
    exit /b 1
)
set "TARGET_DIR=%~dp0releases\%DATE_DIR%"

if not exist "%TARGET_DIR%" mkdir "%TARGET_DIR%"

set "APK_SRC=%~dp0app\build\outputs\apk\release\app-release.apk"
set "AAB_SRC=%~dp0app\build\outputs\bundle\release\app-release.aab"
set "APK_DST=%TARGET_DIR%\LittleClicker.apk"
set "AAB_DST=%TARGET_DIR%\app-release.aab"
set "HOMEPAGE_APK_DST=F:\Documents\GitHub\HomePage\littleclicker\apk\LittleClicker.apk"

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

for %%D in ("%HOMEPAGE_APK_DST%") do (
    if not exist "%%~dpD" mkdir "%%~dpD"
)
copy /Y "%APK_SRC%" "%HOMEPAGE_APK_DST%" >nul

echo [2/4] Copied APK to: %APK_DST%
echo [3/4] Copied AAB to: %AAB_DST%
echo [4/4] Copied APK to: %HOMEPAGE_APK_DST%
echo Done.

exit /b 0
