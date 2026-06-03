package com.dogancaglar.paymentservice.ports.outbound

interface IdGeneratorPort {

    fun nextPaymentIntentId(): Long

    fun nextPaymentId(): Long



    fun nextCaptureId():Long



    }