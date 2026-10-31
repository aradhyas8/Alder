package dev.alder

/** A profile's account. Only its backend interprets the credential. */
data class AccountRef(val provider: String, val credential: String) {
    override fun toString() = "AccountRef(provider=$provider)"
}

/** Library items use Alder's current library schema; each backend converts its own wire format. */
interface AccountBackend {
    fun libraryItems(
        credential: String,
        ids: List<String> = emptyList(),
        videosByItem: Map<String, List<Video>> = emptyMap()
    ): List<LibraryItem>
    fun savedLibraryItems(credential: String, videosByItem: Map<String, List<Video>> = emptyMap()): List<LibraryItem>?
    fun saveLibraryItems(credential: String, items: List<LibraryItem>, videosByItem: Map<String, List<Video>> = emptyMap())
    fun watchedEpisodes(credential: String, item: LibraryItem, videos: List<Video>): Set<String>
    fun userId(credential: String): String
    fun logout(credential: String)
    fun isExpired(error: Throwable): Boolean
}

private object StremioAccount : AccountBackend {
    override fun libraryItems(credential: String, ids: List<String>, videosByItem: Map<String, List<Video>>) =
        Stremio.libraryItems(credential, ids, videosByItem)
    override fun savedLibraryItems(credential: String, videosByItem: Map<String, List<Video>>) =
        Stremio.savedLibraryItems(credential, videosByItem)
    override fun saveLibraryItems(credential: String, items: List<LibraryItem>, videosByItem: Map<String, List<Video>>) =
        Stremio.saveLibraryItems(credential, items, videosByItem)
    override fun watchedEpisodes(credential: String, item: LibraryItem, videos: List<Video>) =
        Stremio.watchedEpisodes(item, videos)
    override fun userId(credential: String) = Stremio.userId(credential)
    override fun logout(credential: String) = Stremio.logout(credential)
    override fun isExpired(error: Throwable) =
        (error as? Stremio.ApiException)?.code == Stremio.NO_SESSION
}

/** The app uses this boundary for account data; provider details stay in the backend. */
object Accounts {
    const val STREMIO = "stremio"
    private val backends: Map<String, AccountBackend> = mapOf(STREMIO to StremioAccount)

    private fun backend(account: AccountRef) = backendFor(account, backends)

    internal fun backendFor(account: AccountRef, registry: Map<String, AccountBackend>): AccountBackend =
        registry[account.provider] ?: error("Unknown account provider: ${account.provider}")

    fun libraryItems(
        account: AccountRef,
        ids: List<String> = emptyList(),
        videosByItem: Map<String, List<Video>> = emptyMap()
    ) = backend(account).libraryItems(account.credential, ids, videosByItem)
    fun savedLibraryItems(account: AccountRef, videosByItem: Map<String, List<Video>> = emptyMap()) =
        backend(account).savedLibraryItems(account.credential, videosByItem)
    fun saveLibraryItems(
        account: AccountRef,
        items: List<LibraryItem>,
        videosByItem: Map<String, List<Video>> = emptyMap()
    ) = backend(account).saveLibraryItems(account.credential, items, videosByItem)
    fun watchedEpisodes(account: AccountRef, item: LibraryItem, videos: List<Video>) =
        item.progress.watchedEpisodes ?: backend(account).watchedEpisodes(account.credential, item, videos)
    fun withWatchedEpisodes(account: AccountRef, item: LibraryItem, videos: List<Video>) =
        if (item.progress.watchedEpisodes != null) item else item.copy(
            progress = item.progress.copy(watchedEpisodes = backend(account).watchedEpisodes(account.credential, item, videos))
        )
    fun userId(account: AccountRef) = backend(account).userId(account.credential)
    fun logout(account: AccountRef) = backend(account).logout(account.credential)
    fun isExpired(account: AccountRef, error: Throwable) = backend(account).isExpired(error)
}
