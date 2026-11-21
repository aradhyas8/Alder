package dev.alder

import android.annotation.SuppressLint
import android.util.Base64
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/**
 * Stremio library items (each title's watch history), following stremio-core's player rules so progress made
 * here shows up in Stremio on other devices. Items stay raw JSON so fields this app doesn't use survive the trip.
 */
object Library {
    private const val WATCHED = 0.7 // stremio-core WATCHED_THRESHOLD_COEF: watched this much, it counts as seen
    private const val CREDITS = 0.9 // CREDITS_THRESHOLD_COEF: stopped past this, it's finished

    fun continueWatching(
        items: List<JSONObject>,
        unreleased: (JSONObject, String) -> Boolean = { _, _ -> false },
    ): List<JSONObject> = items
        .filter { it.optString("type") != "other" && (!it.optBoolean("removed") || it.optBoolean("temp")) }
        .filter {
            val videoId = savedVideoId(it)
            if (nextEpisodePending(it)) !unreleased(it, videoId) else resumeAt(it, videoId) > 0
        }
        .sortedByDescending { it.state.text("lastWatched") }

    /** A title that isn't in the library yet: Stremio keeps those as temporary, hidden items. */
    fun newItem(id: String, type: String, name: String, poster: String?): JSONObject {
        val now = now()
        return JSONObject().put("_id", id).put("name", name).put("type", type).put("poster", poster ?: "")
            .put("posterShape", "poster").put("removed", true).put("temp", true).put("_ctime", now).put("_mtime", now)
            .put("behaviorHints", JSONObject())
            .put("state", JSONObject().put("lastWatched", now).put("timeWatched", 0).put("timeOffset", 0)
                .put("overallTimeWatched", 0).put("timesWatched", 0).put("flaggedWatched", 0).put("duration", 0)
                .put("video_id", JSONObject.NULL).put("watched", JSONObject.NULL).put("noNotif", false))
    }

    /** In the library ("My List") rather than only remembered for Continue Watching. */
    fun inList(item: JSONObject?) = item != null && !item.optBoolean("removed")

    /** Adds the title to the library or takes it out; its watch history stays either way. */
    fun setInList(item: JSONObject, on: Boolean) {
        item.put("removed", !on).put("temp", false).put("_mtime", now())
    }

    /** Marks the current title complete without discarding its watch history. */
    fun markTitleWatched(item: JSONObject, videos: List<Video> = emptyList()) {
        val s = item.state
        s.text("video_id")?.let { id -> if (videos.any { it.id == id }) markWatched(item, videos, id) }
        s.put("timesWatched", maxOf(1, s.optInt("timesWatched")))
        s.put("flaggedWatched", 1).put("timeOffset", 0)
        item.put("_mtime", now())
    }

    /**
     * Off Continue Watching, as Stremio's own dismiss does (stremio-core's RewindLibraryItem): the resume point goes;
     * watch history, the episode reached and My List stay, and watching it again brings it back. It syncs, so the
     * phone and the web hide it too. Returns the resume point it had, so [unhide] can put it back.
     */
    fun hideFromContinueWatching(item: JSONObject): Long {
        val offset = item.state.optLong("timeOffset")
        item.state.put("timeOffset", 0)
        item.put("_mtime", now())
        return offset
    }

    /** Back on Continue Watching at [offset]; left alone when it was watched again since (it's back already). */
    fun unhide(item: JSONObject, offset: Long) {
        if (item.state.optLong("timeOffset") > 0) return
        item.state.put("timeOffset", offset)
        item.put("_mtime", now())
    }

    fun savedVideoId(item: JSONObject): String = item.state.text("video_id") ?: item.optString("_id")

    /** A 1 ms offset points to the next episode without any playback progress. */
    fun nextEpisodePending(item: JSONObject): Boolean =
        item.state.optLong("timeOffset") == 1L && item.optString("type") != "movie" && item.state.text("video_id") != null

    /** Where to resume [videoId] in milliseconds, 0 to start from the beginning. */
    fun resumeAt(item: JSONObject, videoId: String): Long {
        val s = item.state
        val offset = s.optLong("timeOffset")
        if (s.text("video_id") == null && item.optString("type") != "movie") return 0
        return if (savedVideoId(item) == videoId && offset > 1 && !pastCredits(offset, s.optLong("duration"))) offset else 0
    }

    private fun pastCredits(offset: Long, duration: Long) = duration > 0 && offset >= duration * CREDITS

    private class WatchedBits(val anchor: String, val anchorLength: Int, val bits: ByteArray)

    @SuppressLint("NewApi")
    private fun decodeBase64(raw: String): ByteArray =
        runCatching { Base64.decode(raw, Base64.DEFAULT) }.getOrNull()
            ?: java.util.Base64.getMimeDecoder().decode(raw)

    @SuppressLint("NewApi")
    private fun encodeBase64(bytes: ByteArray): String =
        runCatching { Base64.encodeToString(bytes, Base64.NO_WRAP) }.getOrNull()
            ?: java.util.Base64.getEncoder().encodeToString(bytes)

    private fun parseWatched(field: String?): WatchedBits? {
        if (field.isNullOrBlank()) return null
        val parts = field.split(":")
        if (parts.size < 3) return null
        return runCatching {
            val anchor = parts.dropLast(2).joinToString(":") // ids may hold ':' themselves
            val anchorLength = parts[parts.size - 2].toInt()
            if (anchor.isEmpty() || anchorLength <= 0) return null
            val bits = InflaterInputStream(decodeBase64(parts.last()).inputStream()).use { it.readBytes() }
            WatchedBits(anchor, anchorLength, bits)
        }.getOrNull()
    }

    private fun encodeBits(bits: ByteArray): String {
        val zipped = ByteArrayOutputStream().also { out ->
            DeflaterOutputStream(out, Deflater(6)).use { it.write(bits) }
        }.toByteArray()
        return encodeBase64(zipped)
    }

    private fun isBitSet(bits: ByteArray, index: Int): Boolean =
        index >= 0 && index / 8 < bits.size && ((bits[index / 8].toInt() and 0xff) ushr (index % 8)) and 1 == 1

    private fun setBit(bits: ByteArray, index: Int) {
        if (index >= 0 && index / 8 < bits.size) {
            bits[index / 8] = (bits[index / 8].toInt() or (1 shl (index % 8))).toByte()
        }
    }

    private fun highestSetBit(bits: ByteArray): Int {
        for (i in bits.size * 8 - 1 downTo 0) if (isBitSet(bits, i)) return i
        return -1
    }

    /**
     * Merges two stremio-core watched bitfields (`{anchor}:{anchorLength}:{base64Zlib}`) without needing the full
     * episode list, returning `(mergedBitfield, newlyMarkedCount)`.
     */
    internal fun mergeWatchedBitfields(remoteWatched: String?, localWatched: String?): Pair<String?, Int> {
        if (localWatched.isNullOrBlank()) return remoteWatched to 0
        if (remoteWatched.isNullOrBlank()) {
            val count = parseWatched(localWatched)?.bits?.sumOf { Integer.bitCount(it.toInt() and 0xff) } ?: 1
            return localWatched to maxOf(1, count)
        }
        if (remoteWatched == localWatched) return remoteWatched to 0
        val remote = parseWatched(remoteWatched) ?: return localWatched to 1
        val local = parseWatched(localWatched) ?: return localWatched to 1

        val shift = when {
            remote.anchor == local.anchor -> local.anchorLength - remote.anchorLength
            else -> {
                val rEp = episodeNumbers(remote.anchor)
                val lEp = episodeNumbers(local.anchor)
                if (rEp != null && lEp != null && rEp.first == lEp.first) {
                    (local.anchorLength - (lEp.second - rEp.second)) - remote.anchorLength
                } else 0
            }
        }
        val remoteShift = maxOf(0, shift)
        val localShift = maxOf(0, -shift)
        val rLen = remote.anchorLength + remoteShift
        val lLen = local.anchorLength + localShift
        val maxBit = maxOf(
            highestSetBit(remote.bits) + remoteShift,
            highestSetBit(local.bits) + localShift,
            rLen - 1,
            lLen - 1,
        )
        val out = ByteArray((maxBit + 8) / 8)
        for (i in 0 until remote.bits.size * 8) {
            if (isBitSet(remote.bits, i)) setBit(out, i + remoteShift)
        }
        var addedBits = 0
        for (i in 0 until local.bits.size * 8) {
            if (isBitSet(local.bits, i)) {
                val target = i + localShift
                if (!isBitSet(remote.bits, target - remoteShift)) addedBits++
                setBit(out, target)
            }
        }
        if (addedBits == 0) return remoteWatched to 0
        val (anchor, len) = if (lLen >= rLen) local.anchor to lLen else remote.anchor to rLen
        return "$anchor:$len:${encodeBits(out)}" to addedBits
    }

    /**
     * The episodes Stremio has ticked as watched, read from stremio-core's watched bitfield (`state.watched`):
     * `{anchor video id}:{anchor length}:{base64 zlib bits}`, one bit per episode in season-then-episode order, shifted
     * so the anchor (the last one watched) lines up even after new episodes come out.
     */
    fun watched(item: JSONObject?, videos: List<Video>): Set<String> {
        val parsed = parseWatched(item?.optJSONObject("state")?.text("watched")) ?: return emptySet()
        val ids = videos.sortedWith(compareBy({ it.season }, { it.episode })).map { it.id }
        val anchorAt = ids.indexOf(parsed.anchor)
        if (anchorAt < 0) return emptySet() // stremio-core: anchor gone, nothing counts as watched
        val offset = parsed.anchorLength - anchorAt - 1
        return ids.filterIndexed { i, _ -> isBitSet(parsed.bits, i + offset) }.toSet()
    }

    /** Sets the watched episodes in stremio-core's bitfield, removing it if [seen] is empty. */
    fun setWatched(item: JSONObject, videos: List<Video>, seen: Set<String>) {
        val ids = videos.sortedWith(compareBy({ it.season }, { it.episode })).map { it.id }
        val active = seen.filter { it in ids }.toSet()
        if (active.isEmpty()) {
            item.state.remove("watched")
            item.put("_mtime", now())
            return
        }
        val bits = ByteArray((ids.size + 7) / 8)
        ids.forEachIndexed { i, id -> if (id in active) setBit(bits, i) }
        val last = ids.indexOfLast { it in active }.takeIf { it >= 0 } ?: run {
            item.state.remove("watched")
            item.put("_mtime", now())
            return
        }
        item.state.put("watched", "${ids[last]}:${last + 1}:${encodeBits(bits)}")
        item.put("_mtime", now())
    }

    /** Ticks [videoId] as watched in that bitfield, as stremio-core's `set_video` does, so other devices show the ✓. */
    fun markWatched(item: JSONObject, videos: List<Video>, videoId: String) =
        setWatched(item, videos, watched(item, videos) + videoId)

    /** Removes [videoId] from that bitfield. */
    fun markUnwatched(item: JSONObject, videos: List<Video>, videoId: String) =
        setWatched(item, videos, watched(item, videos) - videoId)

    /** Ticks multiple episodes as watched in that bitfield. */
    fun markEpisodesWatched(item: JSONObject, videos: List<Video>, videoIds: Collection<String>) =
        setWatched(item, videos, watched(item, videos) + videoIds)

    /** Removes multiple episodes from that bitfield. */
    fun unmarkEpisodesWatched(item: JSONObject, videos: List<Video>, videoIds: Collection<String>) =
        setWatched(item, videos, watched(item, videos) - videoIds.toSet())

    /** Episodes strictly prior to [target] in series order (specials excluded unless target is a special). */
    fun priorEpisodes(videos: List<Video>, target: Video): List<Video> {
        val sorted = videos.sortedWith(compareBy({ it.season }, { it.episode }))
        return sorted.filter {
            if (target.season > 0) it.season > 0 && (it.season < target.season || (it.season == target.season && it.episode < target.episode))
            else it.season == 0 && it.episode < target.episode
        }
    }

    /** The episode after [target] in series order, or null at the end of the series (not advancing into specials). */
    fun nextEpisode(videos: List<Video>, target: Video): Video? {
        val sorted = videos.sortedWith(compareBy({ it.season }, { it.episode }))
        val at = sorted.indexOfFirst { it.id == target.id }.takeIf { it >= 0 } ?: return null
        return sorted.getOrNull(at + 1)?.takeIf { it.season != 0 || sorted[at].season == 0 }
    }

    /** The first episode of the next season after [season], or null if this was the last season. */
    fun nextEpisodeAfterSeason(videos: List<Video>, season: Int): Video? {
        val sorted = videos.sortedWith(compareBy({ it.season }, { it.episode }))
        return sorted.firstOrNull { it.season > season }
    }

    /**
     * The count on a series poster's badge, or 0 when the title is a movie, has notifications muted (`noNotif`),
     * or has no watched episodes yet. [mode] is [Settings.episodeBadges]:
     * - `aired`: unwatched episodes released after the title was last watched, as Stremio's notifications count.
     * - `season`: aired episodes left in the season of the next unwatched episode, so a long show never shows +200.
     * - `off`: always 0.
     */
    fun newEpisodeCount(item: JSONObject, videos: List<Video>, mode: String, isAired: (String?) -> Boolean): Int =
        newEpisodeCount(item.optString("type"), item.state.optBoolean("noNotif"), watched(item, videos), videos, mode,
            item.state.text("lastWatched").orEmpty(), isAired)

    internal fun newEpisodeCount(
        type: String,
        noNotif: Boolean,
        seen: Set<String>,
        videos: List<Video>,
        mode: String,
        lastWatched: String,
        isAired: (String?) -> Boolean,
    ): Int {
        if (mode == "off" || type == "movie" || noNotif || seen.isEmpty()) return 0
        val aired = videos.filter { it.season > 0 && isAired(it.released) }
            .sortedWith(compareBy({ it.season }, { it.episode }))
        val lastSeen = aired.indexOfLast { it.id in seen }
        if (lastSeen < 0) return 0
        val after = aired.drop(lastSeen + 1)
        if (mode == "season") return after.count { it.season == after.first().season }.takeIf { after.isNotEmpty() } ?: 0
        // Both are ISO 8601 in UTC, so text order is time order.
        if (lastWatched.isEmpty()) return 0
        return after.count { !it.released.isNullOrBlank() && it.released > lastWatched }
    }

    /** Playback progress, called every few seconds. [playedMs] is time actually watched since the last call. */
    fun record(item: JSONObject, videoId: String, positionMs: Long, durationMs: Long, playedMs: Long) {
        val s = item.state
        val now = now()
        s.put("lastWatched", now)
        if (s.text("video_id") != videoId) {
            s.put("video_id", videoId)
            s.put("overallTimeWatched", s.optLong("overallTimeWatched") + s.optLong("timeWatched"))
            s.put("timeWatched", 0).put("flaggedWatched", 0)
        }
        s.put("timeWatched", s.optLong("timeWatched") + playedMs)
        s.put("overallTimeWatched", s.optLong("overallTimeWatched") + playedMs)
        s.put("timeOffset", positionMs)
        if (durationMs > 0) s.put("duration", durationMs)
        val duration = s.optLong("duration")
        if (s.optInt("flaggedWatched") == 0 && duration > 0 && s.optLong("timeWatched") > duration * WATCHED) {
            s.put("flaggedWatched", 1)
            s.put("timesWatched", s.optInt("timesWatched") + 1)
            // ponytail: the per-episode "watched" ticks (stremio-core's watched bitfield) aren't written yet,
            // so episodes finished here don't get Stremio's checkmark. Port WatchedBitField when that matters.
        }
        if (item.optBoolean("temp") && s.optInt("timesWatched") == 0) item.put("removed", true)
        if (item.optBoolean("removed")) item.put("temp", true)
        item.put("_mtime", now)
    }

    /** When playback stops: past the credits it's finished, and a series moves on to [nextVideoId]. */
    fun stopped(item: JSONObject, nextVideoId: String?) {
        val s = item.state
        if (!pastCredits(s.optLong("timeOffset"), s.optLong("duration"))) return
        s.put("timeOffset", 0)
        if (nextVideoId != null) {
            s.put("video_id", nextVideoId)
            s.put("overallTimeWatched", s.optLong("overallTimeWatched") + s.optLong("timeWatched"))
            s.put("timeWatched", 0).put("flaggedWatched", 0)
            s.put("timeOffset", 1) // stremio-core's marker for "start the next episode", which keeps it in Continue Watching
        }
        item.put("_mtime", now())
    }

    fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}

val JSONObject.state: JSONObject get() = optJSONObject("state") ?: JSONObject().also { put("state", it) }
