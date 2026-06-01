import os

replacements = [
    ("package com.dogancaglar.paymentservice.port.inbound.consumers", "package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners"),
    ("import com.dogancaglar.paymentservice.port.inbound.consumers.", "import com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners."),
    
    ("package com.dogancaglar.paymentservice.application.maintenance", "package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler"),
    ("import com.dogancaglar.paymentservice.application.maintenance.", "import com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler."),
    
    ("package com.dogancaglar.paymentservice.service", "package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler"),
    ("import com.dogancaglar.paymentservice.service.", "import com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler."),
    
    ("package com.dogancaglar.paymentservice.edgeworkers.config", "package com.dogancaglar.paymentservice.infra.config"),
    ("import com.dogancaglar.paymentservice.edgeworkers.config.", "import com.dogancaglar.paymentservice.infra.config."),
    
    ("package com.dogancaglar.paymentservice.adapter.outbound.psp", "package com.dogancaglar.paymentservice.infra.adapter.outbound.psp"),
    ("import com.dogancaglar.paymentservice.adapter.outbound.psp.", "import com.dogancaglar.paymentservice.infra.adapter.outbound.psp."),
    
    ("package com.dogancaglar.paymentinfra", "package com.dogancaglar.paymentservice.infra.config"),
    ("import com.dogancaglar.paymentinfra.", "import com.dogancaglar.paymentservice.infra.config.")
]

# Note: OutboxRelayJob.kt was moved from port.inbound.consumers to infra.adapter.inbound.scheduler.
# The general replacement changes it to kafka.listeners. We need to fix this specific file separately.
# Also AccountBalanceSnapshotJob.kt and LocalOutboxForwarderJob.kt and OutboxPartitionCreator.kt 
# were in application.maintenance. The general replacement to scheduler works for them.

for root, dirs, files in os.walk("."):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            new_content = content
            for old, new in replacements:
                new_content = new_content.replace(old, new)
                
            # Specific fixes for files moved to scheduler instead of listeners
            if "OutboxRelayJob.kt" in file:
                new_content = new_content.replace("package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners", "package com.dogancaglar.paymentservice.infra.adapter.inbound.scheduler")
            
            if content != new_content:
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Updated {path}")
