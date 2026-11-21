package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueWatchingTest {
    @Test
    fun shelfAndPlaybackAgreeOnSavedProgress() {
        val movie = entry("movie", "movie", 29_000, 7_560_000)
        val credits = entry("credits", "movie", 90_000, 100_000)
        val shortEpisode = entry("short", "series", 75_000, 100_000)
        val pending = entry("pending", "series", 1, 100_000)
        val unknownDuration = entry("unknown", "series", 31_000, 0)

        assertEquals(listOf(movie, shortEpisode, pending, unknownDuration),
            Library.continueWatching(listOf(movie, credits, shortEpisode, pending, unknownDuration)))
        assertEquals(29_000L, Library.resumeAt(movie, "movie"))
        assertEquals(0L, Library.resumeAt(credits, "credits"))
        assertEquals(75_000L, Library.resumeAt(shortEpisode, "short:1:2"))
        assertEquals(0L, Library.resumeAt(shortEpisode, "short:1:1"))
        assertEquals(0L, Library.resumeAt(pending, "pending:1:2"))
        assertEquals(31_000L, Library.resumeAt(unknownDuration, "unknown:1:2"))

        Library.stopped(credits, null)
        assertEquals(0L, credits.state.optLong("timeOffset"))
    }

    @Test
    fun releaseCheckOnlyAppliesToPendingEpisodes() {
        val pending = entry("pending", "series", 1, 100_000)
        val playing = entry("playing", "series", 12_000, 100_000)
        val checked = mutableListOf<String>()

        val visible = Library.continueWatching(listOf(pending, playing)) { _, videoId ->
            checked += videoId
            true
        }

        assertEquals(listOf("pending:1:2"), checked)
        assertEquals(listOf(playing), visible)
    }

    @Test
    fun episodeLabelsUseNumbersFromStructuredIds() {
        assertEquals(1 to 3, episodeNumbers("tt123:1:3"))
        assertEquals(0 to 2, episodeNumbers("tt123:0:2"))
        assertNull(episodeNumbers("addon:e:eyJzZXJpZXMi"))
        assertNull(episodeNumbers("tt123:1:0"))
        assertNull(episodeNumber(-1, 4))
    }

    @Test
    fun newEpisodeCountCountsAiredEpisodesAfterLastWatched() {
        val videos = listOf(
            Video("s:0:1", "Special", 0, 1, null, null, "2026-01-01"),
            Video("s:1:1", "Pilot", 1, 1, null, null, "2026-01-01"),
            Video("s:1:2", "Second", 1, 2, null, null, "2026-01-08"),
            Video("s:2:1", "Return", 2, 1, null, null, "2026-10-01"),
            Video("s:2:2", "Next", 2, 2, null, null, "2026-10-08"),
            Video("s:2:3", "Future", 2, 3, null, null, "2099-01-01"),
        )
        val isAired: (String?) -> Boolean = { it == null || it < "2026-11-01" }

        val seen = setOf("s:1:1", "s:1:2")
        fun count(mode: String, seen: Set<String>, last: String = "2026-09-01T00:00:00.000Z", type: String = "series", noNotif: Boolean = false) =
            Library.newEpisodeCount(type, noNotif, seen, videos, mode, last, isAired)

        assertEquals(0, count("aired", emptySet()))
        assertEquals(0, count("aired", seen, noNotif = true))
        assertEquals(0, count("aired", setOf("s:1:1"), type = "movie"))
        assertEquals(0, count("off", seen))
        // Newly aired: only episodes out after the last watch.
        assertEquals(2, count("aired", seen))
        assertEquals(1, count("aired", seen, last = "2026-10-05T00:00:00.000Z"))
        assertEquals(0, count("aired", setOf("s:1:1"), last = "2026-10-09T00:00:00.000Z"))
        // Current season: the rest of the season of the next episode, never later seasons.
        assertEquals(1, count("season", setOf("s:1:1")))
        assertEquals(2, count("season", seen))
        assertEquals(0, count("season", setOf("s:2:2")))

        val long = (1..12).flatMap { s -> (1..24).map { e -> Video("s:$s:$e", "E", s, e, null, null, "2010-01-01") } }
        assertEquals(23, Library.newEpisodeCount("series", false, setOf("s:1:1"), long, "season", "2026-01-01T00:00:00Z", isAired))
        assertEquals(0, Library.newEpisodeCount("series", false, setOf("s:1:1"), long, "aired", "2026-01-01T00:00:00Z", isAired))
    }

    private fun entry(id: String, type: String, offset: Long, duration: Long) =
        Library.newItem(id, type, id, null).apply {
            state.put("lastWatched", "2026-11-12T12:00:00.000Z")
                .put("timeOffset", offset).put("duration", duration)
            if (type == "series") state.put("video_id", "$id:1:2")
        }
}
