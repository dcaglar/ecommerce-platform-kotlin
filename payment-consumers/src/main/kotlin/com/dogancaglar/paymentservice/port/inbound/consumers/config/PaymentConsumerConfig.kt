package com.dogancaglar.paymentservice.port.inbound.consumers.config


import com.dogancaglar.paymentservice.application.command.PaymentOrderCaptureCommand
import com.dogancaglar.paymentservice.application.service.ProcessPaymentService
import com.dogancaglar.paymentservice.application.service.RecordLedgerEntriesService
import com.dogancaglar.paymentservice.application.service.RequestLedgerRecordingService
import com.dogancaglar.paymentservice.application.service.AccountBalanceService
import com.dogancaglar.paymentservice.application.service.CapturePaymentService
import com.dogancaglar.paymentservice.application.service.RecordAuthorizationLedgerEntriesService
import com.dogancaglar.paymentservice.infra.adapter.outbound.kafka.PaymentEventPublisher
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.AccountDirectoryImpl
import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.LedgerEntryTxAdapter
import com.dogancaglar.paymentservice.infra.adapter.outbound.redis.PaymentOrderRetryQueueAdapter
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.AccountDirectoryPort
import com.dogancaglar.paymentservice.ports.outbound.EventPublisherPort
import com.dogancaglar.paymentservice.ports.outbound.IdGeneratorPort
import com.dogancaglar.paymentservice.ports.outbound.LedgerEntryPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentOrderModificationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentRepository
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.RetryQueuePort
import com.dogancaglar.paymentservice.ports.outbound.SerializationPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
open class PaymentConsumerConfig {


    @Bean
    fun processPaymentService(
        paymentOrderModificationPort: PaymentOrderModificationPort,
        @Qualifier("syncPaymentEventPublisher") syncPaymentEventPublisher: EventPublisherPort,
        paymentOrderRetryQueueAdapter: RetryQueuePort<PaymentOrderCaptureCommand>,
        accountDirectoryPort: AccountDirectoryPort,
        paymentTxPort: com.dogancaglar.paymentservice.ports.outbound.PaymentTxPort,
        ledgerEntryPort: LedgerEntryPort,
        idGeneratorPort: IdGeneratorPort
    ): ProcessPaymentService {
        return ProcessPaymentService(
            paymentOrderModificationPort = paymentOrderModificationPort,
            eventPublisher = syncPaymentEventPublisher,
            retryQueuePort = paymentOrderRetryQueueAdapter,
            accountDirectory = accountDirectoryPort,
            paymentTxPort = paymentTxPort,
            ledgerWritePort = ledgerEntryPort,
            idGeneratorPort = idGeneratorPort
        )
    }




    @Bean
    fun requestLedgerRecordingService(
        @Qualifier(
            "syncPaymentEventPublisher") syncPaymentEventPublisher: EventPublisherPort): RequestLedgerRecordingService{

        return RequestLedgerRecordingService(syncPaymentEventPublisher)
    }


    @Bean
    fun recordAuthorizationLedgerEntriesService(ledgerWriterPort: LedgerEntryPort,accountDirectoryPort: AccountDirectoryPort)
= RecordAuthorizationLedgerEntriesService(ledgerWritePort = ledgerWriterPort,accountDirectoryPort=accountDirectoryPort)

    @Bean
    fun recordLedgerEntriesService(
        ledgerEntrPort: LedgerEntryPort,
        syncPaymentEventPublisher: EventPublisherPort,
        @Qualifier(
            "accountDirectoryImpl") accountDirectoryImpl: AccountDirectoryPort,
        paymentTxPort: PaymentTxPort,
        idGeneratorPort: IdGeneratorPort): RecordLedgerEntriesService{

        return RecordLedgerEntriesService(ledgerEntrPort, syncPaymentEventPublisher, accountDirectoryImpl, paymentTxPort, idGeneratorPort)
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