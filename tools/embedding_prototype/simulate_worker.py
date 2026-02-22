import os
import json
import requests
import struct

BASE = os.path.join(os.path.dirname(__file__), 'local_app_files')
PENDING = os.path.join(BASE, 'embeddings', 'pending')
OUT = os.path.join(BASE, 'embeddings')

os.makedirs(PENDING, exist_ok=True)
os.makedirs(OUT, exist_ok=True)

# create sample pending files
samples = [
    {'id':'1001','content':'变压器 发热 异常 需 停运'},
    {'id':'1002','content':'变压器 短路 故障 故障 原因 分析'}
]
for s in samples:
    path = os.path.join(PENDING, f"{s['id']}.json")
    if not os.path.exists(path):
        with open(path,'w',encoding='utf-8') as f:
            json.dump(s, f, ensure_ascii=False)

# read pending files
batch = []
files = [f for f in os.listdir(PENDING) if f.endswith('.json')]
for fn in files:
    with open(os.path.join(PENDING, fn),'r',encoding='utf-8') as f:
        obj = json.load(f)
        batch.append({'id': obj.get('id'), 'content': obj.get('content')})

if not batch:
    print('no pending')
    raise SystemExit(0)

url = 'http://127.0.0.1:8000/embed_batch'
resp = requests.post(url, json=batch, timeout=120)
resp.raise_for_status()
res = resp.json().get('results', {})

for k,v in res.items():
    arr = v
    # write binary float32
    binpath = os.path.join(OUT, f"{k}.emb")
    with open(binpath,'wb') as bf:
        bf.write(struct.pack('<' + 'f'*len(arr), *arr))
    # write meta
    with open(os.path.join(OUT, f"{k}.json"),'w',encoding='utf-8') as mf:
        json.dump({'id': k, 'status':'done'}, mf, ensure_ascii=False)

# remove pending files
for fn in files:
    try: os.remove(os.path.join(PENDING, fn))
    except: pass

print('simulated worker done, wrote', len(res), 'embeddings to', OUT)
