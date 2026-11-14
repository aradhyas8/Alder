package dev.alder

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TorrentSourceTest {
    private val hash = "0123456789abcdef".repeat(2) + "01234567"

    @Test fun torrentNeedsManualSourceListAndSurvivesPlayerHandoff() {
        val tracker = "https://tracker.example/announce?key=a&b=c"
        val json = JSONObject().put("infoHash", hash).put("fileIdx", 0)
            .put("sources", JSONArray(listOf("tracker:$tracker", "tracker:file:///tmp/announce", "tracker:$tracker")))
        assertNull(Stream.from(json))

        val stream = requireNotNull(Stream.from(json, allowTorrents = true))
        val torrent = requireNotNull(Stream.read(stream.toJson()).torrent)
        assertEquals(0, torrent.fileIndex)
        assertTrue(torrent.magnet.startsWith("magnet:?xt=urn:btih:$hash&tr=https%3A%2F%2Ftracker.example%2Fannounce%3Fkey%3Da%26b%3Dc&tr="))
        assertEquals(1, torrent.magnet.split("tracker.example").size - 1)
        assertFalse(stream.opensAtOnce)
    }

    @Test fun directUrlTakesPriorityAndInvalidTorrentIsRejected() {
        val json = JSONObject().put("infoHash", hash).put("fileIdx", -1)
        assertNull(Stream.from(json, allowTorrents = true))
        assertNull(Stream.from(JSONObject().put("infoHash", "invalid"), allowTorrents = true))
        val stream = requireNotNull(Stream.from(json.put("url", "https://example.org/video"), allowTorrents = true))
        assertNull(stream.torrent)
        assertEquals("https://example.org/video", stream.url)
        assertTrue(stream.opensAtOnce)
    }

    @Test fun torrentUrlsUseTheP2pPlayerAndKeepTheFileIndex() {
        val urls = listOf("torrent://${hash.uppercase()}",
            "MAGNET:?xt=urn%3Abtih%3A$hash&tr=https%3A%2F%2Ftracker.example%2Fannounce%3Fa%3D1%26b%3D2")
        for (url in urls) {
            val json = JSONObject().put("url", url).put("fileIdx", 2)
            assertNull(Stream.from(json))

            val stream = requireNotNull(Stream.from(json, allowTorrents = true))
            assertFalse(stream.opensAtOnce)
            val torrent = requireNotNull(Stream.read(stream.toJson()).torrent)
            assertEquals(hash, torrent.infoHash)
            assertEquals(2, torrent.fileIndex)
            if (url.startsWith("MAGNET")) {
                assertTrue(torrent.magnet.contains("&tr=https%3A%2F%2Ftracker.example%2Fannounce%3Fa%3D1%26b%3D2&tr="))
            }
        }
    }

    @Test fun magnetSupportsBase32AndRejectsInvalidTorrentUrls() {
        val base32 = "AAAQEAYEAUDAOCAJBIFQYDIOB4IBCEQT"
        val stream = Stream.from(JSONObject().put("url", "magnet:?xt=urn:btih:$base32"), allowTorrents = true)
        assertEquals("000102030405060708090a0b0c0d0e0f10111213", stream?.torrent?.infoHash)
        for (url in listOf("torrent://invalid", "magnet:?xt=urn:btih:%zz",
            "magnet:?xt=urn:btih:extra:$hash", "magnet:?tr=https://tracker.example")) {
            assertNull(Stream.from(JSONObject().put("url", url), allowTorrents = true))
        }
    }
}
