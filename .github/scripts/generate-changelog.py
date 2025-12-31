#!/usr/bin/env python3
"""
Script to generate changelog from conventional commits.
Detects which app is affected by checking file paths.
"""
import os
import sys
import subprocess
import re
from pathlib import Path
from collections import defaultdict
from typing import Optional, List, Dict, Set, Any


def get_app_from_path(file_path: str) -> Optional[str]:
    """Extract app name from file path if it's in src directory."""
    # Pattern: src/{category}/{app_name}/...
    match = re.match(r'src/([^/]+)/([^/]+)/', file_path)
    if match:
        return match.group(2)  # Return app_name
    return None


def get_commits_since_tag(tag: Optional[str]) -> List[str]:
    """Get commits since the given tag, or all commits if no tag."""
    if tag:
        cmd = ["git", "log", f"{tag}..HEAD", "--pretty=format:%H"]
    else:
        cmd = ["git", "log", "--pretty=format:%H"]
    
    result = subprocess.run(cmd, capture_output=True, text=True, check=True)
    return result.stdout.strip().split('\n') if result.stdout.strip() else []


def get_commit_info(commit_hash: str) -> Dict[str, Any]:
    """Get commit message and changed files for a commit."""
    try:
        # Get commit message
        msg_result = subprocess.run(
            ["git", "log", "-1", "--pretty=format:%s", commit_hash],
            capture_output=True,
            text=True,
            check=True
        )
        message = msg_result.stdout.strip()
        
        # Get changed files - use git show for initial commits
        files_result = subprocess.run(
            ["git", "show", "--name-only", "--pretty=", commit_hash],
            capture_output=True,
            text=True,
            check=True
        )
        files = files_result.stdout.strip().split('\n') if files_result.stdout.strip() else []
        
        return {
            'message': message,
            'files': files,
            'hash': commit_hash[:7]
        }
    except subprocess.CalledProcessError as e:
        print(f"Warning: Failed to get info for commit {commit_hash}: {e}", file=sys.stderr)
        return {
            'message': f"<commit {commit_hash[:7]}>",
            'files': [],
            'hash': commit_hash[:7]
        }


def parse_conventional_commit(message: str) -> Optional[Dict[str, Any]]:
    """
    Parse conventional commit message.
    Format: type(scope)?: description, type!: description, or type(scope)!: description
    """
    # Match: type(scope)?: description, type!: description, or type(scope)!: description
    pattern = r'^(\w+)(?:\(([^)]+)\))?(!)?:\s*(.+)$'
    match = re.match(pattern, message)
    
    if match:
        return {
            'type': match.group(1),
            'scope': match.group(2),
            'breaking': match.group(3) == '!',
            'description': match.group(4)
        }
    return None


def detect_affected_apps(files: List[str]) -> Set[str]:
    """Detect which apps are affected by the changed files."""
    apps = set()
    for file_path in files:
        app = get_app_from_path(file_path)
        if app:
            apps.add(app)
    return apps


def generate_changelog(since_tag: Optional[str] = None) -> str:
    """Generate changelog from commits since the given tag."""
    commits = get_commits_since_tag(since_tag)
    
    if not commits:
        return "No changes since last release."
    
    # Organize commits by type
    changelog_data = defaultdict(list)
    
    for commit_hash in commits:
        commit_info = get_commit_info(commit_hash)
        message = commit_info['message']
        files = commit_info['files']
        
        # Parse conventional commit
        parsed = parse_conventional_commit(message)
        
        # Detect affected apps
        affected_apps = detect_affected_apps(files)
        
        if parsed:
            commit_type = parsed['type']
            description = parsed['description']
            
            # Add app names to description if detected
            if affected_apps:
                app_tags = ', '.join(sorted(affected_apps))
                description = f"{description} ({app_tags})"
            
            changelog_data[commit_type].append({
                'description': description,
                'breaking': parsed['breaking'],
                'hash': commit_info['hash']
            })
        else:
            # Non-conventional commit
            description = message
            if affected_apps:
                app_tags = ', '.join(sorted(affected_apps))
                description = f"{description} ({app_tags})"
            
            changelog_data['other'].append({
                'description': description,
                'breaking': False,
                'hash': commit_info['hash']
            })
    
    # Build changelog markdown
    changelog_lines = []
    
    # Type ordering and labels
    type_labels = {
        'feat': '✨ Features',
        'fix': '🐛 Bug Fixes',
        'docs': '📚 Documentation',
        'style': '💄 Styles',
        'refactor': '♻️ Refactoring',
        'perf': '⚡ Performance',
        'test': '✅ Tests',
        'build': '👷 Build',
        'ci': '🔧 CI/CD',
        'chore': '🔨 Chores',
        'other': '📝 Other Changes'
    }
    
    # Add breaking changes first if any
    breaking_changes = []
    for commit_type, commits_list in changelog_data.items():
        for commit in commits_list:
            if commit['breaking']:
                breaking_changes.append(f"- **BREAKING**: {commit['description']} ({commit['hash']})")
    
    if breaking_changes:
        changelog_lines.append("## ⚠️ Breaking Changes\n")
        changelog_lines.extend(breaking_changes)
        changelog_lines.append("")
    
    # Add sections by type
    for commit_type in ['feat', 'fix', 'docs', 'style', 'refactor', 'perf', 'test', 'build', 'ci', 'chore', 'other']:
        if commit_type in changelog_data and changelog_data[commit_type]:
            label = type_labels.get(commit_type, commit_type.capitalize())
            changelog_lines.append(f"## {label}\n")
            
            for commit in changelog_data[commit_type]:
                if not commit['breaking']:  # Non-breaking changes
                    changelog_lines.append(f"- {commit['description']} ({commit['hash']})")
            
            changelog_lines.append("")
    
    return '\n'.join(changelog_lines).strip()


def main():
    """Main entry point."""
    since_tag = sys.argv[1] if len(sys.argv) > 1 else None
    
    if since_tag and since_tag.strip():
        print(f"Generating changelog since {since_tag}...", file=sys.stderr)
    else:
        print("Generating changelog for all commits...", file=sys.stderr)
        since_tag = None
    
    changelog = generate_changelog(since_tag)
    print(changelog)


if __name__ == "__main__":
    main()
