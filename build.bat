@echo off
setlocal enabledelayedexpansion
cd /d F:\StellaCilent

set JAVA_HOME=C:\Program Files\Zulu\zulu-21
set GRADLE=C:\Users\马文\.gradle\wrapper\dists\gradle-8.10.2-bin\e0thjr3we83usdufs66z371ne\gradle-8.10.2\bin\gradle.bat

echo [1/3] Building Java (executer) with Fabric Loom...
cd /d F:\StellaCilent\executer
"%GRADLE%" build
if %errorlevel% neq 0 (echo JAVA BUILD FAILED & exit /b 1)
echo   OK: Java build complete

echo [2/3] Encrypting Lua scripts...
"%GRADLE%" encryptLua
if %errorlevel% neq 0 (echo LUA ENCRYPT FAILED & exit /b 1)
echo   OK: Lua scripts encrypted

echo [3/3] Obfuscating JAR with crazy-obfuscator...
"%GRADLE%" obfuscateJar
if %errorlevel% neq 0 (
    echo   WARNING: Obfuscation failed, using unobfuscated jar
    copy /Y build\libs\stella-executer-0.1.0.jar build\libs\StellaCilent-0.1.0-obf.jar >nul
)

rem Deploy to mods folder
if not exist "D:\PCL\.minecraft\versions\StellaCilent\mods" mkdir "D:\PCL\.minecraft\versions\StellaCilent\mods"
copy /Y build\libs\StellaCilent-0.1.0-obf.jar "D:\PCL\.minecraft\versions\StellaCilent\mods\StellaCilent-0.1.0.jar" >nul
echo   OK: Deployed to mods folder

echo.
echo === Build Complete ===
echo   JAR: F:\StellaCilent\executer\build\libs\StellaCilent-0.1.0-obf.jar
echo   Mods: D:\PCL\.minecraft\versions\StellaCilent\mods\StellaCilent-0.1.0.jar
