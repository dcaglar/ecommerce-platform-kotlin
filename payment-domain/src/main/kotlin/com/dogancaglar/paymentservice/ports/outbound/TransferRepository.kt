package com.dogancaglar.paymentservice.ports.outbound

import com.dogancaglar.paymentservice.domain.model.payment.InternalTransfer
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId

interface TransferRepository {
    fun save(transfer: InternalTransfer)
    fun findById(transferId: InternalTransferId): InternalTransfer?
}
