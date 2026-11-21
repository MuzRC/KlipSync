use jni::objects::{JClass, JFloatArray, JLongArray, JShortArray};
use jni::sys::{jfloat, jint, jlong};
use jni::JNIEnv;

/// Penapis laluan-rendah (Low-Pass IIR) dua peringkat untuk mengasingkan dentuman bass/kick drum (< 160 Hz)
/// daripada vokal dan instrumen berfrekuensi tinggi sebelum pengiraan tenaga beat.
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
        // Puratakan semua saluran audio ke mono
        let mut mono = 0.0_f32;
        for ch in 0..channel_count {
            mono += (samples[idx + ch] as f32) / 32768.0_f32;
        }
        mono /= channel_count as f32;

        // Penapis low-pass dua peringkat (12 dB/oktaf)
        lp1 += alpha * (mono - lp1);
        lp2 += alpha * (lp1 - lp2);

        // Gabungkan 75% tenaga bass + 25% tenaga penuh supaya ketukan snare/clap turut dikesan
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
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_detectBeatsNative(
    env: JNIEnv,
    _class: JClass,
    pcm_samples: JShortArray,
    sample_rate: jint,
    channel_count: jint,
    min_interval_ms: jlong,
    threshold_multiplier: jfloat,
) -> JLongArray {
    let empty_fallback = || -> JLongArray {
        env.new_long_array(0)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
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

    // Tetingkap 30ms untuk ketepatan masa beat lebih tinggi daripada Kotlin (40ms)
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
    let history_radius = 12_usize; // ~360ms kiri & kanan
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
                out_arr.into_raw()
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}

#[no_mangle]
pub extern "system" fn Java_com_muzrc_klipsync_nativecore_NativeCoreBridge_extractWaveformPeaksNative(
    env: JNIEnv,
    _class: JClass,
    pcm_samples: JShortArray,
    target_bars: jint,
) -> JFloatArray {
    let empty_fallback = || -> JFloatArray {
        env.new_float_array(0)
            .map(|a| a.into_raw())
            .unwrap_or(std::ptr::null_mut())
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
        *p = (*p / max_peak).clamp(0.08_f32, 1.0_f32);
    }

    match env.new_float_array(bars as i32) {
        Ok(out_arr) => {
            if env.set_float_array_region(&out_arr, 0, &peaks).is_ok() {
                out_arr.into_raw()
            } else {
                empty_fallback()
            }
        }
        Err(_) => empty_fallback(),
    }
}
