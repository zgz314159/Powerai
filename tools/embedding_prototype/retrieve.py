#!/usr/bin/env python3
"""
Simple retrieval script that loads embeddings.npy and metadata.json and answers nearest neighbors.
Usage:
  python retrieve.py "your query here" --k 5
"""
import argparse
import json
import os
import numpy as np

try:
    from sentence_transformers import SentenceTransformer
except Exception:
    raise SystemExit('Missing sentence-transformers. Install via requirements.txt')

from sklearn.neighbors import NearestNeighbors

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
MODEL_NAME = 'sentence-transformers/all-MiniLM-L6-v2'


def load_index():
    emb_path = os.path.join(OUT_DIR, 'embeddings.npy')
    meta_path = os.path.join(OUT_DIR, 'metadata.json')
    if not os.path.exists(emb_path) or not os.path.exists(meta_path):
        raise SystemExit('Run compute_embeddings.py first to generate embeddings and metadata')
    emb = np.load(emb_path)
    with open(meta_path, 'r', encoding='utf-8') as f:
        meta = json.load(f)
    return emb, meta


def build_nn(embeddings):
    nn = NearestNeighbors(n_neighbors=10, algorithm='auto', metric='cosine')
    nn.fit(embeddings)
    return nn


def query_topk(model, nn, embeddings, meta, q, k=5):
    q_emb = model.encode([q])
    dists, idxs = nn.kneighbors(q_emb, n_neighbors=k)
    out = []
    for dist, idx in zip(dists[0], idxs[0]):
        item = meta[idx]
        out.append({'score': float(1 - dist), 'title': item.get('title'), 'content': item.get('content'), 'source': item.get('source_file')})
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('query', type=str)
    parser.add_argument('--k', type=int, default=5)
    args = parser.parse_args()
    emb, meta = load_index()
    model = SentenceTransformer(MODEL_NAME)
    nn = build_nn(emb)
    results = query_topk(model, nn, emb, meta, args.query, args.k)
    for i, r in enumerate(results, 1):
        print(f"#{i} score={r['score']:.4f} src={r['source']} title={r['title']}")
        print(r['content'][:600].replace('\n', ' '))
        print('-' * 60)


if __name__ == '__main__':
    main()
