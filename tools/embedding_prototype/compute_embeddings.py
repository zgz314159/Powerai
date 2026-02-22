#!/usr/bin/env python3
"""
Compute embeddings for local KB JSON files under project `tmp/` and save embeddings + metadata.
Usage: python compute_embeddings.py
"""
import json
import os
import glob
from typing import List, Dict, Any
import numpy as np

try:
    from sentence_transformers import SentenceTransformer
except Exception as e:
    raise SystemExit("Missing sentence-transformers. Install via requirements.txt")

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
# Search both project-level `tmp/` and `build/tmp/` where existing exports live
TMP_DIRS = [os.path.join(ROOT, 'tmp'), os.path.join(ROOT, 'build', 'tmp')]
OUT_DIR = os.path.join(os.path.dirname(__file__), 'output')
os.makedirs(OUT_DIR, exist_ok=True)
MODEL_NAME = 'sentence-transformers/all-MiniLM-L6-v2'


def discover_json_files(tmp_dirs: List[str]) -> List[str]:
    patterns = ['*.json', '*.jsonl']
    files = []
    for d in tmp_dirs:
        if not os.path.isdir(d):
            continue
        for p in patterns:
            files.extend(glob.glob(os.path.join(d, p)))
    return sorted(files)


def extract_text(obj: Any) -> str:
    # Best-effort extraction from various possible shapes
    if obj is None:
        return ''
    if isinstance(obj, str):
        return obj
    if isinstance(obj, dict):
        for k in ('contentNormalized','searchContent','content','text','title'):
            v = obj.get(k)
            if isinstance(v, str) and v.strip():
                return v.strip()
        # fallback: join string values
        parts = []
        for v in obj.values():
            if isinstance(v, str) and v.strip():
                parts.append(v.strip())
        return ' '.join(parts)
    return ''


def load_items_from_file(path: str) -> List[Dict[str, Any]]:
    items = []
    with open(path, 'r', encoding='utf-8') as f:
        data = None
        try:
            data = json.load(f)
        except Exception:
            # try line-delimited JSONL
            f.seek(0)
            data = []
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    obj = json.loads(line)
                    data.append(obj)
                except Exception:
                    continue
    if isinstance(data, dict):
        # may be a root object with items under a key
        # try to find array-like fields
        for k, v in data.items():
            if isinstance(v, list):
                data = v
                break
    if not isinstance(data, list):
        return []
    for idx, el in enumerate(data):
        text = extract_text(el)
        if not text:
            continue
        items.append({
            'source_file': os.path.basename(path),
            'original_index': idx,
            'title': (el.get('title') if isinstance(el, dict) else '') or '',
            'content': text
        })
    return items


def collect_all_items(tmp_dirs: List[str]) -> List[Dict[str, Any]]:
    files = discover_json_files(tmp_dirs)
    all_items = []
    for f in files:
        all_items.extend(load_items_from_file(f))
    return all_items


def main():
    print('Using model:', MODEL_NAME)
    model = SentenceTransformer(MODEL_NAME)
    items = collect_all_items(TMP_DIRS)
    if not items:
        print('No items found under', TMP_DIR)
        return
    contents = [it['content'] for it in items]
    print(f'Found {len(contents)} items, computing embeddings...')
    embeddings = model.encode(contents, show_progress_bar=True, batch_size=64)
    emb = np.array(embeddings, dtype=np.float32)
    np.save(os.path.join(OUT_DIR, 'embeddings.npy'), emb)
    with open(os.path.join(OUT_DIR, 'metadata.json'), 'w', encoding='utf-8') as f:
        json.dump(items, f, ensure_ascii=False, indent=2)
    print('Saved embeddings (embeddings.npy) and metadata (metadata.json) in', OUT_DIR)


if __name__ == '__main__':
    main()
