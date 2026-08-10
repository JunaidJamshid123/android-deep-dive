package com.example.android_practice.smartcache.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class CacheTTL(
    val seconds: Int = 60,
    val maxSize: Int = 1000,
    val strategy: EvictionStrategy = EvictionStrategy.LRU
)

enum class EvictionStrategy { LRU, LFU, FIFO }