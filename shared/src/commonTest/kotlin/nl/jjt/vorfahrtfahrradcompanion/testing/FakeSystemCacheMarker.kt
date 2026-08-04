package nl.jjt.vorfahrtfahrradcompanion.testing

import nl.jjt.vorfahrtfahrradcompanion.util.platform.SystemCacheMarker

/** Reports one OS-level cache wipe and then stops, as the real marker does. */
class FakeSystemCacheMarker(private var cleared: Boolean = false) : SystemCacheMarker {
    override suspend fun consumeCacheCleared() = cleared.also { cleared = false }
}
