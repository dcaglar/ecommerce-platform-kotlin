package com.dogancaglar.paymentservice.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Component
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


    @Bean("createPaymentIntentExecutor")
    fun createPaymentIntentExecutor(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 250          // Align with Tomcat max-threads
            maxPoolSize = 250
            queueCapacity = 50       // Minimal queue to ensure low latency
            setThreadNamePrefix("po-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.DiscardPolicy())
            initialize()
        }


    @Bean("authorizePaymentIntentExecutor")
    fun authorizePaymentIntentExecutor(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 80          // Align with Tomcat max-threads
            maxPoolSize = 200
            queueCapacity = 50       // Minimal queue to ensure low latency
            setThreadNamePrefix("po-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.DiscardPolicy())
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

     // threaad pool for background completion

    @Bean("resilientExecutor")
    fun resilientExecutor(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 200
            maxPoolSize = 200
            queueCapacity = 500
            setThreadNamePrefix("resilient-callback-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
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

