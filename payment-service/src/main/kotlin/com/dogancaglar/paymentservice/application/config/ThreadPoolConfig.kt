package com.dogancaglar.paymentservice.application.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component


@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry, private val decorator: MdcTaskDecorator) {
    @Bean("outboxTaskScheduler")
    fun outboxTaskScheduler(
        @Value("\${outbox-dispatcher.pool-size:8}") poolSize: Int,
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

    @Bean
    fun outboxEventPartitionMaintenanceScheduler(): ThreadPoolTaskScheduler {
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize =
            4 // or we ca increase it maybe 2 or 4 or 8, but this is dedicated to partition maintenance job
        scheduler.setTaskDecorator(decorator)
        scheduler.setThreadNamePrefix("outbox-mainenance-pool-")
        scheduler.initialize()
        return scheduler
    }

    @Bean("taskScheduler")
    fun defaultSpringScheduler(decorator: MdcTaskDecorator): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 2
            setThreadNamePrefix("spring-scheduled-")
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

