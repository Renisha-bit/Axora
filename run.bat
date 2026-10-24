@echo off
setlocal
if not defined JAVA_HOME (
    echo Error: Set JAVA_HOME to a JDK 25 installation.
    exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Error: JAVA_HOME does not point to a valid JDK: %JAVA_HOME%
    exit /b 1
)
set "PATH=C:\tools\apache-maven-3.9.6\bin;%JAVA_HOME%\bin;%PATH%"
echo Starting Axora...
call mvn javafx:run
if errorlevel 1 exit /b %errorlevel%
pause
