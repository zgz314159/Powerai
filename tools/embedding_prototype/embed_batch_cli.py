#!/usr/bin/env python3
"""
A minimal CLI to compute embeddings for a batch JSON file.
Usage: python embed_batch_cli.py /path/to/batch.json
Batch JSON format: [{"id": "...", "content": "..."}, ...]
Outputs JSON to stdout: {"results": {"id": [float,...], ...}}
"""
import sys
import json
from sentence_transformers import SentenceTransformer

if len(sys.argv) < 2:
    print('usage: embed_batch_cli.py batch.json', file=sys.stderr)
    sys.exit(2)

batch_path = sys.argv[1]
with open(batch_path, 'r', encoding='utf-8') as f:
    items = json.load(f)

model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')
texts = [it.get('content','') for it in items]
ids = [str(it.get('id','')) for it in items]
embs = model.encode(texts, show_progress_bar=False)

results = {ids[i]: embs[i].tolist() for i in range(len(ids))}
print(json.dumps({'results': results}, ensure_ascii=False))
