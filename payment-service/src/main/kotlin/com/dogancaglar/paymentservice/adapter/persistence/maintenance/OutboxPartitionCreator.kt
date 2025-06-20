package com.dogancaglar.paymentservice.adapter.persistence.maintenance

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class OutboxPartitionCreator(
    private val entityManager: EntityManager,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val partitionFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private val sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PARTITION_WINDOW_MINUTES = 30L

    // --- Ensure current partition on app startup ---
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun ensureCurrentPartitionOnStartup() {
        val now = LocalDateTime.now(clock)
        val windowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val windowEnd = windowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        logger.info("Startup: Ensuring partition for window [$windowStart, $windowEnd)")
        ensurePartitionExists(windowStart, windowEnd)
    }

    // --- Ensure next partition on schedule (keep us always ahead) ---
    @Scheduled(fixedDelay = 10 * 60 * 1000) // every 10 minutes
    @Transactional
    fun ensureNextPartitionScheduled() {
        val now = LocalDateTime.now(clock)
        val windowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val nextWindowStart = windowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        val nextWindowEnd = nextWindowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        logger.info("Scheduled: Ensuring partition for next window [$nextWindowStart, $nextWindowEnd)")
        ensurePartitionExists(nextWindowStart, nextWindowEnd)
    }

    // --- Partition creation logic ---
    private fun ensurePartitionExists(from: LocalDateTime, to: LocalDateTime) {
        val partitionName = "outbox_event_${from.format(partitionFormatter)}"
        val fromStr = from.format(sqlFormatter)
        val toStr = to.format(sqlFormatter)
        val sql = """
            DO $$
            BEGIN
                IF NOT EXISTS (
                    SELECT 1 FROM pg_class WHERE relname = '$partitionName'
                ) THEN
                    EXECUTE format('CREATE TABLE %I PARTITION OF outbox_event FOR VALUES FROM (%L) TO (%L);',
                        '$partitionName', '$fromStr', '$toStr');
                END IF;
            END
            $$;
        """.trimIndent()

        val timer = Timer.start()
        var success = false
        val zoneId = clock.zone
        try {
            entityManager.createNativeQuery(sql).executeUpdate()
            logger.info("Ensured partition exists: $partitionName for [$fromStr, $toStr)")
            meterRegistry.counter("outbox_partition_creator.success").increment()
            success = true
        } catch (e: Exception) {
            logger.error("Error creating partition $partitionName: ${e.message}", e)
            meterRegistry.counter("outbox_partition_creator.failure").increment()
        } finally {
            timer.stop(meterRegistry.timer("outbox_partition_creator.duration"))
            meterRegistry.gauge(
                "outbox_partition_creator.last_from_epoch",
                from.atZone(zoneId).toEpochSecond().toDouble()
            )
            meterRegistry.gauge("outbox_partition_creator.last_to_epoch", to.atZone(zoneId).toEpochSecond().toDouble())
            meterRegistry.gauge("outbox_partition_creator.last_success", if (success) 1.0 else 0.0)
        }
    }
}