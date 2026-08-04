package nl.jjt.vorfahrtfahrradcompanion.platform

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Marker file in [Context.getCacheDir]. `createNewFile` reports whether it did create the file, which is
 * exactly the "the marker was gone" signal — and it is atomic, so concurrent callers can't both claim it.
 */
class AndroidSystemCacheMarker(private val context: Context) : SystemCacheMarker {
    override suspend fun consumeCacheCleared(): Boolean = withContext(Dispatchers.IO) {
        val cacheDir = context.cacheDir
        cacheDir.mkdirs() // "Clear cache" can remove the directory itself
        File(cacheDir, MARKER_NAME).createNewFile()
    }

    private companion object {
        const val MARKER_NAME = "cache-marker"
    }
}
