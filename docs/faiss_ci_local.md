# 本地化 FAISS 子模块与 CI 调试指南

目的
- 在本地添加 FAISS 子模块并运行 CI 原型以便调试构建错误和调整 CMake 选项。

步骤（本地）
1. 在仓库根目录添加子模块：

```bash
git submodule add https://github.com/facebookresearch/faiss.git native/third_party/faiss
git submodule update --init --recursive
```

2. 在本地先做一次 Host 构建以验证依赖（Ubuntu）:

```bash
mkdir -p native/third_party/faiss/build
cd native/third_party/faiss/build
cmake -DFAISS_ENABLE_GPU=OFF -DFAISS_ENABLE_PYTHON=OFF -DFAISS_ENABLE_TESTS=OFF -DBUILD_SHARED_LIBS=ON -DFAISS_ENABLE_SSE=OFF ..
cmake --build . --config Release -j8
```

3. 若 Host 构建成功，再尝试 Android 交叉编译（需安装 Android NDK 并设置 `ANDROID_NDK_HOME`）：

```bash
mkdir -p native/third_party/faiss/android_build_arm64
cd native/third_party/faiss/android_build_arm64
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 -DFAISS_ENABLE_GPU=OFF -DFAISS_ENABLE_PYTHON=OFF -DFAISS_ENABLE_TESTS=OFF -DBUILD_SHARED_LIBS=ON -DFAISS_ENABLE_SSE=OFF ..
cmake --build . --config Release -j8
```

4. 若上述 Android 交叉构建失败，请尝试这些变体以定位问题：

```bash
# 变体 1: no_blas（关闭 BLAS 依赖）
mkdir -p native/third_party/faiss/android_build_no_blas
cd native/third_party/faiss/android_build_no_blas
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 -DFAISS_ENABLE_GPU=OFF -DFAISS_ENABLE_PYTHON=OFF -DFAISS_ENABLE_TESTS=OFF -DBUILD_SHARED_LIBS=ON -DFAISS_ENABLE_SSE=OFF -DFAISS_USE_BLAS=OFF ..
cmake --build . --config Release -j8

# 变体 2: shared_libs（强制共享库）
mkdir -p native/third_party/faiss/android_build_shared_libs
cd native/third_party/faiss/android_build_shared_libs
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 -DFAISS_ENABLE_GPU=OFF -DFAISS_ENABLE_PYTHON=OFF -DFAISS_ENABLE_TESTS=OFF -DBUILD_SHARED_LIBS=ON -DFAISS_ENABLE_SSE=OFF ..
cmake --build . --config Release -j8

# 变体 3: static_libs（静态库）
mkdir -p native/third_party/faiss/android_build_static_libs
cd native/third_party/faiss/android_build_static_libs
cmake -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-21 -DFAISS_ENABLE_GPU=OFF -DFAISS_ENABLE_PYTHON=OFF -DFAISS_ENABLE_TESTS=OFF -DBUILD_SHARED_LIBS=OFF -DFAISS_ENABLE_SSE=OFF ..
cmake --build . --config Release -j8
```

常见问题与调试建议
- 如果构建失败并报 BLAS/ATLAS/CPUID 或 SSE 指令错误：尝试禁用 SSE（`-DFAISS_ENABLE_SSE=OFF`）或指定 `-DBUILD_SHARED_LIBS=ON`。
- 若缺少 BLAS，FAISS 在某些配置下需要 BLAS/ATLAS。可以尝试安装 `libopenblas-dev`（Host 构建）或启用 FAISS 的无 BLAS 模式（定制 CMake），但 Android 交叉构建通常需要预构建的 BLAS for Android 或链接到一个轻量实现。
- 在 CI 中请查看上传的日志包 `faiss-build-logs-<ABI>` 来定位失败阶段。

下一步
- 我已在 CI workflow 中添加了更多构建尝试与日志上载；请在本地运行上述子模块命令并推动一次 CI 触发（或允许我继续在 workflow 上迭代）。
