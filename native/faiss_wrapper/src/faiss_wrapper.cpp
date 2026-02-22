#include "../include/faiss_wrapper.h"
#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define LOG_TAG "faiss_wrapper_proto"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

bool faiss_init() {
    LOGI("faiss_init (prototype) called");
    return true;
}

int faiss_open_index(const char* path) {
    LOGI("faiss_open_index (prototype) path=%s", path ? path : "(null)");
    // Prototype: return fake handle 1 if path non-null
    if (path == nullptr) return -1;
    return 1;
}

void faiss_close_index(int handle) {
    LOGI("faiss_close_index (prototype) handle=%d", handle);
}

int faiss_search(int handle, const float* query, int dim, int k, int* out_ids, float* out_scores) {
    LOGI("faiss_search (prototype) handle=%d dim=%d k=%d", handle, dim, k);
    if (handle <= 0 || query == nullptr || out_ids == nullptr) return 0;
    // Prototype: return k dummy ids 0..k-1 and descending scores
    for (int i = 0; i < k; ++i) {
        out_ids[i] = i;
        if (out_scores) out_scores[i] = 1.0f - (float)i * 0.01f;
    }
    return k;
}

// Simple JNI bridge to be used by Kotlin until full API is implemented
JNIEXPORT jint JNICALL
Java_com_example_powerai_faiss_FaissNative_openIndex(JNIEnv* env, jclass /*cls*/, jstring path) {
    const char* cpath = env->GetStringUTFChars(path, nullptr);
    int handle = faiss_open_index(cpath);
    env->ReleaseStringUTFChars(path, cpath);
    return handle;
}

JNIEXPORT void JNICALL
Java_com_example_powerai_faiss_FaissNative_closeIndex(JNIEnv* env, jclass /*cls*/, jint handle) {
    faiss_close_index(handle);
}

JNIEXPORT jintArray JNICALL
Java_com_example_powerai_faiss_FaissNative_search(JNIEnv* env, jclass /*cls*/, jint handle, jfloatArray query, jint k) {
    jintArray out = env->NewIntArray(k);
    if (!out) return nullptr;
    jfloat* q = env->GetFloatArrayElements(query, nullptr);
    std::vector<int> ids(k);
    std::vector<float> scores(k);
    int found = faiss_search(handle, q, env->GetArrayLength(query), k, ids.data(), scores.data());
    env->ReleaseFloatArrayElements(query, q, JNI_ABORT);
    env->SetIntArrayRegion(out, 0, k, ids.data());
    return out;
}

} // extern C
