$ProjectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$ToolsDir = Join-Path $ProjectRoot ".tools"

$env:RUSTUP_HOME = Join-Path $ToolsDir "rustup"
$env:CARGO_HOME = Join-Path $ToolsDir "cargo"
$env:GRADLE_USER_HOME = Join-Path $ToolsDir "gradle"

$SdkDefault = Join-Path $env:LOCALAPPDATA "Android\Sdk"
if (-not $env:ANDROID_HOME -and (Test-Path $SdkDefault)) {
    $env:ANDROID_HOME = $SdkDefault
}
if (-not $env:ANDROID_SDK_ROOT -and $env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}
if (-not $env:ANDROID_NDK_HOME -and $env:ANDROID_HOME) {
    $NdkDir = Join-Path $env:ANDROID_HOME "ndk"
    if (Test-Path $NdkDir) {
        $LatestNdk = Get-ChildItem $NdkDir -Directory | Sort-Object Name -Descending | Select-Object -First 1
        if ($LatestNdk) {
            $env:ANDROID_NDK_HOME = $LatestNdk.FullName
        }
    }
}

$CargoBin = Join-Path $env:CARGO_HOME "bin"
if ($env:PATH -notlike "*$CargoBin*") {
    $env:PATH = "$CargoBin;$env:PATH"
}

Write-Host "VelocityPDF project environment activated:"
Write-Host "  RUSTUP_HOME      = $env:RUSTUP_HOME"
Write-Host "  CARGO_HOME       = $env:CARGO_HOME"
Write-Host "  GRADLE_USER_HOME = $env:GRADLE_USER_HOME"
Write-Host "  ANDROID_HOME     = $env:ANDROID_HOME"
Write-Host "  ANDROID_NDK_HOME = $env:ANDROID_NDK_HOME"

