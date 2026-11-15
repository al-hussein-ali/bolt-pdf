use std::fs::File;
use std::io::{Read, Seek, SeekFrom};
use std::pin::Pin;

use memmap2::Mmap;
use pdfium_render::prelude::*;

use crate::db::compute_file_hash;
use crate::{PageDimension, PageRotation, PdfError};

pub enum DocumentBacking {
    Mmap(Pin<Box<Mmap>>),
    Bytes(Pin<Box<Vec<u8>>>),
}

pub struct ActiveDocument {
    // Drop `doc` before `_backing` so PDFium closes before freeing/unmapping memory.
    pub doc: PdfDocument<'static>,
    _backing: DocumentBacking,
    pub file_hash: String,
}

impl ActiveDocument {
    pub fn backing_bytes(&self) -> &[u8] {
        match &self._backing {
            DocumentBacking::Mmap(mmap) => mmap.as_ref().get_ref(),
            DocumentBacking::Bytes(bytes) => bytes.as_ref().get_ref().as_slice(),
        }
    }
}

unsafe impl Send for ActiveDocument {}

pub fn bind_pdfium(pdfium_lib_dir: &str) -> Result<Pdfium, PdfError> {
    let bindings = if !pdfium_lib_dir.is_empty() {
        let lib_path = Pdfium::pdfium_platform_library_name_at_path(pdfium_lib_dir);
        Pdfium::bind_to_library(lib_path)
            .or_else(|_| Pdfium::bind_to_system_library())
    } else {
        Pdfium::bind_to_system_library()
    }
    .map_err(|e| PdfError::EngineError(format!("Failed to bind libpdfium: {e}")))?;

    Ok(Pdfium::new(bindings))
}

pub fn load_document_from_file(
    pdfium: &'static Pdfium,
    mut file: File,
) -> Result<ActiveDocument, PdfError> {
    let (backing, slice_static): (DocumentBacking, &'static [u8]) =
        match unsafe { Mmap::map(&file) } {
            Ok(mmap) if !mmap.is_empty() => {
                let pinned = Box::pin(mmap);
                let slice = unsafe {
                    std::slice::from_raw_parts(pinned.as_ptr(), pinned.len())
                };
                (DocumentBacking::Mmap(pinned), slice)
            }
            _ => {
                // Some Android content:// FDs (pipes/virtual providers) do not support mmap().
                // Fall back to reading the stream into memory.
                let _ = file.seek(SeekFrom::Start(0));
                let mut bytes = Vec::new();
                file.read_to_end(&mut bytes)
                    .map_err(|_| PdfError::InvalidSource)?;
                if bytes.is_empty() {
                    return Err(PdfError::InvalidSource);
                }
                let pinned = Box::pin(bytes);
                let slice = unsafe {
                    std::slice::from_raw_parts(pinned.as_ptr(), pinned.len())
                };
                (DocumentBacking::Bytes(pinned), slice)
            }
        };

    let file_hash = compute_file_hash(slice_static);

    let doc = pdfium
        .load_pdf_from_byte_slice(slice_static, None)
        .map_err(map_pdfium_open_error)?;

    Ok(ActiveDocument {
        doc,
        _backing: backing,
        file_hash,
    })
}

fn map_pdfium_open_error(err: PdfiumError) -> PdfError {
    let msg = err.to_string();
    let lower = msg.to_ascii_lowercase();
    if lower.contains("password") || lower.contains("security") || lower.contains("encrypted") {
        PdfError::PasswordRequired
    } else {
        PdfError::EngineError(msg)
    }
}

pub fn to_pdfium_rotation(rot: PageRotation) -> PdfPageRenderRotation {
    match rot {
        PageRotation::Deg0 => PdfPageRenderRotation::None,
        PageRotation::Deg90 => PdfPageRenderRotation::Degrees90,
        PageRotation::Deg180 => PdfPageRenderRotation::Degrees180,
        PageRotation::Deg270 => PdfPageRenderRotation::Degrees270,
    }
}

pub fn compute_page_dimensions(
    doc: &PdfDocument<'_>,
    manual_rotation: PageRotation,
) -> Result<Vec<PageDimension>, PdfError> {
    let pages = doc.pages();
    let count = pages.len();
    let mut dims = Vec::with_capacity(count as usize);

    for idx in 0..count {
        let size = pages
            .page_size(idx)
            .map_err(|e| PdfError::EngineError(format!("Failed to read page size {idx}: {e}")))?;
        let raw_w = size.width().value.max(1.0);
        let raw_h = size.height().value.max(1.0);
        let (width_pts, height_pts) = match manual_rotation {
            PageRotation::Deg90 | PageRotation::Deg270 => (raw_h, raw_w),
            PageRotation::Deg0 | PageRotation::Deg180 => (raw_w, raw_h),
        };
        dims.push(PageDimension {
            page_index: idx as u32,
            width_pts,
            height_pts,
            aspect_ratio: width_pts / height_pts,
        });
    }

    Ok(dims)
}

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

pub fn apply_night_filter_strided(buf: &mut [u8], width_px: u32, height_px: u32, stride_bytes: usize) {
    let row_bytes = (width_px as usize) * 4;
    if stride_bytes == row_bytes {
        apply_night_filter_rgba(&mut buf[..row_bytes * (height_px as usize)]);
    } else {
        for y in 0..(height_px as usize) {
            let start = y * stride_bytes;
            let end = start + row_bytes;
            if end <= buf.len() {
                apply_night_filter_rgba(&mut buf[start..end]);
            }
        }
    }
}

pub fn render_page_into_buffer(
    pdfium: &Pdfium,
    doc: &PdfDocument<'_>,
    page_index: u32,
    width_px: u32,
    height_px: u32,
    stride_bytes: usize,
    buffer: &mut [u8],
    manual_rotation: PageRotation,
    dark_mode: bool,
) -> Result<(), PdfError> {
    if width_px == 0 || height_px == 0 {
        return Err(PdfError::EngineError("Invalid zero render dimensions".into()));
    }
    let page_u16 = u16::try_from(page_index).map_err(|_| PdfError::PageOutOfBounds(page_index))?;
    let page = doc
        .pages()
        .get(page_u16)
        .map_err(|_| PdfError::PageOutOfBounds(page_index))?;

    let row_bytes = (width_px as usize) * 4;
    let required_bytes = stride_bytes.saturating_mul(height_px as usize);
    if stride_bytes < row_bytes || buffer.len() < required_bytes {
        return Err(PdfError::EngineError("Destination bitmap buffer too small".into()));
    }

    if stride_bytes == row_bytes {
        let mut bitmap = unsafe {
            PdfBitmap::from_bytes(
                width_px as Pixels,
                height_px as Pixels,
                PdfBitmapFormat::BGRA,
                &mut buffer[..required_bytes],
                pdfium.bindings(),
            )
        }
        .map_err(|e| PdfError::EngineError(format!("Bitmap wrap error: {e}")))?;

        let config = PdfRenderConfig::new()
            .set_fixed_size(width_px as Pixels, height_px as Pixels)
            .rotate(to_pdfium_rotation(manual_rotation), false)
            .set_clear_color(PdfColor::WHITE)
            .clear_before_rendering(true)
            .set_reverse_byte_order(true);

        page.render_into_bitmap_with_config(&mut bitmap, &config)
            .map_err(|e| PdfError::EngineError(format!("Render error: {e}")))?;
    } else {
        // Fallback when AndroidBitmap stride has padding bytes
        let mut packed = vec![255u8; row_bytes * (height_px as usize)];
        {
            let mut bitmap = unsafe {
                PdfBitmap::from_bytes(
                    width_px as Pixels,
                    height_px as Pixels,
                    PdfBitmapFormat::BGRA,
                    &mut packed,
                    pdfium.bindings(),
                )
            }
            .map_err(|e| PdfError::EngineError(format!("Bitmap wrap error: {e}")))?;

            let config = PdfRenderConfig::new()
                .set_fixed_size(width_px as Pixels, height_px as Pixels)
                .rotate(to_pdfium_rotation(manual_rotation), false)
                .set_clear_color(PdfColor::WHITE)
                .clear_before_rendering(true)
                .set_reverse_byte_order(true);

            page.render_into_bitmap_with_config(&mut bitmap, &config)
                .map_err(|e| PdfError::EngineError(format!("Render error: {e}")))?;
        }
        for y in 0..(height_px as usize) {
            let src_start = y * row_bytes;
            let dst_start = y * stride_bytes;
            buffer[dst_start..dst_start + row_bytes]
                .copy_from_slice(&packed[src_start..src_start + row_bytes]);
        }
    }

    if dark_mode {
        apply_night_filter_strided(buffer, width_px, height_px, stride_bytes);
    }

    Ok(())
}

pub fn render_zoom_tile_into_buffer(
    pdfium: &Pdfium,
    doc: &PdfDocument<'_>,
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
    if tile_width_px == 0
        || tile_height_px == 0
        || full_scaled_width_px == 0
        || full_scaled_height_px == 0
    {
        return Err(PdfError::EngineError("Invalid zero tile dimensions".into()));
    }

    let page_u16 = u16::try_from(page_index).map_err(|_| PdfError::PageOutOfBounds(page_index))?;
    let page = doc
        .pages()
        .get(page_u16)
        .map_err(|_| PdfError::PageOutOfBounds(page_index))?;

    let row_bytes = (tile_width_px as usize) * 4;
    let required_bytes = stride_bytes.saturating_mul(tile_height_px as usize);
    if stride_bytes < row_bytes || buffer.len() < required_bytes {
        return Err(PdfError::EngineError("Destination tile buffer too small".into()));
    }

    let source_w = page.width().value.max(1.0);
    let source_h = page.height().value.max(1.0);

    let (eff_w, eff_h) = match manual_rotation {
        PageRotation::Deg90 | PageRotation::Deg270 => (source_h, source_w),
        PageRotation::Deg0 | PageRotation::Deg180 => (source_w, source_h),
    };

    let scale_x = full_scaled_width_px as f32 / eff_w;
    let scale_y = full_scaled_height_px as f32 / eff_h;

    let vx = visible_x_px as f32;
    let vy = visible_y_px as f32;
    let matrix = match manual_rotation {
        PageRotation::Deg0 => PdfMatrix::new(scale_x, 0.0, 0.0, scale_y, -vx, -vy),
        PageRotation::Deg90 => PdfMatrix::new(
            0.0,
            scale_y,
            -scale_x,
            0.0,
            scale_x * source_h - vx,
            -vy,
        ),
        PageRotation::Deg180 => PdfMatrix::new(
            -scale_x,
            0.0,
            0.0,
            -scale_y,
            scale_x * source_w - vx,
            scale_y * source_h - vy,
        ),
        PageRotation::Deg270 => PdfMatrix::new(
            0.0,
            -scale_y,
            scale_x,
            0.0,
            -vx,
            scale_y * source_w - vy,
        ),
    };

    let config = PdfRenderConfig::new()
        .set_fixed_size(tile_width_px as Pixels, tile_height_px as Pixels)
        .render_form_data(false)
        .set_clear_color(PdfColor::WHITE)
        .clear_before_rendering(true)
        .set_reverse_byte_order(true)
        .clip(0, 0, tile_width_px as Pixels, tile_height_px as Pixels)
        .apply_matrix(matrix)
        .map_err(|e| PdfError::EngineError(format!("Tile matrix error: {e}")))?;

    if stride_bytes == row_bytes {
        let mut bitmap = unsafe {
            PdfBitmap::from_bytes(
                tile_width_px as Pixels,
                tile_height_px as Pixels,
                PdfBitmapFormat::BGRA,
                &mut buffer[..required_bytes],
                pdfium.bindings(),
            )
        }
        .map_err(|e| PdfError::EngineError(format!("Tile bitmap error: {e}")))?;

        page.render_into_bitmap_with_config(&mut bitmap, &config)
            .map_err(|e| PdfError::EngineError(format!("Tile render error: {e}")))?;
    } else {
        let mut packed = vec![255u8; row_bytes * (tile_height_px as usize)];
        {
            let mut bitmap = unsafe {
                PdfBitmap::from_bytes(
                    tile_width_px as Pixels,
                    tile_height_px as Pixels,
                    PdfBitmapFormat::BGRA,
                    &mut packed,
                    pdfium.bindings(),
                )
            }
            .map_err(|e| PdfError::EngineError(format!("Tile bitmap error: {e}")))?;

            page.render_into_bitmap_with_config(&mut bitmap, &config)
                .map_err(|e| PdfError::EngineError(format!("Tile render error: {e}")))?;
        }
        for y in 0..(tile_height_px as usize) {
            let src_start = y * row_bytes;
            let dst_start = y * stride_bytes;
            buffer[dst_start..dst_start + row_bytes]
                .copy_from_slice(&packed[src_start..src_start + row_bytes]);
        }
    }

    if dark_mode {
        apply_night_filter_strided(buffer, tile_width_px, tile_height_px, stride_bytes);
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn night_filter_maps_white_and_black_accurately() {
        let mut buf = [
            255, 255, 255, 255, // white -> #141414 (20)
            0, 0, 0, 255,       // black -> #E6E6E6 (230)
        ];
        apply_night_filter_rgba(&mut buf);
        assert_eq!(&buf[0..4], &[20, 20, 20, 255]);
        assert_eq!(&buf[4..8], &[230, 230, 230, 255]);
    }
}

