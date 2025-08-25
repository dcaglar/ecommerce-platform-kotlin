package com.dogancaglar.paymentservice.application.maintenance

import com.dogancaglar.common.logging.LogContext
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


@Component
class OutboxPartitionCreator(
    private val jdbcTemplate: JdbcTemplate,
    private val clock: Clock,
    @Qualifier("outboxEventPartitionMaintenanceScheduler") private val taskScheduler: ThreadPoolTaskScheduler
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val partitionFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
    private val sqlFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val PARTITION_SIZE_MIN = 30L


    @EventListener(ApplicationReadyEvent::class)
    @Scheduled(
        initialDelayString = "\${outbox.partition.initial-delay:PT10M}",
        fixedDelayString = "\${outbox.partition.fixed-delay:PT10M}"
    )
    fun ensureCurrentAndNextScheduled() {
        val now = LocalDateTime.now(clock)
        taskScheduler.execute {
            val start = LocalDateTime.now(clock)
            ensureCurrentAndNext()
            val end = LocalDateTime.now(clock)
            val durationMs = ChronoUnit.MILLIS.between(start, end)  // long
            logger.info("Partition check complete started at $start, ended at $end, duration: $durationMs ")

        }
    }


    fun ensureCurrentAndNext() {
        val now = LocalDateTime.now(clock)
        val start = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        // current window
        ensurePartitionExists(start, start.plusMinutes(PARTITION_SIZE_MIN))
        // next window
        ensurePartitionExists(start.plusMinutes(PARTITION_SIZE_MIN), start.plusMinutes(PARTITION_SIZE_MIN * 2))
    }

    // --- Partition creation (idempotent with IF NOT EXISTS) ---
    private fun ensurePartitionExists(from: LocalDateTime, to: LocalDateTime) {
        val partitionName = "outbox_event_${from.format(partitionFormatter)}"
        val fromStr = from.format(sqlFormatter)
        val toStr = to.format(sqlFormatter)

        val mdcContext = mapOf("partitionName" to partitionName, "from" to fromStr, "to" to toStr)
        LogContext.with(mdcContext) {
            val sql = """
                CREATE TABLE IF NOT EXISTS $partitionName
                PARTITION OF outbox_event
                FOR VALUES FROM ('$fromStr') TO ('$toStr');
            """.trimIndent()

            try {
                jdbcTemplate.execute(sql)
                logger.info("Ensured partition exists: $partitionName for [$fromStr, $toStr)")
            } catch (e: Exception) {
                logger.error("Error creating partition $partitionName: ${e.message}", e)
            } finally {
            }
        }
    }

    // Partition creation logic for current window
    fun ensurePartitionForNow() {
        val now = LocalDateTime.now(clock)
        val currentPartitionStartTime = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val currentPartitionEndTime = currentPartitionStartTime.plusMinutes(PARTITION_SIZE_MIN)
        ensurePartitionExists(currentPartitionStartTime, currentPartitionEndTime)
    }

    // Partition creation logic for next window
    fun ensurePartitionForNext() {
        val now = LocalDateTime.now(clock)
        val windowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val nextWindowStart = windowStart.plusMinutes(PARTITION_SIZE_MIN)
        val nextWindowEnd = nextWindowStart.plusMinutes(PARTITION_SIZE_MIN)
        ensurePartitionExists(nextWindowStart, nextWindowEnd)
    }


    private fun floorToPartitionStart(t: LocalDateTime): LocalDateTime =
        t.withMinute(((t.minute / PARTITION_SIZE_MIN).toInt()) * PARTITION_SIZE_MIN.toInt()).withSecond(0).withNano(0)


    @Scheduled(fixedDelay = 21 * 60 * 1000)
    fun pruneOldPartitionsScheduled() {
        taskScheduler.execute {
            val start = LocalDateTime.now(clock)
            pruneOldPartitions()
            val end = LocalDateTime.now(clock)
            val durationMs = ChronoUnit.MILLIS.between(start, end)  // long
            logger.info("Partition prune complete started at $start, ended at $end, duration: $durationMs ")
        }
    }

    fun pruneOldPartitions() {
        prunePartitionsSafe()
    }

    fun prunePartitionsSafe() {
        val now = LocalDateTime.now(clock)
        val currWindowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        MDC.put("currWindowStart", currWindowStart.toString())
        val mdcContext = mapOf(
            "currWindowStart" to currWindowStart.toString(),
        )
        LogContext.with(mdcContext) {
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
            } catch (e: Exception) {
                logger.error("Partition prune failed: ${e.message}", e)
            }
        }

    }


    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    fun vacuumOldPartitionsWithNewRowsScheduled() {
        taskScheduler.execute {
            val start = LocalDateTime.now(clock)


            vacuumOldPartitionsWithNewRows()
            val end = LocalDateTime.now(clock)
            val durationMs = ChronoUnit.MILLIS.between(start, end)  // long
            logger.info("Partition vacuum check complete started at $start, ended at $end, duration: $durationMs ")
        }
    }


    fun vacuumOldPartitionsWithNewRows() {
        val now = LocalDateTime.now(clock)
        val partitionFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")
        val currWindowStart = now.withMinute((now.minute / 30) * 30).withSecond(0).withNano(0)
        val nextWindowStart = currWindowStart.plusMinutes(PARTITION_SIZE_MIN)

        val currPartitionName = "outbox_event_${currWindowStart.format(partitionFormatter)}"
        val nextPartitionName = "outbox_event_${nextWindowStart.format(partitionFormatter)}"

        logger.info("Vacuum check for partitions (skipping: $currPartitionName and $nextPartitionName)")

        val partitionNames: List<String> = jdbcTemplate.queryForList(
            """
        SELECT inhrelid::regclass::text AS partition_name
        FROM pg_inherits
        WHERE inhparent = 'outbox_event'::regclass
        """, String::class.java
        )

        for (partitionName in partitionNames) {
            if (partitionName == currPartitionName || partitionName == nextPartitionName) continue

            val newCount: Int? = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM $partitionName WHERE status = 'NEW'", Int::class.java
            )
            if ((newCount ?: 0) > 0) {
                logger.info("VACUUM: $partitionName ($newCount NEW rows remaining)")
                try {
                    jdbcTemplate.execute("VACUUM $partitionName")
                } catch (ex: Exception) {
                    logger.warn("VACUUM failed for $partitionName: ${ex.message}", ex)
                }
            }
        }
    }
}

@Configuration
class AsyncConfig {
    @Bean("partitionCreationExecutor")
    fun partitionCreationExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        setThreadNamePrefix("partition-creation-")
        initialize()
    }

    @Bean("partitionRemovalExecutor")
    fun partitionRemovalExecutor(): ThreadPoolTaskExecutor = ThreadPoolTaskExecutor().apply {
        corePoolSize = 2
        maxPoolSize = 2
        setThreadNamePrefix("partition-removal-")
        initialize()
    }
}
