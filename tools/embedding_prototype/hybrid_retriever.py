#!/usr/bin/env python3
"""
Simple hybrid retriever: combine ANN (embeddings) + lexical substring search over metadata content.
Usage: python hybrid_retriever.py "query" --k 10
"""
import argparse
import json
import os
import numpy as np
import pickle

OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
EMB_PATH = os.path.join(OUT_DIR, 'embeddings.npy')
META_PATH = os.path.join(OUT_DIR, 'metadata.json')
NN_PKL = os.path.join(OUT_DIR, 'nn_model.pkl')

if not os.path.exists(EMB_PATH) or not os.path.exists(META_PATH):
    raise SystemExit('Run compute_embeddings.py first')

emb = np.load(EMB_PATH)
with open(META_PATH, 'r', encoding='utf-8') as f:
    meta = json.load(f)

try:
    with open(NN_PKL, 'rb') as f:
        nn = pickle.load(f)
        use_nn = True
except Exception:
    nn = None
    use_nn = False

from sentence_transformers import SentenceTransformer
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')


def lexical_search(meta, q, k):
    q_norm = q.lower().strip()
    hits = []
    for i, m in enumerate(meta):
        text = (m.get('content','') + ' ' + (m.get('title') or '')).lower()
        if q_norm in text:
            hits.append((i, 1.0))
    return sorted(hits, key=lambda x: x[1], reverse=True)[:k]


def ann_search(q, k):
    if nn is None:
        return []
    q_emb = model.encode([q])
    dists, idxs = nn.kneighbors(q_emb, n_neighbors=k)
    results = []
    for dist, idx in zip(dists[0], idxs[0]):
        results.append((int(idx), float(1 - dist)))
    return results


def merge(ann, lex, k):
    # Simple merge: assign weights, dedupe by index
    scores = {}
    for idx, s in ann:
        scores[idx] = scores.get(idx, 0.0) + 1.0 * s
    for idx, s in lex:
        scores[idx] = scores.get(idx, 0.0) + 2.0 * s
    ordered = sorted(scores.items(), key=lambda x: x[1], reverse=True)[:k]
    return ordered


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('query')
    parser.add_argument('--k', type=int, default=10)
    args = parser.parse_args()
    ann = ann_search(args.query, args.k)
    lex = lexical_search(meta, args.query, args.k)
    merged = merge(ann, lex, args.k)
    for rank, (idx, score) in enumerate(merged, 1):
        m = meta[idx]
        print(f"#{rank} idx={idx} score={score:.4f} src={m.get('source_file')} title={m.get('title')}")
        print(m.get('content')[:400].replace('\n',' '))
        print('-'*60)

if __name__=='__main__':
    main()
