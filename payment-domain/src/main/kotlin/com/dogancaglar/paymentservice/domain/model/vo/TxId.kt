package com.dogancaglar.paymentservice.domain.model.vo

/**
 * TxId
 *
 * Typed value wrapper for transaction identifiers (Tx.txId).
 * Using a dedicated type prevents the accidental passing of a paymentId
 * where a txId is expected — a class of primitive-obsession bug that
 * causes silent data corruption in financial systems.
 */
@JvmInline
value class TxId(val value: Long)
