package com.dogancaglar.paymentservice.adapter.outbound.psp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "stripe")
class StripeProperties {
    var apiKey: String = ""
    var clientId: String? = null
    var stripeAccount: String? = null
}

