package dev.alder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArchitectureTest {
    private class FakeSource(override val id: String, private val kind: String = "movie") : ContentSource {
        override val name = id
        override val providesStreams = true
        override fun serves(resource: String, type: String, id: String) = resource == "stream" && type == kind
    }

    @Test fun oldProfileKeepsItsAccountAndPin() {
        val old = JSONObject().put("id", "2").put("name", "Me")
            .put("color", Profiles.COLORS[0]).put("authKey", "old-key").put("pin", "1234")
        val profile = profileFromJson(old)
        assertEquals(AccountRef(Accounts.STREMIO, "old-key"), profile.account)
        assertEquals(listOf(profile.account), profile.sourceAccounts)
        assertFalse(profile.account.toString().contains("old-key"))
        assertEquals("1234", profile.pin)
        val saved = profileToJson(profile)
        assertFalse(saved.has("authKey"))
        assertEquals(profile.account, profileFromJson(saved).account)
    }

    @Test fun accountChangeKeepsIndependentContentSource() {
        val old = AccountRef(Accounts.STREMIO, "old-key")
        val linked = AccountRef("other-source", "source-key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], old, sourceAccounts = listOf(old, linked))
        val next = AccountRef(Accounts.STREMIO, "new-key")
        val saved = profileFromJson(profileToJson(profile.withAccount(next)))
        assertEquals(next, saved.account)
        assertEquals(listOf(next, linked), saved.sourceAccounts)
    }

    @Test fun newAccountCanKeepOldContentSource() {
        val stremio = AccountRef(Accounts.STREMIO, "old-key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], stremio)
        val own = AccountRef("alder", "own-key")
        val changed = profile.withAccount(own)
        assertEquals(own, changed.account)
        assertEquals(listOf(stremio), changed.sourceAccounts)
    }

    @Test fun sourceOrderKeepsSharedFirstWithoutDuplicate() {
        val shared = FakeSource("shared")
        val own = listOf(FakeSource("own"), FakeSource("shared"), FakeSource("series-only", "series"))
        assertEquals(listOf("shared", "own"), orderedForVideo(own, listOf(shared), "movie", "tt1").map { it.id })
    }
}
