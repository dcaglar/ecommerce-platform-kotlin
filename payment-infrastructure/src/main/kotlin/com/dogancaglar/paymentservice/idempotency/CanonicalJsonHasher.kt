// payment-infrastructure/src/main/kotlin/.../idempotency/CanonicalJsonHasher.kt
package com.dogancaglar.paymentservice.idempotency

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.security.MessageDigest

class CanonicalJsonHasher(
    private val objectMapper: ObjectMapper
) {
    fun hashBody(body: Any): String {
        val node: JsonNode = objectMapper.valueToTree(body)
        val canonical = objectMapper.writeValueAsString(node)
        val sha = MessageDigest.getInstance("SHA-256")
        val bytes = sha.digest(canonical.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}