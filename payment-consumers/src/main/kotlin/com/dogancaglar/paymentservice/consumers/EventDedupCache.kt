package com.dogancaglar.paymentservice.consumers

/**
 * Local-JVM idempotency cache for deduplication.
 * Best-effort: correctness still guaranteed by deterministic eventId + DB idempotency.
 */

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * In-memory deduplication cache for consumer-side idempotency.
 *
 * Stores eventId -> true with TTL.
 * All consumer instances have their own local cache — but DB writes are idempotent,
 * so duplicates are safe even if another instance reprocesses.
 */

class EventDedupCache {

    private val cache = Caffeine.newBuilder()
        .expireAfterWrite(10, TimeUnit.MINUTES)   // TTL for dedupe window
        .maximumSize(100_000)
        .build<String, Boolean>()

    /**
     * Returns true if this eventId has already been processed.
     */
    fun isDuplicate(eventId: String): Boolean =
        cache.getIfPresent(eventId) == true

    /**
     * Record eventId as processed.
     */
    fun markSeen(eventId: String) {
        cache.put(eventId, true)
    }
}