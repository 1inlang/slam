@echo off
cd /d "%~dp0"
echo Starting SLAM Backend...
mvn spring-boot:run
pause
