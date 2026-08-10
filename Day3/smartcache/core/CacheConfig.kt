package com.example.android_practice.smartcache.core

import com.example.android_practice.smartcache.annotations.EvictionStrategy

data class CacheConfig(
    val ttlMs: Long = 60_000L,
    val maxSize: Int = 1000,
    val strategy: EvictionStrategy = EvictionStrategy.LRU,
    val onEvict: ((key: Any, value: Any) -> Unit)? = null
)