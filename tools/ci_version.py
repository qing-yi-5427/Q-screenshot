#!/usr/bin/env python3
"""Deterministic Android versions and stable-release ordering; no network or mutations."""
import argparse
import json
import re
from pathlib import Path

SEMVER = re.compile(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)')

def version_key(value):
    match = SEMVER.fullmatch(value)
    if not match:
        raise ValueError('Use canonical MAJOR.MINOR.PATCH, without leading zeros or suffixes')
    return tuple(int(part) for part in match.groups())

def android_version(value):
    major, minor, patch = version_key(value)
    if major > 2099 or minor > 999 or patch > 999:
        raise ValueError('Version range: major0..2099, minor/patch0..999')
    code = major * 1_000_000 + minor * 1_000 + patch
    if not 0 < code <= 2_100_000_000:
        raise ValueError('versionCode must be positive and within the Android publishing limit')
    return value, code

def should_mark_latest(tag, pages):
    """Caller serializes publication; rerunning an older stable tag never promotes it."""
    if not tag.startswith('v'):
        raise ValueError('Release tags must start with v')
    target = version_key(tag[1:])
    releases = [item for page in pages for item in page] if pages and isinstance(pages[0], list) else pages
    stable = []
    target_exists = False
    for release in releases:
        if release.get('draft') or release.get('prerelease'):
            continue
        name = release.get('tag_name', '')
        if not name.startswith('v'):
            continue
        try:
            key = version_key(name[1:])
        except ValueError:
            continue
        stable.append(key)
        target_exists |= name == tag
    if not target_exists:
        raise ValueError('Current stable release must exist before latest comparison')
    return target == max(stable)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    version = sub.add_parser('version')
    source = version.add_mutually_exclusive_group(required=True)
    source.add_argument('--tag')
    source.add_argument('--version')
    version.add_argument('--github-output', type=Path)
    latest = sub.add_parser('latest')
    latest.add_argument('--tag', required=True)
    latest.add_argument('--releases', type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.command == 'version':
            if args.tag is not None and not args.tag.startswith('v'):
                raise ValueError('Release tags must start with v')
            name, code = android_version(args.tag[1:] if args.tag else args.version)
            result = f'version_name={name}\nversion_code={code}\n'
            if args.github_output:
                with args.github_output.open('a', encoding='utf-8') as output:
                    output.write(result)
            print(result, end='')
        else:
            print(str(should_mark_latest(args.tag, json.loads(args.releases.read_text()))).lower())
    except ValueError as error:
        parser.error(str(error))

if __name__ == '__main__':
    main()
