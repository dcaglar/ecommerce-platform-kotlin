package com.dogancaglar.paymentservice.domain.model.ledger

import com.dogancaglar.paymentservice.domain.model.common.Currency

enum class NormalBalance{
    DEBIT,
    CREDIT
}
enum class AccountCategory{
    ASSET,//dr
    EXPENSE,//dr
    LIABILITY,//cr
    EQUITY,//cr
    REVENUE //cr
}

enum class AuthType{
    AUTH,
    SALE
}
enum class AccountType(val normalBalance: NormalBalance, val category: AccountCategory) {
    // === ASSETS ===
    PLATFORM_CASH(NormalBalance.DEBIT, AccountCategory.ASSET), //which is a real bank account where PSp send the money to
    PSP_RECEIVABLES(NormalBalance.DEBIT, AccountCategory.ASSET),     //which is a virtual account n our platform  which does track the moeny  expected from PSP soon
    AUTH_RECEIVABLE(NormalBalance.DEBIT, AccountCategory.ASSET),

    // === LIABILITIES ===
    AUTH_LIABILITY(NormalBalance.CREDIT, AccountCategory.LIABILITY),
    MARKETPLACE_OPERATOR(NormalBalance.CREDIT, AccountCategory.LIABILITY),
    MARKETPLACE_SUB_SELLER(NormalBalance.CREDIT, AccountCategory.LIABILITY),
    PLATFORM_COMMISSION_ESCROW(NormalBalance.CREDIT, AccountCategory.LIABILITY),

    // === REVENUE this keeo track of  the money
    PLATFORM_OPERATIONAL_REVENUE(NormalBalance.CREDIT, AccountCategory.REVENUE),

   // EXPENSE ===
    PSP_FEE_EXPENSE(NormalBalance.DEBIT, AccountCategory.EXPENSE)
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