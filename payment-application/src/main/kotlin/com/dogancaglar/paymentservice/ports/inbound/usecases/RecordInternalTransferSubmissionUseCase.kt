package com.dogancaglar.paymentservice.ports.inbound.usecases

import com.dogancaglar.paymentservice.domain.model.ledger.AccountType
import com.dogancaglar.paymentservice.domain.model.payment.PaymentSplit
import com.dogancaglar.paymentservice.domain.model.vo.PaymentId
import com.dogancaglar.paymentservice.domain.model.vo.PaymentIntentId
import com.dogancaglar.paymentservice.domain.model.vo.TxId

interface RecordInternalTransferSubmissionUseCase {
    fun recordSubmission(
        paymentId: PaymentId,
        paymentIntentId: PaymentIntentId,
        publicPaymentIntentId: String,
        captureTxId: TxId,
        sourceAccountType: AccountType,
        sourceEntityId: String,
        split: PaymentSplit
    )
}
