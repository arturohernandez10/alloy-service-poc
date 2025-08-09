@echo off
setlocal EnableExtensions EnableDelayedExpansion

rem Generate a timestamp via PowerShell (robust across locales)
for /f %%i in ('powershell -NoProfile -Command "(Get-Date).ToString(\"yyyyMMdd-HHmmss\")"') do set DATESTAMP=%%i

set LOGDIR=build-logs
if not exist "%LOGDIR%" mkdir "%LOGDIR%"
set LOGFILE=%LOGDIR%\build-%DATESTAMP%.log

echo ==== Alloy Service POC Build [%DATESTAMP%] ==== > "%LOGFILE%"
echo CWD: %CD% >> "%LOGFILE%"
echo JAVA_HOME: %JAVA_HOME% >> "%LOGFILE%"
echo PATH: %PATH% >> "%LOGFILE%"
echo. >> "%LOGFILE%"
echo == java -version == >> "%LOGFILE%"
java -version >> "%LOGFILE%" 2>&1
echo. >> "%LOGFILE%"
echo == mvn --version == >> "%LOGFILE%"
mvn --version >> "%LOGFILE%" 2>&1
echo. >> "%LOGFILE%"

where mvn >nul 2>&1
if errorlevel 1 (
  echo ERROR: Maven (mvn) not found on PATH >> "%LOGFILE%"
  echo Build complete. Log saved to "%LOGFILE%".
  exit /b 9009
)

echo == mvn -B -DskipTests clean package == >> "%LOGFILE%"
echo Starting Maven build... >> "%LOGFILE%"
call mvn -B -DskipTests clean package >> "%LOGFILE%" 2>&1
set BUILD_EXIT_CODE=%ERRORLEVEL%
echo Maven build finished with exit code !BUILD_EXIT_CODE! >> "%LOGFILE%"
echo. >> "%LOGFILE%"

rem Try to find the shaded JAR produced by the shade plugin
set JAR=
for /f "delims=" %%F in ('dir /b /a:-d "target\*-shaded.jar" 2^>nul') do (
  set JAR=target\%%F
  goto :after_find_jar
)
:after_find_jar

if defined JAR (
  echo Shaded JAR: !JAR! >> "%LOGFILE%"
) else (
  echo Shaded JAR not found in target\ >> "%LOGFILE%"
)

echo Build complete. Log saved to "%LOGFILE%".
exit /b %BUILD_EXIT_CODE%
