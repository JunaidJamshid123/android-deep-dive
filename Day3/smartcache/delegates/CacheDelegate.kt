package com.example.android_practice.smartcache.delegates

import kotlin.reflect.KProperty

/**
 * A read-only property delegate that auto-reloads its value from [loader]
 * whenever the TTL has expired.
 *
 * Usage:
 *   val liveScores: List<Score> by CacheDelegate(ttlMs = 30_000) { fetchScores() }
 */
class CacheDelegate<T>(
    private val ttlMs: Long,
    private val loader: () -> T
) {
    private var cachedValue: T? = null
    private var cachedAt: Long = 0L

    private val isExpired: Boolean
        get() = System.currentTimeMillis() - cachedAt > ttlMs

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T {
        if (cachedValue == null || isExpired) {
            println("Cache MISS — reloading '${property.name}'")
            cachedValue = loader()
            cachedAt = System.currentTimeMillis()
        } else {
            println("Cache HIT  — '${property.name}' (${remainingTtl()}ms remaining)")
        }
        @Suppress("UNCHECKED_CAST")
        return cachedValue as T
    }

    private fun remainingTtl(): Long = ttlMs - (System.currentTimeMillis() - cachedAt)
}

/**
 * A read-write property delegate whose value expires after [ttlMs].
 * After expiry, reads return [default] until a new value is assigned.
 *
 * Usage:
 *   var authToken: String by ExpirableDelegate(ttlMs = 3_600_000L, default = "")
 */
class ExpirableDelegate<T : Any>(
    private val ttlMs: Long,
    private val default: T
) {
    private var value: T = default
    private var setAt: Long = 0L

    private val isValid: Boolean
        get() = setAt > 0 && System.currentTimeMillis() - setAt <= ttlMs

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        if (isValid) value else default

    operator fun setValue(thisRef: Any?, property: KProperty<*>, newValue: T) {
        value = newValue
        setAt = System.currentTimeMillis()
    }
}

/** Convenience factory — matches the `by cached(ttlMs) { ... }` syntax from the lesson. */
fun <T> cached(ttlMs: Long, loader: () -> T): CacheDelegate<T> =
    CacheDelegate(ttlMs, loader)