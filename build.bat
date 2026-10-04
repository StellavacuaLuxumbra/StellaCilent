@echo off
setlocal enabledelayedexpansion
cd /d F:\StellaCilent

set JAVA_HOME=C:\Program Files\Zulu\zulu-21

echo [1/2] Building Stella client (stella/) with Fabric Loom...
cd /d F:\StellaCilent\stella
call gradlew.bat build
if %errorlevel% neq 0 (echo JAVA BUILD FAILED & exit /b 1)
echo   OK: build complete (no obfuscation)

rem Deploy to mods folder
if not exist "D:\PCL\.minecraft\versions\StellaCilent\mods" mkdir "D:\PCL\.minecraft\versions\StellaCilent\mods"
for %%F in (build\libs\*.jar) do (
    if not "%%~nF"=="*sources*" copy /Y "%%F" "D:\PCL\.minecraft\versions\StellaCilent\mods\%%~nxF" >nul
)
echo   OK: Deployed to mods folder

echo.
echo === Build Complete ===
echo   JAR: F:\StellaCilent\stella\build\libs\
echo   Mods: D:\PCL\.minecraft\versions\StellaCilent\mods\
