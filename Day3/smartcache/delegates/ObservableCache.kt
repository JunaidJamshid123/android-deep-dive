package com.example.android_practice.smartcache.delegates

import com.example.android_practice.smartcache.core.CacheConfig
import com.example.android_practice.smartcache.core.CacheStore
import com.example.android_practice.smartcache.impl.SmartCache
import kotlin.properties.Delegates

/**
 * Wraps a [SmartCache] and exposes observable/vetoable config properties.
 * External listeners are notified whenever an entry is added or evicted.
 */
class ObservableCache<K : Any, V : Any>(
    initialConfig: CacheConfig = CacheConfig()
) : CacheStore<K, V> {

    // ── Observable config properties ───────────────────────────────────

    /** Fires a warning when reduced below the current number of entries. */
    var maxSize: Int by Delegates.observable(initialConfig.maxSize) { prop, old, new ->
        println("${prop.name} changed: $old → $new")
        if (new < old) println("WARNING: maxSize reduced — entries may be evicted")
    }

    /** Rejects zero or negative values; keeps the old TTL instead. */
    var ttlMs: Long by Delegates.vetoable(initialConfig.ttlMs) { prop, old, new ->
        val valid = new > 0
        if (!valid) println("Rejected invalid ttlMs: $new (keeping $old)")
        valid
    }

    // ── Internal delegate cache ────────────────────────────────────────

    private val inner: SmartCache<K, V> = SmartCache(initialConfig)

    // ── CacheStore delegation ──────────────────────────────────────────

    override fun put(key: K, value: V) = inner.put(key, value)
    override fun get(key: K): V?       = inner.get(key)
    override fun evict(key: K): V?     = inner.evict(key)
    override fun clear()               = inner.clear()
    override val size: Int             get() = inner.size
    override val keys: Set<K>          get() = inner.keys

    // ── Operator passthrough ───────────────────────────────────────────

    operator fun get(key: K): V?               = inner[key]
    operator fun set(key: K, value: V)         { inner[key] = value }
    operator fun contains(key: K): Boolean     = key in inner
    operator fun minusAssign(key: K)           { inner -= key }
    operator fun plusAssign(entry: Pair<K, V>) { inner += entry }
    operator fun invoke(): Map<K, V>           = inner()

    // ── Listener registration — returns this for fluent chaining ───────

    fun onEvict(listener: (key: K, value: V) -> Unit): ObservableCache<K, V> {
        inner.onEvict(listener)
        return this
    }

    fun onChange(listener: (key: K, old: V?, new: V) -> Unit): ObservableCache<K, V> {
        inner.onChange(listener)
        return this
    }
}