import os

files_to_fix = [
    "payment-infrastructure/src/test/resources/application-integration.yaml",
    "payment-infrastructure/src/test/resources/application-test.yaml",
    "payment-infrastructure/src/test/resources/application.yaml",
    "payment-service/src/test/resources/application.yaml"
]

for path in files_to_fix:
    if os.path.exists(path):
        with open(path, "r") as f:
            content = f.read()
        
        # fix type-aliases-package
        content = content.replace(
            "com.dogancaglar.paymentservice.adapter.outbound.persistence.entity",
            "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.entity"
        )
        
        # fix type-handlers-package
        content = content.replace(
            "com.dogancaglar.paymentservice.infra.adapter.outbound.persistence.typehandler",
            "com.dogancaglar.common.db.typehandler"
        )
        
        with open(path, "w") as f:
            f.write(content)
        print(f"Fixed {path}")

