package com.dogancaglar.paymentservice.psp

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "psp.simulation")
class PspSimulationProperties {
    var timeouts: TimeoutConfig = TimeoutConfig()
    var latency: LatencyConfig = LatencyConfig()

    class TimeoutConfig {
        var enabled: Boolean = true
        var probability: Int = 50
    }

    class LatencyConfig {
        var fast: Int = 30
        var moderate: Int = 40
        var slow: Int = 30
    }
}