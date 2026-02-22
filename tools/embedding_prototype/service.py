from fastapi import FastAPI, HTTPException
from sentence_transformers import SentenceTransformer
from typing import List, Dict

app = FastAPI()
model = SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2')


@app.post('/embed_batch')
async def embed_batch(batch: List[Dict]):
    try:
        ids = [str(b.get('id', '')) for b in batch]
        texts = [b.get('content', '') for b in batch]
        embs = model.encode(texts, show_progress_bar=False)
        results = {ids[i]: embs[i].tolist() for i in range(len(ids))}
        return {'results': results}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get('/health')
async def health():
    return {'status': 'ok'}
