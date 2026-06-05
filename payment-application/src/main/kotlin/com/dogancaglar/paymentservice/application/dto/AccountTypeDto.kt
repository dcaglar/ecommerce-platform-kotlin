package com.dogancaglar.paymentservice.application.dto

import com.dogancaglar.paymentservice.domain.model.ledger.AccountType

/**
 * AccountTypeDto
 *
 * The API representation of the split target types. This enum is safely
 * decoupled from the core domain [AccountType] but maintains semantic parity.
 */
enum class AccountTypeDto {
    MARKETPLACE_OPERATOR,
    MARKETPLACE_SUB_SELLER,
    PLATFORM_COMMISSION_ESCROW;

    fun toDomain(): AccountType {
        return when (this) {
            MARKETPLACE_OPERATOR -> AccountType.MARKETPLACE_OPERATOR
            MARKETPLACE_SUB_SELLER -> AccountType.MARKETPLACE_SUB_SELLER
            PLATFORM_COMMISSION_ESCROW -> AccountType.PLATFORM_COMMISSION_ESCROW
        }
    }
}
