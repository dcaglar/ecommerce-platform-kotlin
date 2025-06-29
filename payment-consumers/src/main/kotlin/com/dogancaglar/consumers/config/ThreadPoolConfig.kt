package com.dogancaglar.consumers.config

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component


@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry, private val decorator: MdcTaskDecorator) {

    @Bean
    fun paymentOrderExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8       // Match Kafka consumer concurrency (partitions)
        executor.maxPoolSize = 16
        executor.setQueueCapacity(200)    // Optional small buffer
        executor.setThreadNamePrefix("payment-order-executor-")
        executor.setTaskDecorator(decorator)
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean
    fun paymentOrderRetryExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8      // One per partition/consumer thread
        executor.maxPoolSize = 16       // Allow for some burst/buffering
        executor.setQueueCapacity(200)  // Buffer for transient spikes (tune per your workload)
        executor.setTaskDecorator(decorator)
        executor.setThreadNamePrefix("payment-order-retry-executor-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.initialize()
        return executor
    }

    @Bean
    fun externalPspExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 8      // One per partition/consumer thread
        executor.maxPoolSize = 16       // Allow for some burst/buffering
        executor.setQueueCapacity(40)  // Buffer for transient spikes (tune per your workload)
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

