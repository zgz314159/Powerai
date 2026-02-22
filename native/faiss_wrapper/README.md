FAISS wrapper - prebuilt expectations

This directory contains a small JNI wrapper (`faiss_wrapper`) that links
against FAISS artifacts. The CI workflow will attempt to download a
prebuilt FAISS/OpenBLAS tarball and extract it to `native/third_party/faiss_prebuilt`.

Expected layout (recommended) for prebuilt tarball when extracted under
`native/third_party/faiss_prebuilt`:

- For Android multi-ABI tarball (recommended):

  faiss_prebuilt/
    arm64-v8a/
      lib/
        libfaiss.so or libfaiss.a
      include/
        faiss/*.h
    armeabi-v7a/
      lib/
      include/
    x86_64/
      lib/
      include/

- For host (x86_64) tarball (optional):

  faiss_prebuilt/
    lib/
      libfaiss.so
    include/

Notes:
- The wrapper CMake will prefer a per-ABI prebuilt folder (e.g. `${ABI}/lib`).
- If no prebuilt is found, the workflow attempts to build FAISS from source
  under `native/third_party/faiss` (requires appropriate build tools and BLAS).
- When producing a static `libfaiss.a`, ensure it's built with
  position-independent code (`-fPIC`) so it can be linked into a shared
  JNI library on Android.

Troubleshooting tips:
- If CMake cannot find `libfaiss`, check the extracted paths inside the
  workflow job artifacts (the workflow uploads `faiss-candidates-*.zip`).
- For BLAS problems, prefer providing a prebuilt OpenBLAS per-ABI and
  set `OPENBLAS_PREBUILT_URL` in workflow secrets.
