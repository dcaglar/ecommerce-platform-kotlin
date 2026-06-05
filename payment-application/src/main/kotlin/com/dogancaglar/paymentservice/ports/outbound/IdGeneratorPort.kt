package com.dogancaglar.paymentservice.ports.outbound

interface IdGeneratorPort {

    fun nextPaymentIntentId(): Long

    fun nextPaymentId():Long

    fun nextTxId():Long




    fun nextCaptureId():Long



    }