mod android_jni;
mod cache;
mod db;
mod renderer;
mod search;
mod tools;

use std::fs::File;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};

use parking_lot::Mutex;
use pdfium_render::prelude::Pdfium;

use crate::cache::{TileCache, TileCacheKey, DEFAULT_CACHE_BUDGET_BYTES};
use crate::db::Database;
use crate::renderer::{
    bind_pdfium, compute_page_dimensions, load_document_from_file, render_page_into_buffer,
    render_zoom_tile_into_buffer, ActiveDocument,
};
use crate::search::{extract_page_text_content, search_document_pages};

uniffi::setup_scaffolding!();

const MAX_TILE_EDGE_PX: u32 = 2048;

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
    pub aspect_ratio: f32,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct NormalizedRect {
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct HighlightRecord {
    pub id: i64,
    pub file_hash: String,
    pub page_index: u32,
    pub start_char: u32,
    pub end_char: u32,
    pub rects: Vec<NormalizedRect>,
    pub color_argb: u32,
    pub selected_text: String,
    pub created_unix: i64,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct PageTextChar {
    pub char_index: u32,
    pub text: String,
    pub rect: NormalizedRect,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct PageTextContent {
    pub page_index: u32,
    pub full_text: String,
    pub chars: Vec<PageTextChar>,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct SearchMatch {
    pub match_index: u32,
    pub page_index: u32,
    pub rects: Vec<NormalizedRect>,
    pub context_snippet: String,
}

#[derive(uniffi::Record, Clone, Debug)]
pub struct ReadingProgress {
    pub file_hash: String,
    pub file_uri: String,
    pub fallback_cache_path: Option<String>,
    pub display_name: String,
    pub last_page_index: u32,
    pub page_offset_fraction: f32,
    pub zoom_scale: f32,
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

#[derive(uniffi::Object)]
pub struct PdfEngine {
    db: Database,
    cache: TileCache,
    pdfium: *mut Pdfium,
    active_doc: Mutex<Option<ActiveDocument>>,
    search_cancel_flag: Arc<AtomicBool>,
}

unsafe impl Send for PdfEngine {}
unsafe impl Sync for PdfEngine {}

impl Drop for PdfEngine {
    fn drop(&mut self) {
        // Ensure active document closes before freeing Pdfium
        *self.active_doc.lock() = None;
        if !self.pdfium.is_null() {
            unsafe {
                drop(Box::from_raw(self.pdfium));
            }
            self.pdfium = std::ptr::null_mut();
        }
    }
}

#[uniffi::export]
impl PdfEngine {
    #[uniffi::constructor]
    pub fn new(db_path: String, pdfium_lib_dir: String) -> Result<Arc<Self>, PdfError> {
        let db = Database::open(&db_path)?;
        let pdfium = bind_pdfium(&pdfium_lib_dir)?;
        let pdfium_ptr = Box::into_raw(Box::new(pdfium));

        Ok(Arc::new(Self {
            db,
            cache: TileCache::new(DEFAULT_CACHE_BUDGET_BYTES),
            pdfium: pdfium_ptr,
            active_doc: Mutex::new(None),
            search_cancel_flag: Arc::new(AtomicBool::new(false)),
        }))
    }

    pub fn open_from_fd(
        &self,
        raw_fd: i32,
        file_uri: String,
        display_name: String,
        fallback_cache_path: Option<String>,
    ) -> Result<DocumentOpenResult, PdfError> {
        self.open_from_fd_impl(raw_fd, file_uri, display_name, fallback_cache_path)
    }

    pub fn open_from_path(
        &self,
        file_path: String,
        file_uri: String,
        display_name: String,
    ) -> Result<DocumentOpenResult, PdfError> {
        let file = File::open(&file_path).map_err(|_| PdfError::InvalidSource)?;
        let fallback = if file_uri != file_path {
            Some(file_path)
        } else {
            None
        };
        self.open_file_internal(file, file_uri, display_name, fallback)
    }

    pub fn get_page_dimensions(
        &self,
        manual_rotation: PageRotation,
    ) -> Result<Vec<PageDimension>, PdfError> {
        let guard = self.active_doc.lock();
        let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
        compute_page_dimensions(&active.doc, manual_rotation)
    }

    pub fn render_page(
        &self,
        page_index: u32,
        target_width_px: u32,
        target_height_px: u32,
        manual_rotation: PageRotation,
        dark_mode: bool,
    ) -> Result<RenderedTile, PdfError> {
        let file_hash = {
            let guard = self.active_doc.lock();
            guard
                .as_ref()
                .ok_or(PdfError::InvalidSource)?
                .file_hash
                .clone()
        };

        let cache_key = TileCacheKey::BasePage {
            file_hash,
            page_index,
            width_px: target_width_px,
            height_px: target_height_px,
            rotation: manual_rotation,
            dark_mode,
        };
        if let Some(cached) = self.cache.get(&cache_key) {
            return Ok((*cached).clone());
        }

        let stride = (target_width_px as usize) * 4;
        let mut rgba = vec![255u8; stride * (target_height_px as usize)];
        self.render_page_into_rgba_buffer(
            page_index,
            target_width_px,
            target_height_px,
            stride,
            &mut rgba,
            manual_rotation,
            dark_mode,
        )?;

        let tile = Arc::new(RenderedTile {
            width_px: target_width_px,
            height_px: target_height_px,
            rgba_bytes: rgba,
        });
        self.cache.put(cache_key, Arc::clone(&tile));
        Ok((*tile).clone())
    }

    #[allow(clippy::too_many_arguments)]
    pub fn render_zoom_tile(
        &self,
        page_index: u32,
        full_scaled_width_px: u32,
        full_scaled_height_px: u32,
        visible_x_px: i32,
        visible_y_px: i32,
        tile_width_px: u32,
        tile_height_px: u32,
        manual_rotation: PageRotation,
        dark_mode: bool,
    ) -> Result<RenderedTile, PdfError> {
        let capped_w = tile_width_px.min(MAX_TILE_EDGE_PX).max(1);
        let capped_h = tile_height_px.min(MAX_TILE_EDGE_PX).max(1);

        let file_hash = {
            let guard = self.active_doc.lock();
            guard
                .as_ref()
                .ok_or(PdfError::InvalidSource)?
                .file_hash
                .clone()
        };

        let cache_key = TileCacheKey::ZoomTile {
            file_hash,
            page_index,
            full_scaled_width_px,
            full_scaled_height_px,
            visible_x_px,
            visible_y_px,
            tile_width_px: capped_w,
            tile_height_px: capped_h,
            rotation: manual_rotation,
            dark_mode,
        };
        if let Some(cached) = self.cache.get(&cache_key) {
            return Ok((*cached).clone());
        }

        let stride = (capped_w as usize) * 4;
        let mut rgba = vec![255u8; stride * (capped_h as usize)];
        self.render_zoom_tile_into_rgba_buffer(
            page_index,
            full_scaled_width_px,
            full_scaled_height_px,
            visible_x_px,
            visible_y_px,
            capped_w,
            capped_h,
            stride,
            &mut rgba,
            manual_rotation,
            dark_mode,
        )?;

        let tile = Arc::new(RenderedTile {
            width_px: capped_w,
            height_px: capped_h,
            rgba_bytes: rgba,
        });
        self.cache.put(cache_key, Arc::clone(&tile));
        Ok((*tile).clone())
    }

    pub fn search_document(
        &self,
        query: String,
        manual_rotation: PageRotation,
    ) -> Result<Vec<SearchMatch>, PdfError> {
        self.search_cancel_flag.store(false, Ordering::Release);
        search_document_pages(
            &self.active_doc,
            &self.search_cancel_flag,
            &query,
            manual_rotation,
        )
    }

    pub fn cancel_active_search(&self) {
        self.search_cancel_flag.store(true, Ordering::Release);
    }

    pub fn save_progress(&self, progress: ReadingProgress) -> Result<(), PdfError> {
        self.db.save_progress(&progress)
    }

    pub fn get_recent_files(&self, limit: u32) -> Result<Vec<ReadingProgress>, PdfError> {
        self.db.get_recent_files(limit)
    }

    pub fn remove_recent_file(&self, file_hash: String) -> Result<(), PdfError> {
        self.db.remove_recent_file(&file_hash)
    }

    pub fn raw_engine_ptr(&self) -> i64 {
        self as *const Self as i64
    }

    pub fn close_document(&self) {
        self.cancel_active_search();
        *self.active_doc.lock() = None;
        self.cache.clear();
    }

    pub fn get_page_text_content(
        &self,
        page_index: u32,
        manual_rotation: PageRotation,
    ) -> Result<PageTextContent, PdfError> {
        extract_page_text_content(&self.active_doc, page_index, manual_rotation)
    }

    pub fn add_highlight(
        &self,
        file_hash: String,
        page_index: u32,
        start_char: u32,
        end_char: u32,
        rects: Vec<NormalizedRect>,
        color_argb: u32,
        selected_text: String,
    ) -> Result<HighlightRecord, PdfError> {
        self.db.add_highlight(
            &file_hash,
            page_index,
            start_char,
            end_char,
            &rects,
            color_argb,
            &selected_text,
        )
    }

    pub fn remove_highlight(&self, highlight_id: i64) -> Result<(), PdfError> {
        self.db.remove_highlight(highlight_id)
    }

    pub fn get_highlights_for_file(&self, file_hash: String) -> Result<Vec<HighlightRecord>, PdfError> {
        self.db.get_highlights_for_file(&file_hash)
    }

    pub fn export_pdf(&self, output_path: String, manual_rotation: PageRotation) -> Result<(), PdfError> {
        use pdfium_render::prelude::*;

        tools::ensure_parent_dir(&output_path)?;

        let guard = self.active_doc.lock();
        let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
        let highlights = self.db.get_highlights_for_file(&active.file_hash)?;

        if highlights.is_empty() && manual_rotation == PageRotation::Deg0 {
            std::fs::write(&output_path, active.backing_bytes())
                .map_err(|e| PdfError::EngineError(format!("Failed to write export PDF: {e}")))?;
            return Ok(());
        }

        let pdfium_ref: &Pdfium = unsafe { &*self.pdfium };
        let temp_doc = pdfium_ref
            .load_pdf_from_byte_slice(active.backing_bytes(), None)
            .map_err(|e| PdfError::EngineError(format!("Failed to load export PDF: {e}")))?;

        let mut highlights_by_page: std::collections::HashMap<u16, Vec<&HighlightRecord>> =
            std::collections::HashMap::new();
        for hl in &highlights {
            if let Ok(p_idx) = u16::try_from(hl.page_index) {
                highlights_by_page.entry(p_idx).or_default().push(hl);
            }
        }

        let total_pages = temp_doc.pages().len();
        for page_idx in 0..total_pages {
            let page_hls = highlights_by_page.get(&page_idx);
            if page_hls.is_none() && manual_rotation == PageRotation::Deg0 {
                continue;
            }

            let mut page = temp_doc
                .pages()
                .get(page_idx)
                .map_err(|e| PdfError::EngineError(format!("Export page error: {e}")))?;

            let disp_w = page.width().value;
            let disp_h = page.height().value;
            let intrinsic_deg = match page.rotation() {
                Ok(PdfPageRenderRotation::Degrees90) => 90,
                Ok(PdfPageRenderRotation::Degrees180) => 180,
                Ok(PdfPageRenderRotation::Degrees270) => 270,
                _ => 0,
            };
            let (unrot_w, unrot_h) = match intrinsic_deg {
                90 | 270 => (disp_h, disp_w),
                _ => (disp_w, disp_h),
            };
            let effective_deg = (intrinsic_deg + manual_rotation.degrees()) % 360;

            if manual_rotation != PageRotation::Deg0 {
                let target_rot = match effective_deg {
                    90 => PdfPageRenderRotation::Degrees90,
                    180 => PdfPageRenderRotation::Degrees180,
                    270 => PdfPageRenderRotation::Degrees270,
                    _ => PdfPageRenderRotation::None,
                };
                let _ = page.set_rotation(target_rot);
            }

            if let Some(hls) = page_hls {
                for hl in hls {
                    let a = ((hl.color_argb >> 24) & 0xFF) as u8;
                    let r = ((hl.color_argb >> 16) & 0xFF) as u8;
                    let g = ((hl.color_argb >> 8) & 0xFF) as u8;
                    let b = (hl.color_argb & 0xFF) as u8;
                    let color = PdfColor::new(r, g, b, a.max(102));

                    for rect in &hl.rects {
                        let (u_left, u_top, u_right, u_bottom) = match effective_deg {
                            90 => (rect.top, 1.0 - rect.right, rect.bottom, 1.0 - rect.left),
                            180 => (1.0 - rect.right, 1.0 - rect.bottom, 1.0 - rect.left, 1.0 - rect.top),
                            270 => (1.0 - rect.bottom, rect.left, 1.0 - rect.top, rect.right),
                            _ => (rect.left, rect.top, rect.right, rect.bottom),
                        };

                        let pdf_left = (u_left * unrot_w).clamp(0.0, unrot_w);
                        let pdf_right = (u_right * unrot_w).clamp(0.0, unrot_w);
                        let pdf_top = ((1.0 - u_top) * unrot_h).clamp(0.0, unrot_h);
                        let pdf_bottom = ((1.0 - u_bottom) * unrot_h).clamp(0.0, unrot_h);

                        let bounds = PdfRect::new_from_values(
                            pdf_bottom.min(pdf_top),
                            pdf_left.min(pdf_right),
                            pdf_bottom.max(pdf_top),
                            pdf_left.max(pdf_right),
                        );
                        if let Ok(mut annot) = page.annotations_mut().create_highlight_annotation() {
                            let _ = annot.set_bounds(bounds);
                            let _ = annot.set_fill_color(color);
                        }
                    }
                }
            }
        }

        temp_doc
            .save_to_file(&output_path)
            .map_err(|e| PdfError::EngineError(format!("Failed to save exported PDF: {e}")))?;

        Ok(())
    }

    pub fn get_pdf_page_count(&self, input_path: String) -> Result<u32, PdfError> {
        let pdfium_ref: &Pdfium = unsafe { &*self.pdfium };
        let doc = pdfium_ref
            .load_pdf_from_file(&input_path, None)
            .map_err(|e| PdfError::EngineError(format!("Failed to open PDF: {e}")))?;
        Ok(doc.pages().len() as u32)
    }

    pub fn merge_pdfs(&self, input_paths: Vec<String>, output_path: String) -> Result<(), PdfError> {
        let pdfium_ref: &Pdfium = unsafe { &*self.pdfium };
        tools::merge_pdfs_impl(pdfium_ref, &input_paths, &output_path)
    }

    pub fn split_pdf(
        &self,
        input_path: String,
        page_indices: Vec<u32>,
        output_path: String,
    ) -> Result<(), PdfError> {
        let pdfium_ref: &Pdfium = unsafe { &*self.pdfium };
        tools::split_pdf_impl(pdfium_ref, &input_path, &page_indices, &output_path)
    }

    pub fn images_to_pdf(
        &self,
        image_paths: Vec<String>,
        output_path: String,
    ) -> Result<(), PdfError> {
        let pdfium_ref: &Pdfium = unsafe { &*self.pdfium };
        tools::images_to_pdf_impl(pdfium_ref, &image_paths, &output_path)
    }
}

impl PdfEngine {
    #[cfg(unix)]
    fn open_from_fd_impl(
        &self,
        raw_fd: i32,
        file_uri: String,
        display_name: String,
        fallback_cache_path: Option<String>,
    ) -> Result<DocumentOpenResult, PdfError> {
        use std::os::unix::io::FromRawFd;
        if raw_fd < 0 {
            return Err(PdfError::InvalidSource);
        }
        let file = unsafe { File::from_raw_fd(raw_fd) };
        self.open_file_internal(file, file_uri, display_name, fallback_cache_path)
    }

    #[cfg(not(unix))]
    fn open_from_fd_impl(
        &self,
        _raw_fd: i32,
        _file_uri: String,
        _display_name: String,
        _fallback_cache_path: Option<String>,
    ) -> Result<DocumentOpenResult, PdfError> {
        Err(PdfError::InvalidSource)
    }

    fn pdfium_ref(&self) -> &'static Pdfium {
        unsafe { &*self.pdfium }
    }

    fn open_file_internal(
        &self,
        file: File,
        file_uri: String,
        display_name: String,
        fallback_cache_path: Option<String>,
    ) -> Result<DocumentOpenResult, PdfError> {
        self.cancel_active_search();

        // Close any previously open document before loading the new one
        {
            *self.active_doc.lock() = None;
        }
        self.cache.clear();

        let active = load_document_from_file(self.pdfium_ref(), file)?;
        let file_hash = active.file_hash.clone();
        let total_pages = active.doc.pages().len() as u32;

        let now_unix = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0);

        let existing = self.db.get_progress(&file_hash)?;
        let progress = match existing {
            Some(mut saved) => {
                saved.file_uri = file_uri;
                if fallback_cache_path.is_some() {
                    saved.fallback_cache_path = fallback_cache_path;
                }
                saved.display_name = display_name;
                saved.total_pages = total_pages;
                saved.last_opened_unix = now_unix;
                saved
            }
            None => ReadingProgress {
                file_hash,
                file_uri,
                fallback_cache_path,
                display_name,
                last_page_index: 0,
                page_offset_fraction: 0.0,
                zoom_scale: 1.0,
                pan_offset_x_fraction: 0.0,
                manual_rotation: PageRotation::Deg0,
                dark_mode_enabled: false,
                last_opened_unix: now_unix,
                total_pages,
            },
        };

        let dimensions = compute_page_dimensions(&active.doc, progress.manual_rotation)?;
        self.db.save_progress(&progress)?;
        *self.active_doc.lock() = Some(active);

        Ok(DocumentOpenResult {
            progress,
            dimensions,
        })
    }

    #[allow(clippy::too_many_arguments)]
    pub(crate) fn render_page_into_rgba_buffer(
        &self,
        page_index: u32,
        width_px: u32,
        height_px: u32,
        stride_bytes: usize,
        buffer: &mut [u8],
        manual_rotation: PageRotation,
        dark_mode: bool,
    ) -> Result<(), PdfError> {
        let guard = self.active_doc.lock();
        let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
        render_page_into_buffer(
            self.pdfium_ref(),
            &active.doc,
            page_index,
            width_px,
            height_px,
            stride_bytes,
            buffer,
            manual_rotation,
            dark_mode,
        )
    }

    #[allow(clippy::too_many_arguments)]
    pub(crate) fn render_zoom_tile_into_rgba_buffer(
        &self,
        page_index: u32,
        full_scaled_width_px: u32,
        full_scaled_height_px: u32,
        visible_x_px: i32,
        visible_y_px: i32,
        tile_width_px: u32,
        tile_height_px: u32,
        stride_bytes: usize,
        buffer: &mut [u8],
        manual_rotation: PageRotation,
        dark_mode: bool,
    ) -> Result<(), PdfError> {
        let capped_w = tile_width_px.min(MAX_TILE_EDGE_PX).max(1);
        let capped_h = tile_height_px.min(MAX_TILE_EDGE_PX).max(1);
        let guard = self.active_doc.lock();
        let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
        render_zoom_tile_into_buffer(
            self.pdfium_ref(),
            &active.doc,
            page_index,
            full_scaled_width_px,
            full_scaled_height_px,
            visible_x_px,
            visible_y_px,
            capped_w,
            capped_h,
            stride_bytes,
            buffer,
            manual_rotation,
            dark_mode,
        )
    }
}

#[cfg(test)]
mod pdfium_e2e_tests {
    use super::*;
    use pdfium_render::prelude::*;
    use std::path::PathBuf;

    fn win_pdfium_dir() -> Option<String> {
        let dir = PathBuf::from(env!("CARGO_MANIFEST_DIR"))
            .join("..")
            .join(".tools")
            .join("pdfium-win")
            .join("bin");
        if dir.join("pdfium.dll").exists() {
            Some(dir.to_string_lossy().into_owned())
        } else {
            None
        }
    }

    #[test]
    fn e2e_render_zoom_tile_and_search_alignment() {
        let Some(pdfium_dir) = win_pdfium_dir() else {
            return;
        };
        let pdfium = bind_pdfium(&pdfium_dir).expect("bind pdfium.dll");

        let mut doc = pdfium.create_new_pdf().expect("create pdf");
        let font = doc.fonts_mut().helvetica();

        // Page 0: portrait 400x800, text near top-left (x=40, y=700 in bottom-left pts)
        {
            let mut p0 = doc
                .pages_mut()
                .create_page_at_end(PdfPagePaperSize::Custom(
                    PdfPoints::new(400.0),
                    PdfPoints::new(800.0),
                ))
                .expect("page 0");
            let mut obj = PdfPageTextObject::new(&doc, "VelocityTest", font, PdfPoints::new(24.0))
                .expect("text obj");
            obj.translate(PdfPoints::new(40.0), PdfPoints::new(700.0))
                .expect("translate");
            p0.objects_mut().add_text_object(obj).expect("add text");
        }

        // Page 1: intrinsic 90° rotation
        {
            let mut p1 = doc
                .pages_mut()
                .create_page_at_end(PdfPagePaperSize::Custom(
                    PdfPoints::new(400.0),
                    PdfPoints::new(800.0),
                ))
                .expect("page 1");
            let mut obj = PdfPageTextObject::new(&doc, "RotatedHit", font, PdfPoints::new(24.0))
                .expect("text obj 1");
            obj.translate(PdfPoints::new(40.0), PdfPoints::new(700.0))
                .expect("translate 1");
            p1.objects_mut().add_text_object(obj).expect("add text 1");
            p1.set_rotation(PdfPageRenderRotation::Degrees90);
        }

        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        let pdf_path = std::env::temp_dir().join(format!("vel_e2e_{nanos}.pdf"));
        let db_path = std::env::temp_dir().join(format!("vel_e2e_{nanos}.db"));
        doc.save_to_file(&pdf_path).expect("save pdf");
        // Release the test's Pdfium instance before creating PdfEngine (Pdfium's thread_safe lock is per-instance).
        drop(doc);
        drop(pdfium);

        let engine = PdfEngine::new(
            db_path.to_string_lossy().into_owned(),
            pdfium_dir,
        )
        .expect("engine new");

        let open_res = engine
            .open_from_path(
                pdf_path.to_string_lossy().into_owned(),
                "file://e2e.pdf".into(),
                "e2e.pdf".into(),
            )
            .expect("open pdf");
        assert_eq!(open_res.dimensions.len(), 2);
        // Page 1 has intrinsic 90° so width > height
        assert!(open_res.dimensions[1].width_pts > open_res.dimensions[1].height_pts);

        // Verify search highlight falls inside the actual rendered dark pixels for all 4 rotations
        for rot in [
            PageRotation::Deg0,
            PageRotation::Deg90,
            PageRotation::Deg180,
            PageRotation::Deg270,
        ] {
            let dims = engine.get_page_dimensions(rot).expect("dims");
            let w = 400u32;
            let h = (w as f32 / dims[0].aspect_ratio).round() as u32;
            let tile = engine
                .render_page(0, w, h, rot, false)
                .expect("render page 0");

            let matches = engine
                .search_document("VelocityTest".into(), rot)
                .expect("search");
            assert_eq!(matches.len(), 2.min(matches.len()).max(1));
            let m0 = matches.iter().find(|m| m.page_index == 0).expect("page 0 match");
            let rect = &m0.rects[0];

            // Count dark pixels inside the highlight box on rendered page 0
            let x0 = ((rect.left * w as f32).floor() as u32).saturating_sub(2);
            let x1 = ((rect.right * w as f32).ceil() as u32 + 2).min(w);
            let y0 = ((rect.top * h as f32).floor() as u32).saturating_sub(2);
            let y1 = ((rect.bottom * h as f32).ceil() as u32 + 2).min(h);

            let mut dark_pixels = 0usize;
            for y in y0..y1 {
                for x in x0..x1 {
                    let idx = ((y * w + x) * 4) as usize;
                    if tile.rgba_bytes[idx] < 200 {
                        dark_pixels += 1;
                    }
                }
            }
            assert!(
                dark_pixels > 20,
                "Expected rendered text pixels inside search box for {rot:?}, rect={rect:?}, found {dark_pixels}"
            );

            // Also verify zoom tile covering the match box contains dark pixels
            let full_w = w * 2;
            let full_h = h * 2;
            let vis_x = (rect.left * full_w as f32).floor() as i32;
            let vis_y = (rect.top * full_h as f32).floor() as i32;
            let ztile = engine
                .render_zoom_tile(0, full_w, full_h, vis_x, vis_y, 200, 100, rot, false)
                .expect("zoom tile");
            let z_dark = ztile
                .rgba_bytes
                .chunks_exact(4)
                .filter(|px| px[0] < 200)
                .count();
            assert!(
                z_dark > 20,
                "Expected rendered text pixels inside zoom tile for {rot:?}, found {z_dark}"
            );
        }

        // Verify intrinsically rotated page 1 search highlight aligns with rendered pixels too
        let dims = engine.get_page_dimensions(PageRotation::Deg0).expect("dims");
        let w1 = 400u32;
        let h1 = (w1 as f32 / dims[1].aspect_ratio).round() as u32;
        let tile1 = engine
            .render_page(1, w1, h1, PageRotation::Deg0, false)
            .expect("render page 1");
        let matches1 = engine
            .search_document("RotatedHit".into(), PageRotation::Deg0)
            .expect("search page 1");
        let m1 = matches1.iter().find(|m| m.page_index == 1).expect("page 1 match");
        let rect1 = &m1.rects[0];
        let x0 = ((rect1.left * w1 as f32).floor() as u32).saturating_sub(2);
        let x1 = ((rect1.right * w1 as f32).ceil() as u32 + 2).min(w1);
        let y0 = ((rect1.top * h1 as f32).floor() as u32).saturating_sub(2);
        let y1 = ((rect1.bottom * h1 as f32).ceil() as u32 + 2).min(h1);
        let mut dark1 = 0usize;
        for y in y0..y1 {
            for x in x0..x1 {
                let idx = ((y * w1 + x) * 4) as usize;
                if tile1.rgba_bytes[idx] < 200 {
                    dark1 += 1;
                }
            }
        }
        assert!(
            dark1 > 20,
            "Expected rendered text pixels on intrinsically rotated page 1 inside {rect1:?}, found {dark1}"
        );

        engine.close_document();
        drop(engine);
        let _ = std::fs::remove_file(&pdf_path);
        let _ = std::fs::remove_file(&db_path);
    }
}
