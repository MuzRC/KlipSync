package com.muzrc.klipsync.model

enum class CropScaleMode(val label: String) {
    FILL_CROP("Penuh (Center Crop)"),
    FIT_BLUR("Muat Penuh + Latar Kabur")
}

enum class AspectRatioPreset(
    val label: String,
    val ratioWidth: Int,
    val ratioHeight: Int
) {
    ORIGINAL("Asal", 0, 0),
    TIKTOK_9_16("9:16 TikTok", 9, 16),
    YOUTUBE_16_9("16:9 YouTube", 16, 9),
    SQUARE_1_1("1:1 Segi Empat", 1, 1),
    PORTRAIT_4_5("4:5 IG Feed", 4, 5);

    val aspectRatioFloat: Float?
        get() = if (ratioWidth == 0 || ratioHeight == 0) null else ratioWidth.toFloat() / ratioHeight.toFloat()
}
