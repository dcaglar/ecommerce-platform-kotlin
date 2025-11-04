package com.dogancaglar.paymentservice.adapter.inbound.rest

import com.dogancaglar.paymentservice.adapter.inbound.rest.mapper.AmountMapper
import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.port.out.web.dto.BalanceDto
import com.dogancaglar.port.out.web.dto.CurrencyEnum
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BalanceService(
    private val accountBalanceReadUseCase: AccountBalanceReadUseCase,
    private val accountDirectory: AccountDirectoryPort
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Retrieves the real-time balance for a seller's merchant account.
     * 
     * Real-time balance = snapshot balance + Redis delta (ephemeral)
     * This provides the most up-to-date balance without merging deltas into the snapshot.
     * 
     * @param sellerId The seller identifier
     * @return BalanceDto containing balance, currency, accountCode, and sellerId
     * @throws IllegalArgumentException if the seller's account profile is not found
     */
    fun getSellerBalance(sellerId: String): BalanceDto {
        logger.debug("Retrieving balance for seller: {}", sellerId)
        
        // Get account profile to determine account code and currency
        val profile = accountDirectory.getAccountProfile(AccountType.MERCHANT_ACCOUNT, sellerId)
        val accountCode = profile.accountCode
        
        // Get real-time balance (snapshot + Redis delta)
        val balance = accountBalanceReadUseCase.getRealTimeBalance(accountCode)
        
        logger.debug("Balance for seller {} (account {}): {} {}", sellerId, accountCode, balance, profile.currency.currencyCode)
        
        return BalanceDto(
            balance = balance,
            currency = CurrencyEnum.valueOf(profile.currency.currencyCode),
            accountCode = accountCode,
            sellerId = sellerId
        )
    }
}

