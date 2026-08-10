package com.example.android_practice.smartcache.core

interface CacheStore<K : Any, V : Any> {
    fun put(key: K, value: V)
    fun get(key: K): V?
    fun evict(key: K): V?
    fun clear()
    val size: Int
    val keys: Set<K>
}

// Split into read-only and write-only views:
interface CacheReader<out V : Any> {
    fun get(key: String): V?
    val size: Int
}

interface CacheWriter<in V : Any> {
    fun put(key: String, value: V)
    fun evict(key: String)
}