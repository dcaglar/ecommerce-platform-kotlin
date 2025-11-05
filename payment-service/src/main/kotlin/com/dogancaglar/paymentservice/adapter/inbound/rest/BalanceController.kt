package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.port.out.web.dto.BalanceDto
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class BalanceController(
    private val balanceService: BalanceService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Get balance for a specific seller (Finance/Admin only).
     * 
     * Requires FINANCE or ADMIN role.
     * 
     * @param sellerId The seller identifier
     * @return BalanceDto containing the seller's current balance
     */
    @PreAuthorize("hasRole('FINANCE') or hasRole('ADMIN')")
    @GetMapping("/sellers/{sellerId}/balance")
    fun getSellerBalance(@PathVariable sellerId: String): ResponseEntity<BalanceDto> {
        logger.debug("📊 Retrieving balance for seller: {}", sellerId)
        val balance = balanceService.getSellerBalance(sellerId)
        return ResponseEntity.ok(balance)
    }

    /**
     * Get balance for the authenticated seller (self-service).
     * 
     * Supports two authentication scenarios:
     * - Case 1: User with SELLER role (via customer-area frontend, OIDC Authorization Code flow)
     * - Case 3: Machine client with SELLER_API role (via merchant API, Client Credentials flow)
     * 
     * The sellerId is extracted from the JWT token's "seller_id" claim.
     * 
     * @param authentication JWT authentication token containing seller_id claim
     * @return BalanceDto containing the seller's current balance
     * @throws IllegalArgumentException with 400 Bad Request if seller_id claim is missing
     */
    @PreAuthorize("hasRole('SELLER') or hasRole('SELLER_API')")
    @GetMapping("/sellers/me/balance")
    fun getMyBalance(authentication: JwtAuthenticationToken): ResponseEntity<BalanceDto> {
        val sellerId = authentication.token.claims["seller_id"] as? String
            ?: throw IllegalArgumentException("seller_id claim not found in JWT token")
        logger.debug("📊 Retrieving balance for authenticated seller: {}", sellerId)
        val balance = balanceService.getSellerBalance(sellerId)
        return ResponseEntity.ok(balance)
    }
}