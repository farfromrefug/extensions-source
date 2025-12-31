#!/usr/bin/env python3
"""
Script to update extension version codes in build.gradle files.
Increments extVersionCode based on semver type.
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


def update_version_code(file_path: Path, increment: int = 1) -> Tuple[str, int, int]:
    """
    Update extVersionCode in a build.gradle file.
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
    new_version = old_version + increment
    
    # Replace only the first occurrence of extVersionCode
    new_content = re.sub(
        version_pattern,
        f"extVersionCode = {new_version}",
        content,
        count=1
    )
    
    file_path.write_text(new_content)
    return app_name, old_version, new_version


def get_version_increment(update_type: str) -> int:
    """
    Get the increment value based on update type.
    For extVersionCode, we use simple increments:
    - none: no change (0)
    - patch: +1
    - minor: +10
    - major: +100
    
    Args:
        update_type: One of 'none', 'patch', 'minor', or 'major'
    
    Returns:
        Increment value
        
    Raises:
        ValueError: If update_type is not recognized
    """
    increments = {
        'none': 0,
        'patch': 1,
        'minor': 10,
        'major': 100
    }
    
    if update_type not in increments:
        raise ValueError(
            f"Invalid update_type '{update_type}'. "
            f"Must be one of: {', '.join(increments.keys())}"
        )
    
    return increments[update_type]


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
