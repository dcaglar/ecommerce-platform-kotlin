package com.dogancaglar.paymentservice.adapter.outbound.persistance

import com.dogancaglar.paymentservice.adapter.outbound.persistance.entity.AccountBalanceEntity
import com.dogancaglar.paymentservice.adapter.outbound.persistance.mybatis.AccountBalanceMapper
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshot
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
            lastSnapshotAt = snapshot.lastSnapshotAt,
            updatedAt = snapshot.updatedAt
        )
        accountBalanceMapper.insertOrUpdateSnapshot(entity)
    }
    
    override fun findAllSnapshots(): List<AccountBalanceSnapshot> {
        return accountBalanceMapper.findAll()
            .map { toSnapshot(it) }
    }
    
    private fun toSnapshot(entity: AccountBalanceEntity): AccountBalanceSnapshot {
        return AccountBalanceSnapshot(
            accountCode = entity.accountCode,
            balance = entity.balance,
            lastSnapshotAt = entity.lastSnapshotAt,
            updatedAt = entity.updatedAt
        )
    }
}

