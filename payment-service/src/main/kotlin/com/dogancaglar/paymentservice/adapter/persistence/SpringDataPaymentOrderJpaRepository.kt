package com.dogancaglar.paymentservice.adapter.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SpringDataPaymentOrderJpaRepository : JpaRepository<PaymentOrderEntity, String> {

    @Query(
        value = "SELECT COUNT(*) FROM payment_orders WHERE payment_id = :paymentId",
        nativeQuery = true
    )
    fun countByPaymentId(@Param("paymentId") paymentId: String): Long

    @Query(
        value = """
            SELECT COUNT(*) 
            FROM payment_orders 
            WHERE payment_id = :paymentId 
              AND status IN (:statuses)
        """,
        nativeQuery = true
    )
    fun countByPaymentIdAndStatusIn(
        @Param("paymentId") paymentId: String,
        @Param("statuses") statuses: List<String>
    ): Long

    @Query(
        value = """
            SELECT EXISTS(
                SELECT 1 
                FROM payment_orders 
                WHERE payment_id = :paymentId 
                  AND status = :status
            )
        """,
        nativeQuery = true
    )
    fun existsByPaymentIdAndStatus(
        @Param("paymentId") paymentId: String,
        @Param("status") status: String
    ): Boolean


    fun save(entity: PaymentOrderEntity): PaymentOrderEntity
}