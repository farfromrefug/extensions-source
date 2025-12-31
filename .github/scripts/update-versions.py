#!/usr/bin/env python3
"""
Script to update extension version codes in build.gradle files.
Increments extVersionCode based on semver type.
"""
import os
import sys
import re
from pathlib import Path
from typing import List


def find_build_gradle_files(src_dir: str) -> List[Path]:
    """Find all build.gradle files in src directory."""
    src_path = Path(src_dir)
    return list(src_path.rglob("build.gradle"))


def update_version_code(file_path: Path, increment: int = 1) -> tuple[str, int, int]:
    """
    Update extVersionCode in a build.gradle file.
    Returns tuple of (app_name, old_version, new_version)
    """
    content = file_path.read_text()
    
    # Find extName
    name_match = re.search(r"extName\s*=\s*['\"](.+?)['\"]", content)
    app_name = name_match.group(1) if name_match else file_path.parent.name
    
    # Find and update extVersionCode
    version_match = re.search(r"(extVersionCode\s*=\s*)(\d+)", content)
    if not version_match:
        print(f"Warning: No extVersionCode found in {file_path}", file=sys.stderr)
        return app_name, 0, 0
    
    old_version = int(version_match.group(2))
    new_version = old_version + increment
    
    # Replace the version
    new_content = content.replace(
        version_match.group(0),
        f"{version_match.group(1)}{new_version}"
    )
    
    file_path.write_text(new_content)
    return app_name, old_version, new_version


def get_version_increment(update_type: str) -> int:
    """
    Get the increment value based on update type.
    For extVersionCode, we use simple increments:
    - patch: +1
    - minor: +10
    - major: +100
    """
    increments = {
        'none': 0,
        'patch': 1,
        'minor': 10,
        'major': 100
    }
    return increments.get(update_type, 0)


def main():
    if len(sys.argv) < 3:
        print("Usage: update-versions.py <src_dir> <update_type>", file=sys.stderr)
        print("  update_type: none, patch, minor, or major", file=sys.stderr)
        sys.exit(1)
    
    src_dir = sys.argv[1]
    update_type = sys.argv[2]
    
    increment = get_version_increment(update_type)
    
    if increment == 0:
        print(f"No version update (type: {update_type})", file=sys.stderr)
        return
    
    build_files = find_build_gradle_files(src_dir)
    
    if not build_files:
        print(f"No build.gradle files found in {src_dir}", file=sys.stderr)
        return
    
    print(f"Updating versions with {update_type} increment (+{increment}):", file=sys.stderr)
    for build_file in build_files:
        app_name, old_ver, new_ver = update_version_code(build_file, increment)
        print(f"  {app_name}: {old_ver} -> {new_ver}", file=sys.stderr)


if __name__ == "__main__":
    main()
