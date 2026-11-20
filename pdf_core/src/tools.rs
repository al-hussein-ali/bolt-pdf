use std::collections::HashMap;
use std::path::Path;

use image::imageops::FilterType;
use image::ImageReader;
use pdfium_render::prelude::*;

use crate::PdfError;

#[derive(Debug, Clone, uniffi::Enum)]
pub enum EditPageSource {
    Original { page_index: u32 },
    Blank { width_pts: f32, height_pts: f32 },
    ExternalPdf { path: String, page_index: u32 },
    Image { path: String },
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct PdfTextEditOp {
    pub cover_left: Option<f32>,
    pub cover_top: Option<f32>,
    pub cover_right: Option<f32>,
    pub cover_bottom: Option<f32>,
    pub cover_color_argb: u32,
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
    pub text: String,
    pub font_size_pts: f32,
    pub color_argb: u32,
    pub is_bold: bool,
    pub overlay_image_path: Option<String>,
}

#[derive(Debug, Clone, uniffi::Record)]
pub struct EditPageSpec {
    pub source: EditPageSource,
    pub rotation_degrees: i32,
    pub text_edits: Vec<PdfTextEditOp>,
}

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

fn argb_to_pdf_color(argb: u32) -> PdfColor {
    let a = ((argb >> 24) & 0xFF) as u8;
    let r = ((argb >> 16) & 0xFF) as u8;
    let g = ((argb >> 8) & 0xFF) as u8;
    let b = (argb & 0xFF) as u8;
    PdfColor::new(r, g, b, a)
}

fn norm_rect_to_pdf_rect(
    left: f32,
    top: f32,
    right: f32,
    bottom: f32,
    effective_deg: i32,
    unrot_w: f32,
    unrot_h: f32,
) -> PdfRect {
    let (u_left, u_top, u_right, u_bottom) = match effective_deg {
        90 => (top, 1.0 - right, bottom, 1.0 - left),
        180 => (1.0 - right, 1.0 - bottom, 1.0 - left, 1.0 - top),
        270 => (1.0 - bottom, left, 1.0 - top, right),
        _ => (left, top, right, bottom),
    };

    let pdf_left = (u_left * unrot_w).clamp(0.0, unrot_w);
    let pdf_right = (u_right * unrot_w).clamp(0.0, unrot_w);
    let pdf_top = ((1.0 - u_top) * unrot_h).clamp(0.0, unrot_h);
    let pdf_bottom = ((1.0 - u_bottom) * unrot_h).clamp(0.0, unrot_h);

    PdfRect::new_from_values(
        pdf_bottom.min(pdf_top),
        pdf_left.min(pdf_right),
        pdf_bottom.max(pdf_top),
        pdf_left.max(pdf_right),
    )
}

pub fn apply_pdf_edits_impl(
    pdfium: &Pdfium,
    input_path: &str,
    pages: &[EditPageSpec],
    output_path: &str,
) -> Result<(), PdfError> {
    if pages.is_empty() {
        return Err(PdfError::InvalidSource);
    }
    ensure_parent_dir(output_path)?;

    let src_doc = pdfium
        .load_pdf_from_file(input_path, None)
        .map_err(|e| PdfError::EngineError(format!("Failed to open PDF: {e}")))?;
    let src_total = src_doc.pages().len() as u32;

    let mut external_docs: HashMap<String, PdfDocument<'_>> = HashMap::new();
    for spec in pages {
        if let EditPageSource::ExternalPdf { path, .. } = &spec.source {
            if !external_docs.contains_key(path) {
                let ext_doc = pdfium
                    .load_pdf_from_file(path, None)
                    .map_err(|e| PdfError::EngineError(format!("Failed to open PDF '{path}': {e}")))?;
                external_docs.insert(path.clone(), ext_doc);
            }
        }
    }

    let mut dest_doc = pdfium
        .create_new_pdf()
        .map_err(|e| PdfError::EngineError(format!("Failed to create PDF: {e}")))?;

    let font_regular = dest_doc.fonts_mut().helvetica();
    let font_bold = dest_doc.fonts_mut().helvetica_bold();

    for (dest_idx, spec) in pages.iter().enumerate() {
        let dest_u16 = u16::try_from(dest_idx)
            .map_err(|_| PdfError::EngineError("Too many pages in edited output".into()))?;

        match &spec.source {
            EditPageSource::Original { page_index } => {
                if *page_index >= src_total {
                    return Err(PdfError::PageOutOfBounds(*page_index));
                }
                let src_u16 = u16::try_from(*page_index)
                    .map_err(|_| PdfError::PageOutOfBounds(*page_index))?;
                dest_doc
                    .pages_mut()
                    .copy_page_from_document(&src_doc, src_u16, dest_u16)
                    .map_err(|e| PdfError::EngineError(format!("Failed to copy page {}: {e}", page_index + 1)))?;
            }
            EditPageSource::Blank { width_pts, height_pts } => {
                let w = width_pts.max(72.0);
                let h = height_pts.max(72.0);
                let paper = PdfPagePaperSize::Custom(PdfPoints::new(w), PdfPoints::new(h));
                dest_doc
                    .pages_mut()
                    .create_page_at_end(paper)
                    .map_err(|e| PdfError::EngineError(format!("Failed to create blank page: {e}")))?;
            }
            EditPageSource::ExternalPdf { path, page_index } => {
                let ext_doc = external_docs
                    .get(path)
                    .ok_or_else(|| PdfError::EngineError(format!("Missing external PDF '{path}'")))?;
                let ext_total = ext_doc.pages().len() as u32;
                if *page_index >= ext_total {
                    return Err(PdfError::PageOutOfBounds(*page_index));
                }
                let src_u16 = u16::try_from(*page_index)
                    .map_err(|_| PdfError::PageOutOfBounds(*page_index))?;
                dest_doc
                    .pages_mut()
                    .copy_page_from_document(ext_doc, src_u16, dest_u16)
                    .map_err(|e| PdfError::EngineError(format!("Failed to copy external page: {e}")))?;
            }
            EditPageSource::Image { path } => {
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
                    .map_err(|e| PdfError::EngineError(format!("Failed to create image page: {e}")))?;
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
                    .map_err(|e| PdfError::EngineError(format!("Failed to finalize image page: {e}")))?;
            }
        }

        let extra_deg = spec.rotation_degrees.rem_euclid(360);
        if extra_deg == 0 && spec.text_edits.is_empty() {
            continue;
        }

        let mut page = dest_doc
            .pages_mut()
            .get(dest_u16)
            .map_err(|e| PdfError::EngineError(format!("Failed to access edited page: {e}")))?;

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
        let effective_deg = (intrinsic_deg + extra_deg) % 360;

        if extra_deg != 0 {
            let target_rot = match effective_deg {
                90 => PdfPageRenderRotation::Degrees90,
                180 => PdfPageRenderRotation::Degrees180,
                270 => PdfPageRenderRotation::Degrees270,
                _ => PdfPageRenderRotation::None,
            };
            let _ = page.set_rotation(target_rot);
        }

        for op in &spec.text_edits {
            if let (Some(cl), Some(ct), Some(cr), Some(cb)) = (
                op.cover_left,
                op.cover_top,
                op.cover_right,
                op.cover_bottom,
            ) {
                let cover_rect = norm_rect_to_pdf_rect(
                    cl,
                    ct,
                    cr,
                    cb,
                    effective_deg,
                    unrot_w,
                    unrot_h,
                );
                let fill = argb_to_pdf_color(op.cover_color_argb);
                let _ = page
                    .objects_mut()
                    .create_path_object_rect(cover_rect, None, None, Some(fill));
            }

            let target_rect = norm_rect_to_pdf_rect(
                op.left,
                op.top,
                op.right,
                op.bottom,
                effective_deg,
                unrot_w,
                unrot_h,
            );
            let rect_w = (target_rect.right().value - target_rect.left().value).max(1.0);
            let rect_h = (target_rect.top().value - target_rect.bottom().value).max(1.0);

            if let Some(img_path) = &op.overlay_image_path {
                if let Ok(reader) = ImageReader::open(img_path) {
                    if let Ok(guessed) = reader.with_guessed_format() {
                        if let Ok(mut dyn_img) = guessed.decode() {
                            dyn_img = match effective_deg {
                                90 => dyn_img.rotate270(),
                                180 => dyn_img.rotate180(),
                                270 => dyn_img.rotate90(),
                                _ => dyn_img,
                            };
                            let _ = page.objects_mut().create_image_object(
                                target_rect.left(),
                                target_rect.bottom(),
                                &dyn_img,
                                Some(PdfPoints::new(rect_w)),
                                Some(PdfPoints::new(rect_h)),
                            );
                        }
                    }
                }
            } else if !op.text.is_empty() {
                let font = if op.is_bold { font_bold } else { font_regular };
                let font_size = op.font_size_pts.clamp(6.0, 96.0);
                let baseline_y = (target_rect.top().value - font_size * 0.85).max(target_rect.bottom().value);
                if let Ok(mut text_obj) = page.objects_mut().create_text_object(
                    target_rect.left(),
                    PdfPoints::new(baseline_y),
                    &op.text,
                    font,
                    PdfPoints::new(font_size),
                ) {
                    let _ = text_obj.set_fill_color(argb_to_pdf_color(op.color_argb));
                }
            }
        }

        page.regenerate_content()
            .map_err(|e| PdfError::EngineError(format!("Failed to finalize edited page: {e}")))?;
    }

    dest_doc
        .save_to_file(output_path)
        .map_err(|e| PdfError::EngineError(format!("Failed to save edited PDF: {e}")))?;
    Ok(())
}
