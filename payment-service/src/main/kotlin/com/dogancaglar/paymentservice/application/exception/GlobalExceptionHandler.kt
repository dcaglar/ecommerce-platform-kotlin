package com.dogancaglar.paymentservice.application.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.time.Instant

@RestControllerAdvice
class GlobalExceptionHandler {
    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    data class ErrorResponse(
        val timestamp: String = Instant.now().toString(),
        val status: Int,
        val error: String,
        val message: String?,
        val path: String?,
        val traceId: String? = null
    )

    private fun traceId(request: HttpServletRequest): String? =
        MDC.get("traceId") ?: request.getHeader("X-Trace-Id")

    @ExceptionHandler(NoResourceFoundException::class)
    fun handleNoResourceFound(
        ex: NoResourceFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.debug("No resource found: ${request.requestURI}")
        val response = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.localizedMessage,
            path = request.requestURI,
            traceId = traceId(request)
        )
        return ResponseEntity(response, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFound(
        ex: NoHandlerFoundException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.info("No handler for path: ${request.requestURI}")
        val response = ErrorResponse(
            status = HttpStatus.NOT_FOUND.value(),
            error = "Not Found",
            message = ex.message,
            path = request.requestURI,
            traceId = traceId(request)
        )
        return ResponseEntity(response, HttpStatus.NOT_FOUND)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(
        ex: MissingServletRequestParameterException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Missing parameter: ${ex.parameterName} on ${request.requestURI}")
        val response = ErrorResponse(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.localizedMessage,
            path = request.requestURI,
            traceId = traceId(request)
        )
        return ResponseEntity(response, HttpStatus.BAD_REQUEST)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponse> {
        logger.error("Unhandled exception at ${request.requestURI}", ex)
        val response = ErrorResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = ex.localizedMessage,
            path = request.requestURI,
            traceId = traceId(request)
        )
        return ResponseEntity(response, HttpStatus.INTERNAL_SERVER_ERROR)
    }
}