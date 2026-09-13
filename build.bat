@echo off
setlocal enabledelayedexpansion
cd /d F:\StellaCilent

set JAVA_HOME=C:\Program Files\Zulu\zulu-21
set GRADLE=C:\Users\马文\.gradle\wrapper\dists\gradle-8.10.2-bin\e0thjr3we83usdufs66z371ne\gradle-8.10.2\bin\gradle.bat
set MINGW=D:\MinGW\bin

echo [1/3] Building Lua static library...
cd /d F:\StellaCilent\cilent
if not exist build mkdir build

set LUA_SRCS=lapi.c lauxlib.c lbaselib.c lcode.c lcorolib.c lctype.c ldebug.c ldo.c ldump.c lfunc.c lgc.c linit.c llex.c lmathlib.c lmem.c loadlib.c lobject.c lopcodes.c lparser.c lstate.c lstring.c lstrlib.c ltable.c ltablib.c ltm.c lundump.c lutf8lib.c lvm.c lzio.c
set LUA_OBJS=

for %%f in (%LUA_SRCS%) do (
    %MINGW%\gcc.exe -c -O2 -I"src\lua" -o "build\%%~nf.o" "src\lua\%%f"
    set LUA_OBJS=!LUA_OBJS! build\%%~nf.o
)

%MINGW%\ar.exe rcs build\liblua.a %LUA_OBJS%
if %errorlevel% neq 0 (echo LUA BUILD FAILED & exit /b 1)
echo   OK: liblua.a

echo [2/3] Building C++ (cilent)...
%MINGW%\g++.exe -std=c++17 -O2 -mwindows -Isrc -o build\stella_client.exe src\main.cpp src\brain\lua_host.cpp build\liblua.a -lws2_32 -lcomctl32 -static
if %errorlevel% neq 0 (echo C++ COMPILE FAILED & exit /b 1)
echo   OK: stella_client.exe

rem Deploy exe + scripts to root build\ for easy access
if not exist ..\build mkdir ..\build
copy /Y build\stella_client.exe ..\build\stella_client.exe >nul
xcopy /E /Y /Q scripts ..\build\scripts >nul
echo   OK: deployed to F:\StellaCilent\build\

echo [3/3] Building Java (executer) with Fabric Loom...
cd /d F:\StellaCilent\executer
"%GRADLE%" build
if %errorlevel% neq 0 (echo JAVA BUILD FAILED & exit /b 1)

rem Copy remapped JAR to mods folder
if not exist build mkdir build
copy /Y build\libs\stella-executer-0.1.0.jar build\stella-executer-0.1.0.jar >nul
copy /Y build\libs\stella-executer-0.1.0.jar "D:\PCL\.minecraft\versions\StellaCilent\mods\stella-executer-0.1.0.jar" >nul
echo   OK: stella-executer-0.1.0.jar

echo.
echo === Build Complete ===
echo   F:\StellaCilent\cilent\build\stella_client.exe
echo   F:\StellaCilent\executer\build\libs\stella-executer-0.1.0.jar
