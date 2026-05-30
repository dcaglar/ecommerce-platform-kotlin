import os
import shutil
import re

# 1. Map all source files to their packages
src_map = {}  # ClassName -> (module, package_path, full_path, package_declaration)
for root, dirs, files in os.walk("."):
    if "src/main/kotlin" not in root or ".git" in root or "target" in root:
        continue
    for file in files:
        if file.endswith(".kt"):
            class_name = file.replace(".kt", "")
            with open(os.path.join(root, file), "r") as f:
                content = f.read()
            # find package
            pkg_match = re.search(r"^package\s+([a-zA-Z0-9_.]+)", content, re.MULTILINE)
            if pkg_match:
                pkg = pkg_match.group(1)
                module = root.split("/")[1]  # ./payment-service/src/...
                src_map[class_name] = (module, root, os.path.join(root, file), pkg)

# 2. Iterate test files
moves = 0
for root, dirs, files in os.walk("."):
    if "src/test/kotlin" not in root or ".git" in root or "target" in root:
        continue
    for file in files:
        if file.endswith("Test.kt"):
            test_class_name = file.replace(".kt", "")
            target_class_name = test_class_name.replace("Test", "")
            target_class_name = target_class_name.replace("Integration", "")
            
            # Find the target class in src_map
            # First try exact match
            match = src_map.get(target_class_name)
            
            if match:
                module, src_dir, src_path, src_pkg = match
                # check if test file is in the right package
                with open(os.path.join(root, file), "r") as f:
                    content = f.read()
                test_pkg_match = re.search(r"^package\s+([a-zA-Z0-9_.]+)", content, re.MULTILINE)
                if test_pkg_match:
                    test_pkg = test_pkg_match.group(1)
                    if test_pkg != src_pkg:
                        # We need to move the test file!
                        print(f"Mismatch for {file}: src package is {src_pkg}, test package is {test_pkg}")
                        
                        # Determine new test path
                        # e.g. payment-service/src/test/kotlin/com/dogancaglar/...
                        new_test_dir = src_dir.replace("src/main/kotlin", "src/test/kotlin")
                        os.makedirs(new_test_dir, exist_ok=True)
                        
                        old_test_path = os.path.join(root, file)
                        new_test_path = os.path.join(new_test_dir, file)
                        
                        if old_test_path != new_test_path:
                            shutil.move(old_test_path, new_test_path)
                            
                            # Update package line
                            new_content = re.sub(r"^package\s+[a-zA-Z0-9_.]+", f"package {src_pkg}", content, flags=re.MULTILINE)
                            with open(new_test_path, "w") as f:
                                f.write(new_content)
                            print(f"Moved {old_test_path} to {new_test_path}")
                            moves += 1

print(f"Total moves: {moves}")
