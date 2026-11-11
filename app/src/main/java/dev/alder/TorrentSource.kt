package dev.alder

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder

internal val supportsTorrentPlayback get() = Build.VERSION.SDK_INT >= 24

class TorrentSource private constructor(val infoHash: String, val fileIndex: Int?, private val trackers: List<String>) {
    val magnet: String get() = buildString {
        append("magnet:?xt=urn:btih:").append(infoHash)
        trackers.forEach { append("&tr=").append(URLEncoder.encode(it, "UTF-8")) }
    }

    fun toJson() = JSONObject().put("infoHash", infoHash).put("fileIdx", fileIndex)
        .put("sources", JSONArray(trackers.map { "tracker:$it" }))

    companion object {
        private val hashFormat = Regex("[0-9a-fA-F]{40}")

        fun from(json: JSONObject): TorrentSource? {
            val hash = json.text("infoHash")?.takeIf(hashFormat::matches) ?: return null
            val index = json.text("fileIdx")?.let { it.toIntOrNull()?.takeIf { i -> i >= 0 } ?: return null }
            val trackers = json.optJSONArray("sources").items().asSequence().filterIsInstance<String>()
                .filter { it.startsWith("tracker:") }.map { it.removePrefix("tracker:") }
                .filter { tracker ->
                    tracker.length <= 512 && runCatching {
                        val uri = URI(tracker)
                        uri.scheme in setOf("http", "https", "udp") && uri.host != null
                    }.getOrDefault(false)
                }.distinct().take(16).toList()
            return TorrentSource(hash.lowercase(), index, trackers)
        }
    }
}
