package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Test

class ContinueWatchingTest {
    @Test fun checksReleaseOnlyForAnEpisodeWithNoPlaybackProgress() {
        val unreleased = Library.newItem("tt1", "series", "Unaired", null)
        val started = Library.newItem("tt2", "series", "Started", null)
        val unknown = Library.newItem("tt3", "series", "Unknown", null)
        unreleased.getJSONObject("state").put("timeOffset", 1).put("video_id", "tt1:1:2")
        started.getJSONObject("state").put("timeOffset", 1000).put("video_id", "tt2:1:2")
        unknown.getJSONObject("state").put("timeOffset", 1)
        val checked = mutableListOf<String>()

        val visible = Library.continueWatching(listOf(unreleased, started, unknown)) { _, episode ->
            checked += episode
            true
        }

        assertEquals(listOf("tt1:1:2"), checked)
        assertEquals(setOf(started, unknown), visible.toSet())
    }
}
