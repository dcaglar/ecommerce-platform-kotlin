package com.dogancaglar.paymentservice.infra.config

import com.dogancaglar.paymentservice.ports.outbound.*
import com.dogancaglar.paymentservice.application.service.AuthorizePaymentIntentService
import com.dogancaglar.paymentservice.application.service.CapturePaymentService
import com.dogancaglar.paymentservice.application.service.CreatePaymentIntentService
import com.dogancaglar.paymentservice.application.service.GetPaymentIntentService
import com.dogancaglar.paymentservice.application.service.UpdatePaymentIntentService
import com.dogancaglar.paymentservice.ports.inbound.usecases.CreatePaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.inbound.usecases.GetPaymentIntentUseCase
import com.dogancaglar.paymentservice.ports.outbound.PaymentTransactionalFacadePort
import com.dogancaglar.paymentservice.ports.outbound.PspAuthorizationGatewayPort
import com.dogancaglar.paymentservice.ports.outbound.ResilientExecutionPort
import com.stripe.StripeClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executor

@Configuration
class PaymentServiceConfig {

    @Bean
    fun stripeClient(
        @Value("\${stripe.api.api-key}") apiKey: String,
        @Value("\${stripe.api.connect-timeout:5000}") connectTimeout: Int,
        @Value("\${stripe.api.read-timeout:30000}") readTimeout: Int
    ): StripeClient {
        return StripeClient.StripeClientBuilder()
            .setApiKey(apiKey)
            .setConnectTimeout(connectTimeout)
            .setReadTimeout(readTimeout)
            .build()
    }


    @Bean
    fun capturePaymentService(paymentRepository: PaymentRepository,
                              psp: PspAuthorizationGatewayPort,
                              @Qualifier("outboxWebAdapter")  outboxWebAdapter: LocalOutboxWriterPort,
                              idGeneratorPort: IdGeneratorPort,
                              serializationPort: SerializationPort): CapturePaymentService {
        return CapturePaymentService(paymentRepository,psp,outboxWebAdapter,idGeneratorPort,serializationPort)
    }


    @Bean
    fun authorizePaymentService(
        idGeneratorPort: IdGeneratorPort,
        paymentIntentRepository: PaymentIntentRepository,
        resilientExecutionPort: ResilientExecutionPort,
        serializationPort: SerializationPort,
        pspAuthGatewayPort: PspAuthorizationGatewayPort,
        paymentTransactionalFacadePort : PaymentTransactionalFacadePort
    ): AuthorizePaymentIntentService {
        return AuthorizePaymentIntentService(
            idGeneratorPort = idGeneratorPort,
            paymentIntentRepository = paymentIntentRepository,
            resilientExecutionPort = resilientExecutionPort,
            pspAuthGatewayPort = pspAuthGatewayPort,
            serializationPort = serializationPort,
            paymentTransactionalFacadePort = paymentTransactionalFacadePort
        )
    }


    @Bean
    fun updatePaymentIntentService(paymentIntentRepository: PaymentIntentRepository): UpdatePaymentIntentService{
        return UpdatePaymentIntentService(paymentIntentRepository)
    }

    @Bean
    fun createPaymentService(
        idGeneratorPort: IdGeneratorPort,
        paymentIntentRepository: PaymentIntentRepository,
        pspAuthGatewayPort: PspAuthorizationGatewayPort,
        resilientExecutionPort: ResilientExecutionPort
    ): CreatePaymentIntentUseCase {
        return CreatePaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            idGeneratorPort = idGeneratorPort,
            pspAuthGatewayPort = pspAuthGatewayPort,
            resilientExecutionPort = resilientExecutionPort
        )
    }

    @Bean
    fun getPaymentIntentService(
        paymentIntentRepository: PaymentIntentRepository,
        pspAuthGatewayPort: PspAuthorizationGatewayPort,
        resilientExecutionPort: ResilientExecutionPort
    ): GetPaymentIntentUseCase{
        return GetPaymentIntentService(
            paymentIntentRepository = paymentIntentRepository,
            pspAuthGatewayPort = pspAuthGatewayPort,
            resilientExecutionPort = resilientExecutionPort
            )
    }
}