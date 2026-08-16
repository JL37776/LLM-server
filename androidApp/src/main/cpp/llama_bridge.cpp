// JNI bridge between LlamaCppInferenceEngine (Kotlin) and llama.cpp's C API.
// Supports text-only generation and multimodal (vision) generation via mtmd.

#include "llama_bridge.h"

#include <android/log.h>
#include <llama.h>
#include <mtmd.h>
#include <mtmd-helper.h>

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

void clear_last_error() {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error.clear();
}

void set_last_error(const std::string &message) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    g_last_error = message;
    LOGE("%s", message.c_str());
}

void append_error(const std::string &message) {
    std::lock_guard<std::mutex> lock(g_error_mutex);
    if (!g_last_error.empty() && g_last_error.back() != '\n') {
        g_last_error += ' ';
    }
    g_last_error += message;
    LOGE("%s", message.c_str());
}

std::string trim(const std::string &s) {
    auto start = s.find_first_not_of(" \t\r\n");
    if (start == std::string::npos) return "";
    auto end = s.find_last_not_of(" \t\r\n");
    return s.substr(start, end - start + 1);
}

struct EngineHandle {
    llama_model *model = nullptr;
    llama_context *ctx = nullptr;
    const llama_vocab *vocab = nullptr;
    mtmd_context *mmctx = nullptr;
};

void llama_log_callback(ggml_log_level level, const char *text, void * /*user_data*/) {
    if (level >= GGML_LOG_LEVEL_ERROR) {
        std::string trimmed = trim(text);
        if (!trimmed.empty()) {
            append_error(trimmed);
        }
    }
}

std::string jstring_to_std(JNIEnv *env, jstring value) {
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void init_sampler(llama_sampler *&sampler, const llama_vocab *vocab,
                  float temperature, float topP, int topK, float repeatPenalty) {
    llama_sampler_chain_params sampler_params = llama_sampler_chain_default_params();
    sampler = llama_sampler_chain_init(sampler_params);
    llama_sampler_chain_add(
        sampler,
        llama_sampler_init_penalties(
            llama_vocab_n_tokens(vocab),
            64, repeatPenalty, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_k(topK));
    llama_sampler_chain_add(sampler, llama_sampler_init_top_p(topP, 1));
    llama_sampler_chain_add(sampler, llama_sampler_init_temp(temperature));
    llama_sampler_chain_add(sampler, llama_sampler_init_dist(LLAMA_DEFAULT_SEED));
}

void generate_loop(JNIEnv *env, EngineHandle *engine, llama_sampler *sampler,
                   int maxTokens, jobject callback) {
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");

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
}

} // namespace

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeSupportsGpuOffload(
    JNIEnv * /*env*/, jobject /*thiz*/) {
    return llama_supports_gpu_offload() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeLoadModel(
    JNIEnv *env, jobject /*thiz*/, jstring modelPath, jboolean useGpu, jint nGpuLayers) {
    static std::once_flag backend_init_flag;
    std::call_once(backend_init_flag, [] {
        llama_log_set(llama_log_callback, nullptr);
        llama_backend_init();
    });

    clear_last_error();

    const std::string path = jstring_to_std(env, modelPath);

    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = useGpu ? nGpuLayers : 0;

    llama_model *model = llama_model_load_from_file(path.c_str(), model_params);
    if (model == nullptr) {
        std::lock_guard<std::mutex> lock(g_error_mutex);
        if (g_last_error.empty()) {
            g_last_error = useGpu
                ? "GPU load failed: model rejected requested GPU layers (likely out of VRAM or unsupported op)."
                : "CPU load failed: could not load model file.";
        }
        LOGE("nativeLoadModel failed: %s", g_last_error.c_str());
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

JNIEXPORT jboolean JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeLoadMmproj(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jstring mmprojPath) {
    if (handle == 0) return JNI_FALSE;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);

    clear_last_error();

    if (engine->mmctx) {
        mtmd_free(engine->mmctx);
        engine->mmctx = nullptr;
    }

    const std::string path = jstring_to_std(env, mmprojPath);
    LOGI("Loading mmproj from: %s", path.c_str());

    mtmd_context_params params = mtmd_context_params_default();
    params.use_gpu = false;
    params.n_threads = 4;
    params.warmup = false;

    mtmd_context *mmctx = mtmd_init_from_file(path.c_str(), engine->model, params);
    if (mmctx == nullptr) {
        std::lock_guard<std::mutex> lock(g_error_mutex);
        if (g_last_error.empty()) {
            g_last_error = "Failed to load mmproj file.";
        }
        LOGE("nativeLoadMmproj failed: %s", g_last_error.c_str());
        return JNI_FALSE;
    }

    engine->mmctx = mmctx;
    LOGI("mmproj loaded, vision=%d, audio=%d",
         mtmd_support_vision(mmctx), mtmd_support_audio(mmctx));
    return JNI_TRUE;
}

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeFreeMmproj(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);
    if (engine->mmctx) {
        mtmd_free(engine->mmctx);
        engine->mmctx = nullptr;
        LOGI("mmproj freed");
    }
}

JNIEXPORT jboolean JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeHasVision(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return JNI_FALSE;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);
    if (!engine->mmctx) return JNI_FALSE;
    return mtmd_support_vision(engine->mmctx) ? JNI_TRUE : JNI_FALSE;
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
    if (engine->mmctx) mtmd_free(engine->mmctx);
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
        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (n_tokens < 0) {
        tokens.resize(-n_tokens);
        n_tokens = llama_tokenize(
            engine->vocab, prompt_text.c_str(), static_cast<int32_t>(prompt_text.size()),
            tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    }
    tokens.resize(n_tokens);

    llama_sampler *sampler = nullptr;
    init_sampler(sampler, engine->vocab, temperature, topP, topK, repeatPenalty);

    llama_batch batch = llama_batch_get_one(tokens.data(), static_cast<int32_t>(tokens.size()));
    if (llama_decode(engine->ctx, batch) != 0) {
        set_last_error("Prompt decode failed.");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        llama_sampler_free(sampler);
        return;
    }

    generate_loop(env, engine, sampler, maxTokens, callback);
    llama_sampler_free(sampler);
}

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeGenerateWithImage(
    JNIEnv *env, jobject /*thiz*/, jlong handle, jstring prompt, jbyteArray imageData,
    jint maxTokens, jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty,
    jobject callback) {
    if (handle == 0) return;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);

    if (!engine->mmctx) {
        set_last_error("No mmproj loaded. Load a vision projector first.");
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        return;
    }

    clear_last_error();

    const std::string prompt_text = jstring_to_std(env, prompt);
    LOGI("generateWithImage: prompt_len=%zu", prompt_text.size());

    jsize img_len = env->GetArrayLength(imageData);
    jbyte *img_bytes = env->GetByteArrayElements(imageData, nullptr);
    LOGI("generateWithImage: image_bytes=%d", img_len);

    // Load image from raw bytes (jpg/png/bmp) via stb_image inside mtmd
    mtmd_helper_bitmap_wrapper bmp_wrap = mtmd_helper_bitmap_init_from_buf(
        engine->mmctx,
        reinterpret_cast<const unsigned char *>(img_bytes),
        static_cast<size_t>(img_len),
        false);

    env->ReleaseByteArrayElements(imageData, img_bytes, JNI_ABORT);

    if (!bmp_wrap.bitmap) {
        set_last_error("Failed to decode image data.");
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        return;
    }

    LOGI("generateWithImage: bitmap %ux%u", mtmd_bitmap_get_nx(bmp_wrap.bitmap), mtmd_bitmap_get_ny(bmp_wrap.bitmap));

    // Tokenize: the prompt must contain the media marker (default: <__media__>)
    // If the prompt doesn't contain the marker, prepend it
    std::string full_prompt = prompt_text;
    const char *marker = mtmd_get_marker(engine->mmctx);
    if (full_prompt.find(marker) == std::string::npos) {
        full_prompt = std::string(marker) + "\n" + full_prompt;
    }

    mtmd_input_text input_text;
    input_text.text = full_prompt.c_str();
    input_text.text_len = full_prompt.size();
    input_text.add_special = true;
    input_text.parse_special = true;

    const mtmd_bitmap *bitmaps[] = { bmp_wrap.bitmap };

    mtmd_input_chunks *chunks = mtmd_input_chunks_init();
    int32_t tok_result = mtmd_tokenize(engine->mmctx, chunks, &input_text, bitmaps, 1);
    mtmd_bitmap_free(bmp_wrap.bitmap);

    if (tok_result != 0) {
        LOGE("mtmd_tokenize failed: %d", tok_result);
        set_last_error("Failed to tokenize multimodal input.");
        mtmd_input_chunks_free(chunks);
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        return;
    }

    size_t n_chunks = mtmd_input_chunks_size(chunks);
    LOGI("generateWithImage: %zu chunks after tokenize", n_chunks);

    // Reset KV cache for the new prompt
    llama_memory_clear(llama_get_memory(engine->ctx), true);

    // Evaluate all chunks (text + image embeddings)
    llama_pos new_n_past = 0;
    int32_t eval_result = mtmd_helper_eval_chunks(
        engine->mmctx, engine->ctx, chunks,
        0, 0, 512, true, &new_n_past);

    mtmd_input_chunks_free(chunks);

    if (eval_result != 0) {
        LOGE("mtmd_helper_eval_chunks failed: %d", eval_result);
        set_last_error("Failed to evaluate multimodal input.");
        jclass callbackClass = env->GetObjectClass(callback);
        jmethodID onTokenMethod = env->GetMethodID(callbackClass, "onToken", "(Ljava/lang/String;Z)V");
        env->CallVoidMethod(callback, onTokenMethod, env->NewStringUTF(""), JNI_TRUE);
        return;
    }

    LOGI("generateWithImage: eval done, starting generation");

    llama_sampler *sampler = nullptr;
    init_sampler(sampler, engine->vocab, temperature, topP, topK, repeatPenalty);
    generate_loop(env, engine, sampler, maxTokens, callback);
    llama_sampler_free(sampler);
}

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeVramUsedBytes(
    JNIEnv * /*env*/, jobject /*thiz*/, jlong handle) {
    if (handle == 0) return 0;
    auto *engine = reinterpret_cast<EngineHandle *>(handle);
    return static_cast<jlong>(llama_model_size(engine->model));
}

} // extern "C"
