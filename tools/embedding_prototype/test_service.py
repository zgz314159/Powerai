import requests
import json

url = 'http://127.0.0.1:8000/embed_batch'
batch = [
    {"id": "t1", "content": "变压器发热 并伴有异味"},
    {"id": "t2", "content": "维护规程: 变压器检查 温度 急剧上升"}
]
resp = requests.post(url, json=batch, timeout=60)
print('status', resp.status_code)
print(resp.json())
