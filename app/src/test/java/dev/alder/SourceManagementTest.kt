package dev.alder

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceManagementTest {
    @Test
    fun sourceLinksAppendOnceRemoveOnlyTheirConnectionAndPreserveOrder() {
        val first = SourceRef("one", "first-key")
        val second = SourceRef("two", "second-key")
        val linked = linkSource(listOf(first), second)

        assertEquals(listOf(first, second), linked)
        assertEquals(linked, linkSource(linked, second))
        assertEquals(listOf(second), removeSource(linked, first))
        assertEquals(listOf(second, first), orderSources(linked, listOf(second, first)))
        assertFalse(runCatching { orderSources(linked, listOf(first, first)) }.isSuccess)
    }

    @Test
    fun onlyStremioExposesTheStremioAddonEditor() {
        assertTrue(Sources.stremioAddons(SourceRef(Accounts.STREMIO, "secret")) != null)
        assertNull(Sources.stremioAddons(SourceRef("other", "secret")))
    }

    @Test
    fun contentIdentityUsesVerifiedImdbOnlyWhenTheAdapterOptsIn() {
        val id = "tt123"
        val unverified = Meta(JSONObject().put("id", id), "other", "catalog")
        val verified = Meta(JSONObject().put("id", id), Accounts.STREMIO, "cinemeta", verifiedImdbIds = true)
        val verifiedElsewhere = Meta(JSONObject().put("id", id), Accounts.STREMIO, "other-addon", verifiedImdbIds = true)
        val customOne = Meta(JSONObject().put("id", "custom"), Accounts.STREMIO, "addon-one")
        val customTwo = Meta(JSONObject().put("id", "custom"), Accounts.STREMIO, "addon-two")
        val episode = Meta(JSONObject().put("id", id).put("videos", org.json.JSONArray().put(
            JSONObject().put("id", "$id:1:2"))), Accounts.STREMIO, "cinemeta", verifiedImdbIds = true).videos.single()

        assertEquals(ContentIdentity("other", "catalog", id), unverified.identity)
        assertEquals(ContentIdentity("imdb", "", id), verified.identity)
        assertEquals(verified.identity, verifiedElsewhere.identity)
        assertNotEquals(customOne.identity, customTwo.identity)
        assertEquals(ContentIdentity(Accounts.STREMIO, "cinemeta", "$id:1:2"), episode.identity)
        assertEquals("cinemeta", verified.sourceId)
    }
}
