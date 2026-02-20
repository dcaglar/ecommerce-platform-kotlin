package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

interface IdGeneratorPort {

    fun nextPaymentIntentId(): Long

    fun nextPaymentId(): Long


    fun nextPaymentOrderId(): Long



    }