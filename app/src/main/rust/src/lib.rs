use jni::objects::{JClass, JFloatArray, JIntArray, JLongArray, JObject, JShortArray, JString, JValue};
use jni::sys::{jfloat, jint, jlong, jobjectArray};
use jni::JNIEnv;

// ============================================================================
// FASA 1 & 3A: ENJIN AUDIO DSP (BASS IIR FILTER + BEAT DETECTOR + WAVEFORM)
// ============================================================================

fn compute_bass_window_energies(
    samples: &[i16],
    sample_rate: usize,
    channel_count: usize,
    window_ms: usize,
) -> Vec<(i64, f32)> {
    if samples.is_empty() || sample_rate == 0 || channel_count == 0 {
        return Vec::new();
    }

    let window_frames = ((sample_rate * window_ms) / 1000).max(128);
    let dt = 1.0_f32 / (sample_rate as f32);
    let rc = 1.0_f32 / (2.0_f32 * std::f32::consts::PI * 160.0_f32);
    let alpha = dt / (rc + dt);

    let mut lp1 = 0.0_f32;
    let mut lp2 = 0.0_f32;
    let mut sum_sq = 0.0_f64;
    let mut count_in_window = 0_usize;
    let mut total_frames = 0_i64;

    let estimated_windows = (samples.len() / channel_count) / window_frames + 1;
    let mut energies = Vec::with_capacity(estimated_windows);

    let mut idx = 0_usize;
    while idx + channel_count <= samples.len() {
        let mut mono = 0.0_f32;
        for ch in 0..channel_count {
            mono += (samples[idx + ch] as f32) / 32768.0_f32;
        }
        mono /= channel_count as f32;

        lp1 += alpha * (mono - lp1);
        lp2 += alpha * (lp1 - lp2);

        let weighted = (lp2 * 0.75_f32) + (mono * 0.25_f32);
        sum_sq += (weighted * weighted) as f64;
        count_in_window += 1;
        total_frames += 1;

        if count_in_window >= window_frames {
            let rms = (sum_sq / (count_in_window as f64)).sqrt() as f32;
            let time_ms = (total_frames * 1000_i64) / (sample_rate as i64);
            energies.push((time_ms, rms));
            sum_sq = 0.0;
            count_in_window = 0;
        }

        idx += channel_count;
    }

    energies
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_detectBeatsNative<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    pcm_samples: JShortArray<'local>,
    sample_rate: jint,
    channel_count: jint,
    min_interval_ms: jlong,
    threshold_multiplier: jfloat,
) -> JLongArray<'local> {
    let empty_fallback = || -> JLongArray<'local> {
        env.new_long_array(0).unwrap_or_default()
    };

    let Ok(len) = env.get_array_length(&pcm_samples) else {
        return empty_fallback();
    };
    if len <= 0 || sample_rate <= 0 || channel_count <= 0 {
        return empty_fallback();
    }

    let mut buffer = vec![0_i16; len as usize];
    if env.get_short_array_region(&pcm_samples, 0, &mut buffer).is_err() {
        return empty_fallback();
    }

    let energies = compute_bass_window_energies(
        &buffer,
        sample_rate as usize,
        channel_count as usize,
        30,
    );

    if energies.len() < 8 {
        return empty_fallback();
    }

    let mut flux = vec![0.0_f32; energies.len()];
    for i in 1..energies.len() {
        let diff = energies[i].1 - energies[i - 1].1;
        flux[i] = if diff > 0.0 { diff } else { 0.0 };
    }

    let mut detected_beats: Vec<i64> = Vec::new();
    let history_radius = 12_usize;
    let mut last_accepted_ms = -min_interval_ms;

    for i in 2..(flux.len() - 1) {
        let cur = flux[i];
        if cur <= 0.005_f32 {
            continue;
        }
        if cur < flux[i - 1] || cur < flux[i + 1] {
            continue;
        }

        let start_idx = i.saturating_sub(history_radius);
        let end_idx = (i + history_radius).min(flux.len() - 1);
        let mut sum = 0.0_f32;
        let mut count = 0_usize;
        for &val in &flux[start_idx..=end_idx] {
            sum += val;
            count += 1;
        }

        let local_avg = if count > 0 { sum / (count as f32) } else { 0.0 };
        let threshold = (local_avg * threshold_multiplier).max(0.011_f32);
        let candidate_ms = energies[i].0;

        if cur >= threshold
            && candidate_ms >= 200
            && (candidate_ms - last_accepted_ms) >= min_interval_ms
        {
            detected_beats.push(candidate_ms);
            last_accepted_ms = candidate_ms;
        }
    }

    match env.new_long_array(detected_beats.len() as i32) {
        Ok(out_arr) => {
            if env
                .set_long_array_region(&out_arr, 0, &detected_beats)
                .is_ok()
            {
                out_arr
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_extractWaveformPeaksNative<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    pcm_samples: JShortArray<'local>,
    target_bars: jint,
) -> JFloatArray<'local> {
    let empty_fallback = || -> JFloatArray<'local> {
        env.new_float_array(0).unwrap_or_default()
    };

    let Ok(len) = env.get_array_length(&pcm_samples) else {
        return empty_fallback();
    };
    let bars = target_bars.max(1) as usize;
    if len <= 0 {
        return empty_fallback();
    }

    let mut buffer = vec![0_i16; len as usize];
    if env.get_short_array_region(&pcm_samples, 0, &mut buffer).is_err() {
        return empty_fallback();
    }

    let total = buffer.len();
    let chunk_size = (total / bars).max(1);
    let mut peaks = vec![0.0_f32; bars];
    let mut max_peak = 0.001_f32;

    for b in 0..bars {
        let start = b * chunk_size;
        if start >= total {
            break;
        }
        let end = ((b + 1) * chunk_size).min(total);
        let mut local_max = 0_i32;
        for &s in &buffer[start..end] {
            let abs_val = (s as i32).abs();
            if abs_val > local_max {
                local_max = abs_val;
            }
        }
        let norm = (local_max as f32) / 32768.0_f32;
        peaks[b] = norm;
        if norm > max_peak {
            max_peak = norm;
        }
    }

    for p in &mut peaks {
        *p = (*p / max_peak).clamp(0.12_f32, 1.0_f32);
    }

    match env.new_float_array(bars as i32) {
        Ok(out_arr) => {
            if env.set_float_array_region(&out_arr, 0, &peaks).is_ok() {
                out_arr
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}

// ============================================================================
// FASA 2: ENJIN PIKSEL NATIF (BILINEAR RESIZER + FIT-BLUR COMPOSITOR)
// ============================================================================

fn resize_argb_bilinear(src: &[u32], src_w: usize, src_h: usize, dst_w: usize, dst_h: usize) -> Vec<u32> {
    if src_w == 0 || src_h == 0 || dst_w == 0 || dst_h == 0 {
        return Vec::new();
    }
    if src_w == dst_w && src_h == dst_h {
        return src.to_vec();
    }

    let mut dst = vec![0xFF000000_u32; dst_w * dst_h];
    let scale_x = (src_w as f32) / (dst_w as f32);
    let scale_y = (src_h as f32) / (dst_h as f32);

    for y in 0..dst_h {
        let fy = ((y as f32 + 0.5) * scale_y - 0.5).clamp(0.0, (src_h - 1) as f32);
        let y0 = fy.floor() as usize;
        let y1 = (y0 + 1).min(src_h - 1);
        let wy = fy - (y0 as f32);
        let inv_wy = 1.0 - wy;

        let row0 = y0 * src_w;
        let row1 = y1 * src_w;
        let dst_row = y * dst_w;

        for x in 0..dst_w {
            let fx = ((x as f32 + 0.5) * scale_x - 0.5).clamp(0.0, (src_w - 1) as f32);
            let x0 = fx.floor() as usize;
            let x1 = (x0 + 1).min(src_w - 1);
            let wx = fx - (x0 as f32);
            let inv_wx = 1.0 - wx;

            let c00 = src[row0 + x0];
            let c10 = src[row0 + x1];
            let c01 = src[row1 + x0];
            let c11 = src[row1 + x1];

            let w00 = inv_wx * inv_wy;
            let w10 = wx * inv_wy;
            let w01 = inv_wx * wy;
            let w11 = wx * wy;

            let a = (((c00 >> 24) & 0xFF) as f32 * w00
                + ((c10 >> 24) & 0xFF) as f32 * w10
                + ((c01 >> 24) & 0xFF) as f32 * w01
                + ((c11 >> 24) & 0xFF) as f32 * w11) as u32;
            let r = (((c00 >> 16) & 0xFF) as f32 * w00
                + ((c10 >> 16) & 0xFF) as f32 * w10
                + ((c01 >> 16) & 0xFF) as f32 * w01
                + ((c11 >> 16) & 0xFF) as f32 * w11) as u32;
            let g = (((c00 >> 8) & 0xFF) as f32 * w00
                + ((c10 >> 8) & 0xFF) as f32 * w10
                + ((c01 >> 8) & 0xFF) as f32 * w01
                + ((c11 >> 8) & 0xFF) as f32 * w11) as u32;
            let b = ((c00 & 0xFF) as f32 * w00
                + (c10 & 0xFF) as f32 * w10
                + (c01 & 0xFF) as f32 * w01
                + (c11 & 0xFF) as f32 * w11) as u32;

            dst[dst_row + x] = (a.min(255) << 24)
                | (r.min(255) << 16)
                | (g.min(255) << 8)
                | b.min(255);
        }
    }

    dst
}

fn fast_box_blur_argb(pixels: &mut [u32], width: usize, height: usize, radius: usize) {
    if width == 0 || height == 0 || radius == 0 {
        return;
    }
    let mut temp = vec![0xFF000000_u32; width * height];
    let window = (radius * 2 + 1) as u32;

    for y in 0..height {
        let row = y * width;
        for x in 0..width {
            let mut sum_r = 0_u32;
            let mut sum_g = 0_u32;
            let mut sum_b = 0_u32;
            for k in 0..=(radius * 2) {
                let sx = (x + k).saturating_sub(radius).min(width - 1);
                let c = pixels[row + sx];
                sum_r += (c >> 16) & 0xFF;
                sum_g += (c >> 8) & 0xFF;
                sum_b += c & 0xFF;
            }
            temp[row + x] = 0xFF000000
                | ((sum_r / window) << 16)
                | ((sum_g / window) << 8)
                | (sum_b / window);
        }
    }

    for y in 0..height {
        let dst_row = y * width;
        for x in 0..width {
            let mut sum_r = 0_u32;
            let mut sum_g = 0_u32;
            let mut sum_b = 0_u32;
            for k in 0..=(radius * 2) {
                let sy = (y + k).saturating_sub(radius).min(height - 1);
                let c = temp[sy * width + x];
                sum_r += (c >> 16) & 0xFF;
                sum_g += (c >> 8) & 0xFF;
                sum_b += c & 0xFF;
            }
            let r = ((sum_r / window) * 68) / 100;
            let g = ((sum_g / window) * 68) / 100;
            let b = ((sum_b / window) * 68) / 100;
            pixels[dst_row + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_resizeArgbNative<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    src_pixels: JIntArray<'local>,
    src_w: jint,
    src_h: jint,
    dst_w: jint,
    dst_h: jint,
) -> JIntArray<'local> {
    let empty_fallback = || -> JIntArray<'local> {
        env.new_int_array(0).unwrap_or_default()
    };

    if src_w <= 0 || src_h <= 0 || dst_w <= 0 || dst_h <= 0 {
        return empty_fallback();
    }
    let expected_len = (src_w as usize) * (src_h as usize);
    let Ok(arr_len) = env.get_array_length(&src_pixels) else {
        return empty_fallback();
    };
    if (arr_len as usize) < expected_len {
        return empty_fallback();
    }

    let mut src_buf = vec![0_i32; expected_len];
    if env.get_int_array_region(&src_pixels, 0, &mut src_buf).is_err() {
        return empty_fallback();
    }

    let src_u32: Vec<u32> = src_buf.into_iter().map(|v| v as u32).collect();
    let resized = resize_argb_bilinear(
        &src_u32,
        src_w as usize,
        src_h as usize,
        dst_w as usize,
        dst_h as usize,
    );
    let out_i32: Vec<i32> = resized.into_iter().map(|v| v as i32).collect();

    match env.new_int_array(out_i32.len() as i32) {
        Ok(out_arr) => {
            if env.set_int_array_region(&out_arr, 0, &out_i32).is_ok() {
                out_arr
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_composeFitBlurArgbNative<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    src_pixels: JIntArray<'local>,
    src_w: jint,
    src_h: jint,
    canvas_w: jint,
    canvas_h: jint,
) -> JIntArray<'local> {
    let empty_fallback = || -> JIntArray<'local> {
        env.new_int_array(0).unwrap_or_default()
    };

    if src_w <= 0 || src_h <= 0 || canvas_w <= 0 || canvas_h <= 0 {
        return empty_fallback();
    }
    let sw = src_w as usize;
    let sh = src_h as usize;
    let cw = canvas_w as usize;
    let ch = canvas_h as usize;

    let Ok(arr_len) = env.get_array_length(&src_pixels) else {
        return empty_fallback();
    };
    if (arr_len as usize) < sw * sh {
        return empty_fallback();
    }

    let mut src_buf = vec![0_i32; sw * sh];
    if env.get_int_array_region(&src_pixels, 0, &mut src_buf).is_err() {
        return empty_fallback();
    }
    let src_u32: Vec<u32> = src_buf.into_iter().map(|v| v as u32).collect();

    let blur_w = (cw / 8).max(32);
    let blur_h = (ch / 8).max(32);
    let scale_fill = (blur_w as f32 / sw as f32).max(blur_h as f32 / sh as f32);
    let fill_w = ((sw as f32 * scale_fill).round() as usize).max(blur_w);
    let fill_h = ((sh as f32 * scale_fill).round() as usize).max(blur_h);
    let filled_small = resize_argb_bilinear(&src_u32, sw, sh, fill_w, fill_h);

    let crop_x = (fill_w - blur_w) / 2;
    let crop_y = (fill_h - blur_h) / 2;
    let mut bg_small = vec![0xFF000000_u32; blur_w * blur_h];
    for y in 0..blur_h {
        let s_row = (crop_y + y) * fill_w + crop_x;
        let d_row = y * blur_w;
        bg_small[d_row..d_row + blur_w].copy_from_slice(&filled_small[s_row..s_row + blur_w]);
    }

    fast_box_blur_argb(&mut bg_small, blur_w, blur_h, 6);
    fast_box_blur_argb(&mut bg_small, blur_w, blur_h, 4);
    let mut canvas = resize_argb_bilinear(&bg_small, blur_w, blur_h, cw, ch);

    let scale_fit = (cw as f32 / sw as f32).min(ch as f32 / sh as f32);
    let fit_w = ((sw as f32 * scale_fit).round() as usize).clamp(1, cw);
    let fit_h = ((sh as f32 * scale_fit).round() as usize).clamp(1, ch);
    let fg_pixels = resize_argb_bilinear(&src_u32, sw, sh, fit_w, fit_h);

    let offset_x = (cw - fit_w) / 2;
    let offset_y = (ch - fit_h) / 2;
    for y in 0..fit_h {
        let s_row = y * fit_w;
        let d_row = (offset_y + y) * cw + offset_x;
        canvas[d_row..d_row + fit_w].copy_from_slice(&fg_pixels[s_row..s_row + fit_w]);
    }

    let out_i32: Vec<i32> = canvas.into_iter().map(|v| v as i32).collect();
    match env.new_int_array(out_i32.len() as i32) {
        Ok(out_arr) => {
            if env.set_int_array_region(&out_arr, 0, &out_i32).is_ok() {
                out_arr
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}

// ============================================================================
// FASA 3B: PENGHURAI LIRIK (.LRC & .SRT) PANTAS DALAM RUST
// ============================================================================

struct RawSubtitleEntry {
    start_ms: i64,
    end_ms: i64,
    text: String,
}

fn parse_lrc_timestamp_tag(tag: &str) -> Option<i64> {
    let colon_pos = tag.find(':')?;
    let min: i64 = tag[..colon_pos].trim().parse().ok()?;
    let rest = &tag[colon_pos + 1..];
    if let Some(dot_pos) = rest.find(|c| c == '.' || c == ':') {
        let sec: i64 = rest[..dot_pos].trim().parse().ok()?;
        let frac = rest[dot_pos + 1..].trim();
        let frac_val: i64 = frac.parse().ok()?;
        let ms = match frac.len() {
            1 => frac_val * 100,
            2 => frac_val * 10,
            3 => frac_val,
            _ => 0,
        };
        Some(min * 60_000 + sec * 1000 + ms)
    } else {
        let sec: i64 = rest.trim().parse().ok()?;
        Some(min * 60_000 + sec * 1000)
    }
}

fn parse_lrc_rust(content: &str, fallback_end_ms: i64) -> Vec<RawSubtitleEntry> {
    let mut timed: Vec<(i64, String)> = Vec::new();

    for line in content.lines() {
        let mut timestamps: Vec<i64> = Vec::new();
        let mut cursor = line;
        let mut clean_text = String::new();

        while let Some(open_idx) = cursor.find('[') {
            clean_text.push_str(&cursor[..open_idx]);
            let after_open = &cursor[open_idx + 1..];
            if let Some(close_idx) = after_open.find(']') {
                let inside = &after_open[..close_idx];
                if let Some(ts) = parse_lrc_timestamp_tag(inside) {
                    timestamps.push(ts);
                }
                cursor = &after_open[close_idx + 1..];
            } else {
                clean_text.push('[');
                cursor = after_open;
                break;
            }
        }
        clean_text.push_str(cursor);
        let lyric = clean_text.trim();
        if !timestamps.is_empty() && !lyric.is_empty() {
            for ts in timestamps {
                timed.push((ts, lyric.to_string()));
            }
        }
    }

    timed.sort_by_key(|item| item.0);
    let len = timed.len();
    let mut out = Vec::with_capacity(len);

    for i in 0..len {
        let (start_ms, ref text) = timed[i];
        let end_ms = if i + 1 < len && timed[i + 1].0 > start_ms {
            (timed[i + 1].0 - 50).max(start_ms + 300)
        } else {
            (start_ms + 4000).min(fallback_end_ms.max(start_ms + 1000))
        };
        out.push(RawSubtitleEntry {
            start_ms,
            end_ms,
            text: text.clone(),
        });
    }

    out
}

fn parse_srt_hms(part: &str) -> Option<i64> {
    let clean = part.trim();
    let comma_pos = clean.find(|c| c == ',' || c == '.')?;
    let hms = &clean[..comma_pos];
    let ms: i64 = clean[comma_pos + 1..].trim().parse().ok()?;
    let mut parts = hms.split(':');
    let h: i64 = parts.next()?.trim().parse().ok()?;
    let m: i64 = parts.next()?.trim().parse().ok()?;
    let s: i64 = parts.next()?.trim().parse().ok()?;
    Some(h * 3_600_000 + m * 60_000 + s * 1000 + ms)
}

fn parse_srt_rust(content: &str) -> Vec<RawSubtitleEntry> {
    let normalized = content.replace("\r\n", "\n");
    let mut out = Vec::new();

    for block in normalized.split("\n\n") {
        let lines: Vec<&str> = block
            .lines()
            .map(|l| l.trim())
            .filter(|l| !l.is_empty())
            .collect();

        if let Some(arrow_idx) = lines.iter().position(|l| l.contains("-->")) {
            let mut halves = lines[arrow_idx].split("-->");
            if let (Some(left), Some(right)) = (halves.next(), halves.next()) {
                if let (Some(start_ms), Some(raw_end_ms)) = (parse_srt_hms(left), parse_srt_hms(right)) {
                    let end_ms = raw_end_ms.max(start_ms + 250);
                    let text = lines[arrow_idx + 1..].join("\n").trim().to_string();
                    if !text.is_empty() {
                        out.push(RawSubtitleEntry {
                            start_ms,
                            end_ms,
                            text,
                        });
                    }
                }
            }
        }
    }

    out.sort_by_key(|item| item.start_ms);
    out
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_parseSubtitlesNative<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    raw_content: JString<'local>,
    fallback_end_ms: jlong,
) -> jobjectArray {
    let empty_obj_array = |env: &mut JNIEnv<'local>| -> jobjectArray {
        env.find_class("java/lang/String")
            .and_then(|cls| env.new_object_array(0, cls, JObject::null()))
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
    };

    let Ok(java_str) = env.get_string(&raw_content) else {
        return empty_obj_array(&mut env);
    };
    let content: String = java_str.into();

    let entries = if content.contains("-->") {
        parse_srt_rust(&content)
    } else {
        parse_lrc_rust(&content, fallback_end_ms)
    };

    let Ok(str_cls) = env.find_class("java/lang/String") else {
        return empty_obj_array(&mut env);
    };
    let Ok(result_arr) = env.new_object_array(entries.len() as i32, str_cls, JObject::null()) else {
        return empty_obj_array(&mut env);
    };

    for (idx, item) in entries.iter().enumerate() {
        let encoded = format!("{}|{}|{}", item.start_ms, item.end_ms, item.text);
        if let Ok(jstr) = env.new_string(&encoded) {
            let _ = env.set_object_array_element(&result_arr, idx as i32, JObject::from(jstr));
        }
    }

    let _ = JValue::from(&result_arr);
    result_arr.into_raw()
}
