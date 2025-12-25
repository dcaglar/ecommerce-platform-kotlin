package com.dogancaglar.paymentservice.application.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
import java.util.concurrent.ExecutorService
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ThreadPoolExecutor


@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry, private val decorator: MdcTaskDecorator) {
    @Bean("outboxJobTaskScheduler")
    fun outboxTaskScheduler(
        @Value("\${outbox-dispatcher.pool-size:2}") poolSize: Int,
    ): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = poolSize
        scheduler.setThreadNamePrefix("outbox-dispatcher-pool-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.setTaskDecorator(decorator)
        // Metrics with a unique tag!
        meterRegistry.gauge(
            "scheduler_outbox_active_threads",
            listOf(Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.activeCount.toDouble() }

        meterRegistry.gauge(
            "scheduler_outbox_pool_size_threads",
            listOf(Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.poolSize.toDouble() }



        meterRegistry.gauge(
            "scheduler_outbox_queue_size",
            listOf(Tag.of("name", "outbox-dispatch")),
            scheduler
        ) { it.scheduledThreadPoolExecutor.queue.size.toDouble() }

        return scheduler
    }


    @Bean("pspAuthExecutor")
    fun pspAuthExecutor(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 8          // match per-pod listener concurrency
            maxPoolSize = 8
            queueCapacity = 64       // or 16; keeps back-pressure tight
            setThreadNamePrefix("po-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
            initialize()
        }


    @Bean
    fun outboxEventPartitionMaintenanceScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize =
            2 // or we ca increase it maybe 2 or 4 or 8, but this is dedicated to partition maintenance job
        scheduler.setTaskDecorator(decorator)
        scheduler.setThreadNamePrefix("outbox-mainenance-pool-")
        scheduler.initialize()
        return scheduler
    }

    // ... existing beans ...

    @Bean("pspCallbackExecutor")
    fun pspCallbackExecutor(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 4
            queueCapacity = 256
            setThreadNamePrefix("po-psp-callback-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
            initialize()
        }

    @Bean("taskScheduler")
    fun defaultSpringScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("payment-service-spring-scheduled-")
            setTaskDecorator(decorator)
            setWaitForTasksToCompleteOnShutdown(true)
            initialize()
        }


}

@Component
class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val context = MDC.getCopyOfContextMap()
        return Runnable {
            val previous = MDC.getCopyOfContextMap()
            if (context != null) MDC.setContextMap(context) else MDC.clear()
            try {
                runnable.run()
            } finally {
                if (previous != null) MDC.setContextMap(previous) else MDC.clear()
            }
        }
    }
}

