@echo off
cd /d "%~dp0"
echo =========================================================
echo   Iniciando Sistema Web "Edificio La Cruceta"
echo   URL: http://localhost:8082
echo =========================================================
call mvnw.cmd spring-boot:run
pause
