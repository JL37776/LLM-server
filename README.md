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

**Verified end-to-end on a real physical Android device** (not just a compile check): installed,
launched with zero crashes, live Hugging Face search renders in the Search tab, and an
instrumented test (`EndToEndSmokeTest`) drove the actual production code - search → download a
real GGUF file over the phone's own network → load it into llama.cpp via the JNI bridge → start
the Ktor server → a real `/v1/chat/completions` call → a real generated response. `:core` and
`:androidApp` compile, Room/KSP runs, both `testDebugUnitTest` and the connected instrumented test
pass, and `assembleDebug` produces a real, installable APK.

(Automated UI taps via `adb shell input` don't work on this particular device - its MIUI build
blocks input injection even with "USB debugging (Security settings)" enabled. The instrumented
test above exists specifically to verify the real pipeline without needing taps; the checklist
below still covers the parts only a human tapping through the UI can confirm, like the empty/error
states and the four tabs' visual layout.)

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

## Verifying it yourself

`./gradlew connectedDebugAndroidTest` re-runs the same end-to-end smoke test against whatever
device/emulator `adb devices` sees - search, download, load, start the server, and a real chat
completion, all in one instrumented test (`androidApp/src/androidTest/.../EndToEndSmokeTest.kt`),
independent of whether you can tap through the UI on that device.

What's left is what only a human tapping through the UI can confirm (matches
`development-plan.md`):

1. Confirm all four tabs (Search, Library, Server, Monitor) navigate and look right.
2. Load a model too large for the device's RAM and confirm a real, honest failure/fallback banner
   rather than a silent hang.
3. From a laptop on the same Wi-Fi (not just localhost): `curl http://<phone-ip>:8080/v1/chat/completions`
   and confirm a real streamed response.
4. Send a few requests from another device and watch the Monitor tab move in real time.

## Known gaps / honest limitations

- GPU inference is code-complete but unbuilt: the CPU backend is what's actually been compiled,
  linked, and run in this session (see the Vulkan note above). `LlamaCppInferenceEngine` correctly
  detects this at runtime (`llama_supports_gpu_offload()`) and reports an honest CPU-fallback
  result rather than falsely claiming GPU - verified via the smoke test.
- CPU usage on the Monitor screen reads `/proc/stat`; some OEM builds sandbox this for non-system
  apps via SELinux, in which case it reports 0 rather than crashing.
- VRAM usage is approximated from model file size (llama.cpp doesn't expose a direct VRAM query),
  not measured live.
- Response quality from the smoke test is rough (a 135M model with prompts joined as plain
  `role: content` lines rather than the model's real chat template) - fine for proving the pipeline
  works, not representative of real usage with a 7B+ model and a proper chat template.

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
- `LlamaCppInferenceEngine` reported `backend=GPU` whenever a GPU-requested load succeeded, even
  with `GGML_VULKAN=OFF` - ggml silently ignores `n_gpu_layers` when no GPU backend is registered,
  so it was quietly running CPU while claiming GPU. Only caught by actually loading a model
  on-device and noticing the claim didn't match reality. Fixed by checking
  `llama_supports_gpu_offload()` before attempting a GPU load.
- Any in-app HTTP client call to the app's own server (this smoke test, or a future in-app "test
  it" button) hit Android's default cleartext-blocking network security policy, even against
  `127.0.0.1`. Added a `network_security_config.xml` scoped to localhost only - the LAN server
  itself was never affected, since that policy only gates outbound calls the app makes, not
  inbound connections to the Ktor server it hosts.
