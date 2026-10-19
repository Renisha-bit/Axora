@echo off
setlocal
set JAVA_HOME=C:\jdk-27
set PATH=C:\tools\apache-maven-3.9.6\bin;C:\jdk-27\bin;%PATH%
echo Starting Axora...
mvn javafx:run
pause
