import os

replacements = [
    ("package com.dogancaglar.paymentservice.infra.adapter.outbound.redis.config", "package com.dogancaglar.paymentservice.infra.config"),
    ("import com.dogancaglar.paymentservice.infra.adapter.outbound.redis.config.", "import com.dogancaglar.paymentservice.infra.config."),
    
    ("package com.dogancaglar.paymentservice.infra.adapter.outbound.id.config", "package com.dogancaglar.paymentservice.infra.config"),
    ("import com.dogancaglar.paymentservice.infra.adapter.outbound.id.config.", "import com.dogancaglar.paymentservice.infra.config."),
    
    ("package com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners.config", "package com.dogancaglar.paymentservice.infra.config"),
    ("import com.dogancaglar.paymentservice.infra.adapter.inbound.kafka.listeners.config.", "import com.dogancaglar.paymentservice.infra.config.")
]

for root, dirs, files in os.walk("."):
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            new_content = content
            for old, new in replacements:
                new_content = new_content.replace(old, new)
                
            if content != new_content:
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Updated {path}")
