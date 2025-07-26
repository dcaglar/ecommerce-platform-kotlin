package com.dogancaglar.paymentservice.ports.inbound

import com.dogancaglar.paymentservice.domain.commands.CreatePaymentCommand
import com.dogancaglar.paymentservice.domain.model.Payment


/**
 * Starts the “Create Payment” use-case.
 *
 * Implementations must:
 *  1. validate and persist the Payment aggregate
 *  2. create its PaymentOrder children
 *  3. write outbox events for each order
 *
 * @return the identifier of the newly-created Payment.
 */
fun interface CreatePaymentUseCase {
    fun create(command: CreatePaymentCommand): Payment
}