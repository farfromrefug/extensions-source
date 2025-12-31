#!/usr/bin/env python3
"""
Script to manage GitHub releases for extensions.
Handles versioning and release creation/updates.
"""
import os
import sys
import re
import subprocess
from pathlib import Path
from typing import Optional, Tuple


def get_latest_tag() -> Optional[str]:
    """Get the latest git tag."""
    try:
        result = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            capture_output=True,
            text=True,
            check=True
        )
        return result.stdout.strip()
    except subprocess.CalledProcessError:
        return None


def get_current_tag() -> Optional[str]:
    """Get the current git tag if we're on a tag."""
    try:
        result = subprocess.run(
            ["git", "describe", "--exact-match", "--tags", "HEAD"],
            capture_output=True,
            text=True,
            check=False
        )
        if result.returncode == 0:
            return result.stdout.strip()
        return None
    except subprocess.CalledProcessError:
        return None


def parse_semver(tag: str) -> Tuple[int, int, int]:
    """
    Parse semantic version from tag (e.g., v1.2.3 or 1.2.3).
    
    Args:
        tag: Version tag string
        
    Returns:
        Tuple of (major, minor, patch) version numbers
        
    Raises:
        ValueError: If tag format is invalid
    """
    tag = tag.lstrip('v')
    parts = tag.split('.')
    
    if len(parts) != 3:
        raise ValueError(
            f"Invalid version tag '{tag}'. "
            "Expected format: v<major>.<minor>.<patch> (e.g., v1.2.3)"
        )
    
    try:
        return int(parts[0]), int(parts[1]), int(parts[2])
    except ValueError:
        raise ValueError(
            f"Invalid version tag '{tag}'. "
            "All version components must be integers."
        )


def increment_version(tag: str, update_type: str = 'patch') -> str:
    """
    Increment version based on update type.
    
    Args:
        tag: Current version tag (e.g., v1.2.3)
        update_type: Type of update ('patch', 'minor', or 'major')
    
    Returns:
        New version tag
        
    Raises:
        ValueError: If update_type is not valid
    """
    valid_types = ['patch', 'minor', 'major']
    if update_type not in valid_types:
        raise ValueError(
            f"Invalid update_type '{update_type}'. "
            f"Must be one of: {', '.join(valid_types)}"
        )
    
    major, minor, patch = parse_semver(tag)
    
    if update_type == 'major':
        return f"v{major + 1}.0.0"
    elif update_type == 'minor':
        return f"v{major}.{minor + 1}.0"
    else:  # patch
        return f"v{major}.{minor}.{patch + 1}"


def get_version_from_gradle() -> Optional[str]:
    """
    Get the actual versionName from common.gradle.
    This represents the version that will be in the built APKs.
    
    Returns:
        Version string (e.g., "2.0") extracted from versionName pattern,
        or None if not found.
    """
    common_gradle = Path("common.gradle")
    if not common_gradle.exists():
        print("Warning: common.gradle not found", file=sys.stderr)
        return None
    
    content = common_gradle.read_text()
    # Match versionName pattern like: versionName "2.0.$versionCode"
    # Flexible whitespace and quote handling
    match = re.search(r'versionName\s+["\'](\d+\.\d+)\.\$versionCode["\']', content)
    if match:
        return match.group(1)
    
    print("Warning: versionName pattern not found in common.gradle", file=sys.stderr)
    return None


def get_max_version_code(src_dir: str = "src") -> int:
    """
    Get the maximum extVersionCode from all build.gradle files.
    This is used to determine the patch version for the release tag.
    
    Returns:
        Maximum extVersionCode found, or 0 if none found
    """
    src_path = Path(src_dir)
    if not src_path.exists():
        print(f"Warning: {src_dir} directory not found", file=sys.stderr)
        return 0
    
    max_version_code = 0
    build_files = list(src_path.rglob("build.gradle"))
    
    for build_file in build_files:
        content = build_file.read_text()
        match = re.search(r"extVersionCode\s*=\s*(\d+)", content)
        if match:
            version_code = int(match.group(1))
            max_version_code = max(max_version_code, version_code)
    
    return max_version_code


def get_actual_version() -> Optional[str]:
    """
    Get the actual version that will be in the built APKs.
    Combines versionName from common.gradle with max extVersionCode.
    
    Returns:
        Version string (e.g., "v2.0.65") or None if unable to determine
    """
    gradle_version = get_version_from_gradle()
    max_version_code = get_max_version_code()
    if gradle_version and max_version_code > 0:
        return f"v{gradle_version}.{max_version_code}"
    return None


def determine_version(update_type: str = 'none') -> str:
    """
    Determine the version to use for the release.
    
    For version updates (patch/minor/major), this uses the actual version
    from common.gradle combined with the maximum extVersionCode to create
    a tag like "v2.0.65" that matches what's in the built APKs.
    
    Args:
        update_type: Type of version update (none, patch, minor, major)
    
    Returns:
        Version tag to use (e.g., "v2.0.65")
    """
    current_tag = get_current_tag()
    if current_tag:
        print(f"Building from tag: {current_tag}", file=sys.stderr)
        # If update type is specified, use the actual version from gradle
        if update_type != 'none':
            actual_version = get_actual_version()
            if actual_version:
                print(f"Using actual version from APKs: {actual_version}", file=sys.stderr)
                return actual_version
            # Fallback to old behavior
            new_version = increment_version(current_tag, update_type)
            print(f"Incrementing version: {current_tag} -> {new_version}", file=sys.stderr)
            return new_version
        return current_tag
    
    latest_tag = get_latest_tag()
    if not latest_tag:
        print("No existing tags found, using v1.0.0", file=sys.stderr)
        return "v1.0.0"
    
    # If no update type specified, keep the same version (update release)
    if update_type == 'none':
        print(f"Updating latest release: {latest_tag}", file=sys.stderr)
        return latest_tag
    
    # For version updates, use the actual version from gradle
    actual_version = get_actual_version()
    if actual_version:
        print(f"Creating new version from APKs: {actual_version}", file=sys.stderr)
        return actual_version
    
    # Fallback to old behavior
    new_version = increment_version(latest_tag, update_type)
    print(f"Creating new version: {latest_tag} -> {new_version}", file=sys.stderr)
    return new_version


if __name__ == "__main__":
    # Get update type from command line argument or default to 'none'
    update_type = sys.argv[1] if len(sys.argv) > 1 else 'none'
    version = determine_version(update_type)
    print(version)
