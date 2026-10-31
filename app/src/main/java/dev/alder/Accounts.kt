package dev.alder

import org.json.JSONObject

/** A profile's account. Only its backend interprets the credential. */
data class AccountRef(val provider: String, val credential: String) {
    override fun toString() = "AccountRef(provider=$provider)"
}

/** Library items use Alder's current library schema; each backend converts its own wire format. */
interface AccountBackend {
    fun library(credential: String, ids: List<String> = emptyList()): List<JSONObject>
    fun savedLibrary(credential: String): List<JSONObject>?
    fun saveLibrary(credential: String, items: List<JSONObject>)
    fun userId(credential: String): String
    fun logout(credential: String)
    fun isExpired(error: Throwable): Boolean
}

private object StremioAccount : AccountBackend {
    override fun library(credential: String, ids: List<String>) = Stremio.library(credential, ids)
    override fun savedLibrary(credential: String) = Stremio.savedLibrary(credential)
    override fun saveLibrary(credential: String, items: List<JSONObject>) = Stremio.saveLibrary(credential, items)
    override fun userId(credential: String) = Stremio.userId(credential)
    override fun logout(credential: String) = Stremio.logout(credential)
    override fun isExpired(error: Throwable) =
        (error as? Stremio.ApiException)?.code == Stremio.NO_SESSION
}

/** The app uses this boundary for account data; provider details stay in the backend. */
object Accounts {
    const val STREMIO = "stremio"
    private val backends: Map<String, AccountBackend> = mapOf(STREMIO to StremioAccount)

    private fun backend(account: AccountRef): AccountBackend =
        backends[account.provider] ?: error("Unknown account provider: ${account.provider}")

    fun library(account: AccountRef, ids: List<String> = emptyList()) = backend(account).library(account.credential, ids)
    fun savedLibrary(account: AccountRef) = backend(account).savedLibrary(account.credential)
    fun saveLibrary(account: AccountRef, items: List<JSONObject>) = backend(account).saveLibrary(account.credential, items)
    fun userId(account: AccountRef) = backend(account).userId(account.credential)
    fun logout(account: AccountRef) = backend(account).logout(account.credential)
    fun isExpired(account: AccountRef, error: Throwable) = backend(account).isExpired(error)
}
