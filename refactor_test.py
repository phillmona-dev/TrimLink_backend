import os

replacements = {
    "StaffProfile": "StaffProfile",
    "staffProfile": "staffProfile",
    "staff_profile": "staff_profile",
    "StaffServiceAssignment": "StaffServiceAssignment",
    "staffServiceAssignment": "staffServiceAssignment",
    "StaffSchedule": "StaffSchedule",
    "staffSchedule": "staffSchedule",
    "staff_profiles": "staff_profiles",
    "staff_service_assignments": "staff_service_assignments",
    "staff_schedules": "staff_schedules",
    "Role.STAFF": "Role.STAFF"
}

def rename_files(directory):
    for root, dirs, files in os.walk(directory):
        for name in files:
            if name.endswith('.java'):
                new_name = name.replace("StaffProfile", "StaffProfile") \
                               .replace("StaffServiceAssignment", "StaffServiceAssignment") \
                               .replace("StaffSchedule", "StaffSchedule")
                if new_name != name:
                    os.rename(os.path.join(root, name), os.path.join(root, new_name))

def process_file_content(file_path):
    with open(file_path, 'r') as f:
        content = f.read()
    
    new_content = content
    for old, new in replacements.items():
        new_content = new_content.replace(old, new)
        
    if new_content != content:
        with open(file_path, 'w') as f:
            f.write(new_content)

def main():
    directory = 'src/test/java'
    
    rename_files(directory)
    rename_files(directory) 
    
    for root, dirs, files in os.walk(directory):
        for name in files:
            if name.endswith('.java'):
                process_file_content(os.path.join(root, name))

if __name__ == "__main__":
    main()
