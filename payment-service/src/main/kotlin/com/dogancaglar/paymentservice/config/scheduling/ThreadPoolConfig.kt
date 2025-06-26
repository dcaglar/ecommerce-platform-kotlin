package com.dogancaglar.paymentservice.config.scheduling

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component

@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry, private val decorator: MdcTaskDecorator) {
    @Bean("outboxTaskScheduler")
    fun outboxTaskScheduler(
        @Value("\${outbox-dispatcher.pool-size:8}") poolSize: Int
    ): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize
        scheduler.setThreadNamePrefix("outbox-dispatcher-pool-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.setTaskDecorator(decorator)
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
        scheduler.setTaskDecorator(decorator)
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
        executor.setThreadNamePrefix("payment-order-executor-")
        executor.setTaskDecorator(decorator)
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
        executor.setTaskDecorator(decorator)
        executor.setThreadNamePrefix("payment-order-retry-executor-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean
    fun externalPspExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 16      // One per partition/consumer thread
        executor.maxPoolSize = 16       // Allow for some burst/buffering
        executor.setQueueCapacity(32)  // Buffer for transient spikes (tune per your workload)
        executor.setTaskDecorator(decorator)
        executor.setThreadNamePrefix("payment-order-retry-executor-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }


}


@Component
class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val context = org.slf4j.MDC.getCopyOfContextMap()
        return Runnable {
            val previous = org.slf4j.MDC.getCopyOfContextMap()
            if (context != null) org.slf4j.MDC.setContextMap(context) else org.slf4j.MDC.clear()
            try {
                runnable.run()
            } finally {
                if (previous != null) org.slf4j.MDC.setContextMap(previous) else org.slf4j.MDC.clear()
            }
        }
    }
}
