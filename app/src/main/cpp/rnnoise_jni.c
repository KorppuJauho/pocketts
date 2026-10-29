// The bridge between Kotlin (io.github.korppujauho.pocketts.Rnnoise) and RNNoise: one state per microphone, fed
// 20 ms at a time (960 samples at 48 kHz), which is two of RNNoise's 10 ms frames.
#include <jni.h>
#include <math.h>
#include <stdint.h>
#include "rnnoise.h"

#define FRAME 480

JNIEXPORT jlong JNICALL
Java_io_github_korppujauho_pocketts_Rnnoise_create(JNIEnv *env, jclass cls) {
    return (jlong) (intptr_t) rnnoise_create(NULL); // its built-in model
}

JNIEXPORT void JNICALL
Java_io_github_korppujauho_pocketts_Rnnoise_destroy(JNIEnv *env, jclass cls, jlong state) {
    rnnoise_destroy((DenoiseState *) (intptr_t) state);
}

// Cleans the samples in place (whole 10 ms frames); returns the highest chance of speech among them, 0 - 1
JNIEXPORT jfloat JNICALL
Java_io_github_korppujauho_pocketts_Rnnoise_process(JNIEnv *env, jclass cls, jlong state, jshortArray pcm) {
    DenoiseState *st = (DenoiseState *) (intptr_t) state;
    jsize n = (*env)->GetArrayLength(env, pcm);
    jshort frame[FRAME];
    float in[FRAME], out[FRAME];
    float speech = 0;
    for (jsize at = 0; at + FRAME <= n; at += FRAME) {
        (*env)->GetShortArrayRegion(env, pcm, at, FRAME, frame);
        // RNNoise takes the samples at their 16-bit size, as floats
        for (int i = 0; i < FRAME; i++) in[i] = frame[i];
        float p = rnnoise_process_frame(st, out, in);
        if (p > speech) speech = p;
        for (int i = 0; i < FRAME; i++) {
            float v = out[i];
            frame[i] = (jshort) lrintf(v > 32767.f ? 32767.f : v < -32768.f ? -32768.f : v);
        }
        (*env)->SetShortArrayRegion(env, pcm, at, FRAME, frame);
    }
    return speech;
}
