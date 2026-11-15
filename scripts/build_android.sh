#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
TOOLS_DIR="${ROOT_DIR}/.tools"

if [ -d "${TOOLS_DIR}/rustup" ]; then
  export RUSTUP_HOME="${TOOLS_DIR}/rustup"
fi
if [ -d "${TOOLS_DIR}/cargo" ]; then
  export CARGO_HOME="${TOOLS_DIR}/cargo"
  export PATH="${CARGO_HOME}/bin:${PATH}"
fi

JNI_DIR="${ROOT_DIR}/android/app/src/main/jniLibs/arm64-v8a"
mkdir -p "${JNI_DIR}"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

echo ">>> Downloading latest V8-free pdfium-android-arm64.tgz..."
curl -fsSL "https://github.com/bblanchon/pdfium-binaries/releases/latest/download/pdfium-android-arm64.tgz" -o "${TMP_DIR}/pdfium-android-arm64.tgz"
tar -xzf "${TMP_DIR}/pdfium-android-arm64.tgz" -C "${TMP_DIR}"

PDFIUM_SO="$(find "${TMP_DIR}" -name 'libpdfium.so' | head -n 1)"
if [ -z "${PDFIUM_SO}" ]; then
  echo "ERROR: libpdfium.so not found in archive" >&2
  exit 1
fi
cp "${PDFIUM_SO}" "${JNI_DIR}/libpdfium.so"

echo ">>> Building pdf_core for Android arm64-v8a..."
cd "${ROOT_DIR}/pdf_core"
cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release
rm -f "${JNI_DIR}"/libpdfium_render-*.so

echo ">>> Building unstripped host cdylib for UniFFI metadata..."
cargo build --lib

HOST_LIB=""
for candidate in ../target/debug/libpdf_core.so ../target/debug/libpdf_core.dylib ../target/debug/pdf_core.dll; do
  if [ -f "${candidate}" ]; then
    HOST_LIB="${candidate}"
    break
  fi
done

echo ">>> Generating UniFFI Kotlin bindings..."
cargo run --bin uniffi-bindgen generate \
  --library "${HOST_LIB}" \
  --language kotlin \
  --out-dir ../android/app/src/main/java \
  --no-format

echo ">>> Build complete."
