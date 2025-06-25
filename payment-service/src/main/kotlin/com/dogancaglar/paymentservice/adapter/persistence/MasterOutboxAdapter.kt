package com.dogancaglar.paymentservice.adapter.persistence

import com.dogancaglar.paymentservice.adapter.persistence.mapper.OutboxEventEntityMapper
import com.dogancaglar.paymentservice.adapter.persistence.repository.SpringDataOutboxEventJpaRepository
import com.dogancaglar.paymentservice.domain.model.OutboxEvent
import com.dogancaglar.paymentservice.domain.port.OutboxEventPort
import org.springframework.context.annotation.Primary
import org.springframework.data.domain.PageRequest
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime

/* =========================================================
   MASTER  – default implementation used everywhere
   ========================================================= */
@Repository
@Primary                                           // injected when no qualifier
@Transactional("transactionManager")               // master TM (write-enabled)
class MasterOutboxAdapter(
    private val repo: SpringDataOutboxEventJpaRepository,
    private val redis: StringRedisTemplate
) : OutboxEventPort {
    private val key = "outbox:lastProcessedTs"
    override fun findByStatus(status: String) =
        repo.findByStatus(status).map(OutboxEventEntityMapper::toDomain)

    override fun findByStatusWithLimit(status: String, limit: Int) =
        repo.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit))
            .map(OutboxEventEntityMapper::toDomain)

    override fun saveAll(events: List<OutboxEvent>) =
        repo.saveAll(events.map(OutboxEventEntityMapper::toEntity))
            .map(OutboxEventEntityMapper::toDomain)

    override fun save(event: OutboxEvent) =
        OutboxEventEntityMapper.toDomain(
            repo.save(OutboxEventEntityMapper.toEntity(event))
        )

    override fun countByStatus(status: String) = repo.countByStatus(status)

    override fun findBatchAfter(
        cursor: LocalDateTime, batch: Int
    ) = repo.findBatchAfter(cursor, batch)
        .map(OutboxEventEntityMapper::toDomain)

    override fun read(): Instant =
        redis.opsForValue().get(key)?.let { Instant.parse(it) } ?: Instant.EPOCH

    override fun write(ts: Instant) =
        redis.opsForValue().set(key, ts.toString())
}

/* =========================================================
   REPLICA – read-only adapter, inject with @Qualifier("outboxEventReader")
   ========================================================= */
@Repository("outboxEventReader")                   // explicit bean name
@Transactional(
    transactionManager = "replicaTransactionManager",
    readOnly = true
)
class ReadReplicaOutboxAdapter(
    private val repo: SpringDataOutboxEventJpaRepository,
    private val redis: StringRedisTemplate
) : OutboxEventPort {
    private val key = "outbox:lastProcessedTs"

    /* ---------- READ methods delegate to replica ---------- */

    override fun findByStatus(status: String) =
        repo.findByStatus(status).map(OutboxEventEntityMapper::toDomain)

    override fun findByStatusWithLimit(status: String, limit: Int) =
        repo.findByStatusOrderByCreatedAtAsc(status, PageRequest.of(0, limit))
            .map(OutboxEventEntityMapper::toDomain)

    override fun findBatchAfter(cursor: LocalDateTime, batch: Int): List<OutboxEvent> =
        repo.findBatchAfter(                              // same native query
            cursor, // convert Instant -> LocalDateTime
            batch
        ).map(OutboxEventEntityMapper::toDomain)

    override fun countByStatus(status: String) = repo.countByStatus(status)

    fun findBatchForDispatch(
        @Param("status") status: String,
        @Param("batchSize") batchSize: Int
    ) = repo.findBatchForDispatch(status, batchSize)
        .map(OutboxEventEntityMapper::toDomain)

    override fun read(): Instant =
        redis.opsForValue().get(key)?.let { Instant.parse(it) } ?: Instant.EPOCH

    override fun write(ts: Instant) =
        redis.opsForValue().set(key, ts.toString())

    /* ---------- WRITE methods blocked on replica ---------- */

    override fun saveAll(events: List<OutboxEvent>): List<OutboxEvent> =
        error("Replica adapter is read-only (saveAll called)")

    override fun save(event: OutboxEvent): OutboxEvent =
        error("Replica adapter is read-only (save called)")
}