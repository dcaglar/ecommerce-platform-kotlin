package com.dogancaglar.paymentservice.domain.model.ledger




enum class JournalType {
    AUTHORIZATION,      // DR AUTH_RECEIVABLE / CR AUTH_LIABILITY (Hold position)
    CAPTURE,            // DR AUTH_LIABILITY / CR AUTH_RECEIVABLE & DR PSP_RECEIVABLES / CR MERCHANT_GROSS_POOL
    INTERNAL_TRANSFER,  // DR MERCHANT_GROSS_POOL / CR Sub-Seller or Escrow Balance
    REFUND,             // Reversal of capture balances due to customer return
    SETTLEMENT,         // Multi-payment batch processing from PSP file (clears receivables into platform bank)
    PSP_FEE,
    COMMISSION_FEE,     // DR MERCHANT_GROSS_POOL / CR PLATFORM_COMMISSION_ESCROW
    REVENUE_RECOGNITION,// DR PLATFORM_COMMISSION_ESCROW / CR PLATFORM_OPERATIONAL_REVENUE
    PAYOUT,             // DR MERCHANT or SELLER balance / CR PLATFORM_CASH (Physical transfer out)
    ADJUSTMENT          // Handling settlement discrepancies
}