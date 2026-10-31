package dev.alder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ArchitectureTest {
    private class FakeSource(override val id: String, override val provider: String = "one", private val kind: String = "movie",
                             override val cacheKey: String = "$provider:$id") : ContentSource {
        override val name = id
        override val providesStreams = true
        override fun serves(resource: String, type: String, id: String) = resource == "stream" && type == kind
    }

    @Test fun oldProfileKeepsItsAccountAndPin() {
        val old = JSONObject().put("id", "2").put("name", "Me")
            .put("color", Profiles.COLORS[0]).put("authKey", "old-key").put("pin", "1234")
        val profile = profileFromJson(old)
        assertEquals(AccountRef(Accounts.STREMIO, "old-key"), profile.account)
        assertEquals(listOf(SourceRef(Accounts.STREMIO, "old-key")), profile.sources)
        assertFalse(profile.account.toString().contains("old-key"))
        assertEquals("1234", profile.pin)
        val saved = profileToJson(profile)
        assertFalse(saved.has("authKey"))
        assertEquals(profile.account, profileFromJson(saved).account)
    }

    @Test fun accountChangeKeepsIndependentContentSource() {
        val old = AccountRef(Accounts.STREMIO, "old-key")
        val linked = SourceRef("other-source", "source-key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], old, sources = listOf(SourceRef(Accounts.STREMIO, "old-key"), linked))
        val next = AccountRef(Accounts.STREMIO, "new-key")
        val saved = profileFromJson(profileToJson(profile.withAccount(next)))
        assertEquals(next, saved.account)
        assertEquals(listOf(SourceRef(Accounts.STREMIO, "new-key"), linked), saved.sources)
    }

    @Test fun newAccountCanKeepOldContentSource() {
        val stremio = AccountRef(Accounts.STREMIO, "old-key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], stremio)
        val own = AccountRef("alder", "own-key")
        val changed = profile.withAccount(own)
        assertEquals(own, changed.account)
        assertEquals(listOf(SourceRef(Accounts.STREMIO, "old-key")), changed.sources)
    }

    @Test fun sourceConnectionsKeepOrderAndHideCredentials() {
        val account = AccountRef(Accounts.STREMIO, "account-key")
        val first = SourceRef("other", "source-secret")
        val second = SourceRef(Accounts.STREMIO, "account-key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], account, sources = listOf(first, second))
        assertEquals(listOf(first, second), profileFromJson(profileToJson(profile)).sources)
        assertFalse(first.toString().contains("source-secret"))
    }

    @Test fun sourceOrderKeepsSharedFirstWithoutDuplicate() {
        val shared = FakeSource("shared")
        val own = listOf(FakeSource("own"), FakeSource("shared"), FakeSource("series-only", kind = "series"))
        assertEquals(listOf("shared", "own"), orderedForVideo(own, listOf(shared), "movie", "tt1").map { it.id })
    }

    @Test fun sameSourceIdFromTwoProtocolsStaysDistinct() {
        val first = FakeSource("same", "one")
        val second = FakeSource("same", "two")
        assertEquals(listOf(first, second), orderedForVideo(listOf(second), listOf(first), "movie", "tt1"))
    }

    @Test fun sameAddonIdWithDifferentConfigurationStaysDistinct() {
        val first = FakeSource("same", cacheKey = "https://first/manifest.json")
        val second = FakeSource("same", cacheKey = "https://second/manifest.json")
        assertEquals(listOf(first, second), orderedForVideo(listOf(second), listOf(first), "movie", "tt1"))
    }

    @Test fun customTitleIdsStayWithinTheirSource() {
        assertEquals(ContentIdentity("stremio", "addon-one", "series:1"),
            contentIdentity("stremio", "addon-one", "series:1"))
        assertFalse(contentIdentity("stremio", "addon-one", "series:1") ==
            contentIdentity("stremio", "addon-two", "series:1"))
        assertEquals(contentIdentity("stremio", "addon-one", "tt123", verifiedImdbIds = true),
            contentIdentity("stremio", "addon-two", "tt123", verifiedImdbIds = true))
        assertFalse(contentIdentity("stremio", "addon-one", "tt123", verifiedImdbIds = true) ==
            contentIdentity("other", "source", "tt123"))
    }

    @Test fun savedTitleChoicesUseSourceIdentity() {
        val first = Settings.titleChoiceKey("series:1", "other", "source-a")
        val second = Settings.titleChoiceKey("series:1", "other", "source-b")
        assertFalse(first == second)
        assertEquals("title:series:1", Settings.titleChoiceKey("series:1", null, null))
        assertEquals(Settings.titleChoiceKey("tt123", Accounts.STREMIO, "addon-a"),
            Settings.titleChoiceKey("tt123", Accounts.STREMIO, "addon-b"))
    }

    @Test fun aFailedSourceDoesNotHideAnotherSource() {
        val account = AccountRef(Accounts.STREMIO, "key")
        val profile = Profile("1", "Me", Profiles.COLORS[0], account,
            sources = listOf(SourceRef("failed", "bad"), SourceRef("ready", "good")))
        val ready = FakeSource("ready", "ready")
        val backends = mapOf<String, SourceBackend>(
            "failed" to object : SourceBackend {
                override fun sources(credential: String, refresh: Boolean): List<ContentSource> = error("offline")
                override fun savedSources(credential: String): List<ContentSource>? = null
            },
            "ready" to object : SourceBackend {
                override fun sources(credential: String, refresh: Boolean): List<ContentSource> = listOf(ready)
                override fun savedSources(credential: String): List<ContentSource>? = null
            })
        assertEquals(listOf(ready), loadSources(profile, backends))
    }
}
