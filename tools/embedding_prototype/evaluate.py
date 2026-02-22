#!/usr/bin/env python3
"""
Evaluate retrieval methods on an eval dataset.

Eval dataset format (JSON):
{
  "queries": [
    {"id":"q1","query":"变压器 故障","gold": ["knowledge_base.gemini_2_5_flash.partial.json::32"]},
    ...
  ]
}

Gold identifiers use metadata `source_file::original_index` which is emitted by compute_embeddings.py

Usage:
  python evaluate.py --eval eval_dataset.json --k 10
"""
import argparse
import json
import os
import numpy as np
from collections import defaultdict

OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
EMB_PATH = os.path.join(OUT_DIR, 'embeddings.npy')
META_PATH = os.path.join(OUT_DIR, 'metadata.json')
NN_PKL = os.path.join(OUT_DIR, 'nn_model.pkl')

if not os.path.exists(META_PATH):
    raise SystemExit('Run compute_embeddings.py first')

with open(META_PATH, 'r', encoding='utf-8') as f:
    meta = json.load(f)

id_map = [f"{m.get('source_file')}::{m.get('original_index')}" for m in meta]

# Load embeddings and NN if available
emb = None
nn = None
if os.path.exists(EMB_PATH):
    emb = np.load(EMB_PATH)
if os.path.exists(NN_PKL):
    import pickle
    with open(NN_PKL,'rb') as f:
        nn = pickle.load(f)

from sentence_transformers import SentenceTransformer
MODEL = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')


def lexical_search(q, k):
    qn = q.lower().strip()
    hits = []
    for i,m in enumerate(meta):
        text = (m.get('content','') + ' ' + (m.get('title') or '')).lower()
        if qn in text:
            hits.append(i)
    return hits[:k]


def ann_search(q, k):
    if nn is None:
        return []
    q_emb = MODEL.encode([q])
    dists, idxs = nn.kneighbors(q_emb, n_neighbors=k)
    return [int(x) for x in idxs[0]]


def hybrid_search(q, k):
    ann = ann_search(q, k*2)
    lex = lexical_search(q, k*2)
    # simple merge giving lex higher weight
    score = defaultdict(float)
    for i,idx in enumerate(ann):
        score[idx] += 1.0/(i+1)
    for i,idx in enumerate(lex):
        score[idx] += 2.0/(i+1)
    ordered = sorted(score.items(), key=lambda x: x[1], reverse=True)
    return [idx for idx,_ in ordered][:k]


def recall_at_k(preds, golds):
    hits = 0
    for p in preds:
        if p in golds:
            hits += 1
    return 1.0 if hits>0 else 0.0


def mrr(preds, golds):
    for rank,p in enumerate(preds,1):
        if p in golds:
            return 1.0/ rank
    return 0.0


def parse_gold_list(gold_list):
    # gold_list are identifiers like 'file.json::3'
    idxs = set()
    for g in gold_list:
        if '::' in g:
            try:
                f, oi = g.split('::',1)
                oi = int(oi)
                # find matching indices in meta
                for i,m in enumerate(meta):
                    if m.get('source_file')==f and int(m.get('original_index'))==oi:
                        idxs.add(i)
            except Exception:
                continue
    return idxs


def evaluate(eval_path, k=10):
    with open(eval_path,'r',encoding='utf-8') as f:
        data = json.load(f)
    queries = data.get('queries', [])
    results = {'lexical':[], 'ann':[], 'hybrid':[]}
    for q in queries:
        qq = q.get('query')
        golds = parse_gold_list(q.get('gold',[]))
        if not golds:
            continue
        lex_p = lexical_search(qq, k)
        ann_p = ann_search(qq, k)
        hyb_p = hybrid_search(qq, k)
        results['lexical'].append((recall_at_k(lex_p, golds), mrr(lex_p, golds)))
        results['ann'].append((recall_at_k(ann_p, golds), mrr(ann_p, golds)))
        results['hybrid'].append((recall_at_k(hyb_p, golds), mrr(hyb_p, golds)))
    # aggregate
    for name in results:
        arr = results[name]
        if not arr:
            print(name, 'no queries')
            continue
        recalls = [a for a,_ in arr]
        mrrs = [b for _,b in arr]
        print(f"{name}: Recall@{k}={np.mean(recalls):.4f}  MRR={np.mean(mrrs):.4f}  n={len(arr)}")


if __name__=='__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--eval', required=True)
    p.add_argument('--k', type=int, default=10)
    args = p.parse_args()
    evaluate(args.eval, args.k)
