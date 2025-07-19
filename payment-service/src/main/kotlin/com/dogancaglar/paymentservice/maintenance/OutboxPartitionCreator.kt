package com.dogancaglar.paymentservice.maintenance

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

    /** size of one partition in minutes */
    private val PARTITION_SIZE_MIN = 30L

    /** how far into the future we proactively create partitions */
    private val CREATE_AHEAD_MIN = 360L      // 6 h

    private val partitionFmt = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private val sqlFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    //Startup: ensure all future partitions are created
    @EventListener(ApplicationReadyEvent::class)
    @Transactional
    fun ensureAllFuturePartitionsOnStartup() {
        val now = floorToPartitionStart(LocalDateTime.now(clock))
        val horizon = now.plusMinutes(CREATE_AHEAD_MIN)

        var cursor = now
        while (cursor.isBefore(horizon)) {
            ensurePartitionExists(cursor, cursor.plusMinutes(PARTITION_SIZE_MIN))
            cursor = cursor.plusMinutes(PARTITION_SIZE_MIN)
        }
    }

    //SCHEDULED:keep rolling the window forward
    @Scheduled(fixedDelay = 11 * 60 * 1000)   // every 11 min
    @Transactional
    fun ensureNextPartitionScheduled() {
        val start = floorToPartitionStart(
            LocalDateTime.now(clock).plusMinutes(CREATE_AHEAD_MIN)
        )
        ensurePartitionExists(start, start.plusMinutes(PARTITION_SIZE_MIN))
    }


    /* ---------- helpers ------------------------------------------------ */
    private fun floorToPartitionStart(t: LocalDateTime): LocalDateTime =
        t.withMinute(((t.minute / PARTITION_SIZE_MIN).toInt()) * PARTITION_SIZE_MIN.toInt())
            .withSecond(0).withNano(0)

    private fun ensurePartitionExists(from: LocalDateTime, to: LocalDateTime) {
        val name = "outbox_event_${from.format(partitionFmt)}"
        val sql = """
            DO $$
            BEGIN
              IF NOT EXISTS (SELECT 1 FROM pg_class WHERE relname = '$name') THEN
                EXECUTE format(
                  'CREATE TABLE %I PARTITION OF outbox_event FOR VALUES FROM (%L) TO (%L);',
                  '$name', '${from.format(sqlFmt)}', '${to.format(sqlFmt)}'
                );
              END IF;
            END
            $$;
        """.trimIndent()

        val timer = Timer.start()
        var ok = false
        try {
            jdbcTemplate.execute(sql)
            logger.debug("✅ ensured partition {}", name)
            meterRegistry.counter("outbox.partition.create.success").increment()
            ok = true
        } catch (ex: Exception) {
            logger.error("❌ partition {} could not be created – {}", name, ex.message)
            meterRegistry.counter("outbox.partition.create.failure").increment()
        } finally {
            timer.stop(meterRegistry.timer("outbox.partition.create.duration"))
            meterRegistry.gauge("outbox.partition.create.last_success", ok) { if (ok) 1.0 else 0.0 }
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