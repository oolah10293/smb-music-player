@echo off
setlocal
set "JAR=gradle\wrapper\gradle-wrapper.jar"
set "URL=https://raw.githubusercontent.com/gradle/gradle/v9.6.0/gradle/wrapper/gradle-wrapper.jar"
set "EXPECTED=497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7"

if exist "%JAR%" goto verify

echo Downloading official Gradle 9.6 wrapper JAR...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -Uri '%URL%' -OutFile '%JAR%'"
if errorlevel 1 goto fail

:verify
for /f %%H in ('powershell -NoProfile -Command "(Get-FileHash -Algorithm SHA256 '%JAR%').Hash.ToLower()"') do set "ACTUAL=%%H"
if /I not "%ACTUAL%"=="%EXPECTED%" (
  echo Wrapper checksum FAILED.
  echo Expected: %EXPECTED%
  echo Actual:   %ACTUAL%
  del "%JAR%" 2>nul
  exit /b 1
)

echo Gradle wrapper is ready and checksum verified.
pause
exit /b 0

:fail
echo Download failed.
pause
exit /b 1
