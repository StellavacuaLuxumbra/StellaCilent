$ErrorActionPreference = "Stop"

$baseLoom = "F:\StellaCilent\executer\.gradle\loom-cache"
$baseGradle = "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1"
$srcDir = "F:\StellaCilent\executer\src\main\java"
$outDir = "F:\StellaCilent\executer\build\classes\java\main"
$argFile = "F:\StellaCilent\executer\build\javac_args.txt"

# Build classpath from all remapped mods + gradle cache
$jars = @()

# Minecraft
$mcJar = Get-ChildItem -Recurse "$baseLoom\minecraftMaven" -Filter "minecraft-merged-*.jar" | Where-Object { $_.FullName -match "net\.fabricmc\.yarn" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $mcJar

# All remapped Fabric API jars
$remapped = Get-ChildItem -Recurse "$baseLoom\remapped_mods" -Filter "*.jar" | Where-Object { $_.FullName -notmatch "sources" } | ForEach-Object { $_.FullName }
$jars += $remapped

# JOML
$joml = Get-ChildItem -Recurse "$baseGradle\org.joml" -Filter "joml-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $joml

# LWJGL
$lwjgl = Get-ChildItem -Recurse "$baseGradle\org.lwjgl" -Filter "*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc|natives" } | ForEach-Object { $_.FullName }
$jars += $lwjgl

# Gson
$gson = Get-ChildItem -Recurse "$baseGradle\com.google.code.gson" -Filter "gson-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $gson

# SLF4J
$slf4j = Get-ChildItem -Recurse "$baseGradle\org.slf4j" -Filter "slf4j-api-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $slf4j

# Mixin
$mixin = Get-ChildItem -Recurse "$baseGradle\net.fabricmc\sponge-mixin" -Filter "sponge-mixin-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $mixin

# Fabric Loader (has ClientModInitializer)
$floader = Get-ChildItem -Recurse "$baseGradle\net.fabricmc\fabric-loader" -Filter "fabric-loader-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $floader

# Brigadier (Mojang command framework)
$brigadier = Get-ChildItem -Recurse "$baseGradle\com.mojang" -Filter "brigadier-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $brigadier

# Mojang datafixerupper (serialization)
$dfu = Get-ChildItem -Recurse "$baseGradle\com.mojang\datafixerupper" -Filter "datafixerupper-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $dfu

# authlib
$authlib = Get-ChildItem -Recurse "$baseGradle\com.mojang" -Filter "authlib-*.jar" | Where-Object { $_.FullName -notmatch "sources|javadoc" } | Select-Object -First 1 -ExpandProperty FullName
$jars += $authlib

$cp = ($jars | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique) -join ";"

# Collect all java source files
$javaFiles = Get-ChildItem -Recurse $srcDir -Filter "*.java" | ForEach-Object { $_.FullName }

# Write argfile in UTF-8 without BOM
$lines = @()
$lines += "-d"
$lines += $outDir
$lines += "-sourcepath"
$lines += $srcDir
$lines += "-encoding"
$lines += "UTF-8"
$lines += "-cp"
$lines += $cp
$lines += "-proc:none"
foreach ($f in $javaFiles) {
    $lines += $f
}

$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllLines($argFile, $lines, $utf8NoBom)

Write-Host "Generated argfile with $($javaFiles.Count) source files and $($cp.Split(';').Count) classpath entries"
