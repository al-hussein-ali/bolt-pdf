# MASTER ENGINEERING SPECIFICATION: "VelocityPDF" — Ultra-Fast Rust + Native Mobile PDF Reader

## 1. Role, Philosophy & Performance Targets
You are a Staff Mobile Systems Architect and Rust Embedded Engineer. Your objective is to build **VelocityPDF**, a production-ready, read-only mobile PDF reader modeled after Discord's hybrid mobile architecture: a **Shared Rust Core (`pdf_core`)** handling all heavy lifting, file I/O, state persistence, text search, and PDF rasterization, paired with an **OS-Native UI Layer (Android Jetpack Compose, extensible to iOS SwiftUI)** via **Mozilla UniFFI** and **Zero-Copy JNI**.

### Strict Performance & Efficiency Targets
* **Cold Start Time:** `< 150ms` (zero JS bridge, zero webview, zero Dart/VM runtime overhead).
* **Total Release APK Size:** `~8 MB to 11 MB` (using stripped Rust `.so`, R8 minification, and V8-free `libpdfium.so`).
* **Memory Footprint:** `35 MB to 55 MB` constant RAM ceiling (opening a 1,000-page PDF must consume the exact same RAM as a 3-page PDF via memory-mapped I/O and strict LRU eviction).
* **Frame Rate:** Locked `60Hz / 120Hz / 144Hz` scrolling and zooming with asynchronous background rasterization and instant job cancellation when pages or tiles leave the viewport.

---

## 2. Core Feature Requirements
1. **Read-Only Blazing-Fast PDF Rendering:** Powered by `pdfium-render` wrapping Google's `libpdfium` (no-V8, no-XFA variant).
2. **OS "Open With..." Integration:** Registered in the system manifest so tapping any `.pdf` file in Files, WhatsApp, Telegram, Gmail, or browsers offers VelocityPDF as a viewer and opens straight into the reader.
3. **Recent Files Dashboard:** Home screen displaying recently opened PDFs with title, page count, last-read progress bar (`Page X of Y`), and timestamp.
4. **Instant Resume ("Save Where I Stopped"):** Persists exact page index, normalized intra-page scroll fraction (`0.0..1.0`), zoom scale (`1.0..5.0`), pan offset, manual rotation state, and dark mode preference per document in an embedded SQLite database (`rusqlite` in WAL mode).
5. **Multi-Gesture Zoom & High-DPI Viewport Tiling (`1.0x` to `5.0x`):**
   * **Pinch-to-Zoom & Double-Tap Zoom:** Smooth 2-finger pinch zoom (`1.0x` to `5.0x`) and double-tap smart toggle (`1.0x <-> 2.5x` centered on the tap coordinate).
   * **Two-Tier Rendering (Base Bitmap + Sharp High-DPI Viewport Tile):** When `zoom_scale > 1.25x`, the UI immediately scales the cached 1x page bitmap via GPU matrix transformation for zero-lag 120Hz feedback, and once the gesture settles (debounced by `120ms`), requests a sharp high-DPI cropped viewport tile (`render_tile`) from Rust for the exact visible screen rectangle.
6. **Full-Document Text Search & Highlighting:**
   * **Background Search Engine:** Case-insensitive full-text search across all pages in Rust using PDFium's `FPDFText` APIs, returning normalized bounding boxes (`0.0..1.0` relative to page width/height) and text snippets.
   * **Search Navigation & Overlay:** Compose search bar with live match counter (`Match 3 of 18`), `Next` / `Previous` buttons that auto-scroll to the target page and vertical offset, and semi-transparent yellow highlight boxes (`Color(0x66FFEB3B)`) drawn over all matches (with an orange border on the currently active match) that scale seamlessly with pinch-zoom and rotation.
7. **Dual Rotation Support:**
   * **Device Orientation Toggle:** Seamless switching between Portrait (vertical reading) and Landscape (horizontal fit-to-width reading) via a toolbar toggle or physical phone rotation—without restarting the Activity or losing the exact paragraph position.
   * **Manual 90° Page Rotation:** Rotate poorly scanned pages (`0°`, `90°`, `180°`, `270°`) independently and persist that preference per file.
8. **Dual Light & Dark Mode ("Night Paper" Filter):**
   * **UI Chrome:** Follows system Light/Dark Material 3 theme (with manual override).
   * **PDF Page Pixel Filter:** Sub-2ms Rust SIMD/chunked pixel luminance inversion pass that maps blinding white pages (`#FFFFFF`) to deep charcoal (`#141414`) and black ink (`#000000`) to warm off-white (`#E6E6E6`) while preserving anti-aliased text crispness.

---

## 3. Repository & Directory Structure
Generate the following workspace layout with zero missing files or placeholders:

```text
velocity-pdf/
├── Cargo.toml                          # Workspace root + aggressive release size profile
├── pdf_core/                           # Shared Rust Core Crate
│   ├── Cargo.toml
│   └── src/
│       ├── bin/
│       │   └── uniffi-bindgen.rs       # CLI generator for Kotlin/Swift bindings
│       ├── lib.rs                      # UniFFI exports, Engine facade & lifecycle
│       ├── db.rs                       # rusqlite WAL persistence & fast 128KB file hashing
│       ├── renderer.rs                 # Single-mutex PDFium wrapper, tile zoom, rotation, dark filter
│       ├── search.rs                   # Full-text search & normalized bounding-box extraction
│       ├── cache.rs                    # Thread-safe 64MB LRU cache for base pages & zoom tiles
│       └── android_jni.rs              # Zero-copy AndroidBitmap_lockPixels direct rendering
├── android/                            # Native Android Jetpack Compose Shell
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── app/
│       ├── build.gradle.kts            # R8 shrinking, arm64-v8a ABI filter, JNA + Compose deps
│       ├── proguard-rules.pro          # Keep rules for UniFFI / JNA & JNI entrypoints
│       └── src/main/
│           ├── AndroidManifest.xml     # ACTION_VIEW PDF intent-filter & configChanges
│           ├── jniLibs/
│           │   └── arm64-v8a/          # libpdf_core.so + libpdfium.so
│           └── java/com/velocity/pdf/
│               ├── MainActivity.kt     # Intent handling, FD detachment, URI permission fallback
│               ├── NativeZeroCopy.kt   # JNI bridge declaration for direct Bitmap rendering
│               ├── PdfViewModel.kt     # StateFlow orchestration, search state, zoom tile jobs
│               └── ui/
│                   ├── Theme.kt        # Light & Dark Material3 schemes
│                   ├── HomeScreen.kt   # Recent files list + system file picker trigger
│                   └── ReaderScreen.kt # LazyColumn, pinch-zoom canvas, search bar & highlight overlay
└── scripts/
    └── build_android.sh                # Downloads V8-free libpdfium, runs cargo-ndk & bindgen
```

---

## 4. Workspace & Rust Core Specification (`pdf_core`)

### 4.1 Root `Cargo.toml` (Binary Size & LTO Optimization)
```toml
[workspace]
members = ["pdf_core"]
resolver = "2"

[profile.release]
opt-level = "z"          # Optimize aggressively for binary size
lto = "fat"              # Full cross-crate Link-Time Optimization
codegen-units = 1        # Maximize dead-code elimination
panic = "abort"          # Strip stack unwinding tables (~30% smaller binary)
strip = "symbols"        # Strip all debug and symbol tables from .so output
```

### 4.2 `pdf_core/Cargo.toml`
```toml
[package]
name = "pdf_core"
version = "0.1.0"
edition = "2021"

[lib]
crate-type = ["cdylib", "staticlib"]
name = "pdf_core"

[dependencies]
uniffi = { version = "0.28", features = ["cli"] }
pdfium-render = { version = "0.8", features = ["thread_safe", "pdfium_latest"] }
rusqlite = { version = "0.32", features = ["bundled"] }
lru = "0.12"
thiserror = "1.0"
parking_lot = "0.12"
memmap2 = "0.9"
sha2 = "0.10"
hex = "0.4"

[target.'cfg(target_os = "android")'.dependencies]
jni = "0.21"
ndk-sys = "0.5"

[[bin]]
name = "uniffi-bindgen"
path = "src/bin/uniffi-bindgen.rs"
```

### 4.3 UniFFI Data Contracts (`pdf_core/src/lib.rs`)
Expose the following exact types across UniFFI:

```rust
uniffi::setup_scaffolding!();

#[derive(uniffi::Error, thiserror::Error, Debug)]
pub enum PdfError {
    #[error("Database error: {0}")]
    DatabaseError(String),
    #[error("PDF Engine error: {0}")]
    EngineError(String),
    #[error("PDF is password-protected")]
    PasswordRequired,
    #[error("Invalid file descriptor or path")]
    InvalidSource,
    #[error("Page index {0} out of bounds")]
    PageOutOfBounds(u32),
    #[error("Search cancelled")]
    SearchCancelled,
}

#[derive(uniffi::Enum, Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum PageRotation {
    Deg0 = 0,
    Deg90 = 1,
    Deg180 = 2,
    Deg270 = 3,
}

impl PageRotation {
    pub fn degrees(&self) -> i32 {
        match self {
            PageRotation::Deg0 => 0,
            PageRotation::Deg90 => 90,
            PageRotation::Deg180 => 180,
            PageRotation::Deg270 => 270,
        }
    }
    pub fn next_clockwise(&self) -> Self {
        match self {
            PageRotation::Deg0 => PageRotation::Deg90,
            PageRotation::Deg90 => PageRotation::Deg180,
            PageRotation::Deg180 => PageRotation::Deg270,
            PageRotation::Deg270 => PageRotation::Deg0,
        }
    }
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct PageDimension {
    pub page_index: u32,
    pub width_pts: f32,
    pub height_pts: f32,
    pub aspect_ratio: f32, // Computed AFTER applying (intrinsic_rotation + manual_rotation) % 360
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct NormalizedRect {
    // Normalized 0.0..1.0 coordinates in top-left origin AFTER applying effective page rotation
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct SearchMatch {
    pub match_index: u32,
    pub page_index: u32,
    pub rects: Vec<NormalizedRect>, // Multiple rects if a matched phrase wraps across lines
    pub context_snippet: String,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct ReadingProgress {
    pub file_hash: String,
    pub file_uri: String,
    pub fallback_cache_path: Option<String>,
    pub display_name: String,
    pub last_page_index: u32,
    pub page_offset_fraction: f32, // 0.0..1.0 within current page (survives Portrait <-> Landscape!)
    pub zoom_scale: f32,           // 1.0..5.0
    pub pan_offset_x_fraction: f32,
    pub manual_rotation: PageRotation,
    pub dark_mode_enabled: bool,
    pub last_opened_unix: i64,
    pub total_pages: u32,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct DocumentOpenResult {
    pub progress: ReadingProgress,
    pub dimensions: Vec<PageDimension>,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct RenderedTile {
    pub width_px: u32,
    pub height_px: u32,
    pub rgba_bytes: Vec<u8>,
}
```

### 4.4 Core Rust Engine Methods (`#[uniffi::export]`)
Implement `PdfEngine` as a `#[derive(uniffi::Object)]` with these methods:
* `#[uniffi::constructor] pub fn new(db_path: String, pdfium_lib_dir: String) -> Result<Arc<Self>, PdfError>`
* `pub fn open_from_fd(&self, raw_fd: i32, file_uri: String, display_name: String, fallback_cache_path: Option<String>) -> Result<DocumentOpenResult, PdfError>`
* `pub fn open_from_path(&self, file_path: String, file_uri: String, display_name: String) -> Result<DocumentOpenResult, PdfError>`
* `pub fn get_page_dimensions(&self, manual_rotation: PageRotation) -> Result<Vec<PageDimension>, PdfError>`
* `pub fn render_page(&self, page_index: u32, target_width_px: u32, target_height_px: u32, manual_rotation: PageRotation, dark_mode: bool) -> Result<RenderedTile, PdfError>`
* `pub fn render_zoom_tile(&self, page_index: u32, full_scaled_width_px: u32, full_scaled_height_px: u32, visible_x_px: i32, visible_y_px: i32, tile_width_px: u32, tile_height_px: u32, manual_rotation: PageRotation, dark_mode: bool) -> Result<RenderedTile, PdfError>`
* `pub fn search_document(&self, query: String, manual_rotation: PageRotation) -> Result<Vec<SearchMatch>, PdfError>`
* `pub fn cancel_active_search(&self)`
* `pub fn save_progress(&self, progress: ReadingProgress) -> Result<(), PdfError>`
* `pub fn get_recent_files(&self, limit: u32) -> Result<Vec<ReadingProgress>, PdfError>`
* `pub fn remove_recent_file(&self, file_hash: String) -> Result<(), PdfError>`
* `pub fn raw_engine_ptr(&self) -> i64` (Returns `self as *const Self as i64` for zero-copy JNI calls)
* `pub fn close_document(&self)`

### 4.5 Fast `< 1ms` File Fingerprinting (`pdf_core/src/db.rs`)
Never hash an entire 500MB PDF on open. Compute `file_hash` from the memory-mapped slice `mmap: &[u8]` as follows:
1. Take `len = mmap.len() as u64`.
2. Read the first `65,536` bytes (`&mmap[..len.min(65536)]`) and the last `65,536` bytes (`&mmap[len.saturating_sub(65536)..]`).
3. Feed `len.to_le_bytes()`, head slice, and tail slice into `sha2::Sha256` and hex-encode the digest.
4. Use this `file_hash` as the `PRIMARY KEY` in SQLite `recent_files`.

### 4.6 Single-Mutex PDFium, High-DPI Zoom Tiling & Smart Dark-Mode (`pdf_core/src/renderer.rs`)
1. **Strict Thread Serialization:**
   `libpdfium` is NOT thread-safe across concurrent document/page loads and will segfault (`SIGSEGV`) if called simultaneously from multiple Kotlin coroutines. Hold the active `Mmap` buffer and PDFium document handle inside a single `parking_lot::Mutex<Option<ActiveDocument>>`.
2. **Memory-Mapped Loading (`memmap2`):**
   Convert the Android `raw_fd` via `unsafe { std::fs::File::from_raw_fd(raw_fd) }` and memory-map it via `unsafe { Mmap::map(&file) }`. Keep the `Mmap` struct owned inside `ActiveDocument` for the lifetime of the open document.
3. **High-DPI Cropped Zoom Tile Rendering (`render_zoom_tile`):**
   When the user zooms in (e.g. `3.5x`), rendering an entire page at `3.5x` resolution (`3780x5345` = `80MB` per page) will cause an immediate mobile OOM crash. Instead:
   * Allocate a target bitmap of size `(tile_width_px, tile_height_px)` (the screen viewport size, capped at `2048x2048`).
   * Use PDFium's matrix rendering (`FPDF_RenderPageBitmapWithMatrix`) with a scale matrix `(zoom_scale, 0, 0, zoom_scale, -visible_x_px, -visible_y_px)` so PDFium rasterizes **only** the visible zoomed sub-region directly into the viewport-sized buffer at crisp vector resolution.
4. **Smart Night Mode Pixel Filter (`apply_night_filter_rgba`):**
   ```rust
   #[inline(always)]
   pub fn apply_night_filter_rgba(buf: &mut [u8]) {
       // Maps white (#FFFFFF -> 255) to deep charcoal (#141414 -> 20)
       // and black (#000000 -> 0) to warm off-white (#E6E6E6 -> 230)
       for px in buf.chunks_exact_mut(4) {
           let inv_r = 255 - px[0];
           let inv_g = 255 - px[1];
           let inv_b = 255 - px[2];
           px[0] = 20 + ((inv_r as u16 * 210) / 255) as u8;
           px[1] = 20 + ((inv_g as u16 * 210) / 255) as u8;
           px[2] = 20 + ((inv_b as u16 * 210) / 255) as u8;
       }
   }
   ```

### 4.7 Full-Text Search & Coordinate Normalization (`pdf_core/src/search.rs`)
1. **Cancellation Token (`AtomicBool`):**
   Hold an `Arc<AtomicBool>` (`search_cancel_flag`) inside `PdfEngine`. When the user types a new character in the search box, call `cancel_active_search()` to abort any in-progress multi-page search loop between pages.
2. **Coordinate System Transformation (PDF Bottom-Left -> UI Top-Left + Rotation):**
   PDF internal text coordinates (`page.text().search(...)`) use a **bottom-left origin (`0,0` at bottom-left)** in unrotated PDF points (`width_pts`, `height_pts`). You MUST transform every character highlight rectangle into **normalized `0.0..1.0` top-left UI coordinates** taking `effective_rotation = (intrinsic_rotation + manual_rotation) % 360` into account:
   * First normalize in unrotated top-left space:
     `u_left = pdf_left / page_w`, `u_right = pdf_right / page_w`,
     `u_top = 1.0 - (pdf_top / page_h)`, `u_bottom = 1.0 - (pdf_bottom / page_h)`.
   * Then rotate `(u_left, u_top, u_right, u_bottom)` around `(0.5, 0.5)` for `90°`, `180°`, or `270°` so Jetpack Compose can simply multiply `rect.left * canvasWidth` and `rect.top * canvasHeight` regardless of zoom level or rotation!
3. **Lock Yielding During Search:**
   Do NOT hold the PDFium `Mutex` continuously across all 1,000 pages in a single lock acquisition (which would freeze page rendering while searching). Acquire the lock per page (or in batches of 5 pages), extract matches, release the lock briefly so any pending `render_page` request can run immediately, and check `search_cancel_flag`.

### 4.8 Zero-Copy Android Bitmap JNI Bridge (`pdf_core/src/android_jni.rs`)
Implement both full-page and zoomed-tile zero-copy JNI functions that lock an `android.graphics.Bitmap` (`ARGB_8888`) via `AndroidBitmap_lockPixels`, render directly into the native pixel memory, apply `apply_night_filter_rgba` if `dark_mode` is enabled, and unlock:
* `Java_com_velocity_pdf_NativeZeroCopy_renderPageDirect(...)`
* `Java_com_velocity_pdf_NativeZeroCopy_renderZoomTileDirect(...)`

---

## 5. Android Jetpack Compose Layer (`android/`)

### 5.1 `AndroidManifest.xml`
* Register `MainActivity` with `android:launchMode="singleTop"` and `android:configChanges="orientation|screenSize|screenLayout|smallestScreenSize|keyboardHidden|uiMode"` so rotating the phone or toggling dark mode never destroys the Activity or reloads the PDF.
* Register `<intent-filter>` for `android.intent.action.VIEW` with `category.DEFAULT`, `category.BROWSABLE`, `scheme="content"`, `scheme="file"`, and `mimeType="application/pdf"`.

### 5.2 `ReaderScreen.kt` (Scrolling, Pinch-Zoom, Rotation & Search Overlay)
1. **Unified Zoom & Pan Gesture Layer:**
   Wrap the `LazyColumn` inside a gesture container using `Modifier.pointerInput(Unit)` that handles:
   * **Double-tap (`detectTapGestures(onDoubleTap = ...)`):** Smoothly animates `zoomScale` from `1.0f` to `2.5f` centered on the tapped coordinate (or resets to `1.0f` if already zoomed in).
   * **Pinch-to-zoom & 2D Pan (`detectTransformGestures`):** Clamps `zoomScale` to `1.0f..5.0f` and clamps horizontal/vertical pan offsets so the page edges never detach past the screen bounds.
   * **High-DPI Tile Trigger:** Use `snapshotFlow { Triple(zoomScale, panOffsetX, firstVisiblePageIndex) }.debounce(120L)` to request a sharp high-DPI tile (`renderZoomTileDirect`) whenever `zoomScale > 1.25f` and the user pauses moving for `120ms`. Draw the sharp tile seamlessly over the scaled base bitmap.
2. **Search Bar & Match Highlight Canvas Overlay:**
   * Tapping the Search icon in the `TopAppBar` opens a sleek Top Search Bar with a `TextField`, a live match counter (`"3 / 14"` or `"0 / 0"`), `Previous` (`▲`), `Next` (`▼`), and `Close` (`✕`) buttons.
   * Typing in the search field is debounced by `250ms` (calling `engine.cancelActiveSearch()` immediately on keystroke, then launching `engine.searchDocument(query, manualRotation)` on `Dispatchers.Default`).
   * On each visible page's `Canvas`, draw all `SearchMatch` rectangles belonging to that `pageIndex`:
     * Inactive matches: Fill with `Color(0x55FFEB3B)` (semi-transparent yellow).
     * Active/focused match: Fill with `Color(0x88FF9800)` and stroke with a `2.dp` `Color(0xFFFF5722)` border.
   * Tapping `Next` or `Previous` updates `activeMatchIndex` and scrolls `lazyListState` directly to `match.pageIndex` with `pixelOffset = (match.rects.first().top * pageHeightPx - viewportHeightPx * 0.3f).toInt().coerceAtLeast(0)`.
3. **Orientation & Page Rotation Controls in TopAppBar:**
   * **Screen Orientation Toggle Button:** Switches `activity.requestedOrientation` between Portrait, Landscape, and System Auto.
   * **Rotate Page 90° Button:** Cycles `manualRotation` (`Deg0 -> Deg90 -> Deg180 -> Deg270`), recomputes `aspectRatio` and active search highlight coordinates, and re-renders visible pages.
   * **Dark Mode Toggle Button:** Toggles `darkModeEnabled` (applying both dark UI theme and the Rust `apply_night_filter_rgba` page filter).
4. **Fraction-Based Scroll Preservation Across Rotations:**
   * Track `pageOffsetFraction = firstVisibleItemScrollOffset.toFloat() / currentPageHeightPx.toFloat()`.
   * Inside `LaunchedEffect(maxWidthPx, manualRotation)`, restore the user's exact reading line by computing `newPixelOffset = (savedFraction * newPageHeightPx).roundToInt()` and calling `lazyListState.scrollToItem(savedPageIndex, newPixelOffset)`.

---

## 6. Mandatory Edge Cases & Production Safeguards
Your code MUST implement every single safeguard below:

1. **Ephemeral `content://` URI Expiration (WhatsApp / Gmail / Browser Downloads):**
   When opening a `content://` URI, first attempt `contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`. If it throws a `SecurityException`, transparently copy the file stream in the background to `context.filesDir/recent_cache/<file_hash>.pdf` (enforcing a 150MB rolling cache directory limit) and save `fallback_cache_path` in SQLite. When the user taps that file in "Recent Files" days later, if opening `file_uri` fails, automatically open `fallback_cache_path`.
2. **Out-Of-Memory (OOM) Prevention on Fast Flings & 5x Zoom:**
   * In `LazyColumn`, launch page rendering inside a `DisposableEffect` coroutine scope so any page that scrolls out of the viewport immediately cancels its pending render coroutine before calling into Rust.
   * Never allocate a full-page bitmap scaled by `zoomScale` (which exceeds 100MB at 4x–5x zoom). Always keep the base page bitmap capped at `<= 2560px` max edge and render zoomed detail exclusively via viewport-sized `render_zoom_tile` buffers (`<= 2048x2048`).
3. **PDFium Concurrency Segfault Prevention (`SIGSEGV`) & Search Interleaving:**
   All calls into `pdfium-render` (rendering AND text search) MUST acquire the single `parking_lot::Mutex` lock. During multi-page search, acquire and release the lock per page so a 60fps scroll or zoom render request can cut in front of background search without stalling the UI.
4. **Search Highlight Misalignment on Rotated or Mixed-Aspect Pages:**
   PDFium returns text rectangles in unrotated bottom-left PDF point coordinates. Always normalize (`0.0..1.0`) and rotate highlight rectangles inside `search.rs` using `effective_rotation = (intrinsic_page_rotation + manual_rotation) % 360` so highlights align pixel-perfectly even on landscape tables or upside-down scanned pages.
5. **Debounced Non-Blocking SQLite Writes:**
   Never write to SQLite synchronously on scroll or zoom frames. Use `snapshotFlow { ... }.debounce(300L)` collected on `Dispatchers.IO`, plus an immediate flush inside `Lifecycle.Event.ON_STOP` / `ON_PAUSE`.
6. **Password-Protected & Corrupted PDFs (Zero FFI Panics):**
   Never use `.unwrap()` or `.expect()` in Rust FFI functions. Map encrypted PDFs to `PdfError::PasswordRequired` and corrupted headers to `PdfError::EngineError`, displaying a clean Material 3 `Snackbar` in Kotlin instead of crashing the process with `SIGABRT`.

---

## 7. Build Script (`scripts/build_android.sh`)
Create an executable `scripts/build_android.sh` script that:
1. Downloads the latest V8-free `pdfium-android-arm64.tgz` release from `https://github.com/bblanchon/pdfium-binaries/releases/latest` and extracts `lib/libpdfium.so` into `android/app/src/main/jniLibs/arm64-v8a/libpdfium.so`.
2. Compiles `pdf_core` for Android using `cargo ndk -t arm64-v8a -o ../android/app/src/main/jniLibs build --release`.
3. Runs `cargo run --bin uniffi-bindgen generate --library ../android/app/src/main/jniLibs/arm64-v8a/libpdf_core.so --language kotlin --out-dir ../android/app/src/main/java` to generate the Kotlin bindings.

---

## 8. Execution Instructions for the Agent
Execute this specification step by step:
1. Scaffold the workspace directory and `Cargo.toml` files first.
2. Implement `pdf_core` (`db.rs`, `cache.rs`, `renderer.rs`, `search.rs`, `android_jni.rs`, `lib.rs`, `uniffi-bindgen.rs`) and verify it compiles cleanly with `cargo check` and unit tests (`cargo test`).
3. Create `scripts/build_android.sh` and make it executable (`chmod +x`).
4. Implement the complete Android Jetpack Compose project (`build.gradle.kts`, `proguard-rules.pro`, `AndroidManifest.xml`, `MainActivity.kt`, `NativeZeroCopy.kt`, `PdfViewModel.kt`, `Theme.kt`, `HomeScreen.kt`, `ReaderScreen.kt`) with complete, production-ready code and zero `TODO` stubs.
