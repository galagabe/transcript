<p align="center">
  <img src="docs/assets/muesli-icon.png" alt="Muesli logo — blue audio waveform on dark background" width="160" />
</p>

<h1 align="center">Muesli for Android</h1>

<p align="center">
  <strong>Local-first voice notes and meeting transcription for Android</strong><br>
  On-device speech-to-text | Dictation keyboard | Private by default
</p>

<p align="center">
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT License" /></a>
  <a href="https://github.com/Muesli-HQ/muesli-android/actions/workflows/ci.yml"><img src="https://github.com/Muesli-HQ/muesli-android/actions/workflows/ci.yml/badge.svg" alt="CI" /></a>
  <img src="https://img.shields.io/badge/platform-Android%207.0%2B-lightgrey?logo=android" alt="Android 7.0+" />
  <a href="https://github.com/Muesli-HQ/muesli-android/releases"><img src="https://img.shields.io/badge/status-0.3.0--alpha-orange" alt="0.3.0-alpha" /></a>
</p>

---

For early-access feedback, email [pranav@muesli.works](mailto:pranav@muesli.works).

## What is Muesli for Android?

Muesli for Android is the Android companion to [Muesli for macOS](https://github.com/Muesli-HQ/muesli) and [Muesli for iOS](https://github.com/Muesli-HQ/muesli-ios). It brings the same local-first product philosophy to Android: record speech, transcribe it on device with a quantized Parakeet model, and keep audio and transcripts local unless you explicitly choose otherwise.

The Android app shares its design language, transcription model family, and product structure with the iOS app, but is built around Android-native workflows:

- **Voice notes app** for recording, transcribing, and copying speech, with usage stats and history.
- **Dictation keyboard (IME)** for voice typing into any text field in any app, with a live waveform, press-and-hold key repeat, and a globe key to switch back to QWERTY keyboards.
- **Meeting recorder** for offline conversations: foreground recording with chunked on-device transcription, retained audio, note templates, and searchable history.

The current release is **v0.3.0-alpha**. Install a sideloadable APK from [GitHub Releases](https://github.com/Muesli-HQ/muesli-android/releases), or build from source. Play Store distribution is not set up yet. Alpha APKs support arm64 devices running Android 7.0+ and are signed with the debug key.

---

## Features

- **On-device transcription** — quantized NVIDIA Parakeet models run locally through [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) / ONNX Runtime; audio never needs to leave the phone for transcription. Parakeet 110M is recommended for fresh installs and low-end phones; Parakeet v3 600M provides multilingual coverage on faster devices.
- **Live dictation waveform** — real-time mic-driven waveform during recording, ported bar-for-bar from the iOS implementation, plus an elapsed-time badge.
- **Dictation keyboard** — a dictation-only input method: explicitly start/stop recording, apply filler-word removal and custom-dictionary replacements, then insert the final text into the active text field. Includes punctuation, editing controls, key repeat, and the system keyboard picker.
- **Quick capture (Android-exclusive)** — a draggable floating bubble that lives over other apps: tap to dictate into a compact overlay card; the note is saved and copied to the clipboard. Also available as a Quick Settings tile and a home-screen widget. Enable from **Settings → Voice Notes → Quick Capture**.
- **Keep mic ready** — optionally prewarm the recognizer when the keyboard opens. Recording starts only when you tap the mic; opening the keyboard never starts capture automatically.
- **Meeting recorder** — a foreground service records meetings (with an ongoing notification + stop/discard actions), segments speech with a bundled Silero VAD and decodes each segment through Parakeet for live local transcription, then optionally diarizes the finished recording on-device into speaker-labeled transcripts (pyannote + TitaNet, bundled).
- **Meeting templates** — seven note templates ported from iOS (General, 1:1, Standup, Interview, Lecture, Customer Call, Planning).
- **Meeting detail** — selectable transcript, AI summary, manual notes with auto-save, retained WAV playback with play/pause and seek, text sharing, and status tracking (recording/completed/failed/cancelled).
- **AI meeting summaries** — optional ChatGPT sign-in or OpenRouter with your own API key. Generates template-based notes and a meeting title from transcript text; disabled by default.
- **Recording microphone picker** — Automatic, Phone, Bluetooth, or USB input preference, with a route label. Bluetooth routing is best-effort and varies by device.
- **Personal dictionary** — custom words with Jaro-Winkler fuzzy matching, phrase replacements, and filler-word filtering applied to both dictation and meeting transcripts.
- **Usage stats** — streak, total words, words-per-minute, and meeting counts computed from local history.
- **iOS design parity** — the official Muesli app icon, MuesliTheme design tokens, glass-pill navigation, dark/light mode, and Blue/Green/Slate accent themes.
- **Model catalog** — pick between Parakeet v3 600M (multilingual, transducer; ~670 MB download) and Parakeet 110M (English, CTC; ~126 MB archive download); resumable, cancellable downloads with extraction, progress, and disk-usage display. HuggingFace downloads have a DNS-failure fallback to hf-mirror.com.
- **Guided onboarding** — Profile → Microphone → Keyboard → Speech Model → Try It → AI Summaries, with steps tailored to your use case. Download a model and save a first test dictation during setup.
- **Local storage first** — voice notes, meetings, and preferences use Room/SQLite and SharedPreferences on device. No Muesli account is required; cloud summary providers are optional.

---

## Install

### Install an alpha APK

1. Open [GitHub Releases](https://github.com/Muesli-HQ/muesli-android/releases) and download the APK attached to the latest alpha.
2. On an arm64 Android 7.0+ phone, allow installation from the app used to open the APK, then install it.
3. Complete onboarding, grant microphone access, and download a speech model. **Parakeet 110M** is the recommended English-only option, especially on low-end phones; choose **Parakeet v3 600M** for multilingual transcription on faster hardware.

The v0.3.0-alpha APK is about 85 MB; speech models download separately. Allow additional free space for model extraction and retained meeting audio. Alpha APKs use the debug signing key and are intended for testing.

### Build from source

**Requirements**

- JDK 17+ (JDK 21 recommended)
- Android SDK: platform `android-36` and build-tools
- An arm64 Android 7.0+ device or arm64 emulator for transcription; x86_64 ASR libraries are not bundled
- Free device storage for the app, selected speech model, archive extraction, and any retained meeting audio (at least 1 GB is a useful starting point for the larger model)

```bash
git clone https://github.com/Muesli-HQ/muesli-android.git
cd muesli-android

# Point Gradle at your SDK (or export ANDROID_HOME)
echo "sdk.dir=$HOME/Library/Android/sdk" > local.properties   # macOS example

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch, onboarding walks you through microphone permission, model selection/download, and a test dictation. You can change models later in **Settings → Models**. Voice notes, keyboard dictation, meeting transcription, and speaker labeling work offline once the selected speech model is downloaded. AI summaries require an internet connection and provider credentials.

Optional: enable the **Muesli Dictation Keyboard** from onboarding or **Settings → Voice Notes → Keyboard setup**, then switch to it from any text field using the system keyboard picker (globe key).

---

## Permissions

Muesli for Android asks only for permissions needed by the selected workflow.

| Permission | Why |
|---|---|
| **Microphone** | Record speech for voice notes, meetings, and keyboard dictation |
| **Notifications** | Show ongoing meeting-recording and quick-capture notifications (Android 13+) |
| **Foreground service (microphone)** | Support meeting recording and quick capture outside the main app |
| **Internet** | Download models from HuggingFace / its mirror or GitHub; sign in and request AI summaries when configured |
| **Display over other apps** | Show the optional floating quick-capture bubble |
| **Input method binding** | Offer the Muesli Dictation Keyboard system-wide |

Unlike iOS (where the keyboard extension hands off to the app), the Android keyboard records and transcribes directly through its `InputMethodService`. The activity, keyboard, and services use the application's default process and share the cached recognizer through `SherpaRecognizerHolder`.

---

## Architecture

```text
app/src/main/java/com/phequals7/muesli/
  MainActivity + Navigation      Compose entry, onboarding gate, dashboard host

  engine/                        TranscriptionEngine interface + sole implementation:
                                   SherpaOnnxEngine (Parakeet, AudioRecord capture,
                                   RMS metering), shared recognizer + prewarm

  audio/                         AudioInputRouteManager — mic input preferences

  meetings/                      MeetingRecordingController (shared state bridge),
                                   MeetingRecorderService (foreground capture,
                                   Silero VAD → Parakeet decode, WAV writer,
                                   notification actions), SpeakerDiarizer,
                                   SpeakerLabeler, MeetingTemplates

  ime/                           MuesliInputMethodService (dictation-only IME),
                                   KeyboardController (state machine, text insertion,
                                   key repeat, IME picker), iOS-parity keyboard UI

  bubble/ + widget/              Floating quick capture, Quick Settings tile,
                                   home-screen widget

  model/                         SpeechModels catalog, ModelManager — resumable
                                   downloads + mirror fallback, ModelArchive —
                                   tar.bz2 extraction for Parakeet 110M

  summaries/                     ChatGptAuthManager (OAuth PKCE + token refresh),
                                   MeetingSummaryClient (ChatGPT SSE / OpenRouter)

  data/                          Room v4 (dictations, sessions, transcripts,
                                   custom words), migrations, SharedStore,
                                   SharedPreferences

  ui/                            Guided onboarding, launch warmup,
                                   dashboard (voice notes / meetings / settings),
                                   meeting audio player,
                                   iOS-parity components: MuesliInlineWaveform,
                                   glass-pill navigation, surface cards

  theme/                         MuesliColors (ported from muesli-ios MuesliTheme.swift),
                                   typography, accent themes, AppearanceController

app/src/main/java/com/k2fsa/sherpa/onnx/   Vendored sherpa-onnx Kotlin bindings (v1.13.4)
app/src/main/jniLibs/arm64-v8a/            Prebuilt sherpa-onnx + ONNX Runtime native libs
```

The meeting pipeline is:

1. `AudioRecord` captures 16 kHz mono float audio and writes a 16-bit PCM WAV. When retention is off, the WAV uses a temporary filename and is deleted during finalization.
2. Bundled Silero VAD segments speech for sequential Parakeet decoding, capped at 30 seconds per segment. Fixed 30-second chunks are the fallback if VAD is unavailable; trailing audio is flushed on stop.
3. Chunk transcripts receive filler-word filtering and custom-dictionary replacements, then appear in the live meeting transcript.
4. On stop, optional on-device diarization runs against the WAV using bundled pyannote segmentation and TitaNet embedding models. Text chunks receive inline speaker labels and timestamps.
5. The final transcript and session state are persisted in Room. Audio is kept only when retention is enabled; a temporary WAV used solely for diarization is deleted.
6. If AI summaries are enabled and the selected provider has credentials, transcript text is submitted for structured notes and title generation.

---

## Tech Stack

| Component | Technology |
|---|---|
| App | Kotlin, Jetpack Compose (Material 3), Room |
| Keyboard | InputMethodService with Compose-hosted UI |
| Local ASR | [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) 1.13.4, Parakeet v3 600M / Parakeet 110M (int8) via ONNX Runtime |
| Meeting capture | Foreground service, AudioRecord, WAV retention, Silero VAD |
| Speaker diarization | Bundled pyannote segmentation + NeMo TitaNet-S |
| AI summaries | ChatGPT OAuth + SSE, OpenRouter BYOK, OkHttp |
| Storage | Room v4 (SQLite) + SharedPreferences |
| Model delivery | Resumable HuggingFace / mirror downloads; GitHub tar.bz2 archive for 110M |
| Build | Gradle 9 (Kotlin DSL), AGP 9, KSP |
| Tests | JUnit 4, MockWebServer download tests, Room migration instrumentation test |
| CI | GitHub Actions: unit tests, release assembly / lint vital, API 34 emulator migration test |

---

## Repository Status

Muesli for Android is alpha software. Three prereleases are available: v0.1.0-alpha, v0.2.0-alpha, and v0.3.0-alpha. Development and device testing have used a Samsung Galaxy S20 FE and a low-end POCO C71. The 110M model is recommended for low-end hardware; the larger 600M model can be too demanding on those devices.

The repository includes 29 unit tests covering text processing, model configuration, speaker labeling, archive extraction, download/resume behavior, and voice-note stats, plus a v1 → v4 database migration instrumentation test. CI builds a release APK and runs both unit and instrumented tests.

Remaining gaps and follow-up work:

- **Meeting crash recovery / checkpointing** — interrupted recordings do not yet have a recovery flow.
- **Long-meeting validation on low-end phones** — diarization memory use and processing time need further device testing.
- **Speaker controls** — labels are stored inline in transcript text; dedicated speaker views, renaming, and separate structured segments are not implemented.
- **Voice-note audio retention, playback, and export** — voice notes currently retain text only; meeting audio playback is implemented.
- **ChatGPT account validation** — the OAuth / summary integration is implemented, but real-account end-to-end validation remains outstanding.
- **Cross-device sync** — not implemented; Mac-as-LAN-hub pairing has been discussed as a possible approach.
- **Production distribution** — upload-key signing, AAB / Play Store setup, and a release minification pass remain. Current release builds use the debug key and disable minification.
- **Database hardening** — explicit migrations exist, but the scaffolding-era destructive-migration fallback remains enabled and should be revisited before stable releases.

---

## Development

Build and install to a connected device:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Run the unit test suite:

```bash
./gradlew testDebugUnitTest
```

Run the Room migration test on a connected device or emulator:

```bash
./gradlew connectedDebugAndroidTest
```

The CI migration job uses an API 34 x86_64 emulator and does not exercise the arm64-only transcription runtime. Test speech capture, model loading, and diarization on arm64 hardware.

Build the current alpha release APK:

```bash
./gradlew assembleRelease
```

GitHub Actions runs unit tests, release assembly (including lint vital), and the migration instrumentation test on pushes to `main` and pull requests. It also uploads test reports and the release APK as workflow artifacts.

Notes for contributors:

- The sherpa-onnx Kotlin bindings are vendored under `com.k2fsa.sherpa.onnx` (tag `v1.13.4`); the matching prebuilt arm64 native libraries live in `app/src/main/jniLibs`. To support emulators/other ABIs, fetch the remaining ABIs from the sherpa-onnx release archive.
- Room migrations are explicit (`AppDatabase.kt`); avoid relying on `fallbackToDestructiveMigration` for user-facing schema changes.
- UI colors/type/spacing must come from `theme/` tokens — keep hex values in sync with `muesli-ios` `Shared/MuesliTheme.swift`.

---

## Privacy

Muesli's default design is local-first:

- Speech capture, transcription, and speaker diarization run on device. The system speech recognizer and mock engine have been removed; sherpa-onnx is the only transcription engine.
- Model downloads use HuggingFace (with a DNS-failure mirror fallback) or GitHub. Core recording and transcription workflows work offline once a model is downloaded.
- AI summaries are disabled by default. When enabled and configured, transcript text and any included manual notes are sent to the selected ChatGPT or OpenRouter provider. ChatGPT sign-in also uses network requests for authentication and token refresh.
- Saved text and retained meeting WAVs are stored in app-private storage. Voice-note audio is not retained. Android system backup is currently enabled in the application manifest.
- No Muesli account is required. There is no Muesli-hosted backend, analytics, or crash reporting in the current build. Optional summary-provider credentials are stored in app-private SharedPreferences.
- Keyboard dictation inserts text into the active field; its transcription does not require uploading audio. Opening the keyboard or enabling keep-mic-ready does not automatically record.

---

## Related

- [Muesli for macOS](https://github.com/Muesli-HQ/muesli)
- [Muesli for iOS](https://github.com/Muesli-HQ/muesli-ios)
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx)
- [ONNX Runtime](https://github.com/microsoft/onnxruntime)
- [NVIDIA NeMo / Parakeet](https://github.com/NVIDIA/NeMo)

---

## Contributing

Issues and pull requests are welcome. For larger changes, please open an issue first so implementation details can be discussed against the Android architecture and the iOS feature-parity roadmap.

Before opening a PR:

```bash
./gradlew assembleDebug testDebugUnitTest
```

---

## License

Muesli for Android is released under the [MIT License](LICENSE). The vendored sherpa-onnx bindings retain their original Apache-2.0 license; ONNX Runtime is MIT; Parakeet models are provided by NVIDIA NeMo — see their respective licenses.
