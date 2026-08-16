# LLM Manager

A local LLM manager for Android: search and download GGUF models from Hugging Face, run them
on-device via llama.cpp with automatic GPU→CPU fallback, and expose them to your LAN as an
OpenAI-compatible API. Built from `development-plan.md` and `mockup.html` as a Kotlin
Multiplatform app (`:core` interfaces now, room for a `:desktopApp` later without touching UI logic).

## Module layout

- `:core` - pure Kotlin (multiplatform, `androidTarget()` + `jvm()`), no Android SDK. Defines the
  four contracts everything else is built against: `ModelRepository`, `InferenceEngine`,
  `ApiServer`, `MetricsCollector`, plus their data classes.
- `:androidApp` - Compose UI, Koin DI, and every Android-specific implementation of the four
  interfaces (Room + WorkManager + Hugging Face client, llama.cpp JNI, embedded Ktor server,
  system metrics).

## What's implemented

| Phase | Status |
|---|---|
| 0 - skeleton, nav shell, DI | Done |
| 1 - HF search, Room library, WorkManager download pipeline | Done |
| 2 - llama.cpp JNI engine, GPU→CPU fallback | Code complete, **needs the native build - see below** |
| 3 - Ktor LAN server, OpenAI-compatible routes | Done |
| 4 - Monitor screen wired to real system + inference metrics | Done |
| 5 - empty/error states, DataStore settings persistence, unit tests | Done |

## Before you can build

This was written in an environment without the Android SDK/NDK, so it has **not** been compiled.
Three things to do before it will build in Android Studio:

1. **Fetch the llama.cpp submodule** (referenced in `.gitmodules`, not vendored into this repo):
   ```bash
   git submodule update --init --recursive
   ```
2. **Install the NDK** (Android Studio → SDK Manager → SDK Tools → NDK, or let Android Studio
   prompt you on first sync - `androidApp/build.gradle.kts` pins `ndkVersion = "27.0.12077973"`,
   adjust if you have a different NDK installed).
3. **Generate the Gradle wrapper jar** - it isn't committed (binary files aren't something this
   session could author). Either open the project in Android Studio (it offers to do this
   automatically), or if you have Gradle installed locally:
   ```bash
   gradle wrapper --gradle-version 8.9
   ```

The `androidApp/src/main/cpp/llama_bridge.cpp` bridge is written against llama.cpp's current
`llama_model_load_from_file` / `llama_sampler_*` API. If the exact commit the submodule resolves
to has renamed something, the compiler error will point at the exact line to fix - the call shapes
are otherwise stable across recent llama.cpp releases.

## Verifying each phase actually works

Since this environment couldn't run a build, treat these as the real exit criteria (matches
`development-plan.md`) once you have a device:

1. Install and confirm all four tabs (Search, Library, Server, Monitor) navigate.
2. Search "qwen2.5", download the smallest GGUF result over Wi-Fi, confirm it lands in Library as
   downloaded/idle.
3. Load it - confirm it runs on GPU; then load a model too large for your device's VRAM and confirm
   the "auto-fell-back-to-CPU" banner appears with an honest reason, not a silent switch.
4. From a laptop on the same Wi-Fi: `curl http://<phone-ip>:8080/v1/chat/completions` and confirm a
   real streamed response.
5. Send a few requests from another device and watch the Monitor tab move in real time.

## Known gaps / honest limitations

- The llama.cpp bridge has not been compiled or run against a real model - it's written to the
  documented API but exact function signatures can drift between llama.cpp commits.
- CPU usage on the Monitor screen reads `/proc/stat`; some OEM builds sandbox this for non-system
  apps via SELinux, in which case it reports 0 rather than crashing.
- VRAM usage is approximated from model file size (llama.cpp doesn't expose a direct VRAM query),
  not measured live.
