package com.dogancaglar.paymentservice.domain.model.payment

/**
 * BalanceAccountType
 *
 * Strict enumeration of the internal virtual account buckets that can be
 * targeted by a PaymentSplit instruction. Each value maps to a logical
 * ledger account owned by a specific entity inside the Mor-DC platform.
 *
 *  - MERCHANT_GROSS_POOL:
 *      The primary (direct or marketplace) merchant's holding pool that
 *      receives 100% of the captured asset. Acts as the clearing house
 *      before any sub-seller INTERNAL_TRANSFER postings are applied.
 *
 *  - MARKETPLACE_SUB_SELLER:
 *      A sub-seller's individual balance account within a marketplace
 *      payment. Credited by INTERNAL_TRANSFER journals after the gross
 *      capture is settled into the merchant pool.
 *
 *  - PLATFORM_OPERATIONAL_REV:
 *      The platform's operational revenue bucket. Used to record the
 *      portion of a transaction that belongs to the platform itself
 *      (e.g., transaction margins not classified as commission).
 *
 *  - PLATFORM_COMMISSION_ESCROW:
 *      An escrow account that holds commission amounts before they are
 *      confirmed and released. Provides auditability for commission accruals
 *      that are not yet fully settled.
 */
enum class BalanceAccountType {
    MERCHANT_GROSS_POOL,
    MARKETPLACE_SUB_SELLER,
    PLATFORM_OPERATIONAL_REV,
    PLATFORM_COMMISSION_ESCROW
}
