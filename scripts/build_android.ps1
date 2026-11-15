$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$RootDir = Split-Path -Parent $ScriptDir
. (Join-Path $RootDir "use-project-env.ps1")

$JniDir = Join-Path $RootDir "android\app\src\main\jniLibs\arm64-v8a"
New-Item -ItemType Directory -Force -Path $JniDir | Out-Null

$PdfiumSo = Join-Path $JniDir "libpdfium.so"
if (-not (Test-Path $PdfiumSo)) {
    Write-Host ">>> Downloading latest V8-free pdfium-android-arm64.tgz..."
    $TmpDir = Join-Path $RootDir ".tools\tmp\pdfium_android"
    New-Item -ItemType Directory -Force -Path $TmpDir | Out-Null
    $Tgz = Join-Path $TmpDir "pdfium-android-arm64.tgz"
    Invoke-WebRequest -Uri "https://github.com/bblanchon/pdfium-binaries/releases/latest/download/pdfium-android-arm64.tgz" -OutFile $Tgz
    tar -xzf $Tgz -C $TmpDir
    $FoundSo = Get-ChildItem -Path $TmpDir -Recurse -Filter "libpdfium.so" | Select-Object -First 1
    if (-not $FoundSo) { throw "libpdfium.so not found in archive" }
    Copy-Item $FoundSo.FullName $PdfiumSo -Force
}

Push-Location (Join-Path $RootDir "pdf_core")
try {
    Write-Host ">>> Building pdf_core for Android arm64-v8a..."
    cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Get-ChildItem $JniDir -Filter "libpdfium_render-*.so" -ErrorAction SilentlyContinue | Remove-Item -Force

    Write-Host ">>> Building unstripped host cdylib for UniFFI metadata..."
    cargo build --lib
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

    Write-Host ">>> Generating UniFFI Kotlin bindings..."
    cargo run --bin uniffi-bindgen generate `
        --library ../target/debug/pdf_core.dll `
        --language kotlin `
        --out-dir ../android/app/src/main/java `
        --no-format
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}
finally {
    Pop-Location
}

Write-Host ">>> Build complete."
