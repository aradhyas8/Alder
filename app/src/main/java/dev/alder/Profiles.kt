package dev.alder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Profile(val id: String, val name: String, val color: Int, val account: AccountRef, val pin: String? = null,
              val sources: List<SourceRef> = listOf(SourceRef(account.provider, account.credential)))

/**
 * The profiles on this TV, kept in app storage. Each account owns its library and watch progress.
 */
object Profiles {
    const val MAX = 5
    val COLORS = intArrayOf(0xFFF3D9B1.toInt(), 0xFFC98E6A.toInt(), 0xFF8FA39A.toInt(), 0xFFA6A2BD.toInt(), 0xFFD3A66B.toInt())

    fun all(context: Context): List<Profile> = store(context).all()

    /** The profile in use: the one picked last, or the only one there is. */
    fun active(context: Context): Profile? {
        val list = all(context)
        return list.firstOrNull { it.id == prefs(context).getString("active", null) } ?: list.singleOrNull()
    }

    fun setActive(context: Context, id: String) = prefs(context).edit().putString("active", id).apply()

    fun add(context: Context, name: String, account: AccountRef, color: Int = COLORS[0]): Profile {
        val store = store(context)
        val list = store.all()
        val number = (1..MAX).first { n -> list.none { it.id == "$n" } }
        val profile = Profile("$number", name, color, account)
        store.save(list + profile)
        setActive(context, profile.id)
        return profile
    }

    fun remove(context: Context, id: String) {
        val prefs = prefs(context)
        val store = store(context)
        val current = store.all()
        val removed = current.firstOrNull { it.id == id }
        val remaining = current.filterNot { it.id == id }
        store.save(remaining)
        if (prefs.getString("active", null) == id) prefs.edit().remove("active").apply()
        prefs.edit().remove("shareStreams:$id").remove("signedOut:$id").apply()
        Settings.forget(context, id)
        context.getSharedPreferences("search", Context.MODE_PRIVATE).edit().remove("recent:$id").apply()
        Http.forgetProfile(id)
        removed?.account?.takeIf { account -> remaining.none { it.account == account } }
            ?.let { Http.forget(Http.owner("account", it.provider, it.credential)) }
    }

    /**
     * Stremio no longer knows this profile's sign-in (signed out on the phone or elsewhere, or the session expired). The
     * profile stays, with its name, PIN and settings, and asks to sign in again instead of being removed.
     */
    fun signedOut(context: Context, id: String) = prefs(context).getBoolean("signedOut:$id", false)

    fun markSignedOut(context: Context, id: String) = prefs(context).edit().putBoolean("signedOut:$id", true).apply()

    /** A fresh sign-in for an existing profile: its new account, everything else kept. */
    fun signIn(context: Context, id: String, account: AccountRef) {
        val store = store(context)
        val current = store.all()
        val previous = current.firstOrNull { it.id == id }
        val updated = current.map {
            if (it.id != id) it else it.withAccount(account)
        }
        store.save(updated)
        previous?.let { old ->
            (old.sources - updated.firstOrNull { it.id == id }?.sources.orEmpty().toSet()).forEach { source ->
                Http.forget(Http.owner("profile:$id", source.provider, source.credential))
            }
            if (updated.none { it.account == old.account })
                Http.forget(Http.owner("account", old.account.provider, old.account.credential))
        }
        prefs(context).edit().remove("signedOut:$id").apply()
    }

    /** The ordered content connections of one profile. */
    fun setSources(context: Context, id: String, sources: List<SourceRef>) {
        require(sources.distinct().size == sources.size)
        val store = store(context)
        val current = store.all()
        val profile = current.firstOrNull { it.id == id } ?: return
        val removed = profile.sources - sources.toSet()
        store.save(current.map { if (it.id != id) it else Profile(it.id, it.name, it.color, it.account, it.pin, sources) })
        removed.forEach { Http.forget(Http.owner("profile:$id", it.provider, it.credential)) }
    }

    fun linkSource(context: Context, id: String, source: SourceRef) {
        val profile = all(context).firstOrNull { it.id == id } ?: return
        setSources(context, id, linkSource(profile.sources, source))
    }

    fun removeSource(context: Context, id: String, source: SourceRef) {
        val profile = all(context).firstOrNull { it.id == id } ?: return
        setSources(context, id, removeSource(profile.sources, source))
    }

    fun orderSources(context: Context, id: String, ordered: List<SourceRef>) {
        val profile = all(context).firstOrNull { it.id == id } ?: return
        setSources(context, id, orderSources(profile.sources, ordered))
    }

    /** Whether [id] lends its stream add-ons (`Addon.shareable`) to the other profiles on this TV. */
    fun sharesStreams(context: Context, id: String) = prefs(context).getBoolean("shareStreams:$id", false)

    fun setSharesStreams(context: Context, id: String, on: Boolean) = prefs(context).edit().putBoolean("shareStreams:$id", on).apply()

    /** The other profiles lending their stream add-ons to the active one; watch progress still uses the active account. */
    fun streamLenders(context: Context): List<Profile> {
        val current = active(context) ?: return emptyList()
        return all(context).filter { it.id != current.id && sharesStreams(context, it.id) }
    }

    /** Rename or set the PIN of a profile (clearPin removes it), keeping its place in the list. */
    fun update(context: Context, id: String, name: String? = null, pin: String? = null, clearPin: Boolean = false, color: Int? = null) {
        val store = store(context)
        store.save(store.all().map {
            if (it.id != id) it
            else Profile(it.id, name ?: it.name, color ?: it.color, it.account, if (clearPin) null else pin ?: it.pin,
                it.sources)
        })
    }

    private fun store(context: Context): ProfileStore = SharedPreferencesProfileStore(prefs(context))
    private fun prefs(context: Context) = context.getSharedPreferences("account", Context.MODE_PRIVATE)
}

internal interface ProfileStore {
    fun all(): List<Profile>
    fun save(profiles: List<Profile>)
}

internal fun linkSource(sources: List<SourceRef>, source: SourceRef) =
    if (source in sources) sources else sources + source

internal fun removeSource(sources: List<SourceRef>, source: SourceRef) = sources - source

internal fun orderSources(sources: List<SourceRef>, ordered: List<SourceRef>): List<SourceRef> {
    require(ordered.size == sources.size && ordered.toSet() == sources.toSet())
    return ordered
}

internal class SharedPreferencesProfileStore(private val prefs: SharedPreferences) : ProfileStore {
    override fun all(): List<Profile> {
        migrate()
        return JSONArray(prefs.getString("profiles", "[]")).objects().map(::profileFromJson)
    }

    override fun save(profiles: List<Profile>) {
        prefs.edit().putString("profiles", JSONArray(profiles.map(::profileToJson)).toString()).apply()
    }

    // Before profiles there was one sign-in; it becomes the first profile.
    private fun migrate() {
        // Sharing used to lend one add-on to one profile (2026-10-26); an owner who lent one now shares with everyone.
        prefs.all.keys.filter { it.startsWith("streamSource:") || it.startsWith("streamAddon:") }.takeIf { it.isNotEmpty() }?.let { old ->
            val edit = prefs.edit()
            old.forEach { key ->
                if (key.startsWith("streamSource:")) prefs.getString(key, null)?.let { edit.putBoolean("shareStreams:$it", true) }
                edit.remove(key)
            }
            edit.apply()
        }
        val key = prefs.getString("authKey", null) ?: return
        if (!prefs.contains("profiles")) save(listOf(Profile("1", "Me", Profiles.COLORS[0], AccountRef(Accounts.STREMIO, key))))
        prefs.edit().remove("authKey").putString("active", "1").apply()
    }
}

internal fun profileFromJson(json: JSONObject): Profile {
    val account = AccountRef(json.optString("provider", Accounts.STREMIO),
        json.optString("credential").ifEmpty { json.getString("authKey") })
    val sources = json.optJSONArray("sources")?.objects()?.map {
        SourceRef(it.getString("provider"), it.getString("credential"))
    } ?: listOf(SourceRef(account.provider, account.credential))
    return Profile(json.getString("id"), json.getString("name"),
        json.getInt("color").takeIf { it in Profiles.COLORS } ?: Profiles.COLORS[0],
        account, json.optString("pin").ifEmpty { null }, sources)
}

internal fun profileToJson(profile: Profile) = JSONObject().put("id", profile.id).put("name", profile.name)
    .put("color", profile.color).put("provider", profile.account.provider)
    .put("credential", profile.account.credential).put("pin", profile.pin ?: "")
    .put("sources", JSONArray(profile.sources.map {
        JSONObject().put("provider", it.provider).put("credential", it.credential)
    }))

internal fun Profile.withAccount(next: AccountRef) = Profile(id, name, color, next, pin,
    sources.map { if (it == SourceRef(account.provider, account.credential) && next.provider == account.provider)
        SourceRef(next.provider, next.credential) else it })
