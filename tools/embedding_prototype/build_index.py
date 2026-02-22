#!/usr/bin/env python3
"""
Build a FAISS index if faiss is available; otherwise serialize a sklearn NearestNeighbors model.
Produces `output/index.faiss` or `output/nn_model.pkl`.
"""
import os
import json
import numpy as np
import pickle

OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
EMB_PATH = os.path.join(OUT_DIR, 'embeddings.npy')
META_PATH = os.path.join(OUT_DIR, 'metadata.json')

if not os.path.exists(EMB_PATH):
    raise SystemExit('embeddings.npy not found; run compute_embeddings.py first')

emb = np.load(EMB_PATH)
print('Loaded embeddings', emb.shape)

try:
    import faiss
    use_faiss = True
except Exception:
    use_faiss = False

if use_faiss:
    d = emb.shape[1]
    # Use index suitable for cosine similarity: normalize then use IndexFlatIP
    emb_norm = emb / np.linalg.norm(emb, axis=1, keepdims=True)
    index = faiss.IndexFlatIP(d)
    index.add(emb_norm)
    faiss.write_index(index, os.path.join(OUT_DIR, 'index.faiss'))
    print('Built FAISS Index and saved to', os.path.join(OUT_DIR, 'index.faiss'))
else:
    print('faiss not available; falling back to sklearn NearestNeighbors serialization')
    from sklearn.neighbors import NearestNeighbors
    nn = NearestNeighbors(n_neighbors=10, algorithm='auto', metric='cosine')
    nn.fit(emb)
    with open(os.path.join(OUT_DIR, 'nn_model.pkl'), 'wb') as f:
        pickle.dump(nn, f)
    print('Saved sklearn NN model to', os.path.join(OUT_DIR, 'nn_model.pkl'))
