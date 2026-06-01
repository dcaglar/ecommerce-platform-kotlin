import os
import shutil

moves = [
    # Generic Configs -> config
    ("payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/PaymentInfrastructureAutoConfig.kt", 
     "payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),
     
    ("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/PaymentConsumerConfig.kt", 
     "payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),
     
    ("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/ConsumerThreadPoolConfig.kt", 
     "payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),
     
    ("payment-service/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/PaymentServiceConfig.kt", 
     "payment-service/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),
     
    ("payment-service/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/ThreadPoolConfig.kt", 
     "payment-service/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),
     
    ("payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/ThreadPoolConfig.kt", 
     "payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/config", 
     "com.dogancaglar.paymentservice.config"),

    # Specific Infra Configs -> their folders
    ("payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/RedisConfig.kt", 
     "payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/redis", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.redis"),
     
    ("payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/IdGenerationProperties.kt", 
     "payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/id", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.id"),
     
    ("payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/SnowflakeCore.kt", 
     "payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/id", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.id"),
     
    ("payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/JacksonConfig.kt", 
     "payment-infrastructure/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/serialization", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.serialization"),

    ("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/KafkaTopicsConfig.kt", 
     "payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/inbound/kafka", 
     "com.dogancaglar.paymentservice.infra.adapter.inbound.kafka"),

    ("payment-service/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/IdempotencyConfig.kt", 
     "payment-service/src/main/kotlin/com/dogancaglar/paymentservice/adapter/inbound/rest", 
     "com.dogancaglar.paymentservice.adapter.inbound.rest"),
     
    ("payment-service/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/DataSourceConfig.kt", 
     "payment-service/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence"),

    ("payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/MultiDataSourceConfig.kt", 
     "payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence"),
     
    ("payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/MyBatisFactoriesConfig.kt", 
     "payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence"),
     
    ("payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/config/DBWriterTxManager.kt", 
     "payment-edge-workers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/outbound/persistence", 
     "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence"),
]

replacements = []

for src, dst_dir, new_pkg in moves:
    if not os.path.exists(src):
        print(f"Skipping {src}, doesn't exist")
        continue
    os.makedirs(dst_dir, exist_ok=True)
    filename = os.path.basename(src)
    dst = os.path.join(dst_dir, filename)
    shutil.move(src, dst)
    print(f"Moved {filename} to {dst_dir}")
    
    # Old package for all these was com.dogancaglar.paymentservice.infra.config
    old_pkg = "com.dogancaglar.paymentservice.infra.config"
    class_name = filename.replace(".kt", "")
    
    # Class import replacement
    replacements.append((f"import {old_pkg}.{class_name}", f"import {new_pkg}.{class_name}"))

# Also we must update the package line in the moved files!
# So for all kotlin files we check
for root, dirs, files in os.walk("."):
    if "target" in root or ".git" in root or ".idea" in root:
        continue
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            new_content = content
            
            # 1. Update imports
            for old_imp, new_imp in replacements:
                if old_imp in new_content:
                    # if they are in the same package now, the import might be redundant, but changing it is safe
                    new_content = new_content.replace(old_imp, new_imp)
            
            # 2. Update package declaration if this is one of the moved files
            for src, dst_dir, new_pkg in moves:
                filename = os.path.basename(src)
                if file == filename:
                    # check if the file is in the new dst_dir
                    # A robust way is to just replace the package line if it matches the old one
                    old_pkg_line = f"package com.dogancaglar.paymentservice.infra.config"
                    new_pkg_line = f"package {new_pkg}"
                    
                    # only replace the first occurrence (which is the package line)
                    if old_pkg_line in new_content and new_pkg_line != old_pkg_line:
                        # But wait, what if it's the SAME filename but in a different module (like ThreadPoolConfig.kt)?
                        # It's better to only replace if the path matches dst_dir!
                        if dst_dir.endswith(os.path.dirname(path).replace("./", "")):
                            new_content = new_content.replace(old_pkg_line, new_pkg_line, 1)
                        # Actually a simpler way is to just replace the package line.
                        # Since all of them had the same old package line `package com.dogancaglar.paymentservice.infra.config`
                        # we can just blindly replace it if the file path matches the dst_dir.
                        abspath = os.path.abspath(path)
                        abs_dst = os.path.abspath(dst_dir)
                        if abspath.startswith(abs_dst):
                            new_content = new_content.replace(old_pkg_line, new_pkg_line, 1)

            if content != new_content:
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Updated contents of {path}")

# Cleanup empty infra/config dirs
os.system("find . -type d -empty -delete")

