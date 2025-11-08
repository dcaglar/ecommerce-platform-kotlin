package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.Currency

enum class NormalBalance{
    DEBIT,
    CREDIT
}
enum class AccountCategory{
    ASSET,
    EXPENSE,
    LIABILITY,
    EQUITY,
    REVENUE
}

enum class AuthType{
    AUTH,
    SALE
}
enum class AccountType(val normalBalance: NormalBalance, val category: AccountCategory) {
    // Assets
    CASH(NormalBalance.DEBIT, AccountCategory.ASSET),
    PSP_RECEIVABLES(NormalBalance.DEBIT, AccountCategory.ASSET),
    SHOPPER_RECEIVABLES(NormalBalance.DEBIT, AccountCategory.ASSET),
    AUTH_RECEIVABLE(NormalBalance.DEBIT, AccountCategory.ASSET),
    ACQUIRER_ACCOUNT(NormalBalance.DEBIT, AccountCategory.ASSET),

    // Liabilities
    AUTH_LIABILITY(NormalBalance.CREDIT, AccountCategory.LIABILITY),
    MERCHANT_ACCOUNT(NormalBalance.CREDIT, AccountCategory.LIABILITY),

    // Revenue
    PROCESSING_FEE_REVENUE(NormalBalance.CREDIT, AccountCategory.REVENUE),

    // Expenses
    INTERCHANGE_FEES(NormalBalance.DEBIT, AccountCategory.EXPENSE),
    SCHEME_FEES(NormalBalance.DEBIT, AccountCategory.EXPENSE),
    BANK_FEES(NormalBalance.DEBIT, AccountCategory.EXPENSE)
}

data class Account private  constructor(val type: AccountType,
                                        val entityId: String,
                                        val currency: Currency= Currency("EUR"),
                                        val authType: AuthType?= AuthType.SALE){
    val accountCode = "${type.name}.${entityId}.${currency.currencyCode}"

    init {
        require(entityId.isNotBlank()) { "Entity id cant be empty" }
    }

    companion object {

        fun create(type: AccountType, entityId: String)= Account(type=type,entityId= entityId)

        fun fromProfile(profile: AccountProfile): Account {
            return Account(
                type = profile.type,
                entityId = profile.entityId,
                currency = profile.currency
            )
        }

        /** For test / in-memory usage only */
        fun mock(type: AccountType, entityId: String = "GLOBAL", currencyCode: String = "EUR"): Account {
            return Account(type, entityId, Currency(currencyCode))
        }
    }


    fun isDebitAccount() = type.normalBalance == NormalBalance.DEBIT

    fun isCreditAccount() = type.normalBalance == NormalBalance.CREDIT

}