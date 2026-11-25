# 🎬 KlipSync — Audio & Video Mixer (Android)

Aplikasi penyuntingan video dan pengadun audio ringan untuk Android yang dibina menggunakan **Kotlin**, **Jetpack Compose**, dan teras pemprosesan imej berprestasi tinggi **Rust (NDK)** — 100% berfungsi secara *offline*.

---

## ✨ Ciri-Ciri Utama

* **🎞️ Suntingan Klip & Gambar Multi-Trek:** Gabung video dan foto, potong (*trim*), belah (*split*), putar 90°, cermin (*mirror*), ulang (*loop*), serta kesan gerakan kamera (*Zoom In*, *Zoom Out*, *Pan Kiri/Kanan*) dan transisi antara babak.
* **⚡ Kawalan Kelajuan:** Laras kelajuan klip dari `0.25x` (*Slow-Mo*) sehingga `4.0x` (*Fast-Forward*).
* **🎨 Penapis & Pelarasan Warna:** Pratetap penapis sinematik berserta kawalan manual *Brightness*, *Contrast*, dan *Saturation*.
* **🎧 Pengadun Audio & Auto-Beat Offline:** Masukkan lagu latar atau ekstrak audio terus dari video, kawalan *Fade In/Out*, dan **pengesan rentak (Beat Detection) secara offline** untuk menyelaraskan klip ikut rentak muzik.
* **📝 Sarikata & Import Lirik:** Bina sarikata secara manual atau import fail `.LRC` dan `.SRT` lengkap dengan tetapan fon, warna, dan posisi.
* **📐 Nisbah Skrin & Watermark:** Sokongan format `9:16` (TikTok/Reels), `16:9` (YouTube), `1:1`, `4:5`, mod bingkai penuh/letterbox, serta logo *watermark* PNG.
* **🚀 Enjin Rust + Media3 Transformer:** Pemampatan input imej pantas berasaskan Rust dan pengekodan eksport MP4 berkualiti tinggi (menyokong Android 8.0 hingga Android 15+).

---

## 📥 Muat Turun APK

Fail pemasangan terbaharu (**Signed Release APK**) boleh dimuat turun terus di bahagian [**Releases**](https://github.com/MuzRC/KlipSync/releases/latest) di sebelah kanan halaman ini.

---

## 🛠️ Teknologi & Seni Bina

* **Bahasa Utama:** Kotlin & Rust (`jniLibs` untuk `arm64-v8a` & `armeabi-v7a`)
* **Antaramuka (UI):** Jetpack Compose (Material 3 — *Deep Obsidian & Electric Cyan*)
* **Enjin Media:** AndroidX Media3 (`ExoPlayer` & `Transformer`)
* **CI/CD:** Kompilasi automatik menggunakan GitHub Actions

---

Dibangunkan oleh **Musa Bin Sarudin (MuzRC)**
