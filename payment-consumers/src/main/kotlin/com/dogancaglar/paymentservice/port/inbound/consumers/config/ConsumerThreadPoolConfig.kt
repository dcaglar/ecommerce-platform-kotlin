package com.dogancaglar.paymentservice.port.inbound.consumers.config

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Component
import java.util.concurrent.ThreadPoolExecutor


@Configuration
class ThreadPoolConfig(private val meterRegistry: MeterRegistry, private val decorator: MdcTaskDecorator) {

    @Bean("paymentOrderPspPool")
    fun paymentOrderPspPool(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4          // match per-pod listener concurrency
            maxPoolSize = 6
            queueCapacity = 16       // or 16; keeps back-pressure tight
            setThreadNamePrefix("po-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            initialize()
        }

    @Bean("paymentRetryPspPool")
    fun paymentRetryPspPool(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 4
            maxPoolSize = 6
            queueCapacity = 16
            setThreadNamePrefix("pr-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            initialize()
        }


    @Bean("paymentStatusPspPool")
    fun paymentStatusPspPool(decorator: TaskDecorator): ThreadPoolTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            corePoolSize = 1
            maxPoolSize = 2
            queueCapacity = 4
            setThreadNamePrefix("ps-psp-")
            setTaskDecorator(decorator)
            setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
            setWaitForTasksToCompleteOnShutdown(true)
            initialize()
        }

    @Bean
    fun externalPspExecutorPoolConfig(): ThreadPoolTaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 1      // One per partition/consumer thread
        executor.maxPoolSize = 2       // Allow for some burst/buffering
        executor.setQueueCapacity(4)  // Buffer for transient spikes (tune per your workload)
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

