#pragma once

#include <jni.h>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeSupportsGpuOffload(
    JNIEnv *env, jobject thiz);

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeLoadModel(
    JNIEnv *env, jobject thiz, jstring modelPath, jboolean useGpu, jint nGpuLayers);

JNIEXPORT jstring JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeGetLastError(
    JNIEnv *env, jobject thiz);

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeFree(
    JNIEnv *env, jobject thiz, jlong handle);

JNIEXPORT void JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeGenerate(
    JNIEnv *env, jobject thiz, jlong handle, jstring prompt, jint maxTokens,
    jfloat temperature, jfloat topP, jint topK, jfloat repeatPenalty, jobject callback);

JNIEXPORT jlong JNICALL
Java_com_nzshores_llmserver_engine_llama_jni_LlamaNative_nativeVramUsedBytes(
    JNIEnv *env, jobject thiz, jlong handle);

}
