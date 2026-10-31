package dev.alder

import android.content.Context

/** Each backend supplies ordered sources for a profile. */
interface SourceBackend {
    fun sources(credential: String, refresh: Boolean = false): List<ContentSource>
    fun savedSources(credential: String): List<ContentSource>?
}

private object StremioSources : SourceBackend {
    override fun sources(credential: String, refresh: Boolean): List<ContentSource> = Stremio.addons(credential, refresh)
    override fun savedSources(credential: String): List<ContentSource>? = Stremio.savedAddons(credential)
}

object Sources {
    private val backends: Map<String, SourceBackend> = mapOf(Accounts.STREMIO to StremioSources)

    private fun backend(account: AccountRef): SourceBackend =
        backends[account.provider] ?: error("Unknown source provider: ${account.provider}")

    fun content(profile: Profile, refresh: Boolean = false): List<ContentSource> {
        if (profile.sourceAccounts.isEmpty()) return emptyList()
        val results = profile.sourceAccounts.map { source -> runCatching { backend(source).sources(source.credential, refresh) } }
        if (results.all { it.isFailure }) throw results.first().exceptionOrNull()!!
        return results.flatMap { it.getOrDefault(emptyList()) }
    }

    fun savedContent(profile: Profile): List<ContentSource>? {
        val saved = profile.sourceAccounts.map { source -> runCatching { backend(source).savedSources(source.credential) }.getOrNull() }
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

    /** Stremio add-on management remains specific to its account setup screen. */
    fun addons(account: AccountRef, refresh: Boolean = false): List<Addon> {
        require(account.provider == Accounts.STREMIO)
        return Stremio.addons(account.credential, refresh)
    }

    fun savedAddons(account: AccountRef): List<Addon>? {
        require(account.provider == Accounts.STREMIO)
        return Stremio.savedAddons(account.credential)
    }

    fun setAddons(account: AccountRef, addons: List<Addon>) {
        require(account.provider == Accounts.STREMIO)
        Stremio.setAddons(account.credential, addons)
    }
}

data class AvailableSources(val sources: List<ContentSource>, val hasSources: Boolean)

/** Shared sources answer first; the viewer's own duplicate answers only once. */
internal fun orderedForVideo(own: List<ContentSource>, lent: List<ContentSource>, type: String, videoId: String): List<ContentSource> {
    val household = lent.filter { it.serves("stream", type, videoId) }.distinctBy { it.id }
    return household + own.filter { it.serves("stream", type, videoId) && household.none { shared -> shared.id == it.id } }
}
