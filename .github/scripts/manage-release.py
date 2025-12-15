#!/usr/bin/env python3
"""
Script to manage GitHub releases for extensions.
Handles versioning and release creation/updates.
"""
import os
import sys
import subprocess
from typing import Optional


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


def parse_semver(tag: str) -> tuple[int, int, int]:
    """Parse semantic version from tag (e.g., v1.2.3 or 1.2.3)."""
    tag = tag.lstrip('v')
    parts = tag.split('.')
    return int(parts[0]), int(parts[1]), int(parts[2])


def increment_version(tag: str) -> str:
    """Increment the patch version."""
    major, minor, patch = parse_semver(tag)
    return f"v{major}.{minor}.{patch + 1}"


def determine_version() -> str:
    """
    Determine the version to use for the release.
    - If no tags exist, return v1.0.0
    - If building from a tag, use that tag
    - Otherwise, update the latest existing tag (don't increment)
    """
    current_tag = get_current_tag()
    if current_tag:
        print(f"Building from tag: {current_tag}", file=sys.stderr)
        return current_tag
    
    latest_tag = get_latest_tag()
    if not latest_tag:
        print("No existing tags found, using v1.0.0", file=sys.stderr)
        return "v1.0.0"
    
    print(f"Updating latest release: {latest_tag}", file=sys.stderr)
    return latest_tag


if __name__ == "__main__":
    version = determine_version()
    print(version)
