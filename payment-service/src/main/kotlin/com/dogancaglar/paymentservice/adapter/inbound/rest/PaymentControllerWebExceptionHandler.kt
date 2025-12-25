package com.dogancaglar.paymentservice.adapter.inbound.rest

import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.dao.DataAccessException
import org.springframework.dao.QueryTimeoutException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import org.springframework.transaction.TransactionTimedOutException
import org.postgresql.util.PSQLException
import org.postgresql.util.PSQLState
import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.domain.exception.PaymentIntentNotReadyException
import com.dogancaglar.paymentservice.domain.exception.PaymentNotReadyException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException

@RestControllerAdvice
class PaymentControllerWebExceptionHandler {

    private val logger = LoggerFactory.getLogger(PaymentControllerWebExceptionHandler::class.java)

    // keep log lines short
    private val MAX_MSG = 300
    private fun trunc(s: String?): String =
        (s ?: "").let { if (it.length > MAX_MSG) it.take(MAX_MSG) + "…" else it }

    private fun causeSummary(t: Throwable): String =
        generateSequence(t) { it.cause }
            .joinToString(" -> ") { "${it::class.simpleName}:${trunc(it.message)}" }

    data class ErrorResponse(
        val timestamp: String = Utc.nowInstant().toString(),
        val status: Int,
        val error: String,
        val message: String?,
        val path: String?,
        val traceId: String? = null
    )

    private fun traceId(request: HttpServletRequest): String? =
        MDC.get("traceId") ?: request.getHeader("X-Trace-Id")

    private fun body(
        status: HttpStatus,
        msg: String?,
        request: HttpServletRequest
    ) = ErrorResponse(
        status = status.value(),
        error = status.reasonPhrase,
        message = msg,
        path = request.requestURI,
        traceId = traceId(request)
    )

    private fun <T> respond(
        status: HttpStatus,
        request: HttpServletRequest,
        msg: String? = null,
        headers: HttpHeaders? = null
    ): ResponseEntity<T> {
        @Suppress("UNCHECKED_CAST")
        return ResponseEntity(body(status, msg, request) as T, headers, status)
    }

    // -------- Specific resilience/timeouts/db contention --------

    @ExceptionHandler(
        TimeoutException::class,
        TransactionTimedOutException::class,
        QueryTimeoutException::class
    )
    fun handleServerTimeouts(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Timeout at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.GATEWAY_TIMEOUT, request, "Timed out while processing the request")
    }


    @ExceptionHandler(PSQLException::class)
    fun handlePSQL(ex: PSQLException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val state = ex.sqlState
        return when (state) {
            PSQLState.QUERY_CANCELED.state -> {
                logger.warn("PSQL query canceled at {}: {}", request.requestURI, causeSummary(ex))
                respond(HttpStatus.GATEWAY_TIMEOUT, request, "Database canceled the query (timeout)")
            }
            "55P03" -> { // lock_not_available
                logger.warn("PSQL lock timeout at {}: {}", request.requestURI, causeSummary(ex))
                val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
                ResponseEntity(body(HttpStatus.CONFLICT, "Database lock contention", request), headers, HttpStatus.CONFLICT)
            }
            "40P01" -> { // deadlock_detected
                logger.warn("PSQL deadlock at {}: {}", request.requestURI, causeSummary(ex))
                val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
                ResponseEntity(body(HttpStatus.CONFLICT, "Database deadlock detected", request), headers, HttpStatus.CONFLICT)
            }
            else -> {
                // still no stack; just a one-line summary
                logger.error("PSQL error (state={}) at {}: {}", state, request.requestURI, causeSummary(ex))
                respond(HttpStatus.INTERNAL_SERVER_ERROR, request, "Database error")
            }
        }
    }

    // -------- Validation / routing --------

    @ExceptionHandler(IdempotencyConflictException::class)
    fun handleNoResourceFound(ex: IdempotencyConflictException, request: HttpServletRequest)
            = respond<ErrorResponse>(HttpStatus.CONFLICT, request, trunc(ex.localizedMessage))



    @ExceptionHandler(PaymentIntentNotReadyException::class)
    fun handlePaymentIntentNotReady(ex: PaymentIntentNotReadyException, request: HttpServletRequest): ResponseEntity<ErrorResponse>{
        val headers = HttpHeaders().apply {
            add(HttpHeaders.RETRY_AFTER, "2")
        }
           val respond = respond<ErrorResponse>(HttpStatus.CONFLICT, request, trunc(ex.localizedMessage),
                headers = headers
    )
        return respond
    }





    @ExceptionHandler(PaymentNotReadyException::class)
    fun handlePaymentNotReady(ex: PaymentNotReadyException, request: HttpServletRequest)
            = respond<ErrorResponse>(HttpStatus.CONFLICT, request, trunc(ex.localizedMessage))

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(ex: NoResourceFoundException, request: HttpServletRequest)
            = respond<ErrorResponse>(HttpStatus.NOT_FOUND, request, trunc(ex.localizedMessage))

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFound(ex: NoHandlerFoundException, request: HttpServletRequest)
            = respond<ErrorResponse>(HttpStatus.NOT_FOUND, request, trunc(ex.message))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException, request: HttpServletRequest)
            = respond<ErrorResponse>(HttpStatus.BAD_REQUEST, request, trunc(ex.localizedMessage))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationExceptions(ex: MethodArgumentNotValidException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errors = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return respond(HttpStatus.BAD_REQUEST, request, trunc(errors))
    }

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val errors = ex.constraintViolations.joinToString(", ") { "${it.propertyPath}: ${it.message}" }
        return respond(HttpStatus.BAD_REQUEST, request, trunc(errors))
    }

    // -------- Business Logic Validation Errors (400 Bad Request) --------
    
    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        val message = ex.message ?: "Invalid request"
        
        // Check if it's a "not found" error (account not found, etc.)
        if (message.contains("not found", ignoreCase = true) || 
            message.contains("Account not found", ignoreCase = true)) {
            logger.warn("Resource not found at {}: {}", request.requestURI, trunc(message))
            return respond(HttpStatus.NOT_FOUND, request, trunc(message))
        }
        
        // Otherwise treat as validation error (400)
        logger.debug("Validation error at {}: {}", request.requestURI, trunc(message))
        return respond(HttpStatus.BAD_REQUEST, request, trunc(message))
    }

    // -------- DataAccess (mapped, no stack) --------
    @ExceptionHandler(DataAccessException::class)
    fun handleDataAccess(ex: DataAccessException, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("DataAccessException at {}: {}", request.requestURI, causeSummary(ex))
        // Log the root cause for debugging
        val rootCause = ex.cause
        if (rootCause != null) {
            logger.error("Root cause: {} - {}", rootCause::class.simpleName, rootCause.message)
        }
        // Check if it's a connection/timeout issue
        val message = when {
            ex.message?.contains("timeout", ignoreCase = true) == true -> "Database operation timed out"
            ex.message?.contains("connection", ignoreCase = true) == true -> "Database connection error"
            else -> "Database temporarily unavailable"
        }
        return respond(HttpStatus.SERVICE_UNAVAILABLE, request, message)
    }

    // -------- Pool/exhaustion fast-path (no stack) --------
    @ExceptionHandler(org.springframework.transaction.CannotCreateTransactionException::class)
    fun handleCannotCreateTx(
        ex: org.springframework.transaction.CannotCreateTransactionException,
        req: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Cannot create transaction at {}: {}", req.requestURI, causeSummary(ex))
        val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
        return ResponseEntity(
            body(HttpStatus.SERVICE_UNAVAILABLE, "Database connection pool exhausted", req),
            headers,
            HttpStatus.SERVICE_UNAVAILABLE
        )
    }


    // -------- Generic fallback (no stack) --------
    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: HttpServletRequest): ResponseEntity<ErrorResponse> {
        // Prometheus scrape guard (keep your original behavior)
        val accept = request.getHeader("Accept") ?: ""
        if (accept.contains("application/openmetrics-text")) {
            // log minimal info
            logger.warn("Metrics scrape error at {}: {}", request.requestURI, causeSummary(ex))
            return ResponseEntity.status(500).build()
        }
        logger.error("Unhandled exception at {}: {}", request.requestURI, causeSummary(ex))
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, request, trunc(ex.localizedMessage))
    }

    @ExceptionHandler(org.springframework.jdbc.CannotGetJdbcConnectionException::class)
    fun handleCannotGetConn(ex: org.springframework.jdbc.CannotGetJdbcConnectionException, req: HttpServletRequest)
            : ResponseEntity<ErrorResponse> {
        logger.warn("Cannot get JDBC connection at {}: {}", req.requestURI, causeSummary(ex))
        val headers = HttpHeaders().apply { add(HttpHeaders.RETRY_AFTER, "1") }
        return ResponseEntity(body(HttpStatus.SERVICE_UNAVAILABLE, "Database connection unavailable", req),
            headers, HttpStatus.SERVICE_UNAVAILABLE)
    }

    private fun deepestCause(t: Throwable): Throwable =
        generateSequence(t) { it.cause }.last()
}