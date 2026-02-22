Faiss integration (manual placement)
=================================

This folder is a placeholder for prebuilt Faiss headers and static libraries for Android ABIs.

Goal
----
Provide a prebuilt `libfaiss.a` and headers so the JNI wrapper `native_search_faiss.cpp` can be linked
for a single ABI (e.g. `arm64-v8a`) during app builds.

Expected layout
---------------

app/src/main/cpp/faiss/include/...   # Faiss public headers (faiss/*.h)
app/src/main/cpp/faiss/arm64-v8a/libfaiss.a

Notes
-----
- You must compile Faiss for Android (arm64-v8a) separately (NDK + Bazel/CMake toolchain) and copy the
  resulting `libfaiss.a` and headers into the paths above.
- The CMakeLists.txt will detect the presence of `libfaiss.a` for the active `ANDROID_ABI` and, if found,
  build `native_search_faiss.cpp` and link the static library. Otherwise the original `native_search.cpp`
  fallback will be compiled.
- For a minimal smoke test, placing a prebuilt `libfaiss.a` and headers under `arm64-v8a` is sufficient.

Build hints
-----------
- Build Faiss with `-DFAISS_ENABLE_GPU=OFF` and static linking, targeting Android arm64.
- Ensure C++ ABI compatibility (use gnustl/llvm libc++ consistent with your Android NDK/Gradle settings).
