#[cfg(target_os = "android")]
mod android_impl {
    use jni::objects::{JClass, JObject};
    use jni::sys::{jboolean, jint, jlong, JNI_FALSE, JNI_TRUE};
    use jni::JNIEnv;
    use ndk_sys::{
        AndroidBitmapFormat, AndroidBitmapInfo, AndroidBitmap_getInfo,
        AndroidBitmap_lockPixels, AndroidBitmap_unlockPixels, ANDROID_BITMAP_RESULT_SUCCESS,
    };

    use crate::{PageRotation, PdfEngine};

    fn rotation_from_ordinal(ordinal: jint) -> PageRotation {
        match ordinal {
            1 => PageRotation::Deg90,
            2 => PageRotation::Deg180,
            3 => PageRotation::Deg270,
            _ => PageRotation::Deg0,
        }
    }

    unsafe fn with_locked_bitmap<F>(
        env: &JNIEnv,
        bitmap: &JObject,
        render_fn: F,
    ) -> jboolean
    where
        F: FnOnce(u32, u32, usize, &mut [u8]) -> bool,
    {
        let raw_env = env.get_raw();
        let raw_bitmap = bitmap.as_raw();
        let mut info = std::mem::zeroed::<AndroidBitmapInfo>();

        if AndroidBitmap_getInfo(raw_env, raw_bitmap, &mut info) != ANDROID_BITMAP_RESULT_SUCCESS {
            return JNI_FALSE;
        }
        if info.format != AndroidBitmapFormat::ANDROID_BITMAP_FORMAT_RGBA_8888.0 as i32
            || info.width == 0
            || info.height == 0
        {
            return JNI_FALSE;
        }

        let mut pixels_ptr: *mut std::ffi::c_void = std::ptr::null_mut();
        if AndroidBitmap_lockPixels(raw_env, raw_bitmap, &mut pixels_ptr)
            != ANDROID_BITMAP_RESULT_SUCCESS
            || pixels_ptr.is_null()
        {
            return JNI_FALSE;
        }

        let stride = info.stride as usize;
        let total_bytes = stride.saturating_mul(info.height as usize);
        let slice = std::slice::from_raw_parts_mut(pixels_ptr as *mut u8, total_bytes);
        let ok = render_fn(info.width, info.height, stride, slice);

        let _ = AndroidBitmap_unlockPixels(raw_env, raw_bitmap);
        if ok {
            JNI_TRUE
        } else {
            JNI_FALSE
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_velocity_pdf_NativeZeroCopy_renderPageDirect(
        env: JNIEnv,
        _class: JClass,
        engine_ptr: jlong,
        page_index: jint,
        bitmap: JObject,
        manual_rotation_ordinal: jint,
        dark_mode: jboolean,
    ) -> jboolean {
        if engine_ptr == 0 || page_index < 0 {
            return JNI_FALSE;
        }
        let engine = unsafe { &*(engine_ptr as *const PdfEngine) };
        let rotation = rotation_from_ordinal(manual_rotation_ordinal);
        let is_dark = dark_mode != JNI_FALSE;

        unsafe {
            with_locked_bitmap(&env, &bitmap, |width, height, stride, pixels| {
                engine
                    .render_page_into_rgba_buffer(
                        page_index as u32,
                        width,
                        height,
                        stride,
                        pixels,
                        rotation,
                        is_dark,
                    )
                    .is_ok()
            })
        }
    }

    #[no_mangle]
    pub extern "system" fn Java_com_velocity_pdf_NativeZeroCopy_renderZoomTileDirect(
        env: JNIEnv,
        _class: JClass,
        engine_ptr: jlong,
        page_index: jint,
        full_scaled_width_px: jint,
        full_scaled_height_px: jint,
        visible_x_px: jint,
        visible_y_px: jint,
        bitmap: JObject,
        manual_rotation_ordinal: jint,
        dark_mode: jboolean,
    ) -> jboolean {
        if engine_ptr == 0
            || page_index < 0
            || full_scaled_width_px <= 0
            || full_scaled_height_px <= 0
        {
            return JNI_FALSE;
        }
        let engine = unsafe { &*(engine_ptr as *const PdfEngine) };
        let rotation = rotation_from_ordinal(manual_rotation_ordinal);
        let is_dark = dark_mode != JNI_FALSE;

        unsafe {
            with_locked_bitmap(&env, &bitmap, |width, height, stride, pixels| {
                engine
                    .render_zoom_tile_into_rgba_buffer(
                        page_index as u32,
                        full_scaled_width_px as u32,
                        full_scaled_height_px as u32,
                        visible_x_px,
                        visible_y_px,
                        width,
                        height,
                        stride,
                        pixels,
                        rotation,
                        is_dark,
                    )
                    .is_ok()
            })
        }
    }
}

