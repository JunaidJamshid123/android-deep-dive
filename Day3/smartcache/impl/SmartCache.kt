package com.example.android_practice.smartcache.impl

import com.example.android_practice.smartcache.annotations.EvictionStrategy
import com.example.android_practice.smartcache.core.CacheConfig
import com.example.android_practice.smartcache.core.CacheEntry
import com.example.android_practice.smartcache.core.CacheStore

class SmartCache<K : Any, V : Any>(
    private val config: CacheConfig = CacheConfig()
) : CacheStore<K, V> {

    private val store = LinkedHashMap<K, CacheEntry<V>>()
    private val evictListeners = mutableListOf<(K, V) -> Unit>()
    private val changeListeners = mutableListOf<(K, V?, V) -> Unit>()

    // ── CacheStore implementation ──────────────────────────────────────

    override fun put(key: K, value: V) {
        val old = store[key]?.value
        if (store.size >= config.maxSize && !store.containsKey(key)) {
            evictOne()
        }
        val entry = CacheEntry(
            value = value,
            expiresAt = System.currentTimeMillis() + config.maxSize
                .let { config.ttlMs }
        )
        store[key] = entry
        changeListeners.forEach { it(key, old, value) }
    }

    override fun get(key: K): V? {
        val entry = store[key] ?: return null
        if (entry.isExpired) {
            evict(key)
            return null
        }
        // update access count for LFU tracking
        store[key] = entry.copy(accessCount = entry.accessCount + 1)
        return entry.value
    }

    override fun evict(key: K): V? {
        val entry = store.remove(key) ?: return null
        val value = entry.value
        config.onEvict?.invoke(key, value)
        evictListeners.forEach { it(key, value) }
        return value
    }

    override fun clear() {
        store.keys.toList().forEach { evict(it) }
    }

    override val size: Int get() = store.size

    override val keys: Set<K> get() = store.keys.toSet()

    // ── Operator overloading ───────────────────────────────────────────

    operator fun get(key: K): V? = get(key)

    operator fun set(key: K, value: V) = put(key, value)

    operator fun contains(key: K): Boolean {
        val entry = store[key] ?: return false
        if (entry.isExpired) {
            evict(key)
            return false
        }
        return true
    }

    operator fun minusAssign(key: K) {
        evict(key)
    }

    operator fun plusAssign(entry: Pair<K, V>) {
        put(entry.first, entry.second)
    }

    operator fun plus(other: SmartCache<K, V>): SmartCache<K, V> {
        val merged = SmartCache<K, V>(config)
        store.forEach { (k, e) -> if (e.isAlive) merged.put(k, e.value) }
        other.store.forEach { (k, e) -> if (e.isAlive) merged.put(k, e.value) }
        return merged
    }

    // Returns an immutable snapshot of all non-expired entries
    operator fun invoke(): Map<K, V> {
        purgeExpired()
        return store.mapValues { it.value.value }
    }

    // ── Change / eviction listeners ────────────────────────────────────

    fun onEvict(listener: (key: K, value: V) -> Unit): SmartCache<K, V> {
        evictListeners.add(listener)
        return this
    }

    fun onChange(listener: (key: K, old: V?, new: V) -> Unit): SmartCache<K, V> {
        changeListeners.add(listener)
        return this
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private fun purgeExpired() {
        store.keys.toList().forEach { key ->
            store[key]?.takeIf { it.isExpired }?.let { evict(key) }
        }
    }

    private fun evictOne() {
        if (store.isEmpty()) return
        val keyToEvict: K = when (config.strategy) {
            EvictionStrategy.LRU  -> store.keys.first()           // LinkedHashMap insertion order
            EvictionStrategy.LFU  -> store.minByOrNull { it.value.accessCount }!!.key
            EvictionStrategy.FIFO -> store.keys.first()
        }
        evict(keyToEvict)
    }

    // ── Companion — factory methods ────────────────────────────────────

    companion object
}