package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashesTest {
    @Test fun addressesKeepOnlyTheirHost() {
        val out = Crashes.redact("failed: https://aiostreams.example.com/eyJhbGciOi/stream/movie/tt1.json?key=abc")
        assertEquals("failed: https://aiostreams.example.com/…", out)
    }

    @Test fun keysGoAndClassNamesStay() {
        val out = Crashes.redact("token 9f8e7d6c5b4a39281706f5e4d3c2b1a0 at dev.alder.PlayerActivity.onCreate(PlayerActivity.kt:235)\n" +
            "java.lang.IllegalStateException: ExoPlaybackExceptionWrapper")
        assertFalse(out.contains("9f8e7d6c5b4a"))
        assertTrue(out.contains("dev.alder.PlayerActivity.onCreate(PlayerActivity.kt:235)"))
        assertTrue(out.contains("ExoPlaybackExceptionWrapper"))
    }

    @Test fun phonePageEscapesTheReport() {
        val page = phoneEntryPage("Report a problem", "Copy this", "Open a GitHub issue", null, "at <init> & more")
        assertTrue(page.contains("<textarea readonly"))
        assertTrue(page.contains("at &lt;init&gt; &amp; more"))
        assertTrue(page.contains("github.com/aradhyas8/Alder/issues/new"))
    }

    @Test fun traceHasTheCauseAndStaysShort() {
        val error = RuntimeException("outer", IllegalArgumentException("inner"))
        val out = Crashes.trace(error)
        assertTrue(out.contains("Caused by: java.lang.IllegalArgumentException: inner"))
        assertTrue(out.length <= 6_000)
    }
}
