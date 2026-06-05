#!/bin/bash
find . -name "*.kt" -type f -exec sed -i '' 's/package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter/package com.dogancaglar.common.db.converter/g' {} +
find . -name "*.kt" -type f -exec sed -i '' 's/package com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity/package com.dogancaglar.common.db.entity/g' {} +
find . -name "*.kt" -type f -exec sed -i '' 's/import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity/import com.dogancaglar.common.db.entity/g' {} +
find . -name "*.kt" -type f -exec sed -i '' 's/import com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.converter/import com.dogancaglar.common.db.converter/g' {} +
