$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$formatter = Join-Path $env:ProgramFiles 'Android\Android Studio\bin\format.bat'
$style = Join-Path $PSScriptRoot 'java-style.xml'
$sourceRoot = Join-Path $projectRoot 'app\src'

if (-not (Test-Path -LiteralPath $formatter)) { throw "Android Studio formatter not found: $formatter" }
if (-not (Test-Path -LiteralPath $style)) { throw "Java style file not found: $style" }

$javaRoots = @(@('main', 'test', 'androidTest') | ForEach-Object { Join-Path $sourceRoot "$_\java" } | Where-Object { Test-Path -LiteralPath $_ })
if ($javaRoots.Count -eq 0) { throw 'No Java source directories found.' }

$formatterHome = Join-Path $env:TEMP 'MineSafeJavaFormatter'
$config = Join-Path $formatterHome 'config'
$system = Join-Path $formatterHome 'system'
$android = Join-Path $formatterHome 'android'
New-Item -ItemType Directory -Force -Path $config, $system, $android | Out-Null

$properties = Join-Path $formatterHome 'studio.properties'
@(
    "idea.config.path=$($config.Replace('\', '/'))"
    "idea.system.path=$($system.Replace('\', '/'))"
    "idea.plugins.path=$($config.Replace('\', '/'))/plugins"
) | Set-Content -LiteralPath $properties

$previousProperties = $env:STUDIO_PROPERTIES
$previousAndroidHome = $env:ANDROID_USER_HOME
try {
    $env:STUDIO_PROPERTIES = $properties
    $env:ANDROID_USER_HOME = $android
    & $formatter -settings $style -r @javaRoots
    if ($LASTEXITCODE -ne 0) { throw "Android Studio formatter exited with code $LASTEXITCODE" }
}
finally {
    $env:STUDIO_PROPERTIES = $previousProperties
    $env:ANDROID_USER_HOME = $previousAndroidHome
}
