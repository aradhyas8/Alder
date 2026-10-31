package dev.alder

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpCacheScopeTest {
    @Test
    fun oneProfileCanForgetItsAnswersWithoutRemovingAnotherOrLegacyAnswers() {
        val oldSaved = Http.saved
        val folder = File.createTempFile("alder-cache", "test")
        folder.delete()
        folder.mkdirs()
        Http.saved = folder
        val first = Http.owner("profile:1", "stremio", "shared-key")
        val second = Http.owner("profile:2", "stremio", "shared-key")
        val url = "https://example.invalid/catalog.json"
        try {
            Http.keep(url, null, "one", first)
            Http.keep(url, null, "two", second)
            Http.keep(url, null, "legacy")

            assertEquals("one", Http.savedAnswer(url, owner = first))
            assertEquals("two", Http.savedAnswer(url, owner = second))
            assertFalse(first.toString().contains("shared-key"))

            Http.forget(first)
            assertNull(Http.savedAnswer(url, owner = first))
            assertEquals("two", Http.savedAnswer(url, owner = second))

            Http.forgetProfile("2")
            assertNull(Http.savedAnswer(url, owner = second))
            assertEquals("legacy", Http.savedAnswer(url))
            assertTrue(folder.exists())
        } finally {
            Http.saved = oldSaved
            folder.deleteRecursively()
        }
    }
}
