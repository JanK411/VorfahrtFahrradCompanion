package nl.jjt.vorfahrtfahrradcompanion.util.platform

/**
 * When iOS is picked up: put the marker in `NSCachesDirectory`
 * (`NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)`). iOS has no
 * user-facing "Clear cache", but the system purges that directory under storage pressure — the same
 * signal. `NSFileManager.createFileAtPath` overwrites instead of reporting "did not exist", so the
 * atomic trick used on Android does not carry over: check `fileExistsAtPath` first and accept that
 * concurrent callers could both report a clear (harmless — the second `clear()` is a no-op).
 *
 * Binding it also needs an iOS composition root: [nl.jjt.vorfahrtfahrradcompanion.MainViewController]
 * calls `App()` without platform modules, so `AppDatabase` is unbound there too.
 */
class IosSystemCacheMarker : SystemCacheMarker {
    override suspend fun consumeCacheCleared(): Boolean = TODO("iOS not implemented")
}
