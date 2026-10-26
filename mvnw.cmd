@echo off
REM ---------------------------------------------------------------
REM  Maven Wrapper simplificado: descarga Maven la primera vez
REM  (no necesitas tener Maven instalado, solo Java 17+).
REM ---------------------------------------------------------------
setlocal
set "MVN_VER=3.9.9"
set "WRAP_DIR=%USERPROFILE%\.m2\wrapper"
set "MVN_HOME=%WRAP_DIR%\apache-maven-%MVN_VER%"

if not exist "%MVN_HOME%\bin\mvn.cmd" (
  echo Descargando Maven %MVN_VER% por unica vez...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; New-Item -ItemType Directory -Force -Path '%WRAP_DIR%' | Out-Null; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MVN_VER%/binaries/apache-maven-%MVN_VER%-bin.zip' -OutFile '%WRAP_DIR%\maven.zip'; Expand-Archive -Force '%WRAP_DIR%\maven.zip' '%WRAP_DIR%'; Remove-Item '%WRAP_DIR%\maven.zip'"
)

call "%MVN_HOME%\bin\mvn.cmd" %*
endlocal
