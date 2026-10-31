package dev.alder

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class Profile(val id: String, val name: String, val color: Int, val account: AccountRef, val pin: String? = null,
              val sourceAccounts: List<AccountRef> = listOf(account))

/**
 * The profiles on this TV, kept in app storage. Each account owns its library and watch progress.
 */
object Profiles {
    const val MAX = 5
    val COLORS = intArrayOf(0xFFF3D9B1.toInt(), 0xFFC98E6A.toInt(), 0xFF8FA39A.toInt(), 0xFFA6A2BD.toInt(), 0xFFD3A66B.toInt())

    fun all(context: Context): List<Profile> {
        val prefs = prefs(context)
        migrate(prefs)
        return JSONArray(prefs.getString("profiles", "[]")).objects()
            .map(::profileFromJson)
    }

    /** The profile in use: the one picked last, or the only one there is. */
    fun active(context: Context): Profile? {
        val list = all(context)
        return list.firstOrNull { it.id == prefs(context).getString("active", null) } ?: list.singleOrNull()
    }

    fun setActive(context: Context, id: String) = prefs(context).edit().putString("active", id).apply()

    fun add(context: Context, name: String, account: AccountRef, color: Int = COLORS[0]): Profile {
        val list = all(context)
        val number = (1..MAX).first { n -> list.none { it.id == "$n" } }
        val profile = Profile("$number", name, color, account)
        save(prefs(context), list + profile)
        setActive(context, profile.id)
        return profile
    }

    fun remove(context: Context, id: String) {
        val prefs = prefs(context)
        save(prefs, all(context).filterNot { it.id == id })
        if (prefs.getString("active", null) == id) prefs.edit().remove("active").apply()
        prefs.edit().remove("shareStreams:$id").remove("signedOut:$id").apply()
        Settings.forget(context, id)
        context.getSharedPreferences("search", Context.MODE_PRIVATE).edit().remove("recent:$id").apply()
    }

    /**
     * Stremio no longer knows this profile's sign-in (signed out on the phone or elsewhere, or the session expired). The
     * profile stays, with its name, PIN and settings, and asks to sign in again instead of being removed.
     */
    fun signedOut(context: Context, id: String) = prefs(context).getBoolean("signedOut:$id", false)

    fun markSignedOut(context: Context, id: String) = prefs(context).edit().putBoolean("signedOut:$id", true).apply()

    /** A fresh sign-in for an existing profile: its new account, everything else kept. */
    fun signIn(context: Context, id: String, account: AccountRef) {
        save(prefs(context), all(context).map {
            if (it.id != id) it else it.withAccount(account)
        })
        prefs(context).edit().remove("signedOut:$id").apply()
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
        save(prefs(context), all(context).map {
            if (it.id != id) it
            else Profile(it.id, name ?: it.name, color ?: it.color, it.account, if (clearPin) null else pin ?: it.pin,
                it.sourceAccounts)
        })
    }

    private fun save(prefs: SharedPreferences, list: List<Profile>) {
        val json = JSONArray(list.map(::profileToJson))
        prefs.edit().putString("profiles", json.toString()).apply()
    }

    // Before profiles there was one sign-in; it becomes the first profile.
    private fun migrate(prefs: SharedPreferences) {
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
        if (!prefs.contains("profiles")) save(prefs, listOf(Profile("1", "Me", COLORS[0], AccountRef(Accounts.STREMIO, key))))
        prefs.edit().remove("authKey").putString("active", "1").apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("account", Context.MODE_PRIVATE)
}

internal fun profileFromJson(json: JSONObject): Profile {
    val account = AccountRef(json.optString("provider", Accounts.STREMIO),
        json.optString("credential").ifEmpty { json.getString("authKey") })
    val sources = json.optJSONArray("sources")?.objects()?.map {
        AccountRef(it.getString("provider"), it.getString("credential"))
    } ?: listOf(account)
    return Profile(json.getString("id"), json.getString("name"),
        json.getInt("color").takeIf { it in Profiles.COLORS } ?: Profiles.COLORS[0],
        account, json.optString("pin").ifEmpty { null }, sources)
}

internal fun profileToJson(profile: Profile) = JSONObject().put("id", profile.id).put("name", profile.name)
    .put("color", profile.color).put("provider", profile.account.provider)
    .put("credential", profile.account.credential).put("pin", profile.pin ?: "")
    .put("sources", JSONArray(profile.sourceAccounts.map {
        JSONObject().put("provider", it.provider).put("credential", it.credential)
    }))

internal fun Profile.withAccount(next: AccountRef) = Profile(id, name, color, next, pin,
    sourceAccounts.map { if (it == account && next.provider == account.provider) next else it })
