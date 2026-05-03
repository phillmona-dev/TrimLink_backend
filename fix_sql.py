import os
def fix_sql():
    path = "src/main/resources/db/migration/V1__init_schema.sql"
    with open(path, 'r') as f:
        content = f.read()
    
    # We already replaced "staff_profiles" with "staff_profiles", etc. via refactor.py but let's be exhaustive
    content = content.replace("staff_profiles", "staff_profiles")
    content = content.replace("staff_id", "staff_id")
    content = content.replace("staff_shop_id", "shop_id") # actually wait, shop is StaffShop. Let's not blindly replace staff_id because it might be staff_shop_id. Let's just replace 'STAFF' to 'STAFF' in role constraint
    content = content.replace("'CUSTOMER', 'STAFF'", "'CUSTOMER', 'STAFF'")
    content = content.replace("staff_service_assignments", "staff_service_assignments")
    content = content.replace("staff_schedules", "staff_schedules")
    
    with open(path, 'w') as f:
        f.write(content)
fix_sql()
