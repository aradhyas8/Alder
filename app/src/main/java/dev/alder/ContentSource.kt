package dev.alder

/**
 * Content that a source can give the app. Sources can use different network protocols. Returned Meta and Video IDs must
 * be verified shared IDs or scoped to [provider]; an adapter must map its wire IDs before screens receive them.
 */
interface ContentSource {
    val provider: String
    val id: String
    val identity: Pair<String, String> get() = provider to id
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

data class ContentIdentity(val provider: String, val sourceId: String, val id: String)

internal fun contentIdentity(provider: String, sourceId: String, id: String, verifiedImdbIds: Boolean = false): ContentIdentity =
    if (verifiedImdbIds && id.matches(imdbIdPattern)) ContentIdentity("imdb", "", id) else ContentIdentity(provider, sourceId, id)

private val imdbIdPattern = Regex("tt\\d+")
