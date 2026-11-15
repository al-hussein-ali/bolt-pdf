use std::path::Path;

use image::imageops::FilterType;
use image::ImageReader;
use pdfium_render::prelude::*;

use crate::PdfError;

const MAX_IMAGE_EDGE_PX: u32 = 2400;

pub fn ensure_parent_dir(output_path: &str) -> Result<(), PdfError> {
    if let Some(parent) = Path::new(output_path).parent() {
        if !parent.as_os_str().is_empty() {
            std::fs::create_dir_all(parent)
                .map_err(|e| PdfError::EngineError(format!("Failed to create output dir: {e}")))?;
        }
    }
    Ok(())
}

pub fn merge_pdfs_impl(
    pdfium: &Pdfium,
    input_paths: &[String],
    output_path: &str,
) -> Result<(), PdfError> {
    if input_paths.is_empty() {
        return Err(PdfError::InvalidSource);
    }
    ensure_parent_dir(output_path)?;

    let mut dest_doc = pdfium
        .create_new_pdf()
        .map_err(|e| PdfError::EngineError(format!("Failed to create PDF: {e}")))?;

    for path in input_paths {
        let src_doc = pdfium
            .load_pdf_from_file(path, None)
            .map_err(|e| PdfError::EngineError(format!("Failed to load PDF '{path}': {e}")))?;
        dest_doc
            .pages_mut()
            .append(&src_doc)
            .map_err(|e| PdfError::EngineError(format!("Failed to append pages from '{path}': {e}")))?;
    }

    dest_doc
        .save_to_file(output_path)
        .map_err(|e| PdfError::EngineError(format!("Failed to save merged PDF: {e}")))?;
    Ok(())
}

pub fn split_pdf_impl(
    pdfium: &Pdfium,
    input_path: &str,
    page_indices: &[u32],
    output_path: &str,
) -> Result<(), PdfError> {
    if page_indices.is_empty() {
        return Err(PdfError::InvalidSource);
    }
    ensure_parent_dir(output_path)?;

    let src_doc = pdfium
        .load_pdf_from_file(input_path, None)
        .map_err(|e| PdfError::EngineError(format!("Failed to open PDF: {e}")))?;
    let total = src_doc.pages().len() as u32;

    let mut dest_doc = pdfium
        .create_new_pdf()
        .map_err(|e| PdfError::EngineError(format!("Failed to create PDF: {e}")))?;

    for (dest_idx, &src_idx) in page_indices.iter().enumerate() {
        if src_idx >= total {
            return Err(PdfError::PageOutOfBounds(src_idx));
        }
        let src_u16 = u16::try_from(src_idx).map_err(|_| PdfError::PageOutOfBounds(src_idx))?;
        let dest_u16 = u16::try_from(dest_idx)
            .map_err(|_| PdfError::EngineError("Too many pages in split output".into()))?;
        dest_doc
            .pages_mut()
            .copy_page_from_document(&src_doc, src_u16, dest_u16)
            .map_err(|e| PdfError::EngineError(format!("Failed to copy page {}: {e}", src_idx + 1)))?;
    }

    dest_doc
        .save_to_file(output_path)
        .map_err(|e| PdfError::EngineError(format!("Failed to save split PDF: {e}")))?;
    Ok(())
}

pub fn images_to_pdf_impl(
    pdfium: &Pdfium,
    image_paths: &[String],
    output_path: &str,
) -> Result<(), PdfError> {
    if image_paths.is_empty() {
        return Err(PdfError::InvalidSource);
    }
    ensure_parent_dir(output_path)?;

    let mut dest_doc = pdfium
        .create_new_pdf()
        .map_err(|e| PdfError::EngineError(format!("Failed to create PDF: {e}")))?;

    for path in image_paths {
        let mut dyn_img = ImageReader::open(path)
            .map_err(|e| PdfError::EngineError(format!("Failed to open image '{path}': {e}")))?
            .with_guessed_format()
            .map_err(|e| PdfError::EngineError(format!("Failed to detect image format '{path}': {e}")))?
            .decode()
            .map_err(|e| PdfError::EngineError(format!("Failed to decode image '{path}': {e}")))?;
        let max_dim = dyn_img.width().max(dyn_img.height());
        if max_dim > MAX_IMAGE_EDGE_PX {
            let scale = MAX_IMAGE_EDGE_PX as f32 / max_dim as f32;
            let nw = ((dyn_img.width() as f32 * scale).round() as u32).max(1);
            let nh = ((dyn_img.height() as f32 * scale).round() as u32).max(1);
            dyn_img = dyn_img.resize(nw, nh, FilterType::Triangle);
        }
        let width_pts = (dyn_img.width() as f32 * 72.0 / 150.0).max(72.0);
        let height_pts = (dyn_img.height() as f32 * 72.0 / 150.0).max(72.0);
        let paper = PdfPagePaperSize::Custom(PdfPoints::new(width_pts), PdfPoints::new(height_pts));
        let mut page = dest_doc
            .pages_mut()
            .create_page_at_end(paper)
            .map_err(|e| PdfError::EngineError(format!("Failed to create PDF page: {e}")))?;
        page.objects_mut()
            .create_image_object(
                PdfPoints::ZERO,
                PdfPoints::ZERO,
                &dyn_img,
                Some(PdfPoints::new(width_pts)),
                Some(PdfPoints::new(height_pts)),
            )
            .map_err(|e| PdfError::EngineError(format!("Failed to embed image: {e}")))?;
        page.regenerate_content()
            .map_err(|e| PdfError::EngineError(format!("Failed to finalize PDF page: {e}")))?;
    }

    dest_doc
        .save_to_file(output_path)
        .map_err(|e| PdfError::EngineError(format!("Failed to save PDF: {e}")))?;
    Ok(())
}
