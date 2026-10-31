package dev.alder

import java.util.concurrent.CopyOnWriteArrayList

/** A source answer that may need work before the player can use it. */
sealed class StreamCandidate {
    abstract val provider: String
    abstract val sourceId: String

    data class Direct(override val provider: String, override val sourceId: String, val stream: Stream) : StreamCandidate()

    /** Stremio's infoHash answer stays unresolved until a stream resolver is registered. */
    data class InfoHash(
        override val provider: String,
        override val sourceId: String,
        val infoHash: String,
        val fileIndex: Int?,
        val title: String,
        val description: String,
        val size: Long,
        val filename: String?,
        val bingeGroup: String?,
    ) : StreamCandidate()
}

/** Converts a supported candidate into a URL that Media3 can open. Return null when the resolver cannot handle it. */
interface StreamCandidateResolver {
    fun resolve(candidate: StreamCandidate): Stream?
}

/** Direct URLs pass through. Other candidates need a registered resolver. */
object StreamResolution {
    private val resolvers = CopyOnWriteArrayList<StreamCandidateResolver>()

    /** Registers a resolver and returns a function that removes it. */
    fun register(resolver: StreamCandidateResolver): () -> Unit {
        resolvers.add(resolver)
        return { resolvers.remove(resolver) }
    }

    fun playable(candidates: List<StreamCandidate>): List<Stream> = candidates.mapNotNull { candidate ->
        when (candidate) {
            is StreamCandidate.Direct -> candidate.stream
            is StreamCandidate.InfoHash -> resolvers.firstNotNullOfOrNull { resolver ->
                runCatching { resolver.resolve(candidate) }.getOrNull()?.takeIf { it.url.isAbsoluteUrl() }
            }
        }
    }

    private fun String.isAbsoluteUrl() = runCatching { java.net.URI(this).isAbsolute }.getOrDefault(false)
}
