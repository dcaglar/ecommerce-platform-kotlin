package com.dogancaglar.common.event

import com.fasterxml.jackson.core.type.TypeReference

interface EventMetadata<T> {
    val topic: String
    val eventType: String
    val clazz: Class<T>
    val typeRef: TypeReference<out EventEnvelope<T>>
}