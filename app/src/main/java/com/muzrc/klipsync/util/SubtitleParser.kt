package com.muzrc.klipsync.util

import android.content.Context
import android.net.Uri
import com.muzrc.klipsync.model.SubtitleItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SubtitleParser {

    private val lrcTagRegex = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val srtTimeRegex = Regex(
        """(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})"""
    )

    suspend fun parseFromUri(
        context: Context,
        uri: Uri,
        fallbackEndDurationMs: Long = 180_000L
    ): List<SubtitleItem> = withContext(Dispatchers.IO) {
        val rawText = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (_: Exception) {
            null
        } ?: return@withContext emptyList()

        if (rawText.contains("-->")) {
            parseSrt(rawText)
        } else {
            parseLrc(rawText, fallbackEndDurationMs)
        }
    }

    fun parseLrc(content: String, fallbackEndDurationMs: Long): List<SubtitleItem> {
        val timedLines = mutableListOf<Pair<Long, String>>()

        content.lines().forEach { line ->
            val matches = lrcTagRegex.findAll(line).toList()
            if (matches.isNotEmpty()) {
                val lyricText = line.replace(lrcTagRegex, "").trim()
                if (lyricText.isNotEmpty()) {
                    matches.forEach { match ->
                        val min = match.groupValues[1].toLongOrNull() ?: 0L
                        val sec = match.groupValues[2].toLongOrNull() ?: 0L
                        val fracStr = match.groupValues[3]
                        val ms = when (fracStr.length) {
                            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                            3 -> fracStr.toLongOrNull() ?: 0L
                            else -> 0L
                        }
                        val totalMs = (min * 60_000L) + (sec * 1000L) + ms
                        timedLines.add(totalMs to lyricText)
                    }
                }
            }
        }

        val sorted = timedLines.sortedBy { it.first }
        if (sorted.isEmpty()) return emptyList()

        return sorted.mapIndexed { index, (startMs, text) ->
            val nextStartMs = sorted.getOrNull(index + 1)?.first
            val endMs = if (nextStartMs != null && nextStartMs > startMs) {
                (nextStartMs - 50L).coerceAtLeast(startMs + 300L)
            } else {
                (startMs + 4000L).coerceAtMost(maxOf(fallbackEndDurationMs, startMs + 1000L))
            }
            SubtitleItem(
                text = text,
                startMs = startMs,
                endMs = endMs
            )
        }
    }

    fun parseSrt(content: String): List<SubtitleItem> {
        val results = mutableListOf<SubtitleItem>()
        val blocks = content.replace("\r\n", "\n").split("\n\n")

        blocks.forEach { block ->
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            val timeLineIndex = lines.indexOfFirst { srtTimeRegex.containsMatchIn(it) }
            if (timeLineIndex != -1) {
                val match = srtTimeRegex.find(lines[timeLineIndex])
                if (match != null) {
                    val startMs = hmsToMs(
                        match.groupValues[1],
                        match.groupValues[2],
                        match.groupValues[3],
                        match.groupValues[4]
                    )
                    val endMs = hmsToMs(
                        match.groupValues[5],
                        match.groupValues[6],
                        match.groupValues[7],
                        match.groupValues[8]
                    ).coerceAtLeast(startMs + 250L)

                    val text = lines.drop(timeLineIndex + 1).joinToString("\n").trim()
                    if (text.isNotEmpty()) {
                        results.add(
                            SubtitleItem(
                                text = text,
                                startMs = startMs,
                                endMs = endMs
                            )
                        )
                    }
                }
            }
        }

        return results.sortedBy { it.startMs }
    }

    private fun hmsToMs(h: String, m: String, s: String, ms: String): Long {
        val hours = h.toLongOrNull() ?: 0L
        val mins = m.toLongOrNull() ?: 0L
        val secs = s.toLongOrNull() ?: 0L
        val millis = ms.toLongOrNull() ?: 0L
        return (hours * 3_600_000L) + (mins * 60_000L) + (secs * 1000L) + millis
    }
}
