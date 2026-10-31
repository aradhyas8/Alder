package dev.alder

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Fields that the app needs from an account's My List and watch history. */
data class LibraryItem(
    val id: String,
    val name: String = "",
    val type: String = "",
    val poster: String? = null,
    val background: String? = null,
    val removed: Boolean = false,
    val temporary: Boolean = false,
    val createdAt: String? = null,
    val modifiedAt: String? = null,
    val progress: LibraryProgress = LibraryProgress(),
    internal val adapterData: Any? = null,
    internal val locallyCreated: Boolean = false
)

/** Playback state shared by account providers. A null watched set means the episode list was not available. */
data class LibraryProgress(
    val lastWatched: String? = null,
    val videoId: String? = null,
    val timeWatched: Long = 0,
    val timeOffset: Long = 0,
    val overallTimeWatched: Long = 0,
    val duration: Long = 0,
    val timesWatched: Int = 0,
    val flaggedWatched: Int = 0,
    val watchedEpisodes: Set<String>? = null
)

/**
 * Provider-neutral My List and playback rules. Account adapters own wire formats and episode watch encodings.
 */
object Library {
    private const val WATCHED = 0.7 // stremio-core WATCHED_THRESHOLD_COEF: watched this much, it counts as seen
    private const val CREDITS = 0.9 // CREDITS_THRESHOLD_COEF: stopped past this, it's finished

    fun continueWatchingItems(items: List<LibraryItem>): List<LibraryItem> = items
        .filter { it.type != "other" && (!it.removed || it.temporary) && it.progress.timeOffset > 0 }
        .sortedByDescending { it.progress.lastWatched }

    fun newLibraryItem(id: String, type: String, name: String, poster: String?): LibraryItem {
        val now = now()
        return LibraryItem(
            id = id,
            name = name,
            type = type,
            poster = poster ?: "",
            removed = true,
            temporary = true,
            createdAt = now,
            modifiedAt = now,
            progress = LibraryProgress(lastWatched = now),
            locallyCreated = true
        )
    }

    /** In the library ("My List") rather than only remembered for Continue Watching. */
    fun inList(item: LibraryItem?) = item != null && !item.removed

    /** Adds the title to the library or takes it out; its watch history stays either way. */
    fun setInList(item: LibraryItem, on: Boolean) = item.copy(removed = !on, temporary = false, modifiedAt = now())

    /** Marks the current title complete without discarding its watch history. */
    fun markTitleWatched(item: LibraryItem, videos: List<Video> = emptyList()): LibraryItem {
        var progress = item.progress
        progress.videoId?.let { id -> if (videos.any { it.id == id })
            progress = progress.copy(watchedEpisodes = watchedSet(item) + id)
        }
        return item.copy(
            modifiedAt = now(),
            progress = progress.copy(
                timesWatched = maxOf(1, progress.timesWatched),
                flaggedWatched = 1,
                timeOffset = 0
            )
        )
    }

    /** Remove the resume point while keeping watch history and My List; return the old offset for [unhide]. */
    fun hideFromContinueWatching(item: LibraryItem): Pair<LibraryItem, Long> {
        val offset = item.progress.timeOffset
        return item.copy(modifiedAt = now(), progress = item.progress.copy(timeOffset = 0)) to offset
    }

    /** Back on Continue Watching at [offset]; left alone when it was watched again since (it's back already). */
    fun unhide(item: LibraryItem, offset: Long): LibraryItem = if (item.progress.timeOffset > 0) item else
        item.copy(modifiedAt = now(), progress = item.progress.copy(timeOffset = offset))

    /** Where to resume [videoId] in milliseconds, 0 to start from the beginning. */
    fun resumeAt(item: LibraryItem, videoId: String): Long {
        val progress = item.progress
        val unfinished = progress.duration == 0L || progress.timeOffset < progress.duration * CREDITS
        return if (progress.videoId == videoId && progress.timeOffset > 0 && unfinished) progress.timeOffset else 0
    }

    fun setWatched(item: LibraryItem, videos: List<Video>, seen: Set<String>): LibraryItem = item.copy(
        modifiedAt = now(),
        progress = item.progress.copy(watchedEpisodes = seen.filter { id -> videos.any { it.id == id } }.toSet())
    )

    /** Adds a watched episode while preserving the other ticks already hydrated by the account backend. */
    fun markWatched(item: LibraryItem, videos: List<Video>, videoId: String) =
        setWatched(item, videos, watchedSet(item) + videoId)

    /** Removes [videoId] while preserving other watched episodes. */
    fun markUnwatched(item: LibraryItem, videos: List<Video>, videoId: String) =
        setWatched(item, videos, watchedSet(item) - videoId)

    fun markEpisodesWatched(item: LibraryItem, videos: List<Video>, videoIds: Collection<String>) =
        setWatched(item, videos, watchedSet(item) + videoIds)

    fun unmarkEpisodesWatched(item: LibraryItem, videos: List<Video>, videoIds: Collection<String>) =
        setWatched(item, videos, watchedSet(item) - videoIds.toSet())

    private fun watchedSet(item: LibraryItem): Set<String> = requireNotNull(item.progress.watchedEpisodes) {
        "Watched episodes must be loaded by the account backend before they can be changed"
    }

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

    /** Playback progress, called every few seconds. [playedMs] is time actually watched since the last call. */
    fun record(item: LibraryItem, videoId: String, positionMs: Long, durationMs: Long, playedMs: Long): LibraryItem {
        val now = now()
        var progress = item.progress.copy(lastWatched = now)
        if (progress.videoId != videoId) {
            progress = progress.copy(
                videoId = videoId,
                overallTimeWatched = progress.overallTimeWatched + progress.timeWatched,
                timeWatched = 0,
                flaggedWatched = 0
            )
        }
        progress = progress.copy(
            timeWatched = progress.timeWatched + playedMs,
            overallTimeWatched = progress.overallTimeWatched + playedMs,
            timeOffset = positionMs,
            duration = if (durationMs > 0) durationMs else progress.duration
        )
        if (progress.flaggedWatched == 0 && progress.duration > 0 && progress.timeWatched > progress.duration * WATCHED) {
            progress = progress.copy(flaggedWatched = 1, timesWatched = progress.timesWatched + 1)
        }
        val removed = if (item.temporary && progress.timesWatched == 0) true else item.removed
        return item.copy(
            removed = removed,
            temporary = if (removed) true else item.temporary,
            modifiedAt = now,
            progress = progress
        )
    }

    /** When playback stops: past the credits it's finished, and a series moves on to [nextVideoId]. */
    fun stopped(item: LibraryItem, nextVideoId: String?): LibraryItem {
        var progress = item.progress
        if (progress.duration == 0L || progress.timeOffset <= progress.duration * CREDITS) return item
        progress = progress.copy(timeOffset = 0)
        if (nextVideoId != null) {
            progress = progress.copy(
                videoId = nextVideoId,
                overallTimeWatched = progress.overallTimeWatched + progress.timeWatched,
                timeWatched = 0,
                flaggedWatched = 0,
                timeOffset = 1
            )
        }
        return item.copy(modifiedAt = now(), progress = progress)
    }

    fun now(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
}
