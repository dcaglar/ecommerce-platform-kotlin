package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence

import com.dogancaglar.common.db.converter.TransferEntityMapper
import com.dogancaglar.paymentservice.domain.model.payment.InternalTransfer
import com.dogancaglar.paymentservice.domain.model.vo.InternalTransferId
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.mapper.TransferMapper
import com.dogancaglar.paymentservice.ports.outbound.TransferRepository
import org.springframework.stereotype.Repository

@Repository
class TransferRepositoryAdapter(
    private val transferMapper: TransferMapper
) : TransferRepository {

    override fun save(transfer: InternalTransfer) {
        val entity = TransferEntityMapper.toEntity(transfer)
        transferMapper.upsert(entity)
    }

    override fun findById(transferId: InternalTransferId): InternalTransfer? {
        val entity = transferMapper.findById(transferId.value)
        return entity?.let { TransferEntityMapper.toDomain(it) }
    }
}
