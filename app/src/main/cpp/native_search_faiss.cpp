#include <jni.h>
#include <vector>
#include <mutex>
#include <memory>
#include <android/log.h>
#include <algorithm>

#ifdef HAVE_FAISS
#include <faiss/IndexFlat.h>
#include <faiss/IndexIDMap.h>
#endif

static std::mutex g_mutex;
static int g_dim = 0;

#ifdef HAVE_FAISS
using idx_t = faiss::idx_t;
static std::unique_ptr<faiss::IndexFlatL2> g_index;
static std::unique_ptr<faiss::IndexIDMap> g_idmap;
#endif

static const char* LOG_TAG = "JNI_FAISS";

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeInit(JNIEnv* env, jobject thiz, jint dim) {
    std::lock_guard<std::mutex> lock(g_mutex);
    g_dim = dim;
#ifdef HAVE_FAISS
    try {
        g_index.reset(new faiss::IndexFlatL2(dim));
        // Wrap with ID map to support user-provided ids
        g_idmap.reset(new faiss::IndexIDMap(g_index.get()));
        __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "Faiss index initialized dim=%d", dim);
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Faiss init exception: %s", e.what());
        g_index.reset();
        g_idmap.reset();
        return JNI_FALSE;
    }
    return JNI_TRUE;
#else
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Faiss not available at build time; nativeInit is noop (dim=%d)", dim);
    return JNI_TRUE;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeAddVectors(JNIEnv* env, jobject thiz,
                                                                         jlongArray ids,
                                                                         jfloatArray vectors,
                                                                         jint dim) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (dim != g_dim) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "dim mismatch: got %d expected %d", dim, g_dim);
        return JNI_FALSE;
    }

#ifdef HAVE_FAISS
    if (!g_idmap) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Faiss index not initialized");
        return JNI_FALSE;
    }

    jsize n_ids = env->GetArrayLength(ids);
    jsize n_vecs = env->GetArrayLength(vectors);
    if (n_ids * dim != n_vecs) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "array length mismatch: ids=%d vecs=%d dim=%d", n_ids, n_vecs, dim);
        return JNI_FALSE;
    }

    jlong* id_buf = env->GetLongArrayElements(ids, nullptr);
    jfloat* vec_buf = env->GetFloatArrayElements(vectors, nullptr);
    if (!id_buf || !vec_buf) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "failed to access java arrays");
        return JNI_FALSE;
    }

    try {
        // Faiss expects contiguous float arrays in row-major order
        idx_t n = (idx_t)n_ids;
        idx_t* ids_ptr = new idx_t[n];
        for (idx_t i = 0; i < n; ++i) ids_ptr[i] = (idx_t)id_buf[i];

        g_idmap->add_with_ids(n, reinterpret_cast<const float*>(vec_buf), ids_ptr);
        delete[] ids_ptr;
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Faiss add exception: %s", e.what());
        env->ReleaseLongArrayElements(ids, id_buf, 0);
        env->ReleaseFloatArrayElements(vectors, vec_buf, 0);
        return JNI_FALSE;
    }

    env->ReleaseLongArrayElements(ids, id_buf, 0);
    env->ReleaseFloatArrayElements(vectors, vec_buf, 0);
    return JNI_TRUE;
#else
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Faiss not available at build time; nativeAddVectors no-op");
    return JNI_TRUE;
#endif
}

extern "C" JNIEXPORT jlongArray JNICALL
Java_com_example_powerai_data_retriever_NativeAnnSearcher_nativeSearch(JNIEnv* env, jobject thiz,
                                                                     jfloatArray query,
                                                                     jint k) {
    std::lock_guard<std::mutex> lock(g_mutex);
    jsize qlen = env->GetArrayLength(query);
    if (qlen != g_dim) return nullptr;

#ifdef HAVE_FAISS
    if (!g_idmap) return env->NewLongArray(0);

    jfloat* qbuf = env->GetFloatArrayElements(query, nullptr);
    if (!qbuf) return env->NewLongArray(0);

    try {
        idx_t nq = 1;
        std::vector<float> distances(k * nq);
        std::vector<idx_t> labels(k * nq);

        g_idmap->search(nq, reinterpret_cast<const float*>(qbuf), k, distances.data(), labels.data());

        // Build result: filter out negative labels (faiss uses -1 for empty)
        std::vector<jlong> out_ids;
        for (int i = 0; i < k; ++i) {
            if (labels[i] >= 0) out_ids.push_back((jlong)labels[i]);
        }

        env->ReleaseFloatArrayElements(query, qbuf, 0);

        jlongArray out = env->NewLongArray((jsize)out_ids.size());
        if (!out) return nullptr;
        env->SetLongArrayRegion(out, 0, (jsize)out_ids.size(), out_ids.data());
        return out;
    } catch (const std::exception &e) {
        __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, "Faiss search exception: %s", e.what());
        env->ReleaseFloatArrayElements(query, qbuf, 0);
        return env->NewLongArray(0);
    }
#else
    __android_log_print(ANDROID_LOG_WARN, LOG_TAG, "Faiss not available at build time; nativeSearch fallback returns empty");
    return env->NewLongArray(0);
#endif
}
