package com.example.android_practice.smartcache.extensions

import com.example.android_practice.smartcache.annotations.CacheTTL
import com.example.android_practice.smartcache.annotations.EvictionStrategy
import com.example.android_practice.smartcache.core.CacheConfig
import com.example.android_practice.smartcache.impl.SmartCache

// ── Annotation-driven factory ──────────────────────────────────────────

/**
 * Reads the [@CacheTTL] annotation on [T] (if present) and builds a
 * [SmartCache] pre-configured with the declared TTL, maxSize, and strategy.
 *
 * Usage:
 *   val cache = SmartCache.forType<UserProfile>()
 */
inline fun <reified T : Any> SmartCache.Companion.forType(): SmartCache<String, T> {
    val annotation = T::class.annotations
        .filterIsInstance<CacheTTL>()
        .firstOrNull()
    val config = CacheConfig(
        ttlMs    = (annotation?.seconds ?: 60) * 1_000L,
        maxSize  = annotation?.maxSize ?: 1000,
        strategy = annotation?.strategy ?: EvictionStrategy.LRU
    )
    return SmartCache(config)
}

// ── Load-on-miss ───────────────────────────────────────────────────────

/**
 * Returns the cached value for [key], or calls [loader], caches the result,
 * and returns it.  Never returns null.
 *
 * Usage:
 *   val user = cache.getOrLoad("alice") { fetchFromDatabase("alice") }
 */
inline fun <K : Any, V : Any> SmartCache<K, V>.getOrLoad(
    key: K,
    loader: () -> V
): V = get(key) ?: loader().also { put(key, it) }

// ── Type-safe retrieval from mixed-type caches ─────────────────────────

/**
 * Retrieves the value for [key] and returns it only if it is an instance
 * of [T].  Returns null on type mismatch instead of throwing.
 *
 * Usage:
 *   val user: UserProfile? = mixedCache.getAs<UserProfile>("user")
 */
inline fun <reified T : Any, K : Any> SmartCache<K, *>.getAs(key: K): T? {
    val raw = get(key) ?: return null
    return if (raw is T) raw else null
}

/**
 * Like [getAs] but throws [CacheTypeMismatchException] when the value is
 * present but has the wrong type.
 */
inline fun <reified T : Any, K : Any> SmartCache<K, *>.getOrThrow(key: K): T {
    return getAs<T, K>(key) ?: throw CacheTypeMismatchException(
        "Expected ${T::class.simpleName} for key '$key', " +
        "but found ${get(key)?.let { it::class.simpleName } ?: "nothing"}"
    )
}

// ── Annotation reflection helpers ──────────────────────────────────────

/** Returns the TTL in seconds declared on [T] via [@CacheTTL], or 60 if absent. */
inline fun <reified T : Any> getTtlSeconds(): Int =
    T::class.annotations.filterIsInstance<CacheTTL>().firstOrNull()?.seconds ?: 60

/** Builds a [CacheConfig] from the [@CacheTTL] annotation on [T]. */
inline fun <reified T : Any> buildCacheConfigForType(): CacheConfig {
    val annotation = T::class.annotations
        .filterIsInstance<CacheTTL>()
        .firstOrNull()
    return CacheConfig(
        ttlMs    = (annotation?.seconds ?: 60) * 1_000L,
        maxSize  = annotation?.maxSize ?: 1000,
        strategy = annotation?.strategy ?: EvictionStrategy.LRU
    )
}

// ── Custom exception ───────────────────────────────────────────────────

class CacheTypeMismatchException(message: String) : RuntimeException(message)