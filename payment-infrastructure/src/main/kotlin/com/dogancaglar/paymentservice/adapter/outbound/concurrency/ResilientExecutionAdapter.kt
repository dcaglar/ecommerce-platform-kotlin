package com.dogancaglar.paymentservice.adapter.outbound.concurrency

import com.dogancaglar.paymentservice.ports.outbound.ResilientExecutionPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

@Component
class ResilientExecutionAdapter(
    @Qualifier("pspCallbackExecutor") private val executor: Executor
) : ResilientExecutionPort {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun <T> executeWithTimeoutAndBackgroundFallback(
        primaryTask: CompletableFuture<T>,
        timeoutMs: Long,
        onTimeoutFallback: () -> T,
        onBackgroundSuccess: (T) -> Unit,
        onBackgroundFailure: (Throwable) -> Unit
    ): T {
        try {
            // Try to get the result within the timeout
            return primaryTask.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (e: TimeoutException) {
            logger.info("Task timed out after {}ms, switching to background execution", timeoutMs)
            
            // Schedule background completion handlers
            primaryTask.whenCompleteAsync({ result, error ->
                try {
                    if (error != null) {
                        // Unwrap CompletionException if present
                        val cause = if (error is java.util.concurrent.CompletionException) error.cause ?: error else error
                        onBackgroundFailure(cause)
                    } else {
                        onBackgroundSuccess(result)
                    }
                } catch (e: Exception) {
                    logger.error("Error executing background callbacks", e)
                }
            }, executor)

            // Return the fallback value immediately
            return onTimeoutFallback()
        } catch (e: ExecutionException) {
            // If the task failed immediately (before timeout), rethrow the cause
            throw e.cause ?: e
        }
    }
}
