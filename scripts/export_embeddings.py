#!/usr/bin/env python3
"""
Export knowledge base to SQLite + FAISS index with embeddings.
- Reads: app/src/main/assets/kb/knowledge_base.json
- Embeds using: sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2 (384d)
- Writes: knowledge.db (SQLite), vector_index.bin (FAISS)
- Initializes FTS4 virtual table `knowledge_fts` compatible with app DAO

Usage: python scripts/export_embeddings.py

Requirements:
 pip install sentence-transformers faiss-cpu numpy

"""
import os
import json
import sqlite3
import hashlib
import time
from pathlib import Path
from typing import List

import numpy as np

try:
    from sentence_transformers import SentenceTransformer
except Exception as e:
    raise SystemExit("Missing dependency sentence-transformers. Install with: pip install sentence-transformers")

try:
    import faiss
except Exception:
    raise SystemExit("Missing dependency faiss. Install with: pip install faiss-cpu")


KB_ROOT = Path('app/src/main/assets/kb')
OUT_DB = Path('knowledge.db')
OUT_INDEX = Path('vector_index.bin')
EMBEDDING_DIM = 384
MODEL_NAME = 'paraphrase-multilingual-MiniLM-L12-v2'

APP_PKG = 'com.example.powerai'


def find_all_kb_jsons(root: Path) -> List[Path]:
    if not root.exists():
        raise FileNotFoundError(f'KB root not found at {root.absolute()}')
    return list(root.rglob('knowledge_base.json'))


def load_kb(path: Path):
    with path.open('r', encoding='utf-8') as f:
        data = json.load(f)
    # Accept either list or dict with 'entries'
    if isinstance(data, dict) and 'entries' in data:
        entries = data['entries']
    elif isinstance(data, list):
        entries = data
    else:
        raise ValueError('Unexpected knowledge_base.json format: expected list or {"entries": [...] }')
    return entries


def ensure_str(x):
    return '' if x is None else str(x)


def compute_checksum(vec: np.ndarray) -> str:
    h = hashlib.sha256()
    h.update(vec.astype('float32').tobytes())
    return h.hexdigest()


def create_sqlite_schema(conn: sqlite3.Connection):
    cur = conn.cursor()
    # Create main table matching KnowledgeEntity.kt layout
    cur.execute('''
    CREATE TABLE IF NOT EXISTS knowledge (
        id INTEGER PRIMARY KEY,
        title TEXT,
        content TEXT,
        source TEXT,
        category TEXT,
        keywordsSerialized TEXT,
        contentNormalized TEXT,
        searchContent TEXT,
        pageNumber INTEGER,
        contentBlocksJson TEXT,
        bboxJson TEXT,
        imageUris TEXT,
        vector_checksum TEXT,
        indexed_at INTEGER
    );
    ''')

    # Create FTS4 virtual table name must be `knowledge_fts` to match DAO usage
    # Use content="knowledge" so we can rebuild from the main table
    cur.execute('''
    CREATE VIRTUAL TABLE IF NOT EXISTS knowledge_fts USING fts4(
        content="knowledge",
        title,
        searchContent,
        tokenize=unicode61
    );
    ''')

    conn.commit()


def insert_entries_and_build_fts(conn: sqlite3.Connection, id_mapping: List[tuple]):
    cur = conn.cursor()
    now = int(time.time() * 1000)
    for id_val, e in id_mapping:
        # id_val is ensured integer from earlier mapping
        title = ensure_str(e.get('__computed_title') or e.get('jobTitle') or e.get('title') or e.get('name') or '')
        content = ensure_str(e.get('__computed_content') or e.get('content') or e.get('text') or '')
        source = ensure_str(e.get('fileId') or e.get('source') or 'import')
        category = ensure_str(e.get('category') or '')
        keywordsSerialized = ensure_str(e.get('keywords') or '')
        contentNormalized = ensure_str(e.get('contentNormalized') or content)
        searchContent = ensure_str(e.get('searchContent') or contentNormalized)
        pageNumber = e.get('pageNumber')
        # try to preserve any blocks/bbox/imageUris
        contentBlocksJson = ensure_str(json.dumps(e.get('blocks') or e.get('contentBlocks')) if (e.get('blocks') or e.get('contentBlocks')) else None)
        bboxJson = ensure_str(json.dumps(e.get('bbox')) if e.get('bbox') else None)
        imageUris = ensure_str(json.dumps(e.get('imageUris')) if e.get('imageUris') else None)

        # placeholder vector_checksum / indexed_at - will be updated after embeddings created
        vector_checksum = ''
        indexed_at = 0

        cur.execute('''INSERT OR REPLACE INTO knowledge(id, title, content, source, category, keywordsSerialized, contentNormalized, searchContent, pageNumber, contentBlocksJson, bboxJson, imageUris, vector_checksum, indexed_at)
                       VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)''',
                    (id_val, title, content, source, category, keywordsSerialized, contentNormalized, searchContent, pageNumber, contentBlocksJson, bboxJson, imageUris, vector_checksum, indexed_at))

    conn.commit()

    # Rebuild FTS index from content=knowledge
    # The DAO calls "INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')" to force rebuild
    # We do the same here to populate the FTS table
    cur.execute("INSERT INTO knowledge_fts(knowledge_fts) VALUES('rebuild')")
    conn.commit()


def update_checksums(conn: sqlite3.Connection, id_to_checksum: dict, id_to_indexed: dict):
    cur = conn.cursor()
    for idv, checksum in id_to_checksum.items():
        ts = id_to_indexed.get(idv, 0)
        cur.execute('UPDATE knowledge SET vector_checksum = ?, indexed_at = ? WHERE id = ?', (checksum, ts, idv))
    conn.commit()


def build_faiss_index(vectors: np.ndarray, ids: List[int], out_path: Path):
    # normalize vectors for cosine similarity and use IndexFlatIP
    dim = vectors.shape[1]
    vecs = vectors.astype('float32')
    norms = np.linalg.norm(vecs, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    vecs_norm = vecs / norms

    index = faiss.IndexFlatIP(dim)
    # Create IndexIDMap so we can persist ids alongside vectors
    index_with_ids = faiss.IndexIDMap(index)
    index_with_ids.add_with_ids(vecs_norm, np.array(ids, dtype='int64'))

    faiss.write_index(index_with_ids, str(out_path))


def main():
    # Find all knowledge_base.json under KB_ROOT
    json_paths = find_all_kb_jsons(KB_ROOT)
    if not json_paths:
        raise SystemExit(f'No knowledge_base.json files found under {KB_ROOT}')

    all_entries = []
    for p in sorted(json_paths):
        try:
            # load raw JSON to access fileMetadata when available
            with p.open('r', encoding='utf-8') as f:
                raw = json.load(f)
        except Exception as ex:
            print(f'Warning: failed to load {p}: {ex}')
            continue

        # Determine canonical fileId/source for this JSON
        file_metadata = raw.get('fileMetadata') if isinstance(raw, dict) else None
        if file_metadata and file_metadata.get('fileId'):
            file_id_full = file_metadata.get('fileId')
        else:
            # fallback to relative path under KB_ROOT
            try:
                file_id_full = str(p.parent.relative_to(KB_ROOT)).replace('\\', '/')
            except Exception:
                file_id_full = p.parent.name

        # entries may be under raw['entries'] or raw itself may be a list
        try:
            ents = raw['entries'] if isinstance(raw, dict) and 'entries' in raw else (raw if isinstance(raw, list) else [])
        except Exception:
            ents = []

        # annotate and normalize entries
        for e in ents:
            if not isinstance(e, dict):
                continue
            e['fileId'] = file_id_full
            all_entries.append(e)

    entries = all_entries
    print(f'Loaded total {len(entries)} entries from {len(json_paths)} files')

    # Prepare texts to embed: attempt robust field mapping per KB JSON structure
    texts = []
    entry_ids = []
    id_mapping = []
    for i, e in enumerate(entries, start=1):
        # Determine id
        eid = e.get('id') or e.get('entryId') or e.get('docId') or None
        if eid is None:
            eid = i
        try:
            eid_int = int(eid)
        except Exception:
            # keep original string id mapping but SQL expects integer ids; use incremental
            eid_int = i

        # Determine title: prefer jobTitle/title/name, else fallback to last segment of fileId
        title = (e.get('jobTitle') or e.get('title') or e.get('name') or '').strip()
        if not title:
            # fileId format like '铁路/专业知识/xxx' -> take last part
            fid = e.get('fileId') or ''
            title = fid.split('/')[-1] if fid else '(no title)'

        # Determine content: try contentMarkdown -> contentNormalized -> content -> text -> blocks
        content = ''
        if isinstance(e.get('contentMarkdown'), str) and e.get('contentMarkdown').strip():
            content = e.get('contentMarkdown').strip()
        elif isinstance(e.get('contentNormalized'), str) and e.get('contentNormalized').strip():
            content = e.get('contentNormalized').strip()
        elif isinstance(e.get('content'), str) and e.get('content').strip():
            content = e.get('content').strip()
        elif isinstance(e.get('text'), str) and e.get('text').strip():
            content = e.get('text').strip()
        else:
            # try blocks array
            blocks = e.get('blocks') or e.get('contentBlocks') or []
            if isinstance(blocks, list) and blocks:
                pieces = []
                for b in blocks:
                    if isinstance(b, dict):
                        # common fields: code, text, content
                        piece = b.get('code') or b.get('text') or b.get('content') or ''
                        if piece and isinstance(piece, str):
                            pieces.append(piece.strip())
                if pieces:
                    content = '\n\n'.join(pieces)

        # Defensive: do not export empty content entries
        if not content or not content.strip():
            print(f"Warning: skipping empty content entry id={eid} fileId={e.get('fileId')}")
            continue

        # store computed values back into entry for later DB insertion
        e['__computed_title'] = title
        e['__computed_content'] = content

        texts.append(content)
        entry_ids.append(eid_int)
        id_mapping.append((eid_int, e))

    # Load model
    print('Loading model:', MODEL_NAME)
    model = SentenceTransformer(MODEL_NAME)
    print('Creating embeddings... (this may take a while)')
    embeddings = model.encode(texts, batch_size=32, show_progress_bar=True, convert_to_numpy=True)
    if embeddings.shape[1] != EMBEDDING_DIM:
        print('Warning: model embedding dimension', embeddings.shape[1], '!= expected', EMBEDDING_DIM)

    # Create SQLite DB
    if OUT_DB.exists():
        print('Overwriting', OUT_DB)
        OUT_DB.unlink()
    conn = sqlite3.connect(str(OUT_DB))
    create_sqlite_schema(conn)

    # Insert entries (without checksums yet) - use id_mapping to ensure computed fields
    insert_entries_and_build_fts(conn, id_mapping)

    # Map rowids -> checksums and update table
    id_to_checksum = {}
    id_to_indexed = {}
    for eid, vec in zip(entry_ids, embeddings):
        checksum = compute_checksum(vec)
        id_to_checksum[eid] = checksum
        id_to_indexed[eid] = int(time.time() * 1000)

    update_checksums(conn, id_to_checksum, id_to_indexed)
    conn.close()

    # Print two sample records from DB for audit (from different fileIds if available)
    try:
        conn2 = sqlite3.connect(str(OUT_DB))
        cur = conn2.cursor()
        cur.execute('SELECT id, title, source, vector_checksum FROM knowledge LIMIT 50')
        rows = cur.fetchall()
        samples = {}
        printed = 0
        for r in rows:
            rid, title, source, checksum = r
            # find fileId from original entries mapping
            # we annotated entries earlier; attempt to map
            fileId = None
            for eid, e in id_mapping:
                if int(eid) == int(rid):
                    fileId = e.get('fileId')
                    break
            if fileId not in samples:
                samples[fileId] = (rid, title, fileId, checksum)
                printed += 1
            if printed >= 2:
                break
        print('\nSample records:')
        for k, v in samples.items():
            print(f'id={v[0]} title="{v[1]}" fileId="{v[2]}" vector_checksum={v[3]}')
        conn2.close()
    except Exception as ex:
        print('Warning: could not print samples from DB:', ex)

    # Build FAISS index and write file
    print('Building FAISS index...')
    # Ensure vectors are in same order as entry_ids
    vectors = np.array(embeddings, dtype='float32')
    build_faiss_index(vectors, entry_ids, OUT_INDEX)

    print('Wrote:', OUT_DB.resolve())
    print('Wrote:', OUT_INDEX.resolve())

    # Print adb push commands to copy files to app private dirs (use run-as on device)
    device_db_path = f'/data/data/{APP_PKG}/databases/knowledge.db'
    device_file_path = f'/data/data/{APP_PKG}/files/vector_index.bin'

    print('\n# To push files to device (requires adb and that the app is debuggable), run:')
    print(f'adb push {OUT_DB} /data/local/tmp/knowledge.db')
    print(f'adb push {OUT_INDEX} /data/local/tmp/vector_index.bin')
    print(f"adb shell run-as {APP_PKG} mkdir -p databases files || true")
    print(f'adb shell run-as {APP_PKG} mv /data/local/tmp/knowledge.db {device_db_path}')
    print(f'adb shell run-as {APP_PKG} mv /data/local/tmp/vector_index.bin {device_file_path}')


if __name__ == '__main__':
    main()
