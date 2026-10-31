package dev.alder

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LibraryModelTest {
    private class FakeAccountBackend : AccountBackend {
        private val items = mutableMapOf<String, LibraryItem>()

        override fun libraryItems(credential: String, ids: List<String>, videosByItem: Map<String, List<Video>>) =
            (if (ids.isEmpty()) items.values else ids.mapNotNull(items::get)).toList()
        override fun savedLibraryItems(credential: String, videosByItem: Map<String, List<Video>>) = items.values.toList()
        override fun saveLibraryItems(credential: String, items: List<LibraryItem>, videosByItem: Map<String, List<Video>>) {
            items.forEach { this.items[it.id] = it }
        }
        override fun watchedEpisodes(credential: String, item: LibraryItem, videos: List<Video>) = setOf("fake-episode")
        override fun userId(credential: String) = "fake-user"
        override fun logout(credential: String) = Unit
        override fun isExpired(error: Throwable) = false
    }

    @Test
    fun stremioRoundTripKeepsUnknownFieldsAndProgress() {
        val raw = JSONObject()
            .put("_id", "series:one")
            .put("name", "Example")
            .put("type", "series")
            .put("poster", "https://example.com/poster.jpg")
            .put("removed", false)
            .put("temp", false)
            .put("_ctime", "2026-10-30T12:00:00.000Z")
            .put("_mtime", "2026-10-31T12:00:00.000Z")
            .put("futureField", JSONObject().put("labels", JSONArray().put("one").put("two")))
            .put("state", JSONObject()
                .put("lastWatched", "2026-10-31T12:00:00.000Z")
                .put("video_id", "episode:two")
                .put("timeWatched", 45_000)
                .put("timeOffset", 600_000)
                .put("overallTimeWatched", 90_000)
                .put("duration", 1_200_000)
                .put("timesWatched", 2)
                .put("flaggedWatched", 0)
                .put("watched", "episode:two:2:opaque-bitfield")
                .put("futureState", JSONObject().put("rating", 4)))

        val item = Stremio.libraryItemFromJson(raw)
        val restored = Stremio.libraryItemToJson(item)
        val restoredState = restored.getJSONObject("state")

        assertEquals("series:one", item.id)
        assertEquals("episode:two", item.progress.videoId)
        assertEquals(600_000L, item.progress.timeOffset)
        assertEquals(1_200_000L, item.progress.duration)
        assertEquals(600_000L, Library.resumeAt(item, "episode:two"))
        assertEquals(0L, Library.resumeAt(item, "episode:one"))
        assertNull(item.progress.watchedEpisodes)
        assertEquals("one", restored.getJSONObject("futureField").getJSONArray("labels").getString(0))
        assertEquals("two", restored.getJSONObject("futureField").getJSONArray("labels").getString(1))
        assertEquals(4, restoredState.getJSONObject("futureState").getInt("rating"))
        assertEquals("episode:two:2:opaque-bitfield", restoredState.getString("watched"))
        assertEquals(600_000L, restoredState.getLong("timeOffset"))
    }

    @Test
    fun newTypedItemGetsStremioDefaultsOnlyAtTheAdapterBoundary() {
        val json = Stremio.libraryItemToJson(Library.newLibraryItem("movie:one", "movie", "Example", null))
        val state = json.getJSONObject("state")

        assertEquals("", json.getString("poster"))
        assertEquals("poster", json.getString("posterShape"))
        assertTrue(json.getBoolean("removed"))
        assertTrue(json.getBoolean("temp"))
        assertEquals(0, json.getJSONObject("behaviorHints").length())
        assertEquals(0L, state.getLong("timeOffset"))
        assertTrue(state.isNull("video_id"))
        assertTrue(state.isNull("watched"))
        assertFalse(state.getBoolean("noNotif"))
    }

    @Test
    fun encodingWatchedEpisodesRequiresEpisodeOrder() {
        val item = LibraryItem("series:one", progress = LibraryProgress(watchedEpisodes = setOf("episode:one")))
        try {
            Stremio.libraryItemToJson(item)
            fail("Expected the converter to require episode order")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Episode videos are required to save watched episodes", expected.message)
        }
    }

    @Test
    fun typedWatchedEpisodeOperationsPreserveOtherTicks() {
        val videos = listOf(
            Video("episode:two", "Two", 1, 2, null, null, null),
            Video("episode:one", "One", 1, 1, null, null, null)
        )
        val item = LibraryItem("series:one", progress = LibraryProgress(watchedEpisodes = setOf("episode:one")))

        val both = Library.markWatched(item, videos, "episode:two")
        val onlyFirst = Library.markUnwatched(both, videos, "episode:two")
        val none = Library.markUnwatched(onlyFirst, videos, "episode:one")

        assertEquals(setOf("episode:one", "episode:two"), both.progress.watchedEpisodes)
        assertEquals(setOf("episode:one"), onlyFirst.progress.watchedEpisodes)
        assertEquals(emptySet<String>(), none.progress.watchedEpisodes)
    }

    @Test
    fun typedRecordAndStopKeepContinueWatchingSemantics() {
        val original = LibraryItem("series:one", removed = true, temporary = true, progress = LibraryProgress(
            videoId = "episode:one", timeWatched = 500, overallTimeWatched = 800, duration = 1_000
        ))

        val recorded = Library.record(original, "episode:one", positionMs = 950, durationMs = 1_000, playedMs = 250)
        val stopped = Library.stopped(recorded, "episode:two")
        val continueWatching = Library.continueWatchingItems(listOf(stopped, stopped.copy(temporary = false)))

        assertEquals(1, recorded.progress.timesWatched)
        assertEquals(1, recorded.progress.flaggedWatched)
        assertEquals(750L, recorded.progress.timeWatched)
        assertEquals(1_050L, recorded.progress.overallTimeWatched)
        assertEquals("episode:two", stopped.progress.videoId)
        assertEquals(1L, stopped.progress.timeOffset)
        assertEquals(1_800L, stopped.progress.overallTimeWatched)
        assertEquals(listOf(stopped), continueWatching)
    }

    @Test
    fun secondAccountBackendUsesTypedItemsWithoutStremioAdapterChanges() {
        val account = AccountRef("fake", "credential")
        val backend = Accounts.backendFor(account, mapOf("fake" to FakeAccountBackend()))
        val item = LibraryItem("title:one", name = "Example", type = "movie", progress = LibraryProgress(timeOffset = 42))

        backend.saveLibraryItems(account.credential, listOf(item))

        assertEquals(listOf(item), backend.libraryItems(account.credential))
        assertEquals("fake-user", backend.userId(account.credential))
        assertEquals(setOf("fake-episode"), backend.watchedEpisodes(account.credential, item, emptyList()))
    }
}
