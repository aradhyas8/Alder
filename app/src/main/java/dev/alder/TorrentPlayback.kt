package dev.alder

import android.content.Context
import com.nuvio.engine.NuvioEngine
import com.nuvio.engine.NuvioEngineConfig
import com.nuvio.engine.NuvioEventType
import com.nuvio.engine.NuvioTorrentProfile
import com.nuvio.engine.NuvioUploadMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

// Serialize engine teardown and startup across player activities that share the cache.
private val torrentEngineLock = Mutex()

internal suspend fun playTorrent(context: Context, source: TorrentSource, filename: String?, ready: (String) -> Unit) {
    torrentEngineLock.withLock {
        val cache = File(context.cacheDir, "torrent")
        var engine: NuvioEngine? = null
        try {
            val stream = withTimeout(45_000L) {
                val session = withContext(Dispatchers.IO) {
                    NuvioEngine.create(NuvioEngineConfig(
                        dataDirectory = File(cache, "data"),
                        cacheDirectory = File(cache, "payload"),
                        memoryCacheCapacityBytes = 16L * 1024 * 1024,
                        diskCacheCapacityBytes = 256L * 1024 * 1024,
                        torrentProfile = NuvioTorrentProfile.Soft,
                        uploadMode = NuvioUploadMode.Disabled,
                    )).also { engine = it }
                }
                val id = session.addMagnet(source.magnet)
                session.prepareStream(id, source.fileIndex, filename.takeIf { source.fileIndex == null }).also {
                    check(source.fileIndex == null || it.fileIndex == source.fileIndex)
                }
            }
            ready(stream.url)
            checkNotNull(engine).events.first { it.type == NuvioEventType.TorrentError && it.torrentId == stream.torrentId }
            error("Torrent playback failed")
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                engine?.close()
                File(cache, "payload").deleteRecursively()
            }
        }
    }
}
