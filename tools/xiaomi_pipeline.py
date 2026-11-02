#!/usr/bin/env python3
"""Collect -> review/resolve -> compile profile -> staged Blender build/validate."""
import argparse
import json
from pathlib import Path
from device_pipeline.research import collect, resolve, save
from device_pipeline.model import existing, compile_slab, build, read, safe_output, reconcile


def main():
    p=argparse.ArgumentParser(description=__doc__)
    sub=p.add_subparsers(dest='command',required=True)
    c=sub.add_parser('collect');c.add_argument('--config',required=True,type=Path);c.add_argument('--output',required=True,type=Path);c.add_argument('--offline',type=Path);c.add_argument('--max-pages',type=int,default=12)
    c=sub.add_parser('resolve');c.add_argument('--research',required=True,type=Path);c.add_argument('--selections',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('images');c.add_argument('--research',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('review-images');c.add_argument('--index',required=True,type=Path);c.add_argument('--annotations',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('replay');c.add_argument('--device',required=True);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('reconcile');c.add_argument('--profile',required=True,type=Path);c.add_argument('--research',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('compile-slab');c.add_argument('--research',required=True,type=Path);c.add_argument('--output',required=True,type=Path)
    c=sub.add_parser('build');c.add_argument('--profile',required=True,type=Path);c.add_argument('--output',required=True,type=Path);c.add_argument('--blender');c.add_argument('--dry-run',action='store_true')
    a=p.parse_args()
    if a.command=='collect':
        if not 1 <= a.max_pages <= 30: p.error('max-pages must be 1..30')
        result=collect(read(a.config),safe_output(a.output),a.offline,a.max_pages)
        print(json.dumps({k:len(result[k]) for k in ('sources','candidates','images','failures')}))
        return 0 if result['sources'] else 2
    elif a.command=='resolve':
        safe_output(a.output)
        save(a.output,resolve(read(a.research),read(a.selections)))
    elif a.command=='images':
        from device_pipeline.images import download
        download(read(a.research),safe_output(a.output))
    elif a.command=='review-images':
        from device_pipeline.images import review
        safe_output(a.output)
        save(a.output,review(read(a.index),read(a.annotations)))
    elif a.command=='replay':existing(a.device,a.output)
    elif a.command=='reconcile':reconcile(read(a.profile),read(a.research),a.output)
    elif a.command=='compile-slab':compile_slab(read(a.research),a.output)
    elif a.command=='build':print(json.dumps(build(a.profile,a.output,a.blender,a.dry_run),indent=2))
    return 0

if __name__=='__main__':
    raise SystemExit(main())
