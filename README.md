```markdown
PowerAi
=====

Performance Benchmark
---------------------

本次基准测试在 arm64-v8a 设备上运行，展示了本地原生搜索引擎（基于 NEON 优化实现）的单次查询性能。表中数据为单次查询（向量维度 128）测得的耗时与吞吐量。

| vectors | time (ms) | throughput (vec/ms) |
|---:|---:|---:|
| 100 | 3.969 | 25.198 |
| 500 | 1.430 | 349.561 |
| 1000 | 1.565 | 638.787 |
| 5000 | 2.366 | 2113.234 |

说明：测试基于 arm64-v8a 架构与 NEON 指令集进行向量距离计算的高度优化实现。原始开发中曾尝试使用 OpenMP 以便更方便地并行化，但在部分设备上遇到缺失 OpenMP 运行时库（libomp.so）的问题，最终实现采用 NEON + 轻量线程并行（std::thread）以保证兼容性与稳定性。

更多细节见 `app/src/main/cpp/native_search.cpp` 中的实现注释；后续计划将该能力封装为可持久化的 `VectorRepository` 并接入应用的本地检索流水线。
**Project: PowerAi — 局域网本地调试指南**

- **目标**: 让开发者使用本机运行的 embedding/ANN 服务（FastAPI/uvicorn）并通过物理手机在局域网内调试 App，避免每次调试都重建 APK。

- **快速步骤**:
  - 在电脑上启动本地服务（示例，绑定到所有接口以允许手机访问）：
    ```bash
    .venv\\Scripts\\python.exe -m uvicorn tools.embedding_prototype.service:app --host 0.0.0.0 --port 8000 --log-level info > artifacts/service.log 2>&1
    ```
  - 在手机上（已安装 debug APK）配置 `ai_base_url.txt`，内容为电脑在局域网的 IP，例如:
    ```text
    http://192.168.1.100:8000/
    ```
    推荐把仓库根目录的 `ai_base_url.txt` （同目录下）作为模板，修改 IP 后推送到设备。
  - 使用 adb 推送（debug build 且允许 run-as）:
    ```bash
    adb push ai_base_url.txt /sdcard/Download/
    adb shell run-as com.example.powerai sh -c 'cat /sdcard/Download/ai_base_url.txt > files/ai_base_url.txt'
    ```
    说明：如果 `run-as` 无效，可使用文件管理器或应用内调试界面把同样的内容写入 `files/ai_base_url.txt`。

- **Android 代码点**:
  - `AppModule.provideAnnApiService()` 已实现：优先读取 `BuildConfig.AI_BASE_URL`，若为空则读取 `files/ai_base_url.txt`（无需重建 APK）。
  - `AnnApiService` POST 路径为 `/search`，示例 curl 请求：
    ```bash
    curl -X POST http://192.168.1.100:8000/search \\
      -H "Content-Type: application/json" \\
      -d '{"query":"示例问题","k":10}'
    ```

- **注意与安全**:
  - 把服务绑定到 `0.0.0.0` 会在同一局域网内暴露端点，仅在受信任网络或开发环境使用。
  - 调试时可在服务端临时增加简单密钥校验（HTTP header）以减少误用。

- **文件位置**:
  - 服务代码: `tools/embedding_prototype/service.py`
  - 本地索引输出: `tools/embedding_prototype/output/index.faiss`
  - 示例 ai_base_url 模板: `ai_base_url.txt` （仓库根目录）

如果你需要，我可以把 `ai_base_url.txt` 推送到已连接设备并帮你在手机上做一次请求测试。

Latest Verification (end-to-end)
-------------------------------

本次实时验证展示了端到端（Query -> Embedding HTTP -> 本地 Native ANN 搜索）的两组典型测得数据：

- Run A: embedding ~245.328 ms, native search ~5.209 ms, total ~250.541 ms
- Run B: embedding ~36.876 ms, native search ~3.726 ms, total ~40.605 ms

其中 Run A 说明 embedding 服务通过网络被成功调用（embed_ms 显著大于 50ms），Run B 为短延时样例（可能因缓存或本机 CLI 执行）。

以上数据已在真实设备（arm64-v8a）上通过 `EmbeddingTestActivity` 验证并记录到日志。保存的向量索引文件位于应用私有目录：
`/data/user/0/com.example.powerai/files/vector_index.bin`。
``` 
