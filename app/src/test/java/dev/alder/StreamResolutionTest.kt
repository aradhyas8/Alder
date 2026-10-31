package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class StreamResolutionTest {
    @Test
    fun directUrlsPassThroughAndHashNeedsAResolver() {
        val headers = mapOf("Referer" to "https://example.com")
        val direct = Stream("Direct", "", "https://example.com/video.mkv", headers, 0L, null, null)
        val hash = StreamCandidate.InfoHash("stremio", "addon", "abc123", 2, "Torrent", "1080p", 0L, null, null)
        val candidates = listOf(StreamCandidate.Direct("stremio", "addon", direct), hash)

        assertEquals(listOf(direct), StreamResolution.playable(candidates))

        val resolved = Stream("Torrent", "1080p", "https://example.com/resolved.mkv", emptyMap(), 0L, null, null)
        val removeResolver = StreamResolution.register(object : StreamCandidateResolver {
            override fun resolve(candidate: StreamCandidate): Stream? =
                if (candidate is StreamCandidate.InfoHash && candidate.infoHash == "abc123") resolved else null
        })
        try {
            val playable = StreamResolution.playable(candidates)
            assertEquals(2, playable.size)
            assertSame(direct, playable[0])
            assertEquals(headers, playable[0].headers)
            assertSame(resolved, playable[1])
        } finally {
            removeResolver()
        }

        assertEquals(listOf(direct), StreamResolution.playable(candidates))
    }

    @Test
    fun failedOrInvalidResolversDoNotProducePlayableStreams() {
        val hash = StreamCandidate.InfoHash("stremio", "addon", "abc123", null, "Torrent", "", 0L, null, null)
        val failed = StreamResolution.register(object : StreamCandidateResolver {
            override fun resolve(candidate: StreamCandidate): Stream? = error("resolver failed")
        })
        val invalid = StreamResolution.register(object : StreamCandidateResolver {
            override fun resolve(candidate: StreamCandidate) =
                Stream("Torrent", "", "not a URL", emptyMap(), 0L, null, null)
        })
        try {
            assertEquals(emptyList<Stream>(), StreamResolution.playable(listOf(hash)))
        } finally {
            invalid()
            failed()
        }
    }
}
