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
| 0 - skeleton, nav shell, DI | Done, builds |
| 1 - HF search, Room library, WorkManager download pipeline | Done, builds |
| 2 - llama.cpp JNI engine, GPU→CPU fallback | Done, builds and links (CPU backend) - see GPU note below |
| 3 - Ktor LAN server, OpenAI-compatible routes | Done, builds |
| 4 - Monitor screen wired to real system + inference metrics | Done, builds |
| 5 - empty/error states, DataStore settings persistence, unit tests | Done, tests pass |

**Verified on a real machine**: `:core` and `:androidApp` compile, the Room/KSP annotation
processing runs, `androidApp:testDebugUnitTest` passes, the CMake/NDK native build compiles and
links `llama.cpp` + the JNI bridge for `arm64-v8a` and `x86_64`, and `assembleDebug` produces a
real, installable `androidApp-debug.apk`. This has not yet been run on an actual device/emulator
(no model has been loaded and no request has hit the server), so treat the checklist below as the
remaining verification.

## Building it yourself

1. **Fetch the llama.cpp submodule** (pinned in `.gitmodules`):
   ```bash
   git submodule update --init --recursive
   ```
2. **Install the NDK** (`27.0.12077973`, matching `androidApp/build.gradle.kts`) and **CMake**
   (`3.22.1`) via Android Studio's SDK Manager, or headless:
   ```bash
   sdkmanager "ndk;27.0.12077973" "cmake;3.22.1" "platforms;android-35" "build-tools;35.0.0"
   ```
3. Open in Android Studio, or run `./gradlew assembleDebug` from the command line (the Gradle
   wrapper jar is committed, so no separate Gradle install is required).

### GPU (Vulkan) backend

`CMakeLists.txt` builds the **CPU backend by default** - that's what actually compiled and linked
here. Enabling GPU inference needs `GGML_VULKAN=ON`, which in turn needs the **LunarG Vulkan SDK**
installed on the host machine (its `glslc` compiler and `SPIRV-Headers` are used at build time to
compile llama.cpp's Vulkan compute shaders - the Android NDK does not bundle these). Once the
Vulkan SDK is installed:
```bash
./gradlew assembleDebug -PGGML_VULKAN=ON -PVulkanSdkPath="C:\VulkanSDK\<version>"
```
(`androidApp/build.gradle.kts` forwards both as CMake defines when present.) The GPU→CPU fallback
logic in `LlamaCppInferenceEngine` is written and ready either way - it just has nothing to fall
back *from* until Vulkan is actually compiled in.

## Verifying each phase actually works on-device

The build compiles and packages; it has not been installed on a device yet. These are the real
exit criteria (matches `development-plan.md`):

1. Install and confirm all four tabs (Search, Library, Server, Monitor) navigate.
2. Search "qwen2.5", download the smallest GGUF result over Wi-Fi, confirm it lands in Library as
   downloaded/idle.
3. Load it - confirm it runs (CPU, until Vulkan is built in); then load a model too large for the
   device's RAM and confirm a real, honest failure rather than a silent hang.
4. From a laptop on the same Wi-Fi: `curl http://<phone-ip>:8080/v1/chat/completions` and confirm a
   real streamed response.
5. Send a few requests from another device and watch the Monitor tab move in real time.

## Known gaps / honest limitations

- GPU inference is code-complete but unbuilt: the CPU backend is what's actually been compiled and
  linked in this session (see the Vulkan note above).
- No model has actually been loaded and run yet - the JNI bridge links correctly against llama.cpp's
  API, but token generation hasn't been exercised end to end.
- CPU usage on the Monitor screen reads `/proc/stat`; some OEM builds sandbox this for non-system
  apps via SELinux, in which case it reports 0 rather than crashing.
- VRAM usage is approximated from model file size (llama.cpp doesn't expose a direct VRAM query),
  not measured live.

## Bugs found and fixed by actually running the build

Worth knowing about since they'd otherwise look like plausible-but-wrong code on read-through:

- `AndroidManifest.xml` declared `xmlns:android="http://schemas.android.com/apk/res-auto"` (the
  custom-attribute namespace) instead of `.../apk/res/android` - every `android:name` attribute
  was silently invisible to the manifest merger until this was fixed.
- `LibraryScreen.kt` had an explicit `import androidx.compose.foundation.layout.weight`, which
  shadowed the real `RowScope.weight` modifier (resolved via receiver scope, not import) with an
  internal same-named property and broke compilation.
- `libs.versions.toml` pinned `koin-androidx-compose` to `1.1.5`, a version that doesn't exist on
  Maven Central - corrected to `3.5.6` to match `koin-android`.
- `llama_bridge.cpp` called `llama_sampler_init_penalties` with 4 arguments; the real signature
  takes 5 (`n_vocab, penalty_last_n, penalty_repeat, penalty_freq, penalty_present`) - found by
  diffing against the vendored `llama.h` once the submodule was actually fetched.
