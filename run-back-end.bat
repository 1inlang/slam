@echo off
cd /d "%~dp0backend"
echo Compiling and running SLAM Backend...
call mvn clean spring-boot:run
pause
