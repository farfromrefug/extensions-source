#!/usr/bin/env python3
"""
Script to manage GitHub releases for extensions.
Handles versioning and release creation/updates.
"""
import os
import sys
import subprocess
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
    """Parse semantic version from tag (e.g., v1.2.3 or 1.2.3)."""
    tag = tag.lstrip('v')
    parts = tag.split('.')
    return int(parts[0]), int(parts[1]), int(parts[2])


def increment_version(tag: str, update_type: str = 'patch') -> str:
    """
    Increment version based on update type.
    
    Args:
        tag: Current version tag (e.g., v1.2.3)
        update_type: Type of update (patch, minor, major)
    
    Returns:
        New version tag
    """
    major, minor, patch = parse_semver(tag)
    
    if update_type == 'major':
        return f"v{major + 1}.0.0"
    elif update_type == 'minor':
        return f"v{major}.{minor + 1}.0"
    else:  # patch or default
        return f"v{major}.{minor}.{patch + 1}"


def determine_version(update_type: str = 'none') -> str:
    """
    Determine the version to use for the release.
    
    Args:
        update_type: Type of version update (none, patch, minor, major)
    
    Returns:
        Version tag to use
    """
    current_tag = get_current_tag()
    if current_tag:
        print(f"Building from tag: {current_tag}", file=sys.stderr)
        # If update type is specified, increment from current tag
        if update_type != 'none':
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
    
    # Increment based on update type
    new_version = increment_version(latest_tag, update_type)
    print(f"Creating new version: {latest_tag} -> {new_version}", file=sys.stderr)
    return new_version


if __name__ == "__main__":
    # Get update type from command line argument or default to 'none'
    update_type = sys.argv[1] if len(sys.argv) > 1 else 'none'
    version = determine_version(update_type)
    print(version)
