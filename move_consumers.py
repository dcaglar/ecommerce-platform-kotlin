import os
import shutil

moves = [
    ("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/consumers/DynamicKafkaConsumersProperties.kt", 
     "payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/inbound/kafka", 
     "com.dogancaglar.paymentservice.infra.adapter.inbound.kafka"),
     
    ("payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/consumers/KafkaTypedConsumerFactoryConfig.kt", 
     "payment-consumers/src/main/kotlin/com/dogancaglar/paymentservice/infra/adapter/inbound/kafka", 
     "com.dogancaglar.paymentservice.infra.adapter.inbound.kafka"),
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
    
    old_pkg = "com.dogancaglar.paymentservice.consumers"
    class_name = filename.replace(".kt", "")
    
    replacements.append((f"import {old_pkg}.{class_name}", f"import {new_pkg}.{class_name}"))

# Update imports and package lines
for root, dirs, files in os.walk("."):
    if "target" in root or ".git" in root or ".idea" in root:
        continue
    for file in files:
        if file.endswith(".kt"):
            path = os.path.join(root, file)
            with open(path, "r") as f:
                content = f.read()
            
            new_content = content
            
            # Update imports
            for old_imp, new_imp in replacements:
                if old_imp in new_content:
                    new_content = new_content.replace(old_imp, new_imp)
            
            # Update package declaration if this is one of the moved files
            for src, dst_dir, new_pkg in moves:
                filename = os.path.basename(src)
                if file == filename:
                    old_pkg_line = "package com.dogancaglar.paymentservice.consumers"
                    new_pkg_line = f"package {new_pkg}"
                    
                    abspath = os.path.abspath(path)
                    abs_dst = os.path.abspath(dst_dir)
                    if abspath.startswith(abs_dst):
                        new_content = new_content.replace(old_pkg_line, new_pkg_line, 1)

            if content != new_content:
                with open(path, "w") as f:
                    f.write(new_content)
                print(f"Updated contents of {path}")

# Cleanup empty dirs
os.system("find . -type d -empty -delete")

