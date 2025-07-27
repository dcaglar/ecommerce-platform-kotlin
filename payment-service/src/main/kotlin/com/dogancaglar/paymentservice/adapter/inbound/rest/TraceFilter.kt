package com.dogancaglar.paymentservice.adapter.inbound.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
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

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        MDC.put("traceId", traceId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()          // keep the thread clean for the next request
        }
    }
}