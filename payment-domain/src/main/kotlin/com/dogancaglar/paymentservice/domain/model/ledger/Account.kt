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
    /**
     * 1. The Raw Ingestion Landing Lane (Transient Clearing Pad)
     * Tracks the initial, unassigned gross captured volume received from the PSP webhook on Day 1.
     * This acts strictly as a temporary suspense pool and must ALWAYS be drained down to exactly €0
     * by downstream async internal transfer commands (either via multi-party seller splits or a 100% direct revenue reclassification).
     */
    MERCHANT_GROSS_CAPTURE_SUSPENSE(NormalBalance.CREDIT, AccountCategory.LIABILITY),

    /**
     * 2. The Finalized Operator Direct Sales Folder (Direct Revenue)
     * Holds the accumulated, finalized payable funds from direct e-commerce sales where the marketplace operator
     * sold items from their own inventory (no third-party splits involved).
     * Exactly one account of this type exists per unique marketplace merchant master_account_id.
     */
    MARKETPLACE_DIRECT_REVENUE_BALANCE_ACCOUNT(NormalBalance.CREDIT, AccountCategory.LIABILITY),

    /**
     * 3. The Finalized Operator Commission Earnings Folder (Platform Revenue Share)
     * Tracks the accumulated, finalized balance of earned platform usage or processing commissions charged
     * by the marketplace operator to their third-party sub-sellers via split arrays.
     * Exactly one account of this type exists per unique marketplace merchant master_account_id.
     */
    MARKETPLACE_COMMISSION_REVENUE_BALANCE_ACCOUNT(NormalBalance.CREDIT, AccountCategory.LIABILITY),

    /**
     * 4. The Finalized Sub-Seller Revenue Folder (Vendor Balance)
     * Tracks the finalized, net revenue payable balance belonging to a specific third-party sub-seller
     * onboarded under the marketplace operator's master ecosystem.
     * Multiple accounts of this type can exist under the same master_account_id, isolated by their unique sub-seller entity IDs.
     */
    MARKETPLACE_SELLER_BALANCE_ACCOUNT(NormalBalance.CREDIT, AccountCategory.LIABILITY),
    PLATFORM_COMMISSION_ESCROW(NormalBalance.CREDIT, AccountCategory.LIABILITY), // this account is used to track balacne of commission escrow where we (mor-dc) charge to our mmarketplace merchant
    MARKETPLACE_MASTER_ACCOUNT(NormalBalance.CREDIT, AccountCategory.LIABILITY), // Master account representing the merchant entity itself

    // === REVENUE this keeo track of  the money
    PLATFORM_OPERATIONAL_REVENUE(NormalBalance.CREDIT, AccountCategory.REVENUE), //this the account tracking guaranteed-earning , not  like PLATFORM_COMMISSION_ESCROW
   // EXPENSE ===
    PSP_FEE_EXPENSE(NormalBalance.DEBIT, AccountCategory.EXPENSE) // this is to track the psp-fee we (Mor-DC) pay to external psp.
}

data class Account private constructor(
    val type: AccountType,
    val accountCode: String,
    val currency: Currency = Currency("EUR"),
    val authType: AuthType? = AuthType.SALE
) {
    init {
        require(accountCode.isNotBlank()) { "Account code cant be empty" }
    }

    companion object {

        fun create(type: AccountType, accountCode: String) =
            Account(type = type, accountCode = accountCode)

        fun fromProfile(profile: AccountProfile): Account =
            Account(
                type = profile.type,
                accountCode = profile.accountCode,
                currency = profile.currency
            )

        // Only used for testing
        fun mock(type: AccountType, accountCode: String? = null, currencyCode: String = "EUR"): Account {
            return Account(type, accountCode ?: "${type.name}.GLOBAL.$currencyCode", Currency(currencyCode))
        }
    }


    fun isDebitAccount() = type.normalBalance == NormalBalance.DEBIT

    fun isCreditAccount() = type.normalBalance == NormalBalance.CREDIT

}