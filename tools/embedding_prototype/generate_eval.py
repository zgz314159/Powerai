#!/usr/bin/env python3
"""
Generate a simple eval JSON from metadata.json by sampling entries.
"""
import json
import os
import random

OUT = os.path.join(os.path.dirname(__file__), 'output')
META = os.path.join(OUT, 'metadata.json')

if not os.path.exists(META):
    raise SystemExit('metadata.json not found; run compute_embeddings.py first')

with open(META, 'r', encoding='utf-8') as f:
    meta = json.load(f)

candidates = [m for m in meta if len((m.get('content') or '').strip())>20]
random.shuffle(candidates)

queries = []
for i, m in enumerate(candidates[:100]):
    content = (m.get('content') or '').strip()
    # take a short substring as query (first 30 chars or a meaningful chunk)
    q = content[:30]
    qid = f'q{i+1}'
    gold = [f"{m.get('source_file')}::{m.get('original_index')}" ]
    queries.append({"id": qid, "query": q, "gold": gold})

out = {"queries": queries}
with open(os.path.join(os.path.dirname(__file__), 'eval_generated.json'), 'w', encoding='utf-8') as f:
    json.dump(out, f, ensure_ascii=False, indent=2)

print(f'Wrote {len(queries)} queries to eval_generated.json')
