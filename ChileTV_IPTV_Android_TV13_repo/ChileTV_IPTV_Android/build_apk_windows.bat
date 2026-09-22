@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>nul || (
  echo ERROR: Java no esta disponible. Instala Android Studio y vuelve a ejecutar este archivo.
  pause
  exit /b 1
)

if "%ANDROID_HOME%"=="" (
  if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
)

if "%ANDROID_HOME%"=="" (
  echo ERROR: No se encontro Android SDK. Abre Android Studio una vez e instala Android SDK 35.
  pause
  exit /b 1
)

set "TOOLS=%CD%\.tools"
set "GRADLE_DIR=%TOOLS%\gradle-8.9"
if not exist "%GRADLE_DIR%\bin\gradle.bat" (
  echo Descargando Gradle 8.9...
  if not exist "%TOOLS%" mkdir "%TOOLS%"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile '%TOOLS%\gradle-8.9-bin.zip'"
  if errorlevel 1 goto :error
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Path '%TOOLS%\gradle-8.9-bin.zip' -DestinationPath '%TOOLS%' -Force"
  if errorlevel 1 goto :error
)

call "%GRADLE_DIR%\bin\gradle.bat" assembleDebug
if errorlevel 1 goto :error

echo.
echo APK generado en:
echo %CD%\app\build\outputs\apk\debug\app-debug.apk
pause
exit /b 0

:error
echo.
echo La compilacion fallo. Revisa los mensajes anteriores.
pause
exit /b 1
