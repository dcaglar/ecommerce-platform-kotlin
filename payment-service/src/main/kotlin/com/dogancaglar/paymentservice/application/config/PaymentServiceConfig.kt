package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.adapter.outbound.id.SnowflakeIdGeneratorAdapter
import com.dogancaglar.paymentservice.adapter.outbound.persistence.PaymentIntentOutboundAdapter
import com.dogancaglar.paymentservice.adapter.outbound.psp.StripeProperties
import com.dogancaglar.paymentservice.adapter.outbound.serialization.JacksonSerializationAdapter
import com.dogancaglar.paymentservice.application.usecases.AccountBalanceReadService
import com.dogancaglar.paymentservice.application.usecases.AuthorizePaymentIntentService
import com.dogancaglar.paymentservice.application.usecases.CreatePaymentIntentService
import com.dogancaglar.paymentservice.application.usecases.GetPaymentIntentService
import com.dogancaglar.paymentservice.application.util.PaymentOrderDomainEventMapper
import com.dogancaglar.paymentservice.ports.inbound.AccountBalanceReadUseCase
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceCachePort
import com.dogancaglar.paymentservice.ports.outbound.AccountBalanceSnapshotPort
import com.dogancaglar.paymentservice.ports.outbound.PaymentTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.stripe.StripeClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

@Configuration
class PaymentServiceConfig {

    @Bean
    fun stripeClient(stripeProperties: StripeProperties): StripeClient {
        // Create StripeClient with API key
        // StripeClient constructor takes the API key directly
        return StripeClient.StripeClientBuilder().setApiKey(stripeProperties.apiKey)
            .setConnectTimeout(stripeProperties.connectTimeout)
            .setReadTimeout(stripeProperties.readTimeout)
            .build()
    }



    @Bean
    fun paymentOrderDomainEventMapper(): PaymentOrderDomainEventMapper =
        PaymentOrderDomainEventMapper()


    @Bean
    fun authorizePaymentService(
        idGeneratorPort: SnowflakeIdGeneratorAdapter,
        paymentIntentRepository: PaymentIntentOutboundAdapter,
        serializationPort: JacksonSerializationAdapter,
        paymentOrderDomainEventMapper: PaymentOrderDomainEventMapper,
        pspAuthGatewayPort: PspAuthorizationGatewayPort,
        paymentTransactionalFacadePort : PaymentTransactionalFacadePort
    ): AuthorizePaymentIntentService {
        return AuthorizePaymentIntentService(
            idGeneratorPort = idGeneratorPort,
            paymentIntentRepository = paymentIntentRepository,
            psp = pspAuthGatewayPort,
            serializationPort = serializationPort,
            paymentOrderDomainEventMapper = paymentOrderDomainEventMapper,
            paymentTransactionalFacadePort = paymentTransactionalFacadePort
        )
    }

    @Bean
    fun createPaymentService(
        idGeneratorPort: SnowflakeIdGeneratorAdapter,
        paymentIntentRepository: PaymentIntentOutboundAdapter,
        pspAuthGatewayPort: PspAuthorizationGatewayPort,
        @Qualifier("pspCallbackExecutor") pspCallbackExecutor : ThreadPoolTaskExecutor
        ): CreatePaymentIntentService{
        return CreatePaymentIntentService(paymentIntentRepository,idGeneratorPort,pspAuthGatewayPort,pspCallbackExecutor)
    }

    @Bean
    fun getPaymentIntentService(
        paymentIntentRepository: PaymentIntentOutboundAdapter,
        pspAuthGatewayPort: PspAuthorizationGatewayPort
    ): GetPaymentIntentService {
        return GetPaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            pspAuthGatewayPort = pspAuthGatewayPort
        )
    }

    @Bean
    fun accountBalanceReadUseCase(
        cachePort: AccountBalanceCachePort,
        snapshotPort: AccountBalanceSnapshotPort
    ): AccountBalanceReadUseCase {
        return AccountBalanceReadService(
            cachePort = cachePort,
            snapshotPort = snapshotPort
        )
    }
}