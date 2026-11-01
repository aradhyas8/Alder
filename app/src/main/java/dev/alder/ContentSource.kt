package dev.alder

/**
 * Content that a source can give the app. Sources can use different network protocols. Returned Meta and Video IDs must
 * be verified shared IDs or scoped to [provider]; an adapter must map its wire IDs before screens receive them.
 */
interface ContentSource {
    val provider: String
    val id: String
    val name: String
    val cacheKey: String get() = "$provider:$id"
    val catalogs: List<Catalog> get() = emptyList()
    val providesStreams: Boolean get() = false
    val official: Boolean get() = false
    val shareable: Boolean get() = false
    val configureUrl: String? get() = null

    fun serves(resource: String, type: String, id: String): Boolean = false
    fun catalog(c: Catalog, genre: String? = null, timeoutMs: Int = 20_000): List<Meta> = emptyList()
    fun catalog(c: Catalog, skip: Int): List<Meta> = emptyList()
    fun savedCatalog(c: Catalog, genre: String? = null): List<Meta>? = null
    fun search(query: String): List<Meta> = emptyList()
    fun meta(type: String, id: String): Meta? = null
    fun savedMeta(type: String, id: String): Meta? = null
    fun similar(type: String, id: String): List<Meta>? = null
    fun streams(type: String, videoId: String): SourceStreams = SourceStreams(emptyList())
    fun subtitles(type: String, videoId: String): List<Subtitle> = emptyList()
}

class Catalog(val source: ContentSource, val type: String, val id: String, val name: String, val pages: Boolean,
              val genres: List<String>, val required: Map<String, String> = emptyMap())

class SourceStreams(val candidates: List<StreamCandidate>, val torrents: Int = 0, val links: Int = 0)

data class ContentIdentity(val provider: String, val sourceId: String, val id: String) {
    /**
     * True when both identities name the same title or episode. An empty [sourceId] means the account's wire format
     * only kept the raw ID, so the source is unknown and only the provider and ID take part.
     */
    fun sameContent(other: ContentIdentity): Boolean =
        id == other.id && provider == other.provider &&
            (sourceId == other.sourceId || sourceId.isEmpty() || other.sourceId.isEmpty())
}

/** True when this source is the one the title came from. */
internal fun ContentSource.fromSource(provider: String?, sourceId: String?): Boolean =
    provider == this.provider && sourceId == id

/** The source a screen opened from answers first; the rest keep the profile's order. */
internal fun List<ContentSource>.preferred(provider: String?, sourceId: String?): List<ContentSource> {
    if (provider == null || sourceId == null) return this
    val (first, rest) = partition { it.fromSource(provider, sourceId) }
    return first + rest
}

internal fun contentIdentity(provider: String, sourceId: String, id: String, verifiedImdbIds: Boolean = false): ContentIdentity =
    if (verifiedImdbIds && id.matches(imdbIdPattern)) ContentIdentity("imdb", "", id) else ContentIdentity(provider, sourceId, id)

private val imdbIdPattern = Regex("tt\\d+")
