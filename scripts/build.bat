@echo off
setlocal enabledelayedexpansion

set "PROJECT_DIR=%~dp0.."
set "VARIANT=%~1"
if "%VARIANT%"=="" set "VARIANT=debug"

if /I "%VARIANT%"=="debug" (
    set "GRADLE_TASK=assembleDebug"
    set "APK_PATH=app\build\outputs\apk\debug\app-debug.apk"
    set "OUTPUT_SUFFIX=debug"
) else if /I "%VARIANT%"=="release" (
    set "GRADLE_TASK=assembleRelease"
    set "APK_PATH=app\build\outputs\apk\release\app-release-unsigned.apk"
    set "OUTPUT_SUFFIX=release-unsigned"
) else (
    echo Usage: %~nx0 [debug^|release] 1>&2
    exit /b 2
)

pushd "%PROJECT_DIR%"
call gradlew.bat --no-daemon lintDebug %GRADLE_TASK%
if errorlevel 1 goto :error

for /f "tokens=2 delims='" %%V in ('findstr /R /C:"^[ ]*versionName '" app\build.gradle') do set "VERSION_NAME=%%V"
if not defined VERSION_NAME (
    echo Unable to read versionName from app\build.gradle 1>&2
    goto :error
)

if not exist dist mkdir dist
set "OUTPUT_PATH=dist\HansPolicy-v%VERSION_NAME%-%OUTPUT_SUFFIX%.apk"
copy /Y "%APK_PATH%" "%OUTPUT_PATH%" >nul
if errorlevel 1 goto :error

echo Built %OUTPUT_PATH%
certutil -hashfile "%OUTPUT_PATH%" SHA256
if /I "%VARIANT%"=="release" echo Release output is unsigned. Sign it before installation or distribution.
popd
exit /b 0

:error
popd
exit /b 1
