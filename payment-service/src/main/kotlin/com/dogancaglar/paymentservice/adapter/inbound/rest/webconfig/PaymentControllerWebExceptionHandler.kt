package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.domain.exception.PspInvalidPaymentException
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.application.service.IdempotencyConflictClientException
import com.dogancaglar.paymentservice.domain.exception.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataAccessException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.CannotGetJdbcConnectionException
import org.springframework.transaction.CannotCreateTransactionException
import org.springframework.transaction.TransactionTimedOutException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler
import java.util.concurrent.TimeoutException
import java.lang.Exception

@RestControllerAdvice
class PaymentControllerWebExceptionHandler : ResponseEntityExceptionHandler() {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Standard Error Response Body
     */
    data class ErrorResponse(
        val timestamp: String = Utc.nowInstant().toString(),
        val status: Int,
        val error: String,
        val message: String?,
        val path: String?,
        val traceId: String? = null
    )

    // --- Standard Spring Override ---

    override fun handleExceptionInternal(
        ex: Exception,
        body: Any?,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest
    ): ResponseEntity<Any>? {
        val servletRequest = (request as ServletWebRequest).request
        val message = (body as? Map<*, *>)?.get("message")?.toString() ?: ex.localizedMessage
        val errorBody = createBody(HttpStatus.valueOf(status.value()), message, servletRequest)
        
        log.warn("Spring MVC Error at {}: {}", servletRequest.requestURI, message)
        return super.handleExceptionInternal(ex, errorBody, headers, status, request)
    }

    // --- 504 Gateway Timeout ---

    @ExceptionHandler(
        TimeoutException::class,
        TransactionTimedOutException::class,
        QueryTimeoutException::class
    )
    fun handleGatewayTimeouts(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Gateway Timeout at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.GATEWAY_TIMEOUT, request, "Request timed out")
    }

    @ExceptionHandler(PSQLException::class)
    fun handlePostgreSqlException(ex: PSQLException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        return when (ex.sqlState) {
            PSQLState.QUERY_CANCELED.state -> {
                log.error("Database query canceled (timeout) at {}: {}", request.requestURI, causeSummary(ex))
                respond(HttpStatus.GATEWAY_TIMEOUT, request, "Database timeout")
            }
            "55P03", "40P01" -> { // Lock/Deadlock
                log.warn("Database conflict (lock/deadlock) at {}: {}", request.requestURI, causeSummary(ex))
                val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
                respond(HttpStatus.CONFLICT, request, "Database conflict, please retry", headers)
            }
            else -> {
                log.error("Unhandled PostgreSQL error ({}) at {}: {}", ex.sqlState, request.requestURI, causeSummary(ex))
                respond(HttpStatus.INTERNAL_SERVER_ERROR, request, "Internal database error")
            }
        }
    }

    // --- 503 Service Unavailable (Transient Faults) ---

    @ExceptionHandler(
        PspTransientException::class,
        DataAccessException::class,
        CannotCreateTransactionException::class,
        CannotGetJdbcConnectionException::class
    )
    fun handleTransientFaults(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Service Unavailable (Transient) at {}: {}", request.requestURI, causeSummary(ex))
        val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
        val message = if (ex is CannotCreateTransactionException || ex is CannotGetJdbcConnectionException) 
            "Database connection pool exhausted" else "Service temporarily unavailable"
        return respond(HttpStatus.SERVICE_UNAVAILABLE, request, message, headers)
    }

    // --- 409 Conflict (Business Scenarios) ---

    @ExceptionHandler(
        PaymentIntentNotReadyException::class,
        PaymentNotReadyException::class
    )
    fun handleBusinessConflicts(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Business Conflict at {}: {}", request.requestURI, ex.message)
        val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "2") }
        return respond(HttpStatus.CONFLICT, request, ex.message, headers)
    }

    // --- 400 Bad Request (Validation / Permanent Faults) ---

    @ExceptionHandler(
        PspPermanentException::class,
        PspInvalidPaymentException::class,
        ConstraintViolationException::class
    )
    fun handleValidationAndPermanentFaults(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Bad Request at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.BAD_REQUEST, request, ex.message)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.message ?: "Invalid request"
        return if (message.contains("not found", ignoreCase = true)) {
            log.warn("Resource Not Found at {}: {}", request.requestURI, message)
            respond(HttpStatus.NOT_FOUND, request, message)
        } else {
            log.warn("Illegal Argument at {}: {}", request.requestURI, message)
            respond(HttpStatus.BAD_REQUEST, request, message)
        }
    }

    // --- 422 Unprocessable Entity ---

    @ExceptionHandler(IdempotencyConflictClientException::class)
    fun handleIdempotencyConflict(ex: IdempotencyConflictClientException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.warn("Idempotency Conflict at {}: {}", request.requestURI, ex.message)
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, request, ex.message)
    }

    // --- 500 Internal Server Error & Fallback ---

    @ExceptionHandler(PspUnknownException::class)
    fun handlePspUnknown(ex: PspUnknownException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unknown PSP Error at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, request, "Critical PSP error")
    }

    @ExceptionHandler(Exception::class)
    fun handleAll(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        log.error("Unhandled Exception at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, request, "An unexpected error occurred")
    }

    // --- Helpers ---

    private fun respond(
        status: HttpStatus,
        request: HttpServletRequest,
        msg: String?,
        headers: HttpHeaders? = null
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity(createBody(status, msg, request), headers, status)
    }

    private fun createBody(status: HttpStatus, msg: String?, request: HttpServletRequest) = ErrorResponse(
        status = status.value(),
        error = status.reasonPhrase,
        message = msg,
        path = request.requestURI,
        traceId = MDC.get("traceId") ?: request.getHeader("X-Trace-Id")
    )

    private fun causeSummary(t: Throwable): String {
        return generateSequence(t) { it.cause }
            .take(5)
            .joinToString(" -> ") { "${it::class.simpleName}: ${it.message?.take(100)}" }
    }
}