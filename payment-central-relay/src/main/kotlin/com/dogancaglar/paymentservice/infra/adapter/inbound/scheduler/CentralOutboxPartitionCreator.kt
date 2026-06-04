package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler

import com.dogancaglar.common.logging.EventLogContext
import com.dogancaglar.common.time.Utc
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


import com.dogancaglar.common.db.partitioning.AbstractOutboxPartitionCreator

@Component
class CentralOutboxPartitionCreator(
    jdbcTemplate: JdbcTemplate,
    @param:Qualifier("centralOutboxEventPartitionMaintenanceScheduler") private val taskScheduler: ThreadPoolTaskScheduler
) : AbstractOutboxPartitionCreator(jdbcTemplate) {

    @EventListener(ApplicationReadyEvent::class)
    @Scheduled(
        initialDelay = 30000,
        fixedDelayString = "\${outbox-partition.fixed-delay:PT10M}"
    )
    fun ensureCurrentAndNextScheduled() {
        taskScheduler.execute {
            val start = Utc.nowLocalDateTime()
            ensureCurrentAndNext()
            val end = Utc.nowLocalDateTime()
            val durationMs = ChronoUnit.MILLIS.between(start, end)
            logger.debug("Central partition check complete started at $start, ended at $end, duration: $durationMs ")
        }
    }

    @Scheduled(initialDelay = 45000, fixedDelay = 21 * 60 * 1000)
    fun pruneOldPartitionsScheduled() {
        taskScheduler.execute {
            val start = Utc.nowLocalDateTime()
            pruneOldPartitions()
            val end = Utc.nowLocalDateTime()
            val durationMs = ChronoUnit.MILLIS.between(start, end)
            logger.debug("Central partition prune complete started at $start, ended at $end, duration: $durationMs ")
        }
    }

    @Scheduled(fixedDelay = 30 * 60 * 1000, initialDelay = 15 * 60 * 1000)
    fun vacuumOldPartitionsWithNewRowsScheduled() {
        taskScheduler.execute {
            val start = Utc.nowLocalDateTime()
            vacuumOldPartitionsWithNewRows()
            val end = Utc.nowLocalDateTime()
            val durationMs = ChronoUnit.MILLIS.between(start, end)
            logger.debug("Central partition vacuum check complete started at $start, ended at $end, duration: $durationMs ")
        }
    }
}

@Configuration
class CentralOutboxPartitionCreatorConfig {
    @Bean("centralOutboxEventPartitionMaintenanceScheduler")
    fun centralOutboxEventPartitionMaintenanceScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 1
        scheduler.setThreadNamePrefix("central-outbox-maintenance-pool-")
        scheduler.initialize()
        return scheduler
    }
}
