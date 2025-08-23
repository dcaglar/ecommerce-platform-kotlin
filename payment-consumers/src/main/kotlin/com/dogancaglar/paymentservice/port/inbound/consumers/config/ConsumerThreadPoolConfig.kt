package com.dogancaglar.paymentservice.port.inbound.consumers.config

import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.MDC
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
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
            setRejectedExecutionHandler(ThreadPoolExecutor.AbortPolicy())
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


    @Bean("retryDispatcherScheduler")
    fun retryDispatcherScheduler(decorator: MdcTaskDecorator) =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1                      // one runner is enough; raise if you really want concurrent batches
            setThreadNamePrefix("retry-dispatcher-")
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

