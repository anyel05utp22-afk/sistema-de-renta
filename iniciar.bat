@echo off
cd /d "%~dp0"
echo =========================================================
echo   Iniciando Sistema Web "Edificio La Cruceta"
echo   URL: http://localhost:8080
echo =========================================================
call mvnw.cmd spring-boot:run
pause
