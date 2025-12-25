package com.dogancaglar.paymentservice.adapter.outbound.psp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "stripe.api")
class StripeProperties(val apiKey:String,val connectTimeout:Int,val readTimeout: Int) {
}

