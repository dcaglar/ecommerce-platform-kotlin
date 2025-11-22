package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.adapter.outbound.redis.PaymentOrderRetryQueueAdapter
import com.dogancaglar.paymentservice.application.usecases.ProcessPaymentService
import com.dogancaglar.paymentservice.application.usecases.RecordLedgerEntriesService
import com.dogancaglar.paymentservice.application.usecases.RequestLedgerRecordingService
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceService
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class PaymentConsumerConfig {


    @Bean
    fun paymentOrderDomainEventMapper(): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper()


    @Bean
    fun processPaymentService(
        paymentOrderModificationPort: PaymentOrderModificationPort,
        @Qualifier("syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher,
        paymentOrderRetryQueueAdapter: PaymentOrderRetryQueueAdapter,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderModificationPort = paymentOrderModificationPort,
            eventPublisher = syncPaymentEventPublisher,
            retryQueuePort = paymentOrderRetryQueueAdapter,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper
        )
    }

    @Bean
    fun requestLedgerRecordingService(
        @Qualifier(
            "syncPaymentEventPublisher") syncPaymentEventPublisher: PaymentEventPublisher): RequestLedgerRecordingService{

        return RequestLedgerRecordingService(syncPaymentEventPublisher)
    }


    @Bean
    fun recordLedgerEntriesService(
        ledgerEntrPort: LedgerEntryPort,
        syncPaymentEventPublisher: PaymentEventPublisher,
        @Qualifier(
            "accountDirectoryImpl") accountDirectoryImpl: AccountDirectoryPort): RecordLedgerEntriesService{

        return RecordLedgerEntriesService(ledgerEntrPort,syncPaymentEventPublisher,accountDirectoryImpl)
    }



    @Bean
    fun accountBalanceService(
        @Qualifier("accountBalanceSnapshotAdapter") accountBalanceSnapshotAdapter: AccountBalanceSnapshotPort,
        @Qualifier("accountBalanceRedisCacheAdapter") accountBalanceRedisCacheAdapter : AccountBalanceCachePort
    ): AccountBalanceService {
        return AccountBalanceService(
            snapshotPort =accountBalanceSnapshotAdapter,
            cachePort = accountBalanceRedisCacheAdapter
        )
    }
}