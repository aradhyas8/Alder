package dev.alder

import android.content.Intent
import android.net.Uri

/**
 * Hands a source to another video app (VLC, MX Player, Just Player, mpv, Vimu) and reads back where the viewer stopped.
 * After NuvioTV's ExternalPlayerResultContract (GPL-3.0): players disagree on the extra names and their types, so the
 * resume point goes out under each one and the answer is read leniently.
 */
internal object ExternalPlayer {
    const val REQUEST = 41

    fun intent(url: String, title: String, headers: Map<String, String>, resumeMs: Long, subtitle: String?) =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url), "video/*")
            putExtra("title", title); putExtra(Intent.EXTRA_TITLE, title); putExtra("forcename", title) // Vimu
            if (headers.isNotEmpty()) putExtra("headers", headers.flatMap { listOf(it.key, it.value) }.toTypedArray()) // MX, Just Player
            if (resumeMs > 0) {
                putExtra("position", resumeMs.toInt()) // MX, Just Player, mpv
                putExtra("extra_position", resumeMs) // VLC
                putExtra("startfrom", resumeMs.toInt()); putExtra("forceresume", true) // Vimu
                putExtra("from_start", false) // VLC
            }
            putExtra("return_result", true) // MX only answers when asked
            subtitle?.let { Uri.parse(it) }?.let { sub ->
                putExtra("subs", arrayOf(sub)); putExtra("subs.enable", arrayOf(sub)) // MX, mpv
                putExtra("subtitle_uri", arrayOf(sub)) // Just Player
                putExtra("subtitles_location", sub.toString()) // VLC
            }
        }

    /** Where the viewer stopped: (position, duration) in ms, either 0 when the player didn't say. */
    fun result(data: Intent?): Pair<Long, Long> {
        data ?: return 0L to 0L
        val duration = first(data, "extra_duration", "duration")
        var position = first(data, "extra_position", "position")
        // MX and Just Player report the end of the file this way, often with the position reset to 0.
        if (data.getStringExtra("end_by") == "playback_completion" && duration > 0) position = duration
        return position to duration
    }

    private fun first(data: Intent, vararg keys: String): Long {
        for (k in keys) {
            data.getLongExtra(k, -1L).takeIf { it > 0 }?.let { return it }
            data.getIntExtra(k, -1).takeIf { it > 0 }?.let { return it.toLong() }
        }
        return 0L
    }
}
