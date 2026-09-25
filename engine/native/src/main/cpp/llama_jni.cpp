#include <jni.h>
#include <android/log.h>
#include <cstdio>

#define LOG_TAG "llama_jni"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM* cachedVm = nullptr;

// Cache the JavaVM on load so we can attach threads later if needed.
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    cachedVm = vm;
    JNIEnv* env = nullptr;
    if (vm->GetEnv((void**)&env, JNI_VERSION_1_6) != JNI_OK) {
        return -1;
    }
    return JNI_VERSION_1_6;
}

// Simple helper to attach current thread as a daemon.
static JNIEnv* attachCurrentThread() {
    if (!cachedVm) return nullptr;
    JNIEnv* env = nullptr;
    jint res = cachedVm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (cachedVm->AttachCurrentThreadAsDaemon(&env, nullptr) != 0) {
            return nullptr;
        }
    }
    return env;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_initBackend(JNIEnv* env, jobject thiz, jstring backend) {
    const char* cb = backend ? env->GetStringUTFChars(backend, nullptr) : "<null>";
    LOGI("initBackend requested: %s", cb);
    if (backend) env->ReleaseStringUTFChars(backend, cb);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_setOption(JNIEnv* env, jobject thiz, jstring key, jstring value) {
    const char* k = key ? env->GetStringUTFChars(key, nullptr) : "<null>";
    const char* v = value ? env->GetStringUTFChars(value, nullptr) : "<null>";
    LOGI("setOption %s=%s", k, v);
    if (key) env->ReleaseStringUTFChars(key, k);
    if (value) env->ReleaseStringUTFChars(value, v);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_loadModel(JNIEnv* env, jobject thiz, jstring path) {
    const char* p = path ? env->GetStringUTFChars(path, nullptr) : nullptr;
    if (!p) {
        LOGE("loadModel called with null path");
        return JNI_FALSE;
    }
    LOGI("loadModel called with path %s", p);

    // Basic file sanity check before attempting heavy native load.
    FILE* f = fopen(p, "rb");
    if (!f) {
        LOGE("loadModel: cannot open file %s", p);
        env->ReleaseStringUTFChars(path, p);
        return JNI_FALSE;
    }
    // optionally check file has non-zero size
    fseek(f, 0, SEEK_END);
    long sz = ftell(f);
    fclose(f);
    if (sz <= 0) {
        LOGE("loadModel: file empty or unreadable %s (size=%ld)", p, sz);
        env->ReleaseStringUTFChars(path, p);
        return JNI_FALSE;
    }

    // Release the Java string and proceed with actual load in native implementation.
    env->ReleaseStringUTFChars(path, p);
    // NOTE: real model load implementation should follow here. For safety we
    // currently return true to indicate the stub accepted the request; heavy
    // loading should be performed in native lib with its own guards.
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_unloadModel(JNIEnv* env, jobject thiz) {
    LOGI("unloadModel stub");
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_generate(JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens) {
    LOGI("generate stub maxTokens=%d", maxTokens);
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_generateAsync(JNIEnv* env, jobject thiz, jstring prompt, jint maxTokens) {
    LOGI("generateAsync stub maxTokens=%d", maxTokens);
    // attached example: detach after returning
    JNIEnv* threadEnv = attachCurrentThread();
    if (threadEnv) {
        // nothing to do
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_example_powerai_domain_llm_LlamaJni_nativeGenerateStream(JNIEnv* env, jobject thiz, jstring prompt) {
    LOGI("nativeGenerateStream stub");
    return JNI_FALSE;
}
