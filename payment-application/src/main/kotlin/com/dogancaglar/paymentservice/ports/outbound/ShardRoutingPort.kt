package com.dogancaglar.paymentservice.ports.outbound


interface ShardRoutingPort {
    fun paymentDbShard(paymentId: Long): Int
    fun paymentOrderDbShard(paymentOrderId: Long): Int
    fun ledgerDbShard(sellerId: String): Int

    fun paymentKafkaPartition(paymentOrderId: Long, numPartitions: Int): Int
    fun ledgerKafkaPartition(sellerId: String, numPartitions: Int): Int
}