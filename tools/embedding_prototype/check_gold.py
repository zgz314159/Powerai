#!/usr/bin/env python3
import json, os
BASE = os.path.dirname(__file__)
META = os.path.join(BASE, 'output', 'metadata.json')
EVAL = os.path.join(BASE, 'eval_generated.json')
if not os.path.exists(META):
    print('metadata.json missing; run compute_embeddings.py first'); raise SystemExit(1)
if not os.path.exists(EVAL):
    print('eval_generated.json missing'); raise SystemExit(1)
with open(META,'r',encoding='utf-8') as f:
    meta = json.load(f)
ids = set(f"{m.get('source_file')}::{m.get('original_index')}" for m in meta)
with open(EVAL,'r',encoding='utf-8') as f:
    ev = json.load(f)
queries = ev.get('queries', [])
missing = {}
for q in queries:
    gid = q.get('id')
    for g in q.get('gold', []):
        if g not in ids:
            missing.setdefault(g,[]).append(gid)
print('Total queries:', len(queries))
print('Total unique gold ids referenced:', len(set(g for q in queries for g in q.get('gold',[]))))
print('Missing gold identifiers count:', len(missing))
if missing:
    print('\nExamples of missing gold identifiers (up to 20):')
    for i,(g,qs) in enumerate(list(missing.items())[:20]):
        print(f"  {g} referenced by queries: {qs[:5]}")
else:
    print('All gold identifiers present in metadata.json')
