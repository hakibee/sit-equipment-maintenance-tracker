@echo off
rem Build and run the SIT Equipment Maintenance Tracker.
rem Requires a JDK (17 or newer). Usage: run.bat [--selftest]
setlocal
cd /d "%~dp0"

if exist out rmdir /s /q out
mkdir out
dir /s /b src\*.java > sources.txt
javac -Xlint:all -d out @sources.txt
if errorlevel 1 (
    del sources.txt
    echo Build FAILED.
    exit /b 1
)
del sources.txt

echo Build OK - starting SIT Equipment Maintenance Tracker...
java -cp out com.sit.tracker.Main %*
