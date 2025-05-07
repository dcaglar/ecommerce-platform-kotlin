package com.dogancaglar.paymentservice.adapter.persistance

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SpringDataPaymentJpaRepository : JpaRepository<PaymentEntity, String>