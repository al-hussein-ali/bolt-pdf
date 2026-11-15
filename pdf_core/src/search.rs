use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;

use parking_lot::Mutex;
use pdfium_render::prelude::*;

use crate::renderer::ActiveDocument;
use crate::{NormalizedRect, PageRotation, PageTextChar, PageTextContent, PdfError, SearchMatch};

/// Transforms an unrotated PDFium bounding box (bottom-left origin, in PDF points)
/// into normalized `0.0..1.0` top-left UI coordinates after applying `effective_rotation_deg`.
pub fn normalize_and_rotate_rect(
    pdf_left: f32,
    pdf_top: f32,
    pdf_right: f32,
    pdf_bottom: f32,
    page_width_pts: f32,
    page_height_pts: f32,
    effective_rotation_deg: i32,
) -> NormalizedRect {
    let w = page_width_pts.max(1e-3);
    let h = page_height_pts.max(1e-3);

    let x0 = (pdf_left / w).clamp(0.0, 1.0);
    let x1 = (pdf_right / w).clamp(0.0, 1.0);
    let y0 = (1.0 - (pdf_top / h)).clamp(0.0, 1.0);
    let y1 = (1.0 - (pdf_bottom / h)).clamp(0.0, 1.0);

    let u_left = x0.min(x1);
    let u_right = x0.max(x1);
    let u_top = y0.min(y1);
    let u_bottom = y0.max(y1);

    let deg = ((effective_rotation_deg % 360) + 360) % 360;
    match deg {
        90 => NormalizedRect {
            left: 1.0 - u_bottom,
            top: u_left,
            right: 1.0 - u_top,
            bottom: u_right,
        },
        180 => NormalizedRect {
            left: 1.0 - u_right,
            top: 1.0 - u_bottom,
            right: 1.0 - u_left,
            bottom: 1.0 - u_top,
        },
        270 => NormalizedRect {
            left: u_top,
            top: 1.0 - u_right,
            right: u_bottom,
            bottom: 1.0 - u_left,
        },
        _ => NormalizedRect {
            left: u_left,
            top: u_top,
            right: u_right,
            bottom: u_bottom,
        },
    }
}

fn is_arabic_diacritic_or_tatweel(c: char) -> bool {
    matches!(
        c as u32,
        0x0640          // Tatweel (Kashida)
        | 0x064B..=0x065F // Harakat / Tashkeel
        | 0x0670          // Superscript Alef
        | 0x06D6..=0x06ED // Quranic annotation marks
        | 0x200B..=0x200F // Zero-width space / joiners / LRM / RLM
        | 0xFEFF          // BOM / ZWNBSP
    )
}

fn map_arabic_presentation_form(c: char) -> Option<&'static [char]> {
    // Maps Arabic Presentation Forms-B (U+FE80..=U+FEFC) & common ligatures to base Arabic chars.
    match c as u32 {
        0xFB01 => Some(&['f', 'i']), // ﬁ
        0xFB02 => Some(&['f', 'l']), // ﬂ
        0xFB50..=0xFB51 => Some(&['ا']),
        0xFB56..=0xFB59 => Some(&['پ']),
        0xFB7A..=0xFB7D => Some(&['چ']),
        0xFB8A..=0xFB8B => Some(&['ژ']),
        0xFB8E..=0xFB91 => Some(&['ك']),
        0xFB92..=0xFB95 => Some(&['گ']),
        0xFBFC..=0xFBFF => Some(&['ي']),
        0xFE80 => Some(&['ء']),
        0xFE81..=0xFE82 => Some(&['ا']), // Alef Madda -> Alef
        0xFE83..=0xFE84 => Some(&['ا']), // Alef Hamza Above -> Alef
        0xFE85..=0xFE86 => Some(&['ؤ']),
        0xFE87..=0xFE88 => Some(&['ا']), // Alef Hamza Below -> Alef
        0xFE89..=0xFE8C => Some(&['ئ']),
        0xFE8D..=0xFE8E => Some(&['ا']),
        0xFE8F..=0xFE92 => Some(&['ب']),
        0xFE93..=0xFE94 => Some(&['ه']), // Taa Marbuta -> Heh
        0xFE95..=0xFE98 => Some(&['ت']),
        0xFE99..=0xFE9C => Some(&['ث']),
        0xFE9D..=0xFEA0 => Some(&['ج']),
        0xFEA1..=0xFEA4 => Some(&['ح']),
        0xFEA5..=0xFEA8 => Some(&['خ']),
        0xFEA9..=0xFEAA => Some(&['د']),
        0xFEAB..=0xFEAC => Some(&['ذ']),
        0xFEAD..=0xFEAE => Some(&['ر']),
        0xFEAF..=0xFEB0 => Some(&['ز']),
        0xFEB1..=0xFEB4 => Some(&['س']),
        0xFEB5..=0xFEB8 => Some(&['ش']),
        0xFEB9..=0xFEBC => Some(&['ص']),
        0xFEBD..=0xFEC0 => Some(&['ض']),
        0xFEC1..=0xFEC4 => Some(&['ط']),
        0xFEC5..=0xFEC8 => Some(&['ظ']),
        0xFEC9..=0xFECC => Some(&['ع']),
        0xFECD..=0xFED0 => Some(&['غ']),
        0xFED1..=0xFED4 => Some(&['ف']),
        0xFED5..=0xFED8 => Some(&['ق']),
        0xFED9..=0xFEDC => Some(&['ك']),
        0xFEDD..=0xFEE0 => Some(&['ل']),
        0xFEE1..=0xFEE4 => Some(&['م']),
        0xFEE5..=0xFEE8 => Some(&['ن']),
        0xFEE9..=0xFEEC => Some(&['ه']),
        0xFEED..=0xFEEE => Some(&['و']),
        0xFEEF..=0xFEF0 => Some(&['ي']), // Alef Maksura -> Yeh
        0xFEF1..=0xFEF4 => Some(&['ي']),
        0xFEF5..=0xFEFC => Some(&['ل', 'ا']), // Lam-Alef ligatures
        _ => None,
    }
}

fn push_normalized_char(c: char, out: &mut Vec<char>, orig_idx: usize, idx_map: &mut Vec<usize>) {
    if is_arabic_diacritic_or_tatweel(c) {
        return;
    }

    if let Some(mapped) = map_arabic_presentation_form(c) {
        for &mc in mapped {
            out.push(mc);
            idx_map.push(orig_idx);
        }
        return;
    }

    // Normalize whitespace
    if c.is_whitespace() {
        if out.last() != Some(&' ') {
            out.push(' ');
            idx_map.push(orig_idx);
        }
        return;
    }

    // Normalize Arabic / Persian letter variants
    let normalized = match c {
        'أ' | 'إ' | 'آ' | 'ٱ' => 'ا',
        'ة' => 'ه',
        'ى' | 'ی' => 'ي',
        'ک' => 'ك',
        _ => {
            for lc in c.to_lowercase() {
                out.push(lc);
                idx_map.push(orig_idx);
            }
            return;
        }
    };
    out.push(normalized);
    idx_map.push(orig_idx);
}

fn normalize_query(query: &str) -> Vec<char> {
    let mut out = Vec::with_capacity(query.len());
    let mut dummy_idx = Vec::with_capacity(query.len());
    for (i, c) in query.trim().chars().enumerate() {
        push_normalized_char(c, &mut out, i, &mut dummy_idx);
    }
    while out.first() == Some(&' ') {
        out.remove(0);
    }
    while out.last() == Some(&' ') {
        out.pop();
    }
    out
}

fn contains_rtl_char(chars: &[char]) -> bool {
    chars.iter().any(|&c| {
        let code = c as u32;
        (0x0590..=0x08FF).contains(&code) || (0xFB50..=0xFEFF).contains(&code)
    })
}

fn has_subslice(haystack: &[char], needle: &[char]) -> bool {
    if needle.is_empty() || haystack.len() < needle.len() {
        return false;
    }
    let first = needle[0];
    let limit = haystack.len() - needle.len();
    let mut i = 0;
    while i <= limit {
        if haystack[i] == first && haystack[i..i + needle.len()] == *needle {
            return true;
        }
        i += 1;
    }
    false
}

fn find_all_subslice_matches(haystack: &[char], needle: &[char]) -> Vec<(usize, usize)> {
    let mut matches = Vec::new();
    if needle.is_empty() || haystack.len() < needle.len() {
        return matches;
    }
    let first = needle[0];
    let limit = haystack.len() - needle.len();
    let mut i = 0;
    while i <= limit {
        if haystack[i] == first && haystack[i..i + needle.len()] == *needle {
            matches.push((i, i + needle.len() - 1));
            i += needle.len();
        } else {
            i += 1;
        }
    }
    matches
}

pub fn search_document_pages(
    active_doc: &Mutex<Option<ActiveDocument>>,
    cancel_flag: &Arc<AtomicBool>,
    query: &str,
    manual_rotation: PageRotation,
) -> Result<Vec<SearchMatch>, PdfError> {
    let norm_query = normalize_query(query);
    if norm_query.is_empty() {
        return Ok(Vec::new());
    }

    let is_rtl = contains_rtl_char(&norm_query);
    let mut rev_query = norm_query.clone();
    rev_query.reverse();

    let page_count = {
        let guard = active_doc.lock();
        let doc = guard.as_ref().ok_or(PdfError::InvalidSource)?;
        doc.doc.pages().len()
    };

    let mut all_matches = Vec::new();
    let mut match_counter: u32 = 0;
    const MAX_SEARCH_MATCHES: u32 = 2000;

    let mut prefilter_norm: Vec<char> = Vec::with_capacity(4096);
    let mut prefilter_idx: Vec<usize> = Vec::with_capacity(4096);

    for page_idx in 0..page_count {
        if cancel_flag.load(Ordering::Acquire) {
            return Err(PdfError::SearchCancelled);
        }
        if match_counter >= MAX_SEARCH_MATCHES {
            break;
        }

        // Acquire the PDFium lock per page so render requests can interleave cleanly
        {
            let guard = active_doc.lock();
            let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
            let page = active
                .doc
                .pages()
                .get(page_idx)
                .map_err(|e| PdfError::EngineError(format!("Page access error: {e}")))?;

            let disp_w = page.width().value;
            let disp_h = page.height().value;
            let intrinsic_deg = match page.rotation() {
                Ok(PdfPageRenderRotation::Degrees90) => 90,
                Ok(PdfPageRenderRotation::Degrees180) => 180,
                Ok(PdfPageRenderRotation::Degrees270) => 270,
                _ => 0,
            };
            // FPDF_GetPageWidthF/HeightF return post-intrinsic-rotation sizes, whereas
            // FPDFText_GetRect returns unrotated PDF user-space coordinates.
            let (page_w, page_h) = match intrinsic_deg {
                90 | 270 => (disp_h, disp_w),
                _ => (disp_w, disp_h),
            };
            let effective_deg = (intrinsic_deg + manual_rotation.degrees()) % 360;

            let text = page
                .text()
                .map_err(|e| PdfError::EngineError(format!("Text extraction error: {e}")))?;

            let page_chars = text.chars();
            let char_count = page_chars.len();
            if char_count == 0 {
                continue;
            }

            // Fast bulk UTF-16 read via text.all() (1 FFI call) for unrotated pages
            // to skip non-matching pages without thousands of FPDFText_GetUnicode calls.
            // Note: pdfium-render's text.all() uses post-rotation page bounds internally,
            // so we only use it as a pre-filter when intrinsic_deg == 0.
            if intrinsic_deg == 0 {
                let full_text = text.all();
                if full_text.is_empty() {
                    continue;
                }
                prefilter_norm.clear();
                prefilter_idx.clear();
                for (i, c) in full_text.chars().enumerate() {
                    push_normalized_char(c, &mut prefilter_norm, i, &mut prefilter_idx);
                }
                let maybe_match = has_subslice(&prefilter_norm, &norm_query)
                    || (is_rtl && rev_query != norm_query && has_subslice(&prefilter_norm, &rev_query));
                if !maybe_match {
                    continue;
                }
            }

            let mut norm_chars = Vec::with_capacity(char_count);
            let mut idx_map = Vec::with_capacity(char_count);
            let mut raw_chars: Vec<char> = Vec::with_capacity(char_count);

            for ch in page_chars.iter() {
                let c = ch.unicode_char().unwrap_or('\0');
                let pdf_idx = ch.index();
                raw_chars.push(c);
                if c != '\0' {
                    push_normalized_char(c, &mut norm_chars, pdf_idx, &mut idx_map);
                }
            }

            let mut spans = find_all_subslice_matches(&norm_chars, &norm_query);
            if spans.is_empty() && is_rtl && rev_query != norm_query {
                spans = find_all_subslice_matches(&norm_chars, &rev_query);
            }

            for (norm_start, norm_end) in spans {
                if cancel_flag.load(Ordering::Acquire) {
                    return Err(PdfError::SearchCancelled);
                }

                let orig_start = idx_map[norm_start];
                let orig_end = idx_map[norm_end];
                let count = orig_end.saturating_sub(orig_start) + 1;

                let segments = text.segments_subset(orig_start, count);
                let mut rects = Vec::new();
                for segment in segments.iter() {
                    let bounds = segment.bounds();
                    let r = normalize_and_rotate_rect(
                        bounds.left().value,
                        bounds.top().value,
                        bounds.right().value,
                        bounds.bottom().value,
                        page_w,
                        page_h,
                        effective_deg,
                    );
                    if r.right > r.left && r.bottom > r.top {
                        rects.push(r);
                    }
                }

                // Fallback to per-char tight bounds if segments_subset returned empty
                if rects.is_empty() {
                    for ci in orig_start..=orig_end {
                        if let Ok(ch) = page_chars.get(ci) {
                            if let Ok(bounds) = ch.tight_bounds().or_else(|_| ch.loose_bounds()) {
                                let r = normalize_and_rotate_rect(
                                    bounds.left().value,
                                    bounds.top().value,
                                    bounds.right().value,
                                    bounds.bottom().value,
                                    page_w,
                                    page_h,
                                    effective_deg,
                                );
                                if r.right > r.left && r.bottom > r.top {
                                    rects.push(r);
                                }
                            }
                        }
                    }
                }

                if !rects.is_empty() {
                    let snip_start = norm_start.saturating_sub(15);
                    let snip_end = (norm_end + 15).min(raw_chars.len().saturating_sub(1));
                    let snippet: String = raw_chars[snip_start..=snip_end]
                        .iter()
                        .filter(|&&c| c != '\0' && c != '\r' && c != '\n')
                        .collect();

                    all_matches.push(SearchMatch {
                        match_index: match_counter,
                        page_index: page_idx as u32,
                        rects,
                        context_snippet: snippet.trim().to_string(),
                    });
                    match_counter += 1;
                }
            }
        }

        // Briefly yield CPU between pages so UI render locks can acquire immediately
        std::thread::yield_now();
    }

    Ok(all_matches)
}

pub fn extract_page_text_content(
    active_doc: &Mutex<Option<ActiveDocument>>,
    page_index: u32,
    manual_rotation: PageRotation,
) -> Result<PageTextContent, PdfError> {
    let guard = active_doc.lock();
    let active = guard.as_ref().ok_or(PdfError::InvalidSource)?;
    let page_u16 = u16::try_from(page_index).map_err(|_| PdfError::PageOutOfBounds(page_index))?;
    let page = active
        .doc
        .pages()
        .get(page_u16)
        .map_err(|_| PdfError::PageOutOfBounds(page_index))?;

    let disp_w = page.width().value;
    let disp_h = page.height().value;
    let intrinsic_deg = match page.rotation() {
        Ok(PdfPageRenderRotation::Degrees90) => 90,
        Ok(PdfPageRenderRotation::Degrees180) => 180,
        Ok(PdfPageRenderRotation::Degrees270) => 270,
        _ => 0,
    };
    let (page_w, page_h) = match intrinsic_deg {
        90 | 270 => (disp_h, disp_w),
        _ => (disp_w, disp_h),
    };
    let effective_deg = (intrinsic_deg + manual_rotation.degrees()) % 360;

    let text = page
        .text()
        .map_err(|e| PdfError::EngineError(format!("Text extraction error: {e}")))?;

    let page_chars = text.chars();
    let mut chars = Vec::with_capacity(page_chars.len());
    let mut full_text = String::with_capacity(page_chars.len());

    for (idx, ch) in page_chars.iter().enumerate() {
        let Some(c) = ch.unicode_char() else {
            continue;
        };
        if c == '\0' {
            continue;
        }
        full_text.push(c);

        if c.is_control() && c != '\n' && c != '\r' {
            continue;
        }

        let rect = match ch.loose_bounds().or_else(|_| ch.tight_bounds()) {
            Ok(bounds) => normalize_and_rotate_rect(
                bounds.left().value,
                bounds.top().value,
                bounds.right().value,
                bounds.bottom().value,
                page_w,
                page_h,
                effective_deg,
            ),
            Err(_) if c.is_whitespace() => NormalizedRect {
                left: 0.0,
                top: 0.0,
                right: 0.0,
                bottom: 0.0,
            },
            Err(_) => continue,
        };

        chars.push(PageTextChar {
            char_index: idx as u32,
            text: c.to_string(),
            rect,
        });
    }

    Ok(PageTextContent {
        page_index,
        full_text,
        chars,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn approx_eq(a: f32, b: f32) -> bool {
        (a - b).abs() < 1e-4
    }

    #[test]
    fn normalizes_and_rotates_all_quadrants() {
        // Page 100x200 pts. Word near top-left in PDF bottom-left coords:
        // left=10, right=30 -> x in [0.1, 0.3]
        // bottom=160, top=180 -> y_top=1 - 180/200 = 0.1, y_bottom=1 - 160/200 = 0.2
        let r0 = normalize_and_rotate_rect(10.0, 180.0, 30.0, 160.0, 100.0, 200.0, 0);
        assert!(approx_eq(r0.left, 0.1));
        assert!(approx_eq(r0.right, 0.3));
        assert!(approx_eq(r0.top, 0.1));
        assert!(approx_eq(r0.bottom, 0.2));

        // 90° clockwise: top-left moves to top-right
        let r90 = normalize_and_rotate_rect(10.0, 180.0, 30.0, 160.0, 100.0, 200.0, 90);
        assert!(approx_eq(r90.left, 0.8));
        assert!(approx_eq(r90.right, 0.9));
        assert!(approx_eq(r90.top, 0.1));
        assert!(approx_eq(r90.bottom, 0.3));

        // 180° clockwise: top-left moves to bottom-right
        let r180 = normalize_and_rotate_rect(10.0, 180.0, 30.0, 160.0, 100.0, 200.0, 180);
        assert!(approx_eq(r180.left, 0.7));
        assert!(approx_eq(r180.right, 0.9));
        assert!(approx_eq(r180.top, 0.8));
        assert!(approx_eq(r180.bottom, 0.9));

        // 270° clockwise: top-left moves to bottom-left
        let r270 = normalize_and_rotate_rect(10.0, 180.0, 30.0, 160.0, 100.0, 200.0, 270);
        assert!(approx_eq(r270.left, 0.1));
        assert!(approx_eq(r270.right, 0.2));
        assert!(approx_eq(r270.top, 0.7));
        assert!(approx_eq(r270.bottom, 0.9));
    }

    #[test]
    fn arabic_normalization_matches_diacritics_and_presentation_forms() {
        // PDF text with Tashkeel + Alef Hamza + Taa Marbuta + Presentation Forms
        let pdf_text = "مُحَمَّدٌ وَالإِسْلامُ ﺔ ﻻ";
        let mut norm_text = Vec::new();
        let mut idx_map = Vec::new();
        for (i, c) in pdf_text.chars().enumerate() {
            push_normalized_char(c, &mut norm_text, i, &mut idx_map);
        }

        let q1 = normalize_query("محمد");
        assert!(!find_all_subslice_matches(&norm_text, &q1).is_empty());

        let q2 = normalize_query("والاسلام");
        assert!(!find_all_subslice_matches(&norm_text, &q2).is_empty());

        let q3 = normalize_query("لا");
        assert!(!find_all_subslice_matches(&norm_text, &q3).is_empty());
    }
}

