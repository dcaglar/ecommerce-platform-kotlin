package  com.dogancaglar.payment.application.port.outbound

import com.dogancaglar.payment.domain.model.vo.PaymentOrderId

interface `PspResultCachePort` {
    fun put(pspKey: PaymentOrderId, resultJson: String)
    fun get(pspKey: PaymentOrderId): String?
    fun `remove`(pspKey: PaymentOrderId)
}

