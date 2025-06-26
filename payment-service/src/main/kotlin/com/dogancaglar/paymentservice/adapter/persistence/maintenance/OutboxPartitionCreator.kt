package com.dogancaglar.paymentservice.adapter.persistence.maintenance

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class OutboxPartitionCreator(
    private val jdbcTemplate: JdbcTemplate,
    private val meterRegistry: MeterRegistry,
    private val clock: Clock,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val partitionFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private val sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PARTITION_WINDOW_MINUTES = 30L

    // Ensure current partition exists on startup
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun ensureCurrentPartitionOnStartup() {
        ensurePartitionForNow()
    }

    // Ensure next partition exists (run every 11 min, slightly offset from prune)
    @Scheduled(fixedDelay = 11 * 60 * 1000)
    @Transactional
    fun ensureNextPartitionScheduled() {
        ensurePartitionForNext()
    }

    // Partition creation logic for current window
    fun ensurePartitionForNow() {
        val now = LocalDateTime.now(clock)
        val windowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val windowEnd = windowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        ensurePartitionExists(windowStart, windowEnd)
    }

    // Partition creation logic for next window
    fun ensurePartitionForNext() {
        val now = LocalDateTime.now(clock)
        val windowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val nextWindowStart = windowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        val nextWindowEnd = nextWindowStart.plusMinutes(PARTITION_WINDOW_MINUTES)
        ensurePartitionExists(nextWindowStart, nextWindowEnd)
    }

    // --- Partition creation (safe DDL) ---
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
            jdbcTemplate.execute(sql)
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

    // --- Prune old partitions (every 31 min, slightly offset from create) ---
    @Scheduled(fixedDelay = 21 * 60 * 1000)
    @Transactional
    fun pruneOldPartitions() {
        prunePartitionsSafe()
    }

    fun prunePartitionsSafe() {
        val now = LocalDateTime.now(clock)
        val currWindowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)

        val sql = """
            DO $$
            DECLARE
                part record;
                new_count integer;
                part_end_time timestamp;
                curr_window_start timestamp := '$currWindowStart';
            BEGIN
                FOR part IN
                    SELECT inhrelid::regclass AS partition_name
                    FROM pg_inherits
                    WHERE inhparent = 'outbox_event'::regclass
                LOOP
                    -- Parse partition end time (name: outbox_event_YYYYMMDD_HHMM)
                    part_end_time := to_timestamp(
                        substring(part.partition_name::text from 'outbox_event_(\d{8}_\d{4})'),
                        'YYYYMMDD_HH24MI'
                    ) + interval '30 minutes';

                    -- Only prune partitions strictly before current window
                    IF part_end_time <= curr_window_start THEN
                        EXECUTE format('SELECT count(*) FROM %I WHERE status = %L', part.partition_name, 'NEW') INTO new_count;
                        IF new_count = 0 THEN
                            RAISE NOTICE 'Dropping partition: %', part.partition_name;
                            EXECUTE format('ALTER TABLE outbox_event DETACH PARTITION %I', part.partition_name);
                            EXECUTE format('DROP TABLE %I', part.partition_name);
                        END IF;
                    END IF;
                END LOOP;
            END $$;
        """.trimIndent()

        try {
            jdbcTemplate.execute(sql)
            logger.info("Pruned old partitions up to $currWindowStart")
            meterRegistry.counter("outbox_partition_prune.success").increment()
        } catch (e: Exception) {
            logger.error("Partition prune failed: ${e.message}", e)
            meterRegistry.counter("outbox_partition_prune.failure").increment()
        }
    }
}