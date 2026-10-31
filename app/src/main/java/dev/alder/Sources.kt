package dev.alder

import android.content.Context

/** A content connection is independent of the account that owns watch data. */
data class SourceRef(val provider: String, val credential: String) {
    override fun toString() = "SourceRef(provider=$provider)"
}

/** Each backend supplies ordered sources for a profile. */
interface SourceBackend {
    fun sources(credential: String, refresh: Boolean = false): List<ContentSource>
    fun savedSources(credential: String): List<ContentSource>?

    fun sources(credential: String, refresh: Boolean, owner: Http.CacheOwner?): List<ContentSource> =
        sources(credential, refresh).withContentCacheOwner(owner)
    fun savedSources(credential: String, owner: Http.CacheOwner?): List<ContentSource>? =
        savedSources(credential)?.withContentCacheOwner(owner)
}

/** The existing Stremio add-on editor. Other providers own their management UI and do not implement this API. */
interface StremioAddonManagement {
    fun addons(source: SourceRef, refresh: Boolean = false, owner: Http.CacheOwner? = null): List<Addon>
    fun savedAddons(source: SourceRef, owner: Http.CacheOwner? = null): List<Addon>?
    fun setAddons(source: SourceRef, addons: List<Addon>, owner: Http.CacheOwner? = null)
}

private object StremioSources : SourceBackend, StremioAddonManagement {
    override fun sources(credential: String, refresh: Boolean): List<ContentSource> = Stremio.addons(credential, refresh)
    override fun savedSources(credential: String): List<ContentSource>? = Stremio.savedAddons(credential)

    override fun sources(credential: String, refresh: Boolean, owner: Http.CacheOwner?): List<ContentSource> =
        Stremio.addons(credential, refresh, owner).withAddonCacheOwner(owner)
    override fun savedSources(credential: String, owner: Http.CacheOwner?): List<ContentSource>? =
        Stremio.savedAddons(credential, owner)?.withAddonCacheOwner(owner)

    override fun addons(source: SourceRef, refresh: Boolean, owner: Http.CacheOwner?): List<Addon> {
        require(source.provider == Accounts.STREMIO)
        return Stremio.addons(source.credential, refresh, owner).withAddonCacheOwner(owner)
    }

    override fun savedAddons(source: SourceRef, owner: Http.CacheOwner?): List<Addon>? {
        require(source.provider == Accounts.STREMIO)
        return Stremio.savedAddons(source.credential, owner)?.withAddonCacheOwner(owner)
    }

    override fun setAddons(source: SourceRef, addons: List<Addon>, owner: Http.CacheOwner?) {
        require(source.provider == Accounts.STREMIO)
        Stremio.setAddons(source.credential, addons, owner)
    }
}

object Sources {
    private val backends: Map<String, SourceBackend> = mapOf(Accounts.STREMIO to StremioSources)

    private fun backend(source: SourceRef): SourceBackend =
        backends[source.provider] ?: error("Unknown source provider: ${source.provider}")

    fun content(profile: Profile, refresh: Boolean = false): List<ContentSource> =
        loadSources(profile, backends, refresh)

    fun savedContent(profile: Profile): List<ContentSource>? {
        val saved = profile.sources.map { source -> runCatching {
            backend(source).savedSources(source.credential, cacheOwner(profile.id, source))
        }.getOrNull() }
        return saved.filterNotNull().takeIf { it.isNotEmpty() }?.flatten()
    }

    fun forVideo(context: Context, profile: Profile, type: String, videoId: String): AvailableSources {
        val own = content(profile).filter { it.providesStreams }
        val lent = Profiles.streamLenders(context).flatMap { lender ->
            runCatching { (savedContent(lender) ?: content(lender)).filter { it.shareable } }
                .getOrDefault(emptyList())
        }
        return AvailableSources(orderedForVideo(own, lent, type, videoId), own.any { !it.official } || lent.isNotEmpty())
    }

    fun stremioAddons(source: SourceRef): StremioAddonManagement? = backends[source.provider] as? StremioAddonManagement

    /** Compatibility calls for current Stremio screens; new callers should pass the profile's cache owner. */
    fun addons(source: SourceRef, refresh: Boolean = false, owner: Http.CacheOwner? = null): List<Addon> =
        (stremioAddons(source) ?: error("Source provider does not manage Stremio add-ons"))
            .addons(source, refresh, owner)

    fun savedAddons(source: SourceRef, owner: Http.CacheOwner? = null): List<Addon>? =
        (stremioAddons(source) ?: error("Source provider does not manage Stremio add-ons"))
            .savedAddons(source, owner)

    fun setAddons(source: SourceRef, addons: List<Addon>, owner: Http.CacheOwner? = null) {
        (stremioAddons(source) ?: error("Source provider does not manage Stremio add-ons"))
            .setAddons(source, addons, owner)
    }

    internal fun cacheOwner(profileId: String, source: SourceRef) =
        Http.owner("profile:$profileId", source.provider, source.credential)
}

/** A failed connection does not hide the answers from another connection. */
internal fun loadSources(profile: Profile, backends: Map<String, SourceBackend>, refresh: Boolean = false): List<ContentSource> {
    if (profile.sources.isEmpty()) return emptyList()
    val results = profile.sources.map { source -> runCatching {
        (backends[source.provider] ?: error("Unknown source provider: ${source.provider}"))
            .sources(source.credential, refresh, Sources.cacheOwner(profile.id, source))
    } }
    if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
    return results.flatMap { it.getOrDefault(emptyList()) }
}

private fun List<ContentSource>.withContentCacheOwner(owner: Http.CacheOwner?): List<ContentSource> =
    if (owner == null) this else map { if (it is Addon) it.withCacheOwner(owner) else it }

private fun List<Addon>.withAddonCacheOwner(owner: Http.CacheOwner?): List<Addon> =
    if (owner == null) this else map { it.withCacheOwner(owner) }

data class AvailableSources(val sources: List<ContentSource>, val hasSources: Boolean)

/** Shared sources answer first; the viewer's own duplicate answers only once. */
internal fun orderedForVideo(own: List<ContentSource>, lent: List<ContentSource>, type: String, videoId: String): List<ContentSource> {
    val household = lent.filter { it.serves("stream", type, videoId) }.distinctBy { it.cacheKey }
    return household + own.filter { it.serves("stream", type, videoId) && household.none { shared -> shared.cacheKey == it.cacheKey } }
}
