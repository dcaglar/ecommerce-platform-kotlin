package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.vo.BuyerId
import com.dogancaglar.paymentservice.domain.model.vo.OrderId
import com.dogancaglar.paymentservice.domain.model.vo.SellerId

interface IdGeneratorPort {

    fun nextPaymentIntentId(buyerId: BuyerId, orderId: OrderId): Long

    fun nextPaymentId(buyerId: BuyerId, orderId: OrderId): Long


    fun nextPaymentOrderId(sellerId: SellerId): Long



    }