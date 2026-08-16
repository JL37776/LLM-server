// JNI bridge between LlamaCppInferenceEngine (Kotlin) and llama.cpp's C API.
//
// Written against the current llama.cpp master API (llama_model_load_from_file,
// llama_init_from_model, the llama_sampler_* chain, llama_batch_get_one). llama.cpp's C API
// shifts occasionally between releases; if the vendored submodule commit renamed a function
// used below, the compiler error will point at the exact spot to update - the call shapes here
// are otherwise stable across recent versions.

#include "llama_bridge.h"

#include <android/log.h>
#include <llama.h>

#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOG_TAG "llama_bridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

std::mutex g_error_mutex;
std::string g_last_error;

void set_last_error(const std::string &message) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error = message;
    LOGE("%s", message.c_str());
}

struct EngineHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
};

void llama_log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    if (level >= GGML_LOG_LEVEL_ERROR) {
        set_last_error(text);
    }
}

std::string jstring_to_std(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

} // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeLoadModel(
    JNIEnv *env, jobject /*thiz*/, jstring modelPath, jboolean useGpu, jint nGpuLayers) {
    static std::once_flag backend_init_flag;
    std::call_once(backend_init_flag, [] {
        llama_log_set(llama_log_callback, nullptr);
        llama_backend_init();
    });

    const std::string path = jstring_to_std(env, modelPath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = useGpu ? nGpuLayers : 0;

    llama_model *model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        set_last_error(useGpu
            ? "GPU load failed: model rejected requested GPU layers (likely out of VRAM or unsupported op)."
            : "CPU load failed: could not load model file.");
        return 0;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_batch = 512;

    llama_context *ctx = llama_init_from_model(model, ctx_params);
    if (ctx == nullptr) {
        set_last_error("Failed to create inference context after model load.");
        llama_model_free(model);
        return 0;
    }

    auto *handle = new EngineHandle();
    handle->model = model;
    handle->ctx = ctx;
    handle->vocab = llama_model_get_vocab(model);

    return reinterpret_cast<jlong>(handle);
}

JNIEXPORT jstring JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeGetLastError(
    JNIEnv *env, jobject /*thiz*/) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeFree(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);
    if (engine->ctx) llama_free(engine->ctx);
    if (engine->model) llama_model_free(engine->model);
    delete engine;
}

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeGenerate(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jstring prompt, jint maxTokens,
    jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty, jobject callback) {
    if (handle == 0) return;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);

    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");

    const std::string prompt_text = jstring_to_std(env, prompt);

    std::vector<llama_token> tokens(prompt_text.size() + 16);
    int n_tokens = llama_tokenize(
        engine->vocab, prompt_text.c_str(), static_cast<int32_t>(prompt_text.size()),
        tokens.data(), static_cast<int32_t>(tokens.size()), /*add_special=*/true, /*parse_special=*/true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            engine->vocab, prompt_text.c_str(), static_cast<int32_t>(prompt_text.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    tokens.resize(n_tokens);

    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    llama_sampler *sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(sampler, llama_sampler_init_penalties(64, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(engine->ctx, batch) != 0) {
        set_last_error("Prompt decode failed.");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        llama_sampler_free(sampler);
        return;
    }

    char piece_buf[256];
    for (int i = 0; i < maxTokens; i++) {
        llama_token next = llama_sampler_sample(sampler, engine->ctx, -1);

        if (llama_vocab_is_eog(engine->vocab, next)) {
            env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
            break;
        }

        int piece_len = llama_token_to_piece(engine->vocab, next, piece_buf, sizeof(piece_buf), 0, true);
        std::string piece(piece_buf, piece_len > 0 ? piece_len : 0);

        jstring jpiece = env->NewStringUTF(piece.c_str());
        env->CallVoidMethod(callback, onTokenMethod, jpiece, (i == maxTokens - 1) ? JNI_TRUE : JNI_FALSE);
        env->DeleteLocalRef(jpiece);

        llama_sampler_accept(sampler, next);

        llama_batch next_batch = llama_batch_get_one(&next, 1);
        if (llama_decode(engine->ctx, next_batch) != 0) {
            set_last_error("Decode failed mid-generation.");
            break;
        }
    }

    llama_sampler_free(sampler);
}

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeVramUsedBytes(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);
    // llama.cpp does not expose a direct VRAM query; approximate from model size, which is a
    // reasonable floor for GPU-resident weights.
    return static_cast<jlong>(llama_model_size(engine->model));
}

} // extern "C"
