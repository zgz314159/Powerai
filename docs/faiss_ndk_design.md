# FAISS 本地集成设计（JNI / NDK）

目的
- 在 Android 端添加本地 ANN 检索能力，使用 FAISS 原生库以减少网络依赖、降低查询延迟，并与现有的 `AnnRetriever` 抽象兼容。

设计原则与约束
- 最小入侵：保留当前 `AnnRetriever` 抽象（HTTP 实现），新增 `LocalFaissAnnRetriever` 实现并在 DI 中按可用性选择。 
- 多 ABI 支持：arm64-v8a, armeabi-v7a, x86_64（优先支持 arm64-v8a）。
- 可选降级：当本地库不可用或索引缺失时回退到已有 `HttpAnnRetriever`。
- 存储与隐私：索引文件存放在应用私有目录（`filesDir/faiss/`），并可通过应用内机制更新/删除。

总体架构
- Native layer (C++ FAISS) + JNI wrapper
  - FAISS core: 编译为静态/共享库 (.a / .so) 放入 AAR `jni/<ABI>/` 或 `app/src/main/jniLibs/<ABI>/`。
  - C++ wrapper: 负责打开/关闭索引、执行搜索、批量载入/释放索引、索引元数据管理。
  - JNI 层：暴露简单 C ABI 给 Kotlin：`openIndex(path)`, `closeIndex()`, `search(k, float[] queryEmbedding) -> int[] ids, float[] scores`。

- Kotlin layer
  - `LocalFaissAnnRetriever` 实现 `AnnRetriever`：内部调用 JNI 接口，负责 embedding 获取（或接收预计算 embedding 的路径）并把返回的 id 映射为 `KnowledgeItem`。

索引文件与生命周期
- 存放位置：`{filesDir}/faiss/index_<version>.faiss`，并维护一个小型 JSON 元数据 `index_metadata.json`（indexVersion, dims, metric, algorithm, createdAt）。
- 索引创建策略：
  - 首选：在服务端/桌面端预构建索引并通过更新包下发（节省移动端构建成本）。
  - 次选：在设备上支持增量加载/合并较小的局部索引（高级方案，初期可不实现）。

JNI API 设计（草案）
- C++ 函数签名（示例）
  - bool faiss_init();
  - int faiss_open_index(const char* path); // 返回 index handle 或 -1
  - void faiss_close_index(int handle);
  - int faiss_search(int handle, const float* query, int dim, int k, int* out_ids, float* out_scores); // 返回实际命中数
  - bool faiss_index_info(int handle, IndexInfo* out);

- Kotlin JNI 包装（示例）
  - external fun openIndex(path: String): Int
  - external fun closeIndex(handle: Int)
  - external fun search(handle: Int, query: FloatArray, k: Int): FaissSearchResult

错误处理与回退策略
- 若 `openIndex` 失败或 `search` 抛错，`LocalFaissAnnRetriever` 将抛出或返回空结果并记录日志；上层 `VectorSearchRepository` 检测到空结果后回退到 `HttpAnnRetriever` 或仅使用 lexical FTS。

构建与 CI 建议
- 将 FAISS 源码作为子模块或下载预编译二进制：
  - 推荐先使用预构建 FAISS（如果可得），否则在 CI 中使用交叉编译（GitHub Actions + android-ndk + cmake）。
- CMake 配置：使用 `android.toolchain.cmake` 指定 NDK，输出 .so 到 `app/src/main/jniLibs/<ABI>/`。
- CI 矩阵：在 `/.github/workflows/faiss-native.yml` 中对 `arm64-v8a` 和 `x86_64` 做至少一次构建和 smoke test（运行小型查询用例）。

打包与发布
- 将 native libs 打包进 `app` 或单独 AAR（推荐 AAR 可复用），并在 Gradle 中按 ABI 拆分。

性能与精度选项
- 索引类型选择（根据内存/精度需求）：
  - Flat (精确，内存大)
  - IVF + PQ (压缩存储，低内存，可接受轻微精度损失)
  - HNSW (近似图，低延迟，高召回)
- 推荐初始实现：IVF/PQ（节省存储），同时在实验中评估 Recall@k 与 MRR。生产可提供多份索引或参数可配置。

测试计划
- 单元与集成测试：
  - 本地单测：提供一个极小索引（几十条）放入 `test/resources`，通过 JNI 执行 `openIndex` / `search` 验证返回正确 id。
  - Android instrumentation：启动带 lib 的 APK，在真机/模拟器上做查询并与 Python 构建的 index 比对结果（可用 Mock 数据）。
- 性能测试：记录 p99/p50 延迟、内存峰值、RSS 与加载时间。

迁移与实施里程碑
1. 草拟设计（本文件） — 完成
2. 在 CI 中能交叉编译 FAISS 并产出 `jniLibs`（原型）
3. 实现并发布 `LocalFaissAnnRetriever`（Kotlin + JNI）并在本地跑通查询
4. 与 `VectorSearchRepository` 集成并做 A/B 测试（HTTP vs 本地）
5. 性能调优与索引参数选择（IVF/PQ/HNSW）

风险与缓解
- 风险：FAISS 编译复杂、体积大、ABI/NDK 兼容性问题。
  - 缓解：优先使用预构建二进制或限制初期支持的 ABI；把复杂构建放在 CI 中自动化。
- 风险：移动端内存不足导致查询 OOM。
  - 缓解：使用压缩索引（PQ），控制每次加载的 shard/分片大小，并提供远程回退。

附录：快速试验命令（本地开发）
- 使用 NDK + CMake 交叉编译（示例）
  - mkdir build && cd build
  - cmake -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 ..
  - cmake --build . --target faiss_wrapper -- -j8

下一步
- 我可以继续：
  - 写具体 CMakeLists + JNI 框架代码样板并提交 PR；或
  - 搭建 CI workflow 原型用于交叉编译 FAISS。
请回复“继续：CMake/CI 原型”或“继续：JNI 框架样板”来选择下一步。
