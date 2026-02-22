#!/usr/bin/env python3
import os
import glob
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
TMP_DIRS = [os.path.join(ROOT, 'tmp'), os.path.join(ROOT, 'build', 'tmp')]

found = []
for d in TMP_DIRS:
    if os.path.isdir(d):
        for p in ('*.json','*.jsonl'):
            found.extend(sorted(glob.glob(os.path.join(d,p))))
print('Searched dirs:', TMP_DIRS)
print('Found', len(found), 'files')
for f in found[:200]:
    print('-', os.path.relpath(f, ROOT))
