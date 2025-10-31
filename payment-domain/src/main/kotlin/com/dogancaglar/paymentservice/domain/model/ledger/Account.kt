package com.dogancaglar.paymentservice.domain.model.ledger

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

data class Account private  constructor(val type: AccountType, val entityId: String){
    val accountCode = buildcode()
    fun buildcode(): String = "${type.name}.$entityId"
    companion object{

        fun create(type: AccountType, entityId: String?="GLOBAL")= Account(type,entityId!!)

    }

    fun isDebitAccount() = type.normalBalance == NormalBalance.DEBIT

    fun isCreditAccount() = type.normalBalance == NormalBalance.CREDIT

}