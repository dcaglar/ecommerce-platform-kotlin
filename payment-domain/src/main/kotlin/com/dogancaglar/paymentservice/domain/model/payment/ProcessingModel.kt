package com.dogancaglar.paymentservice.domain.model.payment

/**
 * ProcessingModel
 *
 * Dictates how funds are routed after a capture event:
 *
 *  - DIRECT_MERCHANT: The captured funds settle entirely into the primary
 *    merchant's gross pool. No internal transfers are generated.
 *
 *  - MARKETPLACE: The captured funds first land in the primary merchant's
 *    gross pool (captureGrossAsset journal), and then a series of
 *    INTERNAL_TRANSFER journals redistributes the splits to each sub-seller's
 *    designated BalanceAccountType account (executeSubSellerSplit).
 *
 * This enum is locked into the Payment aggregate at authorization time and
 * must never change for the lifetime of that payment.
 */
enum class ProcessingModel {
    DIRECT_MERCHANT,
    MARKETPLACE
}
