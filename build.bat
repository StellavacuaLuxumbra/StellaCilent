@echo off
setlocal enabledelayedexpansion
cd /d F:\StellaCilent

set JAVA_HOME=C:\Program Files\Zulu\zulu-21
set GRADLE=C:\Users\马文\.gradle\wrapper\dists\gradle-8.10.2-bin\e0thjr3we83usdufs66z371ne\gradle-8.10.2\bin\gradle.bat

echo [1/2] Building C++ (cilent)...
cd /d F:\StellaCilent\cilent
D:\MinGW\bin\g++.exe -std=c++17 -O2 -mwindows -o build\stella_client.exe src\main.cpp -lws2_32 -lcomctl32 -static
if %errorlevel% neq 0 (echo C++ COMPILE FAILED & exit /b 1)
echo   OK: stella_client.exe

echo [2/2] Building Java (executer) with Fabric Loom...
cd /d F:\StellaCilent\executer
"%GRADLE%" build
if %errorlevel% neq 0 (echo JAVA BUILD FAILED & exit /b 1)

rem Copy remapped JAR to mods folder
if not exist build mkdir build
copy /Y build\libs\stella-executer-0.1.0.jar build\stella-executer-0.1.0.jar >nul
copy /Y build\libs\stella-executer-0.1.0.jar "D:\PCL\.minecraft\mods\stella-executer-0.1.0.jar" >nul
copy /Y build\libs\stella-executer-0.1.0.jar "%APPDATA%\.minecraft\mods\stella-executer-0.1.0.jar" >nul
echo   OK: stella-executer-0.1.0.jar

echo.
echo === Build Complete ===
echo   F:\StellaCilent\cilent\build\stella_client.exe
echo   F:\StellaCilent\executer\build\libs\stella-executer-0.1.0.jar
