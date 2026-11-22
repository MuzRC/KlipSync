package com.muzrc.klipsync.model

enum class CropScaleMode(val label: String) {
    FILL_CROP("Isi Penuh (Crop Tengah)"),
    FIT("Muat Penuh (Letterbox)"),
    FIT_BLUR("Muat Penuh + Latar Kabur");
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

    init {
        require(ratioWidth >= 0 && ratioHeight >= 0) {
            "ratioWidth dan ratioHeight mesti >= 0 (dapat $ratioWidth/$ratioHeight)"
        }
    }

    /** Ratio sebagai Float, atau null jika ORIGINAL. */
    val aspectRatioFloat: Float?
        get() = if (ratioWidth == 0 || ratioHeight == 0) null
        else ratioWidth.toFloat() / ratioHeight.toFloat()

    /** Label ratio untuk paparan UI, contoh "9:16" atau "Asal". */
    val ratioLabel: String
        get() = if (ratioWidth == 0 || ratioHeight == 0) "Asal" else "$ratioWidth:$ratioHeight"

    val isPortrait: Boolean
        get() = aspectRatioFloat?.let { it < 1f } ?: false

    val isLandscape: Boolean
        get() = aspectRatioFloat?.let { it > 1f } ?: false

    val isSquare: Boolean
        get() = aspectRatioFloat == 1f
}
