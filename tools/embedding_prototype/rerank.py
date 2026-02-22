#!/usr/bin/env python3
"""
Rerank candidates using a cross-encoder model (sentence-transformers CrossEncoder).
Usage: python rerank.py "query"
"""
import os
import json
import numpy as np
from sentence_transformers import CrossEncoder

OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
META_PATH = os.path.join(OUT_DIR, 'metadata.json')

if not os.path.exists(META_PATH):
    raise SystemExit('Run compute_embeddings.py first')

with open(META_PATH, 'r', encoding='utf-8') as f:
    meta = json.load(f)

model_name = 'cross-encoder/ms-marco-MiniLM-L-6-v2'
print('Loading cross-encoder', model_name)
ce = CrossEncoder(model_name)

import argparse
parser = argparse.ArgumentParser()
parser.add_argument('query')
parser.add_argument('--topk', type=int, default=10)
args = parser.parse_args()

# Collect initial candidates by simple lexical match + embedding-based sample
# For demo, take first N items that contain any query token or top-N by simple embedding distance
q = args.query
q_tokens = q.lower().split()

candidates = []
for i,m in enumerate(meta):
    text = (m.get('title','') + '\n' + m.get('content','')).lower()
    if any(tok in text for tok in q_tokens):
        candidates.append((i, text))
    if len(candidates) >= args.topk*3:
        break

# fallback: if no lexical candidates, sample first topk*3
if not candidates:
    for i,m in enumerate(meta[:args.topk*3]):
        candidates.append((i, (m.get('title','') + '\n' + m.get('content','')).lower()))

pairs = [[q, c[1][:512]] for c in candidates]
scores = ce.predict(pairs)
scored = sorted(zip(candidates, scores), key=lambda x: x[1], reverse=True)[:args.topk]

for rank, ((idx, _), score) in enumerate(scored, 1):
    m = meta[idx]
    print(f"#{rank} idx={idx} score={score:.4f} src={m.get('source_file')} title={m.get('title')}")
    print(m.get('content')[:600].replace('\n',' '))
    print('-'*60)
