package com.dogancaglar.paymentservice.adapter.inbound.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

/**
 * Ensures every incoming HTTP request starts with a traceId in MDC.
 * If the caller already supplies one in the `X-Trace-Id` header,
 * we reuse it; otherwise we generate a new UUID.
 *
 * Controller and service logs can rely on MDC.get("traceId") being present.
 * The value is cleared when the request thread completes.
 */
@Component
class TraceFilter : OncePerRequestFilter() {
    private val traceLogger = LoggerFactory.getLogger(TraceFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        MDC.put("traceId", traceId)
        val start = System.currentTimeMillis()
        traceLogger.info("➡️ HTTP START {} {} traceId={}", request.method, request.requestURI, traceId)
        try {
            filterChain.doFilter(request, response)
        } catch (ex: Exception) {
            logger.error("Unhandled exception BEFORE Spring MVC: ${ex.message}", ex)
            throw ex // rethrow to let Spring handle with ControllerAdvice
        } finally {
            val duration = System.currentTimeMillis() - start
            traceLogger.info(
                "⬅️ HTTP END {} {} status={} duration={}ms traceId={}",
                request.method, request.requestURI, response.status, duration, traceId
            )
            if (duration > 2000) {
                traceLogger.warn(
                    "⚠️ SLOW REQUEST: {} {} took {}ms traceId={}",
                    request.method, request.requestURI, duration, traceId
                )
            }
            MDC.clear()          // keep the thread clean for the next request
        }
    }
}