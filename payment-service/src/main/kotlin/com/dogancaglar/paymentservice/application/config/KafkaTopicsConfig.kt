package com.dogancaglar.paymentservice.application.config

import com.dogancaglar.paymentservice.application.metadata.Topics
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaAdmin

/** Per-topic overrides; keys come from KafkaTopicSetProperties.specs */
data class TopicSpec(
    var partitions: Int = 1,
    var replicas: Short = 1,
    var createDlq: Boolean = true
)

@ConfigurationProperties("app.kafka")
data class


KafkaTopicSetProperties(
    /** key MUST exactly match the base topic names in Topics.ALL */
    var specs: Map<String, TopicSpec> = emptyMap()
)

@Configuration
@EnableConfigurationProperties(KafkaTopicSetProperties::class)
class TopicAdminConfig(
    private val cfg: KafkaTopicSetProperties
) {
    /** Spring Boot will auto-create KafkaAdmin if spring.kafka.bootstrap-servers is set */
    @Bean
    fun newTopics(): KafkaAdmin.NewTopics {
        val topics = mutableListOf<NewTopic>()
        for (base in Topics.ALL) {
            val spec = cfg.specs[base] ?: TopicSpec()
            topics += NewTopic(base, spec.partitions, spec.replicas)
            if (spec.createDlq) {
                topics += NewTopic(Topics.dlqOf(base), spec.partitions, spec.replicas)
            }
        }
        return KafkaAdmin.NewTopics(*topics.toTypedArray())
    }
}