package com.dogancaglar.paymentservice.adapter.workflow

import io.temporal.activity.ActivityOptions
import java.time.Duration

object PaymentWorkflowConfig {
    val activityOptions: ActivityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(10))
        .build()
}