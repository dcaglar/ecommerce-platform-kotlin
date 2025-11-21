package com.dogancaglar.paymentservice.adapter.outbound.persistence

import com.dogancaglar.common.time.Utc
import com.dogancaglar.paymentservice.adapter.outbound.persistence.entity.AccountBalanceEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistence.mybatis.AccountBalanceMapper
import com.dogancaglar.paymentservice.domain.model.balance.AccountBalanceSnapshot
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import org.springframework.stereotype.Repository


@Repository
class AccountBalanceSnapshotAdapter(
    private val accountBalanceMapper: AccountBalanceMapper
) : AccountBalanceSnapshotPort {
    
    override fun getSnapshot(accountCode: String): AccountBalanceSnapshot? {
        val entity = accountBalanceMapper.findByAccountCode(accountCode) ?: return null
        return toSnapshot(entity)
    }
    
    override fun saveSnapshot(snapshot: AccountBalanceSnapshot) {
        val entity = AccountBalanceEntity(
            accountCode = snapshot.accountCode,
            balance = snapshot.balance,
            lastAppliedEntryId = snapshot.lastAppliedEntryId,
            lastSnapshotAt = Utc.toInstant(snapshot.lastSnapshotAt),
            updatedAt = Utc.toInstant(snapshot.updatedAt)
        )
        accountBalanceMapper.insertOrUpdateSnapshot(entity)
    }
    
    override fun findAllSnapshots(): List<AccountBalanceSnapshot> {
        return accountBalanceMapper.findAll()
            .map { toSnapshot(it) }
    }

    override fun findByAccountCodes(accountCodes: Set<String>): List<AccountBalanceSnapshot> {
        return accountBalanceMapper.findByAccountCodes(accountCodes)
            .map { toSnapshot(it) }
            .toList()
    }

    private fun toSnapshot(entity: AccountBalanceEntity): AccountBalanceSnapshot {
        return AccountBalanceSnapshot(
            accountCode = entity.accountCode,
            balance = entity.balance,
            lastAppliedEntryId = entity.lastAppliedEntryId,
            lastSnapshotAt = Utc.fromInstant(entity.lastSnapshotAt),
            updatedAt = Utc.fromInstant(entity.updatedAt)
        )
    }
}

