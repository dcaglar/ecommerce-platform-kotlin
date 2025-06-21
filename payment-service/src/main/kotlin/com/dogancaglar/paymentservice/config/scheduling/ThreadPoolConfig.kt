package com.dogancaglar.paymentservice.config.scheduling

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler

@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry) {
    @Bean("outboxTaskScheduler")
    fun outboxTaskScheduler(
        @Value("\${outbox-dispatcher.pool-size:8}") poolSize: Int
    ): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize
        scheduler.setThreadNamePrefix("scheduled-task-outbox-dispatched-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)

        // Metrics with a unique tag!
        meterRegistry.gauge(
            "scheduler_outbox_active_threads",
            listOf(io.micrometer.core.instrument.Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.activeCount.toDouble() }

        meterRegistry.gauge(
            "scheduler_outbox_pool_size_threads",
            listOf(io.micrometer.core.instrument.Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.poolSize.toDouble() }

        meterRegistry.gauge(
            "scheduler_outbox_queue_size",
            listOf(io.micrometer.core.instrument.Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.scheduledThreadPoolExecutor.queue.size.toDouble() }

        return scheduler
    }

    @Bean
    fun taskScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 2 // or any number you want
        scheduler.setThreadNamePrefix("my-scheduled-task-spring")
        scheduler.initialize()
        return scheduler
    }


    @Bean
    fun paymentOrderExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 16       // Match Kafka consumer concurrency (partitions)
        executor.maxPoolSize = 16
        executor.setQueueCapacity(32)    // Optional small buffer
        executor.setThreadNamePrefix("payment-order-psp-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean
    fun paymentOrderRetryExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 16      // One per partition/consumer thread
        executor.maxPoolSize = 16       // Allow for some burst/buffering
        executor.setQueueCapacity(32)  // Buffer for transient spikes (tune per your workload)
        executor.setThreadNamePrefix("payment-order-retry-executor-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }
}
