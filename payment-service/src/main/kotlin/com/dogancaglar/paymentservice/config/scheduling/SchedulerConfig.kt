package com.dogancaglar.paymentservice.config.scheduling

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class SchedulerConfig(private val meterRegistry: MeterRegistry) {
    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 4 // adjust as needed
        scheduler.setThreadNamePrefix("scheduled-task-")
        // Register active thread count gauge
        meterRegistry.gauge(
            "scheduler_active_threads",
            listOf(io.micrometer.core.instrument.Tag.of("name", "scheduled-task")),
            scheduler
        ) { it.activeCount.toDouble() }

        // Register pool size gauge
        meterRegistry.gauge(
            "scheduler_pool_size_threads",
            listOf(io.micrometer.core.instrument.Tag.of("name", "scheduled-task")),
            scheduler
        ) { it.poolSize.toDouble() }

        // Register scheduled task count gauge
        meterRegistry.gauge(
            "scheduler_scheduled_task_count",
            listOf(io.micrometer.core.instrument.Tag.of("name", "scheduled-task")),
            scheduler
        ) { it.scheduledThreadPoolExecutor.queue.size.toDouble() }


        return scheduler
    }
}