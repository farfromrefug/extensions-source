#!/usr/bin/env python3
"""
Script to update extension version codes in build.gradle files.
Increments extVersionCode by 1 and updates versionName in common.gradle for minor/major.
"""
import os
import sys
import re
from pathlib import Path
from typing import List, Tuple


def find_build_gradle_files(src_dir: str) -> List[Path]:
    """Find all build.gradle files in src directory."""
    src_path = Path(src_dir)
    return list(src_path.rglob("build.gradle"))


def update_version_code(file_path: Path) -> Tuple[str, int, int]:
    """
    Update extVersionCode in a build.gradle file by incrementing by 1.
    Returns tuple of (app_name, old_version, new_version)
    """
    content = file_path.read_text()
    
    # Find extName
    name_match = re.search(r"extName\s*=\s*['\"](.+?)['\"]", content)
    app_name = name_match.group(1) if name_match else file_path.parent.name
    
    # Find and update extVersionCode using consistent regex pattern
    version_pattern = r"extVersionCode\s*=\s*\d+"
    version_match = re.search(version_pattern, content)
    if not version_match:
        print(f"Warning: No extVersionCode found in {file_path}", file=sys.stderr)
        return app_name, 0, 0
    
    # Extract just the number for old version
    old_version_match = re.search(r"\d+", version_match.group(0))
    old_version = int(old_version_match.group(0)) if old_version_match else 0
    new_version = old_version + 1
    
    # Replace only the first occurrence of extVersionCode
    new_content = re.sub(
        version_pattern,
        f"extVersionCode = {new_version}",
        content,
        count=1
    )
    
    file_path.write_text(new_content)
    return app_name, old_version, new_version


def update_version_name(common_gradle_path: str, update_type: str) -> Tuple[str, str]:
    """
    Update versionName in common.gradle for minor/major updates.
    Returns tuple of (old_version_name, new_version_name)
    """
    gradle_file = Path(common_gradle_path)
    if not gradle_file.exists():
        print(f"Warning: common.gradle not found at {common_gradle_path}", file=sys.stderr)
        return "", ""
    
    content = gradle_file.read_text()
    
    # Find current versionName like: versionName "1.4.$versionCode"
    version_name_pattern = r'versionName\s+"(\d+)\.(\d+)\.\$versionCode"'
    match = re.search(version_name_pattern, content)
    
    if not match:
        print("Warning: versionName pattern not found in common.gradle", file=sys.stderr)
        return "", ""
    
    current_major = int(match.group(1))
    current_minor = int(match.group(2))
    old_version = f"{current_major}.{current_minor}.$versionCode"
    
    if update_type == 'minor':
        new_major = current_major
        new_minor = current_minor + 1
    elif update_type == 'major':
        new_major = current_major + 1
        new_minor = 0
    else:
        # patch - no change to versionName
        return old_version, old_version
    
    new_version = f"{new_major}.{new_minor}.$versionCode"
    
    # Update the versionName
    new_content = re.sub(
        version_name_pattern,
        f'versionName "{new_major}.{new_minor}.$versionCode"',
        content,
        count=1
    )
    
    gradle_file.write_text(new_content)
    return old_version, new_version


def main():
    if len(sys.argv) < 3:
        print("Usage: update-versions.py <src_dir> <update_type>", file=sys.stderr)
        print("  update_type: none, patch, minor, or major", file=sys.stderr)
        sys.exit(1)
    
    src_dir = sys.argv[1]
    update_type = sys.argv[2]
    
    # Validate update type
    valid_types = ['none', 'patch', 'minor', 'major']
    if update_type not in valid_types:
        raise ValueError(
            f"Invalid update_type '{update_type}'. "
            f"Must be one of: {', '.join(valid_types)}"
        )
    
    if update_type == 'none':
        print(f"No version update (type: {update_type})", file=sys.stderr)
        return
    
    # Update extVersionCode in all build.gradle files
    build_files = find_build_gradle_files(src_dir)
    
    if not build_files:
        print(f"No build.gradle files found in {src_dir}", file=sys.stderr)
        return
    
    print(f"Updating versions with {update_type} type:", file=sys.stderr)
    print(f"  extVersionCode increment: +1", file=sys.stderr)
    
    for build_file in build_files:
        app_name, old_ver, new_ver = update_version_code(build_file)
        print(f"  {app_name}: extVersionCode {old_ver} -> {new_ver}", file=sys.stderr)
    
    # Update versionName in common.gradle for minor/major
    if update_type in ['minor', 'major']:
        # Assume common.gradle is in the root directory
        root_dir = Path(src_dir).parent if Path(src_dir).name == 'src' else Path(src_dir)
        common_gradle_path = root_dir / "common.gradle"
        
        old_version_name, new_version_name = update_version_name(str(common_gradle_path), update_type)
        if old_version_name and new_version_name and old_version_name != new_version_name:
            print(f"  versionName: {old_version_name} -> {new_version_name}", file=sys.stderr)


if __name__ == "__main__":
    main()
