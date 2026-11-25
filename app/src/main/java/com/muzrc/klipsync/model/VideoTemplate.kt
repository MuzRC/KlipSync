package com.muzrc.klipsync.model

enum class TemplateCategory(val label: String) {
    ALL("Semua"),
    TIKTOK_REELS("TikTok & Reels"),
    CINEMATIC("Sinematik"),
    VLOG_CLEAN("Vlog & Santai"),
    MONTAGE("Montage & Beat")
}

data class VideoTemplate(
    val id: String,
    val name: String,
    val subtitle: String,
    val category: TemplateCategory,
    val badgeText: String,
    val aspectRatio: AspectRatioPreset,
    val cropScaleMode: CropScaleMode,
    val colorConfig: ColorFilterConfig,
    val transitionConfig: TransitionConfig,
    val motionEffect: CameraMotionEffect,
    val motionIntensity: Float,
    val clipSpeed: Float = 1.0f
) {
    val speedLabel: String
        get() = if (clipSpeed == 1.0f) "1.0x Normal" else "${clipSpeed}x"
}

object VideoTemplatePresets {

    val allTemplates: List<VideoTemplate> = listOf(
        VideoTemplate(
            id = "tiktok_cinematic_warm",
            name = "TikTok Sinematik Hangat",
            subtitle = "Tona hangat filem dengan zoom perlahan dan transisi larut lembut untuk kandungan bercerita.",
            category = TemplateCategory.CINEMATIC,
            badgeText = "9:16 • Sinematik",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.CINEMATIC_WARM,
                brightness = 0.02f,
                contrast = 1.08f,
                saturation = 1.06f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.ZOOM_DISSOLVE,
                durationMs = 700L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.ZOOM_IN,
            motionIntensity = 0.18f,
            clipSpeed = 1.0f
        ),
        VideoTemplate(
            id = "tiktok_moody_dark",
            name = "Moody Gelap Estetik",
            subtitle = "Suasana gelap dramatik dengan gerak perlahan 0.9x, sesuai untuk video malam atau lagu galau.",
            category = TemplateCategory.TIKTOK_REELS,
            badgeText = "9:16 • Estetik",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.MOODY_DARK,
                brightness = -0.06f,
                contrast = 1.14f,
                saturation = 0.92f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.FADE_TO_BLACK,
                durationMs = 800L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.ZOOM_OUT,
            motionIntensity = 0.20f,
            clipSpeed = 0.90f
        ),
        VideoTemplate(
            id = "velocity_montage",
            name = "Montage Laju & Dinamik",
            subtitle = "Potongan pantas 1.25x dengan transisi slide kiri dan zoom bertenaga untuk sorotan aksi.",
            category = TemplateCategory.MONTAGE,
            badgeText = "9:16 • Pantas",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.VIVID,
                brightness = 0.03f,
                contrast = 1.12f,
                saturation = 1.18f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.SLIDE_LEFT,
                durationMs = 450L,
                endBehavior = VideoEndBehavior.LOOP_TO_AUDIO
            ),
            motionEffect = CameraMotionEffect.ZOOM_IN,
            motionIntensity = 0.28f,
            clipSpeed = 1.25f
        ),
        VideoTemplate(
            id = "clean_vlog_bright",
            name = "Vlog Cerah & Bersih",
            subtitle = "Pencahayaan semula jadi yang segar dengan gerakan pan kanan halus untuk vlog harian.",
            category = TemplateCategory.VLOG_CLEAN,
            badgeText = "9:16 • Vlog",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.NONE,
                brightness = 0.06f,
                contrast = 1.05f,
                saturation = 1.08f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.CROSSFADE,
                durationMs = 600L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.PAN_RIGHT,
            motionIntensity = 0.14f,
            clipSpeed = 1.0f
        ),
        VideoTemplate(
            id = "retro_vintage_story",
            name = "Story Retro Klasik",
            subtitle = "Gaya filem klasik dengan latar kabur elegan dan transisi crossfade lembut.",
            category = TemplateCategory.TIKTOK_REELS,
            badgeText = "9:16 • Retro",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FIT_BLUR,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.VINTAGE,
                brightness = 0.02f,
                contrast = 1.06f,
                saturation = 0.90f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.CROSSFADE,
                durationMs = 850L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.ZOOM_OUT,
            motionIntensity = 0.16f,
            clipSpeed = 0.95f
        ),
        VideoTemplate(
            id = "noir_dramatic_bw",
            name = "Hitam Putih Dramatik",
            subtitle = "Kontras monokrom tinggi dengan gerak perlahan 0.85x dan fade ke hitam yang mendalam.",
            category = TemplateCategory.CINEMATIC,
            badgeText = "9:16 • Monokrom",
            aspectRatio = AspectRatioPreset.TIKTOK_9_16,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.BLACK_AND_WHITE,
                brightness = -0.02f,
                contrast = 1.24f,
                saturation = 0.0f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.FADE_TO_BLACK,
                durationMs = 950L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.ZOOM_IN,
            motionIntensity = 0.15f,
            clipSpeed = 0.85f
        ),
        VideoTemplate(
            id = "ig_feed_aesthetic",
            name = "IG Feed 4:5 Estetik",
            subtitle = "Format potret 4:5 khusus suapan Instagram dengan tona hangat dan gerakan pan kiri.",
            category = TemplateCategory.VLOG_CLEAN,
            badgeText = "4:5 • IG Feed",
            aspectRatio = AspectRatioPreset.PORTRAIT_4_5,
            cropScaleMode = CropScaleMode.FILL_CROP,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.CINEMATIC_WARM,
                brightness = 0.03f,
                contrast = 1.06f,
                saturation = 1.04f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.CROSSFADE,
                durationMs = 700L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.PAN_LEFT,
            motionIntensity = 0.15f,
            clipSpeed = 1.0f
        ),
        VideoTemplate(
            id = "youtube_widescreen_pro",
            name = "YouTube 16:9 Sinematik",
            subtitle = "Skrin lebar 16:9 dengan warna menyerlah, latar kabur kemas dan transisi zoom larut.",
            category = TemplateCategory.CINEMATIC,
            badgeText = "16:9 • YouTube",
            aspectRatio = AspectRatioPreset.YOUTUBE_16_9,
            cropScaleMode = CropScaleMode.FIT_BLUR,
            colorConfig = ColorFilterConfig(
                preset = ColorFilterPreset.VIVID,
                brightness = 0.02f,
                contrast = 1.08f,
                saturation = 1.12f
            ),
            transitionConfig = TransitionConfig(
                joinMode = JoinMode.OVERLAP,
                transitionType = TransitionType.ZOOM_DISSOLVE,
                durationMs = 800L,
                endBehavior = VideoEndBehavior.FREEZE_FRAME
            ),
            motionEffect = CameraMotionEffect.ZOOM_IN,
            motionIntensity = 0.16f,
            clipSpeed = 1.0f
        )
    )

    fun findById(id: String?): VideoTemplate? {
        if (id.isNullOrBlank()) return null
        return allTemplates.find { it.id == id }
    }
}
