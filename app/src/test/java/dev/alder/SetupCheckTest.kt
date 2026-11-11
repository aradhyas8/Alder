package dev.alder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SetupCheckTest {
    @Test fun asksForAFilmThenAnEpisode() {
        assertEquals("movie" to "tt0111161", setupProbe { _, id -> id.startsWith("tt") })
        assertEquals("series" to "tt0903747:1:1", setupProbe { type, _ -> type == "series" })
        assertNull(setupProbe { _, id -> id.startsWith("kitsu") })
    }

    @Test fun firstFixableAddonIsTorrentsOnlyOrRefusedKey() {
        val failures = arrayOf<Throwable?>(java.net.SocketTimeoutException(), null, HttpError(401))
        assertEquals(1, fixableAddon(listOf(true, true, true), failures, intArrayOf(0, 5, 0)))
        assertEquals(2, fixableAddon(listOf(true, false, true), failures, intArrayOf(0, 5, 0)))
        assertNull(fixableAddon(listOf(true, true, false), failures, intArrayOf(9, 0, 0)))
    }

    @Test fun spotsAppleRelayAddresses() {
        assertTrue(appleRelay("x7f2k@privaterelay.appleid.com"))
        assertFalse(appleRelay("viewer@example.com"))
    }
}
