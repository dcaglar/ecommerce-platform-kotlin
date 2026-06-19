package com.dogancaglar.paymentservice.ports.outbound

/**
 * PspSimulationRulesPort
 *
 * Outbound boundary port providing abstraction over tenant execution environments.
 * Decouples core business workflows from infrastructure configuration, profiling, and testing state rules.
 */
interface PspSimulationRulesPort {
    /**
     * Evaluates whether a given merchant account is designated for simulation routing.
     * @param merchantAccount The raw merchant identifier code string.
     * @return true if the account profiles to a sandboxed simulation node.
     */
    fun isSimulationTarget(merchantAccount: String): Boolean
}
