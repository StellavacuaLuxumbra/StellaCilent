@echo off
setlocal enabledelayedexpansion
cd /d F:\StellaCilent\executer

set CP=
for /r .gradle\loom-cache %%f in (*.jar) do (
    echo %%f | findstr /v "sources" >nul && (
        if defined CP (set "CP=!CP!;%%f") else (set "CP=%%f")
    )
)
for /r "%USERPROFILE%\.gradle\caches\modules-2\files-2.1" %%f in (*.jar) do (
    echo %%f | findstr /v "sources" | findstr /v "javadoc" | findstr /v "linux" | findstr /v "darwin" | findstr /v "natives" | findstr /v "-rc" >nul && (
        if defined CP (set "CP=!CP!;%%f") else (set "CP=%%f")
    )
)

javac -d build\classes\java\main -sourcepath src\main\java -cp "!CP!" src\main\java\dev\stella\executer\ipc\Protocol.java src\main\java\dev\stella\executer\ipc\IpcHost.java src\main\java\dev\stella\executer\StellaExecuter.java 2>&1
if %errorlevel%==0 (echo BUILD SUCCESS) else (echo BUILD FAILED)
