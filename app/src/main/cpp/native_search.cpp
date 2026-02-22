// Optimized native_search using ARM NEON intrinsics and OpenMP
#include <jni.h>
#include <vector>
#include <mutex>
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <chrono>
#include <android/log.h>
#include <thread>
#include <atomic>
#include <fstream>
#include <string>
#if defined(__aarch64__) || defined(__ARM_NEON)
#include <arm_neon.h>
#define HAVE_NEON 1
#else
#define HAVE_NEON 0
#endif

static std::vector<long long> g_ids;
static std::vector<float> g_vectors; // row-major contiguous
static int g_dim = 0;
static std::mutex g_mutex;

static const char* LOG_TAG = "JNI_NEON";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeInit(JNIEnv* env, jobject thiz, jint dim) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_dim = dim;
    g_ids.clear();
    g_vectors.clear();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "nativeInit dim=%d", dim);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeAddVectors(JNIEnv* env, jobject thiz,
                                                                         jlongArray ids,
                                                                         jfloatArray vectors,
                                                                         jint dim) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dim != g_dim) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "dim mismatch: %d vs %d", dim, g_dim);
        return JNI_FALSE;
    }
    jsize n_ids = env->GetArrayLength(ids);
    jsize n_vecs = env->GetArrayLength(vectors);
    if (n_ids * dim != n_vecs) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "array length mismatch ids=%d vecs=%d dim=%d", n_ids, n_vecs, dim);
        return JNI_FALSE;
    }

    jlong* id_buf = env->GetLongArrayElements(ids, nullptr);
    jfloat* vec_buf = env->GetFloatArrayElements(vectors, nullptr);

    for (jsize i = 0; i < n_ids; ++i) {
        g_ids.push_back((long long)id_buf[i]);
        for (int d = 0; d < dim; ++d) {
            g_vectors.push_back(vec_buf[i * dim + d]);
        }
    }

    env->ReleaseLongArrayElements(ids, id_buf, 0);
    env->ReleaseFloatArrayElements(vectors, vec_buf, 0);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "nativeAddVectors added=%d total=%zu", n_ids, g_ids.size());
    return JNI_TRUE;
}

// Helper: compute squared L2 distance between query and vector at base index.
// Use NEON when available, otherwise fallback to scalar loop.
static inline float neon_l2_distance(const float* a, const float* b, int dim) {
#if HAVE_NEON
    int i = 0;
    float32x4_t vsum = vdupq_n_f32(0.0f);
    for (; i <= dim - 4; i += 4) {
        float32x4_t va = vld1q_f32(a + i);
        float32x4_t vb = vld1q_f32(b + i);
        float32x4_t d = vsubq_f32(va, vb);
        float32x4_t sq = vmulq_f32(d, d);
        vsum = vaddq_f32(vsum, sq);
    }
    float sum = vgetq_lane_f32(vsum, 0) + vgetq_lane_f32(vsum, 1) + vgetq_lane_f32(vsum, 2) + vgetq_lane_f32(vsum, 3);
    for (; i < dim; ++i) {
        float diff = a[i] - b[i];
        sum += diff * diff;
    }
    return sum;
#else
    float sum = 0.0f;
    for (int i = 0; i < dim; ++i) {
        float diff = a[i] - b[i];
        sum += diff * diff;
    }
    return sum;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeSearch(JNIEnv* env, jobject thiz,
                                                                     jfloatArray query,
                                                                     jint k) {
    jsize qlen = env->GetArrayLength(query);
    if (qlen != g_dim) return env->NewLongArray(0);

    jfloat* qbuf = env->GetFloatArrayElements(query, nullptr);
    const size_t n = g_ids.size();
    if (n == 0) {
        env->ReleaseFloatArrayElements(query, qbuf, 0);
        return env->NewLongArray(0);
    }

    // High precision timing start
    auto t0 = std::chrono::high_resolution_clock::now();

    // compute distances in parallel using std::thread (portable across ABIs)
    std::vector<std::pair<float, long long>> scores;
    scores.resize(n);

    unsigned int hw_threads = std::thread::hardware_concurrency();
    if (hw_threads == 0) hw_threads = 2;
    size_t chunk = (n + hw_threads - 1) / hw_threads;
    std::vector<std::thread> threads;
    threads.reserve(hw_threads);

    for (unsigned int t = 0; t < hw_threads; ++t) {
        size_t start = t * chunk;
        size_t end = std::min(start + chunk, n);
        if (start >= end) break;
        threads.emplace_back([start, end, &scores, qbuf]() {
            for (size_t i = start; i < end; ++i) {
                const float* vec = g_vectors.data() + i * g_dim;
                float dist = neon_l2_distance(reinterpret_cast<const float*>(qbuf), vec, g_dim);
                scores[i].first = dist;
                scores[i].second = g_ids[i];
            }
        });
    }
    for (auto &th : threads) th.join();

    // High precision timing end
    auto t1 = std::chrono::high_resolution_clock::now();
    double elapsed_ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "search computed %zu distances in %.3f ms (threads may vary)", n, elapsed_ms);

    // select top-k
    if ((int)scores.size() > k) {
        std::nth_element(scores.begin(), scores.begin() + k, scores.end(), [](auto &a, auto &b){ return a.first < b.first; });
        scores.resize(k);
    }
    std::sort(scores.begin(), scores.end(), [](auto &a, auto &b){ return a.first < b.first; });

    env->ReleaseFloatArrayElements(query, qbuf, 0);

    jlongArray out = env->NewLongArray((jsize)scores.size());
    if (!out) return nullptr;
    std::vector<jlong> out_ids;
    out_ids.reserve(scores.size());
    for (auto &p: scores) out_ids.push_back((jlong)p.second);
    env->SetLongArrayRegion(out, 0, (jsize)out_ids.size(), out_ids.data());
    return out;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeSaveIndex(JNIEnv* env, jobject thiz, jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(g_mutex);
    bool ok = false;
    try {
        std::ofstream ofs(path, std::ios::binary);
        if (!ofs) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "saveIndex: open failed %s", path);
            env->ReleaseStringUTFChars(jpath, path);
            return JNI_FALSE;
        }
        int32_t dim32 = g_dim;
        int64_t n = (int64_t)g_ids.size();
        ofs.write(reinterpret_cast<const char*>(&dim32), sizeof(dim32));
        ofs.write(reinterpret_cast<const char*>(&n), sizeof(n));
        if (n > 0) {
            ofs.write(reinterpret_cast<const char*>(g_ids.data()), sizeof(long long) * n);
            ofs.write(reinterpret_cast<const char*>(g_vectors.data()), sizeof(float) * n * g_dim);
        }
        ofs.close();
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "saveIndex: wrote %lld ids dim=%d to %s", (long long)n, g_dim, path);
        ok = true;
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "saveIndex exception: %s", e.what());
        ok = false;
    }
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeLoadIndex(JNIEnv* env, jobject thiz, jstring jpath) {
    const char* path = env->GetStringUTFChars(jpath, nullptr);
    if (!path) return JNI_FALSE;
    std::lock_guard<std::mutex> lock(g_mutex);
    bool ok = false;
    try {
        std::ifstream ifs(path, std::ios::binary);
        if (!ifs) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "loadIndex: open failed %s", path);
            env->ReleaseStringUTFChars(jpath, path);
            return JNI_FALSE;
        }
        int32_t dim32 = 0;
        int64_t n = 0;
        ifs.read(reinterpret_cast<char*>(&dim32), sizeof(dim32));
        ifs.read(reinterpret_cast<char*>(&n), sizeof(n));
        if (dim32 <= 0 || n < 0) {
            __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "loadIndex: invalid header dim=%d n=%lld", dim32, (long long)n);
            env->ReleaseStringUTFChars(jpath, path);
            return JNI_FALSE;
        }
        std::vector<long long> ids;
        std::vector<float> vecs;
        ids.resize((size_t)n);
        vecs.resize((size_t)n * dim32);
        if (n > 0) {
            ifs.read(reinterpret_cast<char*>(ids.data()), sizeof(long long) * n);
            ifs.read(reinterpret_cast<char*>(vecs.data()), sizeof(float) * n * dim32);
        }
        // commit
        g_dim = dim32;
        g_ids.swap(ids);
        g_vectors.swap(vecs);
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "loadIndex: loaded %lld ids dim=%d from %s", (long long)g_ids.size(), g_dim, path);
        ok = true;
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "loadIndex exception: %s", e.what());
        ok = false;
    }
    env->ReleaseStringUTFChars(jpath, path);
    return ok ? JNI_TRUE : JNI_FALSE;
}
