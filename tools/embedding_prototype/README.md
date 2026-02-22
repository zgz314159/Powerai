本地嵌入原型

目的：快速在本地对现有 tmp/ 下的 JSON 知识导出做语义检索原型，量化 ANN 对召回的提升。

依赖：
- 使用 Python 虚拟环境安装：

```powershell
python -m venv .venv
.\.venv\Scripts\activate.ps1
pip install -r tools/embedding_prototype/requirements.txt
```

使用步骤：
1. 运行 embeddings 生成：

```powershell
python tools/embedding_prototype/compute_embeddings.py
```

输出：`tools/embedding_prototype/output/embeddings.npy` 和 `metadata.json`。

2. 语义检索示例：

```powershell
python tools/embedding_prototype/retrieve.py "变压器 故障" --k 5
```

注意：模型默认使用 `sentence-transformers/all-MiniLM-L6-v2`，可在脚本中修改。此原型用于快速验证效果，后续可替换为 FAISS、HNSW 或将 embeddings 存入 Android 可用的 SQLite 向量扩展。
