package dev.alder

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Trakt API v2 client: device authentication, real-time playback scrobbling, and history sync.
 * Kept Plume-sized without extra dependencies: uses standard HttpURLConnection and org.json.
 */
object Trakt {
    const val CLIENT_ID = ""
    private const val API_BASE = "https://api.trakt.tv"
    private const val AUTH_BASE = "https://api.trakt.tv"

    private fun prefs(c: Context) = c.getSharedPreferences("trakt", Context.MODE_PRIVATE)

    fun token(c: Context): String? = prefs(c).getString(Settings.profileKey(c, "token"), null)
    fun username(c: Context): String? = prefs(c).getString(Settings.profileKey(c, "username"), null)
    fun isConnected(c: Context): Boolean = !token(c).isNullOrBlank()

    fun disconnect(c: Context) {
        prefs(c).edit()
            .remove(Settings.profileKey(c, "token"))
            .remove(Settings.profileKey(c, "refreshToken"))
            .remove(Settings.profileKey(c, "username"))
            .apply()
    }

    class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUrl: String,
        val intervalSeconds: Int,
        val expiresInSeconds: Int
    )

    fun requestDeviceCode(): DeviceCode {
        if (CLIENT_ID.isBlank()) throw IOException("Trakt client ID is not configured")
        val json = postJson("$AUTH_BASE/oauth/device/code", JSONObject().put("client_id", CLIENT_ID).toString())
        val obj = JSONObject(json)
        return DeviceCode(
            deviceCode = obj.getString("device_code"),
            userCode = obj.getString("user_code"),
            verificationUrl = obj.optString("verification_url", "https://trakt.tv/activate"),
            intervalSeconds = obj.optInt("interval", 5),
            expiresInSeconds = obj.optInt("expires_in", 600)
        )
    }

    sealed class PollResult {
        class Success(val accessToken: String, val refreshToken: String) : PollResult()
        object Pending : PollResult()
        object Expired : PollResult()
        object Denied : PollResult()
        class Error(val message: String) : PollResult()
    }

    fun pollToken(deviceCode: String): PollResult {
        if (CLIENT_ID.isBlank()) return PollResult.Error("Trakt client ID is not configured")
        val payload = JSONObject().put("client_id", CLIENT_ID).put("code", deviceCode).toString()
        val conn = (URL("$AUTH_BASE/oauth/device/token").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("trakt-api-version", "2")
            setRequestProperty("trakt-api-key", CLIENT_ID)
            setRequestProperty("User-Agent", "Alder/1.0")
            outputStream.use { it.write(payload.toByteArray()) }
        }
        val code = conn.responseCode
        return when (code) {
            200 -> {
                val body = conn.inputStream.use { String(it.readBytes()) }
                val obj = JSONObject(body)
                PollResult.Success(obj.getString("access_token"), obj.optString("refresh_token"))
            }
            400 -> PollResult.Pending
            404, 410 -> PollResult.Expired
            418 -> PollResult.Denied
            429 -> PollResult.Pending
            else -> PollResult.Error("HTTP $code")
        }
    }

    fun fetchAndSaveUser(c: Context, accessToken: String, refreshToken: String): String? {
        val userJson = runCatching { getJson("$API_BASE/users/me", accessToken) }.getOrNull()
        val username = userJson?.let { runCatching { JSONObject(it).getString("username") }.getOrNull() }
        prefs(c).edit()
            .putString(Settings.profileKey(c, "token"), accessToken)
            .putString(Settings.profileKey(c, "refreshToken"), refreshToken)
            .putString(Settings.profileKey(c, "username"), username)
            .apply()
        return username
    }

    private fun mediaIds(id: String): JSONObject {
        val ids = JSONObject()
        when {
            id.startsWith("tt") -> ids.put("imdb", id)
            id.startsWith("tmdb:") -> id.removePrefix("tmdb:").toIntOrNull()?.let { ids.put("tmdb", it) }
            id.toIntOrNull() != null -> ids.put("tmdb", id.toInt())
            else -> ids.put("slug", id)
        }
        return ids
    }

    private fun scrobblePayload(metaId: String, type: String, season: Int?, episode: Int?, progressPercent: Float): JSONObject {
        val body = JSONObject().put("progress", progressPercent.coerceIn(0f, 100f))
        if (type == "movie") {
            body.put("movie", JSONObject().put("ids", mediaIds(metaId)))
        } else {
            body.put("show", JSONObject().put("ids", mediaIds(metaId)))
            val ep = JSONObject().put("season", season ?: 1).put("number", episode ?: 1)
            body.put("episode", ep)
        }
        return body
    }

    fun scrobbleStart(c: Context, metaId: String, type: String, season: Int?, episode: Int?, progressPercent: Float) {
        val token = token(c) ?: run {
            android.util.Log.w("Alder", "Trakt scrobbleStart skipped: no token for profile")
            return
        }
        android.util.Log.i("Alder", "Trakt scrobbleStart: metaId=$metaId type=$type S${season}E${episode} p=$progressPercent")
        runCatching {
            postJson("$API_BASE/scrobble/start", scrobblePayload(metaId, type, season, episode, progressPercent).toString(), token)
        }.onFailure { android.util.Log.e("Alder", "Trakt scrobbleStart failed", it) }
    }

    fun scrobblePause(c: Context, metaId: String, type: String, season: Int?, episode: Int?, progressPercent: Float) {
        val token = token(c) ?: return
        android.util.Log.i("Alder", "Trakt scrobblePause: metaId=$metaId type=$type S${season}E${episode} p=$progressPercent")
        runCatching {
            postJson("$API_BASE/scrobble/pause", scrobblePayload(metaId, type, season, episode, progressPercent).toString(), token)
        }.onFailure { android.util.Log.e("Alder", "Trakt scrobblePause failed", it) }
    }

    fun scrobbleStop(c: Context, metaId: String, type: String, season: Int?, episode: Int?, progressPercent: Float) {
        val token = token(c) ?: return
        android.util.Log.i("Alder", "Trakt scrobbleStop: metaId=$metaId type=$type S${season}E${episode} p=$progressPercent")
        runCatching {
            postJson("$API_BASE/scrobble/stop", scrobblePayload(metaId, type, season, episode, progressPercent).toString(), token)
        }.onFailure { android.util.Log.e("Alder", "Trakt scrobbleStop failed", it) }
    }

    fun markWatched(c: Context, metaId: String, type: String, season: Int? = null, episode: Int? = null, watched: Boolean = true): Boolean {
        val token = token(c) ?: run {
            android.util.Log.w("Alder", "Trakt markWatched skipped: no token for profile")
            return false
        }
        val endpoint = if (watched) "$API_BASE/sync/history" else "$API_BASE/sync/history/remove"
        val payload = JSONObject()
        if (type == "movie") {
            payload.put("movies", JSONArray().put(JSONObject().put("ids", mediaIds(metaId))))
        } else {
            val show = JSONObject().put("ids", mediaIds(metaId))
            if (season != null && episode != null) {
                val ep = JSONObject().put("number", episode)
                val s = JSONObject().put("number", season).put("episodes", JSONArray().put(ep))
                show.put("seasons", JSONArray().put(s))
            } else if (season != null) {
                val s = JSONObject().put("number", season)
                show.put("seasons", JSONArray().put(s))
            }
            payload.put("shows", JSONArray().put(show))
        }
        android.util.Log.i("Alder", "Trakt markWatched: endpoint=$endpoint payload=$payload")
        return runCatching {
            postJson(endpoint, payload.toString(), token)
            true
        }.onFailure { android.util.Log.e("Alder", "Trakt markWatched failed", it) }.getOrDefault(false)
    }

    private fun postJson(urlStr: String, body: String, token: String? = null): String {
        android.util.Log.i("Alder", "Trakt POST $urlStr body=$body")
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("trakt-api-version", "2")
            setRequestProperty("trakt-api-key", CLIENT_ID)
            setRequestProperty("User-Agent", "Alder/1.0")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
            outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream ?: throw IOException("HTTP $code")
        val text = stream.use { String(it.readBytes()) }
        android.util.Log.i("Alder", "Trakt POST $urlStr -> HTTP $code: $text")
        if (code >= 400) throw IOException("HTTP $code: $text")
        return text
    }

    private fun getJson(urlStr: String, token: String? = null): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("trakt-api-version", "2")
            setRequestProperty("trakt-api-key", CLIENT_ID)
            setRequestProperty("User-Agent", "Alder/1.0")
            token?.let { setRequestProperty("Authorization", "Bearer $it") }
        }
        val code = conn.responseCode
        val stream = if (code < 400) conn.inputStream else conn.errorStream ?: throw IOException("HTTP $code")
        val text = stream.use { String(it.readBytes()) }
        if (code >= 400) throw IOException("HTTP $code: $text")
        return text
    }
}
