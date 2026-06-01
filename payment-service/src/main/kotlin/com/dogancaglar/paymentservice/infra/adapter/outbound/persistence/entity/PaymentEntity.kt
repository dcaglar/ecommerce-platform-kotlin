package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity

/*
 * ═══════════════════════════════════════════════════════════════════
 *  MOVED — Epic 1 Architecture Correction
 * ═══════════════════════════════════════════════════════════════════
 *  PaymentEntity has been moved to the payment-consumers module.
 *
 *  Reason: The Payment aggregate is a Central Core concern — it lives
 *  in the Central DB and is written exclusively by PspResultConsumer
 *  in payment-consumers. This POJO belongs with its mapper and adapter.
 *
 *  New location:
 *    payment-consumers/src/main/kotlin/
 *      .../infra/adapter/outbound/persistence/entity/PaymentEntity.kt
 * ═══════════════════════════════════════════════════════════════════
 */

