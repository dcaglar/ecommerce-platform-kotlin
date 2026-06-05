#!/bin/bash
set -e

# Checkout the real file from payment-consumers
git checkout payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence/entity/PaymentEntity.kt

# Copy it to common-db
cp payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence/entity/PaymentEntity.kt common-db/src/main/kotlin/com/dogancaglar/common/db/entity/PaymentEntity.kt

# Update its package
sed -i '' 's/package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity/package com.dogancaglar.common.db.entity/g' common-db/src/main/kotlin/com/dogancaglar/common/db/entity/PaymentEntity.kt

# Delete it from payment-consumers again
rm payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence/entity/PaymentEntity.kt

echo "Fixed PaymentEntity.kt"
