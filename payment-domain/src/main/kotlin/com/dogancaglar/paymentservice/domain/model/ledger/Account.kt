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

data class Account(val accountId:String="",val accountType: AccountType) {
    fun isDebitAccount()=accountType.normalBalance== NormalBalance.DEBIT
    fun isCreditAccount()=accountType.normalBalance== NormalBalance.CREDIT
    fun getAccountCode(): String{
        if (accountId.isNullOrBlank()){
                return "PSP.${accountType.name}"
        } else{
         return "${accountType.name}-$accountId"
    }
    }
}