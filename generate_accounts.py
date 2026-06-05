import csv

accounts = [
    ["PSP_RECEIVABLES.GLOBAL.EUR", "PSP_RECEIVABLES", "GLOBAL", "EUR", "ASSET", "NL", "ACTIVE"],
    ["AUTH_RECEIVABLE.GLOBAL.EUR", "AUTH_RECEIVABLE", "GLOBAL", "EUR", "ASSET", "NL", "ACTIVE"],
    ["AUTH_LIABILITY.GLOBAL.EUR", "AUTH_LIABILITY", "GLOBAL", "EUR", "LIABILITY", "NL", "ACTIVE"],
    ["PROCESSING_FEE_REVENUE.GLOBAL.EUR", "PROCESSING_FEE_REVENUE", "GLOBAL", "EUR", "REVENUE", "NL", "ACTIVE"]
]

for m in range(1, 16):
    operator_id = f"MARKETPLACE-{m}"
    
    # MARKETPLACE_OPERATOR
    accounts.append([
        f"MARKETPLACE_OPERATOR.{operator_id}.EUR", 
        "MARKETPLACE_OPERATOR", 
        operator_id, 
        "EUR", "LIABILITY", "NL", "ACTIVE"
    ])
    
    # PLATFORM_COMMISSION_ESCROW
    accounts.append([
        f"PLATFORM_COMMISSION_ESCROW.{operator_id}.EUR", 
        "PLATFORM_COMMISSION_ESCROW", 
        operator_id, 
        "EUR", "LIABILITY", "NL", "ACTIVE"
    ])
    
    # 10 MARKETPLACE_SUB_SELLER
    for s in range(1, 11):
        seller_id = f"SELLER-{m}-{s}"
        accounts.append([
            f"MARKETPLACE_SUB_SELLER.{seller_id}.EUR", 
            "MARKETPLACE_SUB_SELLER", 
            seller_id, 
            "EUR", "LIABILITY", "NL", "ACTIVE"
        ])

with open('payment-consumers/src/main/resources/db/data/account_directory.csv', 'w', newline='') as f:
    writer = csv.writer(f)
    writer.writerow(["account_code", "account_type", "entity_id", "currency", "category", "country", "status"])
    writer.writerows(accounts)

