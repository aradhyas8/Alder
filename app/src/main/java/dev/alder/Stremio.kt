package dev.alder

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.zip.InflaterInputStream

/**
 * Thin client for Stremio's own services (request shapes from stremio-core). Every call blocks,
 * so run them off the main thread.
 */
object Stremio {
    private const val API = "https://api.strem.io/api/"
    private const val LINK = "https://link.stremio.com/api/v2/"
    private val LIBRARY_ITEM_FIELDS = setOf("_id", "name", "type", "poster", "background", "removed", "temp", "_ctime", "_mtime", "state")
    private val LIBRARY_STATE_FIELDS = setOf(
        "lastWatched", "video_id", "timeWatched", "timeOffset", "overallTimeWatched", "duration", "timesWatched", "flaggedWatched"
    )
    private data class StremioLibraryData(val itemFields: Map<String, Any?>, val stateFields: Map<String, Any?>)

    class Link(val code: String, val url: String)
    class ApiException(val code: Int, message: String) : IOException(message)

    /** Error code for a session that no longer exists (signed out elsewhere). */
    const val NO_SESSION = 1

    private val addonsCache = java.util.concurrent.ConcurrentHashMap<String, List<Addon>>() // per profile's auth key

    /**
     * TV sign-in, the same flow as Stremio's own TV app: show [Link.url] or the code, the person approves
     * it on their phone, and [readLink] then returns the account's auth key. The app never sees a password.
     */
    fun createLink(): Link = result(Http.get(LINK + "create?type=Create"))
        .let { Link(it.getString("code"), it.getString("link")) }

    /** Auth key once the code is approved, null until then. Stremio answers 101 for both "waiting" and "expired". */
    fun readLink(code: String): String? = try {
        result(Http.get(LINK + "read?type=Read&code=" + URLEncoder.encode(code, "UTF-8"))).getString("authKey")
    } catch (e: ApiException) {
        if (e.code == 101) null else throw e
    }

    /** The addons installed on the account, in the account's order. Kept in memory until [refresh]. */
    fun addons(authKey: String, refresh: Boolean = false, owner: Http.CacheOwner? = null): List<Addon> {
        if (!refresh) addonsCache[authKey]?.let { return it }
        val body = addonsBody(authKey)
        val answer = Http.post(API + "addonCollectionGet", body, owner = owner)
        return result(answer).getJSONArray("addons").objects().map(::Addon)
            .also { addonsCache[authKey] = it; Http.keep(API + "addonCollectionGet", body, answer, owner) }
    }

    /** The addon list as it was last time, from disk (null if never fetched), so Home can start before the network answers. */
    fun savedAddons(authKey: String, owner: Http.CacheOwner? = null): List<Addon>? = Http.savedAnswer(API + "addonCollectionGet", addonsBody(authKey), owner)
        ?.let { runCatching { result(it).getJSONArray("addons").objects().map(::Addon) }.getOrNull() }
        ?.also { addonsCache.putIfAbsent(authKey, it) } // until the fresh list arrives, details are asked of these

    private fun addonsBody(authKey: String) =
        JSONObject().put("type", "AddonCollectionGet").put("authKey", authKey).put("update", true).toString()

    /** Replaces the account's addon list (and order) with [addons]. */
    fun setAddons(authKey: String, addons: List<Addon>, owner: Http.CacheOwner? = null) {
        val body = JSONObject().put("type", "AddonCollectionSet").put("authKey", authKey)
            .put("addons", JSONArray(addons.map { it.descriptor }))
        result(post("addonCollectionSet", body, owner))
        addonsCache[authKey] = addons
    }

    /** Library items (watch history included): the given ids, or every item when [ids] is empty. */
    private fun libraryJson(authKey: String, ids: List<String> = emptyList()): List<JSONObject> {
        val body = libraryBody(authKey, ids)
        val owner = libraryOwner(authKey)
        val answer = Http.post(API + "datastoreGet", body, owner = owner)
        if (ids.isEmpty()) Http.keep(API + "datastoreGet", body, answer, owner)
        return newest(authKey, libraryItems(answer))
    }

    /** Typed library data for app code. Supply episode lists to read or change Stremio's watched bitfield. */
    fun libraryItems(
        authKey: String,
        ids: List<String> = emptyList(),
        videosByItem: Map<String, List<Video>> = emptyMap()
    ): List<LibraryItem> = libraryJson(authKey, ids).map { item ->
        libraryItemFromJson(item, videosByItem[item.optString("_id")])
    }

    // Items saved here whose save may not have reached Stremio yet: the player saves as it closes, and the page behind
    // it asks Stremio at the same moment, so without this it showed the progress from before.
    private val saving = java.util.concurrent.ConcurrentHashMap<String, JSONObject>()

    private fun newest(authKey: String, items: List<JSONObject>) = items.map { item ->
        saving["$authKey/${item.optString("_id")}"]?.takeIf { it.optString("_mtime") > item.optString("_mtime") } ?: item
    }

    /** The whole library as it was last time, from disk, or null. */
    private fun savedLibraryJson(authKey: String): List<JSONObject>? = Http.savedAnswer(API + "datastoreGet", libraryBody(authKey, emptyList()), libraryOwner(authKey))
        ?.let { runCatching { newest(authKey, libraryItems(it)) }.getOrNull() }

    fun savedLibraryItems(authKey: String, videosByItem: Map<String, List<Video>> = emptyMap()): List<LibraryItem>? =
        savedLibraryJson(authKey)?.map { item -> libraryItemFromJson(item, videosByItem[item.optString("_id")]) }

    private fun libraryBody(authKey: String, ids: List<String>) = JSONObject().put("authKey", authKey)
        .put("collection", "libraryItem").put("ids", JSONArray(ids)).put("all", ids.isEmpty()).toString()

    private fun libraryOwner(authKey: String) = Http.owner("account", Accounts.STREMIO, authKey)

    private fun libraryItems(answer: String): List<JSONObject> {
        val json = JSONObject(answer)
        json.optJSONObject("error")?.let { throw ApiException(it.optInt("code"), it.optString("message")) }
        val list = json.getJSONArray("result")
        return (0 until list.length()).map { list.getJSONObject(it) }
    }

    private fun saveLibraryJson(authKey: String, items: List<JSONObject>) {
        items.forEach { saving["$authKey/${it.optString("_id")}"] = it }
        val body = JSONObject().put("authKey", authKey).put("collection", "libraryItem").put("changes", JSONArray(items))
        try { result(post("datastorePut", body, libraryOwner(authKey))) }
        catch (error: Throwable) {
            items.forEach { saving.remove("$authKey/${it.optString("_id")}", it) }
            throw error
        }
    }

    fun saveLibraryItems(
        authKey: String,
        items: List<LibraryItem>,
        videosByItem: Map<String, List<Video>> = emptyMap()
    ) = saveLibraryJson(authKey, items.map { item ->
        libraryItemToJson(item, videosByItem[item.id])
    })

    /** Convert Stremio's wire item at the account boundary, keeping fields the app does not model. */
    fun libraryItemFromJson(item: JSONObject, videos: List<Video>? = null): LibraryItem {
        val state = item.optJSONObject("state") ?: JSONObject()
        return LibraryItem(
            id = item.optString("_id"),
            name = item.optString("name"),
            type = item.optString("type"),
            poster = item.text("poster"),
            background = item.text("background"),
            removed = item.optBoolean("removed"),
            temporary = item.optBoolean("temp"),
            createdAt = item.text("_ctime"),
            modifiedAt = item.text("_mtime"),
            progress = LibraryProgress(
                lastWatched = state.text("lastWatched"),
                videoId = state.text("video_id"),
                timeWatched = state.optLong("timeWatched"),
                timeOffset = state.optLong("timeOffset"),
                overallTimeWatched = state.optLong("overallTimeWatched"),
                duration = state.optLong("duration"),
                timesWatched = state.optInt("timesWatched"),
                flaggedWatched = state.optInt("flaggedWatched"),
                watchedEpisodes = videos?.let { watchedEpisodes(item, it) }
            ),
            adapterData = StremioLibraryData(item.extraFields(LIBRARY_ITEM_FIELDS), state.extraFields(LIBRARY_STATE_FIELDS))
        )
    }

    /** Convert app library data back to Stremio without dropping provider or future app fields. */
    fun libraryItemToJson(item: LibraryItem, videos: List<Video>? = null): JSONObject {
        val data = item.adapterData as? StremioLibraryData
        val json = JSONObject().apply {
            data?.itemFields?.forEach { (key, value) -> put(key, value.toJsonValue()) }
            put("_id", item.id)
            put("name", item.name)
            put("type", item.type)
            item.poster?.let { put("poster", it) }
            item.background?.let { put("background", it) }
            if (item.locallyCreated) put("posterShape", "poster")
            put("removed", item.removed)
            put("temp", item.temporary)
            item.createdAt?.let { put("_ctime", it) }
            item.modifiedAt?.let { put("_mtime", it) }
            if (item.locallyCreated) put("behaviorHints", JSONObject())
        }
        val state = JSONObject().apply {
            data?.stateFields?.forEach { (key, value) -> put(key, value.toJsonValue()) }
            item.progress.lastWatched?.let { put("lastWatched", it) }
            put("timeWatched", item.progress.timeWatched)
            put("timeOffset", item.progress.timeOffset)
            put("overallTimeWatched", item.progress.overallTimeWatched)
            put("timesWatched", item.progress.timesWatched)
            put("flaggedWatched", item.progress.flaggedWatched)
            put("duration", item.progress.duration)
            item.progress.videoId?.let { put("video_id", it) } ?: put("video_id", JSONObject.NULL)
            if (item.locallyCreated) put("watched", JSONObject.NULL).put("noNotif", false)
        }
        item.progress.watchedEpisodes?.let { seen ->
            val orderedVideos = requireNotNull(videos) { "Episode videos are required to save watched episodes" }
            watchedField(orderedVideos, seen)?.let { state.put("watched", it) } ?: state.remove("watched")
        }
        return json.put("state", state)
    }

    /** Decode Stremio's anchored watched bitfield using the current episode order. */
    internal fun watchedEpisodes(item: JSONObject?, videos: List<Video>): Set<String> {
        return watchedEpisodes(item?.optJSONObject("state")?.text("watched"), videos)
    }

    internal fun watchedEpisodes(item: LibraryItem, videos: List<Video>): Set<String> =
        item.progress.watchedEpisodes ?: watchedEpisodes(
            (item.adapterData as? StremioLibraryData)?.stateFields?.get("watched") as? String, videos
        )

    private fun watchedEpisodes(field: String?, videos: List<Video>): Set<String> {
        field ?: return emptySet()
        val parts = field.split(":")
        if (parts.size < 3) return emptySet()
        return runCatching {
            val bits = InflaterInputStream(Base64.decode(parts.last(), Base64.DEFAULT).inputStream()).readBytes()
            val anchorLength = parts[parts.size - 2].toInt()
            val anchor = parts.dropLast(2).joinToString(":") // ids may hold ':' themselves
            val ids = videos.sortedWith(compareBy({ it.season }, { it.episode })).map { it.id }
            val anchorAt = ids.indexOf(anchor)
            if (anchorAt < 0) return emptySet() // stremio-core: anchor gone, nothing counts as watched
            val offset = anchorLength - anchorAt - 1
            ids.filterIndexed { i, _ ->
                val bit = i + offset
                bit >= 0 && bit < ids.size && bit / 8 < bits.size && (bits[bit / 8].toInt() shr (bit % 8)) and 1 == 1
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /** Encode watched episodes in stremio-core's anchor-relative bitfield; null means no episodes are watched. */
    internal fun watchedField(videos: List<Video>, seen: Set<String>): String? {
        val ids = videos.sortedWith(compareBy({ it.season }, { it.episode })).map { it.id }
        val active = seen.filter { it in ids }.toSet()
        if (active.isEmpty()) return null
        val bits = ByteArray((ids.size + 7) / 8)
        ids.forEachIndexed { i, id -> if (id in active) bits[i / 8] = (bits[i / 8].toInt() or (1 shl (i % 8))).toByte() }
        val last = ids.indexOfLast { it in active }
        val zipped = java.io.ByteArrayOutputStream().also { out ->
            java.util.zip.DeflaterOutputStream(out, java.util.zip.Deflater(6)).use { it.write(bits) }
        }.toByteArray()
        return "${ids[last]}:${last + 1}:${Base64.encodeToString(zipped, Base64.NO_WRAP)}"
    }

    /** The account behind a sign-in: each sign-in has its own auth key, so two keys can be the same account. */
    fun userId(authKey: String): String =
        result(post("getUser", JSONObject().put("type", "GetUser").put("authKey", authKey))).getString("_id")

    fun logout(authKey: String) {
        addonsCache.remove(authKey)
        post("logout", JSONObject().put("type", "Logout").put("authKey", authKey))
    }

    // Stremio wraps every answer as {"result": …} or {"error": {"code", "message"}}.
    private fun result(body: String): JSONObject {
        val json = JSONObject(body)
        json.optJSONObject("error")?.let { throw ApiException(it.optInt("code"), it.optString("message")) }
        return json.optJSONObject("result") ?: JSONObject()
    }

    private fun JSONObject.extraFields(known: Set<String>): Map<String, Any?> = keys().asSequence()
        .filterNot { it in known }
        .associateWith { key -> get(key).fromJsonValue() }

    private fun Any?.fromJsonValue(): Any? = when (this) {
        JSONObject.NULL -> null
        is JSONObject -> extraFields(emptySet())
        is JSONArray -> (0 until length()).map { opt(it).fromJsonValue() }
        else -> this
    }

    private fun Any?.toJsonValue(): Any = when (this) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().apply {
            entries.forEach { (key, value) -> if (key is String) put(key, value.toJsonValue()) }
        }
        is Iterable<*> -> JSONArray().apply { forEach { put(it.toJsonValue()) } }
        is Array<*> -> JSONArray().apply { forEach { put(it.toJsonValue()) } }
        else -> this
    }

    private fun post(method: String, body: JSONObject, owner: Http.CacheOwner? = null) = Http.post(API + method, body.toString(), owner = owner)
}
