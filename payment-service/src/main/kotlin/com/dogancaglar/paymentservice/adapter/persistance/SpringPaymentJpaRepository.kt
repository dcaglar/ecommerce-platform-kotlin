package com.dogancaglar.paymentservice.adapter.persistance

import org.springframework.data.jpa.repository.JpaRepository

interface SpringPaymentJpaRepository : JpaRepository<PaymentEntity,String>