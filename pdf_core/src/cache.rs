use std::sync::Arc;

use lru::LruCache;
use parking_lot::Mutex;

use crate::{PageRotation, RenderedTile};

pub const DEFAULT_CACHE_BUDGET_BYTES: usize = 64 * 1024 * 1024; // 64 MB

#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum TileCacheKey {
    BasePage {
        file_hash: String,
        page_index: u32,
        width_px: u32,
        height_px: u32,
        rotation: PageRotation,
        dark_mode: bool,
    },
    ZoomTile {
        file_hash: String,
        page_index: u32,
        full_scaled_width_px: u32,
        full_scaled_height_px: u32,
        visible_x_px: i32,
        visible_y_px: i32,
        tile_width_px: u32,
        tile_height_px: u32,
        rotation: PageRotation,
        dark_mode: bool,
    },
}

struct CacheEntry {
    tile: Arc<RenderedTile>,
    byte_len: usize,
}

struct CacheInner {
    lru: LruCache<TileCacheKey, CacheEntry>,
    current_bytes: usize,
    max_bytes: usize,
}

pub struct TileCache {
    inner: Mutex<CacheInner>,
}

impl TileCache {
    pub fn new(max_bytes: usize) -> Self {
        Self {
            inner: Mutex::new(CacheInner {
                lru: LruCache::unbounded(),
                current_bytes: 0,
                max_bytes,
            }),
        }
    }

    pub fn get(&self, key: &TileCacheKey) -> Option<Arc<RenderedTile>> {
        let mut inner = self.inner.lock();
        inner.lru.get(key).map(|entry| Arc::clone(&entry.tile))
    }

    pub fn put(&self, key: TileCacheKey, tile: Arc<RenderedTile>) {
        let byte_len = tile.rgba_bytes.len();
        let mut inner = self.inner.lock();
        if byte_len > inner.max_bytes {
            return;
        }

        if let Some(prev) = inner.lru.pop(&key) {
            inner.current_bytes = inner.current_bytes.saturating_sub(prev.byte_len);
        }

        while inner.current_bytes + byte_len > inner.max_bytes {
            if let Some((_, evicted)) = inner.lru.pop_lru() {
                inner.current_bytes = inner.current_bytes.saturating_sub(evicted.byte_len);
            } else {
                break;
            }
        }

        inner.current_bytes += byte_len;
        inner.lru.put(key, CacheEntry { tile, byte_len });
    }

    pub fn clear(&self) {
        let mut inner = self.inner.lock();
        inner.lru.clear();
        inner.current_bytes = 0;
    }

    #[cfg(test)]
    pub fn current_bytes(&self) -> usize {
        self.inner.lock().current_bytes
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn dummy_tile(bytes: usize) -> Arc<RenderedTile> {
        Arc::new(RenderedTile {
            width_px: 1,
            height_px: 1,
            rgba_bytes: vec![255u8; bytes],
        })
    }

    fn key(page_index: u32) -> TileCacheKey {
        TileCacheKey::BasePage {
            file_hash: "doc".into(),
            page_index,
            width_px: 100,
            height_px: 100,
            rotation: PageRotation::Deg0,
            dark_mode: false,
        }
    }

    #[test]
    fn evicts_lru_to_respect_byte_budget() {
        let cache = TileCache::new(100);
        cache.put(key(0), dummy_tile(40));
        cache.put(key(1), dummy_tile(40));
        assert_eq!(cache.current_bytes(), 80);

        // Touch page 0 so page 1 becomes LRU
        assert!(cache.get(&key(0)).is_some());

        cache.put(key(2), dummy_tile(40));
        assert!(cache.current_bytes() <= 100);
        assert!(cache.get(&key(0)).is_some());
        assert!(cache.get(&key(1)).is_none());
        assert!(cache.get(&key(2)).is_some());
    }

    #[test]
    fn skips_entries_larger_than_budget() {
        let cache = TileCache::new(50);
        cache.put(key(0), dummy_tile(60));
        assert_eq!(cache.current_bytes(), 0);
        assert!(cache.get(&key(0)).is_none());
    }
}

