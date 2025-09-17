package com.dogancaglar.paymentservice.adapter.inbound.rest

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.*

@Component
class TraceFilter : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(TraceFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain
    ) {
        val traceId = request.getHeader("X-Trace-Id") ?: UUID.randomUUID().toString()
        MDC.put("traceId", traceId)

        val path = request.requestURI
        val startNs = System.nanoTime()

        if (path == "/payments") {
            log.debug("💵 HTTP PAYMENT REQUEST START {} {} traceId={}", request.method, path, traceId)
        } else {
            log.debug("HTTP OTHER REQUEST START {} {} traceId={}", request.method, path, traceId)
        }

        var committedAtNs = 0L
        var flushedAtNs = 0L
        var committedBeforeFlush = false
        var contentLengthHdr: String? = null
        var bufferSizeAtCommit: Int? = null

        try {
            chain.doFilter(request, response)
        } catch (ex: Exception) {
            log.error("❌ Unhandled exception BEFORE ControllerAdvice: ${ex.message}", ex)
            throw ex
        } finally {
            // point when your handler + MVC view rendering finished
            committedAtNs = System.nanoTime()
            committedBeforeFlush = response.isCommitted
            contentLengthHdr = response.getHeader("Content-Length")
            bufferSizeAtCommit = try { response.bufferSize } catch (_: IllegalStateException) { null }

            // ensure bytes are pushed; this is where network back-pressure would show up
            try {
                response.flushBuffer()
            } catch (flushEx: Exception) {
                log.warn("flushBuffer() threw: ${flushEx.javaClass.simpleName}: ${flushEx.message} traceId={}", traceId)
            } finally {
                flushedAtNs = System.nanoTime()
            }

            val totalMs = (flushedAtNs - startNs) / 1_000_000
            val appMs   = (committedAtNs - startNs) / 1_000_000
            val flushMs = (flushedAtNs - committedAtNs) / 1_000_000

            val msg = if (path == "/payments") "💵💵 HTTP PAYMENT REQUEST END" else "HTTP OTHER REQUEST END"

            log.debug(
                "🚀 {} {} {} status={} total={}ms app={}ms flush={}ms committed={} contentLength={} bufferSize={} traceId={}",
                request.method, path, msg, response.status, totalMs, appMs, flushMs,
                committedBeforeFlush, contentLengthHdr ?: "n/a", bufferSizeAtCommit ?: "n/a", traceId
            )

            // specific slow flags
            if (totalMs > 2000 && path == "/payments") {
                log.warn("⚠️ SLOW REQUEST total={}ms app={}ms flush={}ms {} {} traceId={}",
                    totalMs, appMs, flushMs, request.method, path, traceId)
            }
            if (flushMs > 1000) {
                log.warn("⚠️ SLOW_FLUSH flush={}ms (bytes={} bufferSize={} committed={}) {} {} traceId={}",
                    flushMs, contentLengthHdr ?: "n/a", bufferSizeAtCommit ?: "n/a",
                    committedBeforeFlush, request.method, path, traceId
                )
            }

            MDC.clear()
        }
    }
}