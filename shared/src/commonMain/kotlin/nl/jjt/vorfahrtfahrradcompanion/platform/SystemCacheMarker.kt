package nl.jjt.vorfahrtfahrradcompanion.platform

/**
 * Detects an OS-level cache wipe ("Clear cache" in the Android app info screen). That wipe only empties
 * the platform cache directory and leaves the database untouched, so anything the app caches in the
 * database has to be dropped explicitly.
 *
 * Implemented by writing a marker into the platform cache directory: if the marker is gone, the
 * directory was wiped.
 */
interface SystemCacheMarker {
    /** True exactly once after the platform cache directory was wiped (and once on a fresh install). */
    suspend fun consumeCacheCleared(): Boolean
}
