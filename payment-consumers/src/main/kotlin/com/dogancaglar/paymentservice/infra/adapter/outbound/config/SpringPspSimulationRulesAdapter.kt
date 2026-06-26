package com.dogancaglar.paymentservice.infra.adapter.outbound.config

import com.dogancaglar.paymentservice.ports.outbound.PspSimulationRulesPort
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "payment.processor.simulation")
class SpringPspSimulationRulesAdapter : PspSimulationRulesPort {

    /**
     * Directly binds to an array list configuration defined in your YAML profile.
     */
    var simulatedMerchantAccounts: List<String> = mutableListOf()

    override fun isSimulationTarget(merchantAccount: String): Boolean {
        // Fallback Step 1: Architectural structural prefix naming convention rule
        if (merchantAccount.startsWith("MARKETPLACE-5", ignoreCase = true)) {
            return true
        }
        
        // Fallback Step 2: Explicit whitelist context configuration array match
        return simulatedMerchantAccounts.contains(merchantAccount)
    }
}
