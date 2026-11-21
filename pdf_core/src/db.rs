use std::path::Path;

use parking_lot::Mutex;
use rusqlite::{params, Connection, OptionalExtension};
use sha2::{Digest, Sha256};

use crate::{HighlightRecord, NormalizedRect, PageRotation, PdfError, ReadingProgress};

const HASH_CHUNK_BYTES: usize = 65_536;

/// Computes a fast `< 1ms` fingerprint for a memory-mapped PDF using its byte length,
/// first 64 KiB, and last 64 KiB.
pub fn compute_file_hash(mmap: &[u8]) -> String {
    let len = mmap.len();
    let len_u64 = len as u64;
    let head = &mmap[..len.min(HASH_CHUNK_BYTES)];
    let tail = &mmap[len.saturating_sub(HASH_CHUNK_BYTES)..];

    let mut hasher = Sha256::new();
    hasher.update(len_u64.to_le_bytes());
    hasher.update(head);
    hasher.update(tail);
    hex::encode(hasher.finalize())
}

pub struct Database {
    conn: Mutex<Connection>,
}

impl Database {
    pub fn open(db_path: &str) -> Result<Self, PdfError> {
        if let Some(parent) = Path::new(db_path).parent() {
            if !parent.as_os_str().is_empty() {
                std::fs::create_dir_all(parent)
                    .map_err(|e| PdfError::DatabaseError(format!("Failed to create db dir: {e}")))?;
            }
        }

        let mut conn = Connection::open(db_path)
            .map_err(|e| PdfError::DatabaseError(format!("Failed to open sqlite db: {e}")))?;

        let schema_sql = "PRAGMA busy_timeout = 5000;
             PRAGMA journal_mode = WAL;
             PRAGMA synchronous = NORMAL;
             PRAGMA temp_store = MEMORY;
             CREATE TABLE IF NOT EXISTS recent_files (
                 file_hash TEXT PRIMARY KEY,
                 file_uri TEXT NOT NULL,
                 fallback_cache_path TEXT,
                 display_name TEXT NOT NULL,
                 last_page_index INTEGER NOT NULL,
                 page_offset_fraction REAL NOT NULL,
                 zoom_scale REAL NOT NULL,
                 pan_offset_x_fraction REAL NOT NULL,
                 manual_rotation INTEGER NOT NULL,
                 dark_mode_enabled INTEGER NOT NULL,
                 last_opened_unix INTEGER NOT NULL,
                 total_pages INTEGER NOT NULL
             );
             CREATE INDEX IF NOT EXISTS idx_recent_files_last_opened
                 ON recent_files(last_opened_unix DESC);
             CREATE TABLE IF NOT EXISTS highlights (
                 id INTEGER PRIMARY KEY AUTOINCREMENT,
                 file_hash TEXT NOT NULL,
                 page_index INTEGER NOT NULL,
                 start_char INTEGER NOT NULL,
                 end_char INTEGER NOT NULL,
                 rects_json TEXT NOT NULL,
                 color_argb INTEGER NOT NULL,
                 selected_text TEXT NOT NULL,
                 created_unix INTEGER NOT NULL
             );
             CREATE INDEX IF NOT EXISTS idx_highlights_file_page
                 ON highlights(file_hash, page_index);";

        if let Err(err) = conn.execute_batch(schema_sql) {
            let msg = err.to_string();
            if msg.contains("malformed") || msg.contains("not a database") {
                drop(conn);
                let _ = std::fs::remove_file(format!("{db_path}-wal"));
                let _ = std::fs::remove_file(format!("{db_path}-shm"));
                let _ = std::fs::rename(db_path, format!("{db_path}.corrupt"));
                conn = Connection::open(db_path)
                    .map_err(|e| PdfError::DatabaseError(format!("Failed to reopen sqlite db: {e}")))?;
                conn.execute_batch(schema_sql)
                    .map_err(|e| PdfError::DatabaseError(format!("Failed to initialize schema: {e}")))?;
            } else {
                return Err(PdfError::DatabaseError(format!("Failed to initialize schema: {err}")));
            }
        }

        Ok(Self {
            conn: Mutex::new(conn),
        })
    }

    pub fn save_progress(&self, progress: &ReadingProgress) -> Result<(), PdfError> {
        let conn = self.conn.lock();
        conn.execute(
            "INSERT INTO recent_files (
                file_hash,
                file_uri,
                fallback_cache_path,
                display_name,
                last_page_index,
                page_offset_fraction,
                zoom_scale,
                pan_offset_x_fraction,
                manual_rotation,
                dark_mode_enabled,
                last_opened_unix,
                total_pages
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12)
            ON CONFLICT(file_hash) DO UPDATE SET
                file_uri = excluded.file_uri,
                fallback_cache_path = COALESCE(excluded.fallback_cache_path, recent_files.fallback_cache_path),
                display_name = excluded.display_name,
                last_page_index = excluded.last_page_index,
                page_offset_fraction = excluded.page_offset_fraction,
                zoom_scale = excluded.zoom_scale,
                pan_offset_x_fraction = excluded.pan_offset_x_fraction,
                manual_rotation = excluded.manual_rotation,
                dark_mode_enabled = excluded.dark_mode_enabled,
                last_opened_unix = excluded.last_opened_unix,
                total_pages = excluded.total_pages",
            params![
                progress.file_hash,
                progress.file_uri,
                progress.fallback_cache_path,
                progress.display_name,
                progress.last_page_index,
                progress.page_offset_fraction,
                progress.zoom_scale,
                progress.pan_offset_x_fraction,
                rotation_to_i32(progress.manual_rotation),
                if progress.dark_mode_enabled { 1 } else { 0 },
                progress.last_opened_unix,
                progress.total_pages,
            ],
        )
        .map_err(|e| PdfError::DatabaseError(format!("Failed to save progress: {e}")))?;
        Ok(())
    }

    pub fn get_progress(&self, file_hash: &str) -> Result<Option<ReadingProgress>, PdfError> {
        let conn = self.conn.lock();
        conn.query_row(
            "SELECT
                file_hash,
                file_uri,
                fallback_cache_path,
                display_name,
                last_page_index,
                page_offset_fraction,
                zoom_scale,
                pan_offset_x_fraction,
                manual_rotation,
                dark_mode_enabled,
                last_opened_unix,
                total_pages
             FROM recent_files
             WHERE file_hash = ?1",
            params![file_hash],
            row_to_progress,
        )
        .optional()
        .map_err(|e| PdfError::DatabaseError(format!("Failed to fetch progress: {e}")))
    }

    pub fn get_recent_files(&self, limit: u32) -> Result<Vec<ReadingProgress>, PdfError> {
        let conn = self.conn.lock();
        let mut stmt = conn
            .prepare(
                "SELECT
                    file_hash,
                    file_uri,
                    fallback_cache_path,
                    display_name,
                    last_page_index,
                    page_offset_fraction,
                    zoom_scale,
                    pan_offset_x_fraction,
                    manual_rotation,
                    dark_mode_enabled,
                    last_opened_unix,
                    total_pages
                 FROM recent_files
                 ORDER BY last_opened_unix DESC
                 LIMIT ?1",
            )
            .map_err(|e| PdfError::DatabaseError(format!("Failed to prepare recent query: {e}")))?;

        let rows = stmt
            .query_map(params![limit], row_to_progress)
            .map_err(|e| PdfError::DatabaseError(format!("Failed to query recent files: {e}")))?;

        let mut results = Vec::new();
        for row in rows {
            results.push(
                row.map_err(|e| PdfError::DatabaseError(format!("Failed to decode row: {e}")))?,
            );
        }
        Ok(results)
    }

    pub fn remove_recent_file(&self, file_hash: &str) -> Result<(), PdfError> {
        let conn = self.conn.lock();
        conn.execute(
            "DELETE FROM recent_files WHERE file_hash = ?1",
            params![file_hash],
        )
        .map_err(|e| PdfError::DatabaseError(format!("Failed to delete recent file: {e}")))?;
        Ok(())
    }

    pub fn add_highlight(
        &self,
        file_hash: &str,
        page_index: u32,
        start_char: u32,
        end_char: u32,
        rects: &[NormalizedRect],
        color_argb: u32,
        selected_text: &str,
    ) -> Result<HighlightRecord, PdfError> {
        let conn = self.conn.lock();
        let now_unix = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .map(|d| d.as_secs() as i64)
            .unwrap_or(0);

        let rects_json = serialize_rects(rects);

        conn.execute(
            "INSERT INTO highlights (
                file_hash, page_index, start_char, end_char, rects_json, color_argb, selected_text, created_unix
            ) VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)",
            params![
                file_hash,
                page_index,
                start_char,
                end_char,
                rects_json,
                color_argb as i64,
                selected_text,
                now_unix
            ],
        )
        .map_err(|e| PdfError::DatabaseError(format!("Failed to insert highlight: {e}")))?;

        let id = conn.last_insert_rowid();

        Ok(HighlightRecord {
            id,
            file_hash: file_hash.to_string(),
            page_index,
            start_char,
            end_char,
            rects: rects.to_vec(),
            color_argb,
            selected_text: selected_text.to_string(),
            created_unix: now_unix,
        })
    }

    pub fn remove_highlight(&self, highlight_id: i64) -> Result<(), PdfError> {
        let conn = self.conn.lock();
        conn.execute(
            "DELETE FROM highlights WHERE id = ?1",
            params![highlight_id],
        )
        .map_err(|e| PdfError::DatabaseError(format!("Failed to delete highlight: {e}")))?;
        Ok(())
    }

    pub fn get_highlights_for_file(&self, file_hash: &str) -> Result<Vec<HighlightRecord>, PdfError> {
        let conn = self.conn.lock();
        let mut stmt = conn
            .prepare(
                "SELECT id, file_hash, page_index, start_char, end_char, rects_json, color_argb, selected_text, created_unix
                 FROM highlights
                 WHERE file_hash = ?1
                 ORDER BY page_index ASC, start_char ASC",
            )
            .map_err(|e| PdfError::DatabaseError(format!("Failed to prepare highlights query: {e}")))?;

        let rows = stmt
            .query_map(params![file_hash], |row| {
                let id: i64 = row.get(0)?;
                let file_hash: String = row.get(1)?;
                let page_index: u32 = row.get(2)?;
                let start_char: u32 = row.get(3)?;
                let end_char: u32 = row.get(4)?;
                let rects_json: String = row.get(5)?;
                let color_raw: i64 = row.get(6)?;
                let selected_text: String = row.get(7)?;
                let created_unix: i64 = row.get(8)?;

                let rects: Vec<NormalizedRect> = deserialize_rects(&rects_json);

                Ok(HighlightRecord {
                    id,
                    file_hash,
                    page_index,
                    start_char,
                    end_char,
                    rects,
                    color_argb: color_raw as u32,
                    selected_text,
                    created_unix,
                })
            })
            .map_err(|e| PdfError::DatabaseError(format!("Failed to query highlights: {e}")))?;

        let mut results = Vec::new();
        for r in rows {
            results.push(r.map_err(|e| PdfError::DatabaseError(format!("Failed to decode highlight row: {e}")))?);
        }
        Ok(results)
    }
}

fn row_to_progress(row: &rusqlite::Row<'_>) -> rusqlite::Result<ReadingProgress> {
    let rotation_raw: i32 = row.get(8)?;
    let dark_raw: i32 = row.get(9)?;
    Ok(ReadingProgress {
        file_hash: row.get(0)?,
        file_uri: row.get(1)?,
        fallback_cache_path: row.get(2)?,
        display_name: row.get(3)?,
        last_page_index: row.get(4)?,
        page_offset_fraction: row.get(5)?,
        zoom_scale: row.get(6)?,
        pan_offset_x_fraction: row.get(7)?,
        manual_rotation: rotation_from_i32(rotation_raw),
        dark_mode_enabled: dark_raw != 0,
        last_opened_unix: row.get(10)?,
        total_pages: row.get(11)?,
    })
}

fn rotation_to_i32(rot: PageRotation) -> i32 {
    match rot {
        PageRotation::Deg0 => 0,
        PageRotation::Deg90 => 1,
        PageRotation::Deg180 => 2,
        PageRotation::Deg270 => 3,
    }
}

fn rotation_from_i32(val: i32) -> PageRotation {
    match val {
        1 => PageRotation::Deg90,
        2 => PageRotation::Deg180,
        3 => PageRotation::Deg270,
        _ => PageRotation::Deg0,
    }
}

fn serialize_rects(rects: &[NormalizedRect]) -> String {
    let mut s = String::new();
    for (i, r) in rects.iter().enumerate() {
        if i > 0 {
            s.push(';');
        }
        s.push_str(&format!("{},{},{},{}", r.left, r.top, r.right, r.bottom));
    }
    s
}

fn deserialize_rects(raw: &str) -> Vec<NormalizedRect> {
    if raw.is_empty() {
        return Vec::new();
    }
    raw.split(';')
        .filter_map(|part| {
            let mut nums = part.split(',').filter_map(|v| v.parse::<f32>().ok());
            Some(NormalizedRect {
                left: nums.next()?,
                top: nums.next()?,
                right: nums.next()?,
                bottom: nums.next()?,
            })
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::time::{SystemTime, UNIX_EPOCH};

    fn temp_db_path(name: &str) -> String {
        let nanos = SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map(|d| d.as_nanos())
            .unwrap_or(0);
        std::env::temp_dir()
            .join(format!("velocity_pdf_{name}_{nanos}.db"))
            .to_string_lossy()
            .into_owned()
    }

    #[test]
    fn hash_handles_small_and_large_buffers() {
        let empty = compute_file_hash(&[]);
        let small = compute_file_hash(b"hello pdf");
        assert_ne!(empty, small);
        assert_eq!(empty.len(), 64);

        let mut large_a = vec![0xAAu8; 200_000];
        let mut large_b = vec![0xAAu8; 200_000];
        // Changing tail changes hash
        large_b[199_999] = 0xBB;
        assert_ne!(compute_file_hash(&large_a), compute_file_hash(&large_b));

        // Same length, head, tail produces same fast fingerprint
        large_a[100_000] = 0x11;
        let large_c = vec![0xAAu8; 200_000];
        assert_eq!(compute_file_hash(&large_a), compute_file_hash(&large_c));
    }

    #[test]
    fn database_roundtrip_and_recent_order() {
        let path = temp_db_path("roundtrip");
        let db = Database::open(&path).expect("open db");

        let p1 = ReadingProgress {
            file_hash: "hash1".into(),
            file_uri: "content://uri/1".into(),
            fallback_cache_path: Some("/cache/1.pdf".into()),
            display_name: "First.pdf".into(),
            last_page_index: 3,
            page_offset_fraction: 0.42,
            zoom_scale: 1.5,
            pan_offset_x_fraction: 0.1,
            manual_rotation: PageRotation::Deg90,
            dark_mode_enabled: true,
            last_opened_unix: 1000,
            total_pages: 20,
        };
        let p2 = ReadingProgress {
            file_hash: "hash2".into(),
            file_uri: "content://uri/2".into(),
            fallback_cache_path: None,
            display_name: "Second.pdf".into(),
            last_page_index: 0,
            page_offset_fraction: 0.0,
            zoom_scale: 1.0,
            pan_offset_x_fraction: 0.0,
            manual_rotation: PageRotation::Deg0,
            dark_mode_enabled: false,
            last_opened_unix: 2000,
            total_pages: 5,
        };

        db.save_progress(&p1).expect("save p1");
        db.save_progress(&p2).expect("save p2");

        let recent = db.get_recent_files(10).expect("list recent");
        assert_eq!(recent.len(), 2);
        assert_eq!(recent[0].file_hash, "hash2");
        assert_eq!(recent[1].file_hash, "hash1");

        let fetched = db.get_progress("hash1").expect("get p1").expect("exists");
        assert_eq!(fetched.manual_rotation, PageRotation::Deg90);
        assert!(fetched.dark_mode_enabled);
        assert_eq!(fetched.fallback_cache_path.as_deref(), Some("/cache/1.pdf"));

        db.remove_recent_file("hash1").expect("remove");
        assert!(db.get_progress("hash1").expect("get").is_none());

        let _ = std::fs::remove_file(&path);
        let _ = std::fs::remove_file(format!("{path}-wal"));
        let _ = std::fs::remove_file(format!("{path}-shm"));
    }
}

