package com.example.android_practice.smartcache.core

data class CacheEntry<V : Any>(
    val value: V,
    val expiresAt: Long,
    val accessCount: Int = 0
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    val isAlive:   Boolean get() = !isExpired

    operator fun component1(): V    = value
    operator fun component2(): Long = expiresAt
}