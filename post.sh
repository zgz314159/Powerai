#!/system/bin/sh
/system/bin/curl -s -X POST http://192.168.0.106:8000/embed_batch -H "Content-Type: application/json" --data-binary @/data/local/tmp/payload.json > /data/local/tmp/embed_response.json
