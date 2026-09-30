# 🎙️ VoxStream

[![Platform](https://img.shields.io/badge/Platform-Android%2014%2B%20(API%2034%2B)-3DDC84?style=flat&logo=android&logoColor=white)](https://android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?style=flat&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Jetpack%20Compose-2024.09.00-4285F4?style=flat&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material 3](https://img.shields.io/badge/Material%203-Expressive-FF6D00?style=flat)](https://m3.material.io)
[![Gemini Live API](https://img.shields.io/badge/Gemini%20Live-WebSocket%20STT-8E24AA?style=flat&logo=google&logoColor=white)](https://ai.google.dev)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**VoxStream** is a high-performance, real-time streaming voice typing assistant for Android. Powered by Google's **Gemini Live Bidirectional WebSocket API** and designed following **Material 3 Expressive** principles, VoxStream floats above your favorite apps (WhatsApp, Gmail, Google Keep, Notion, TikTok, ChatGPT) to deliver instantaneous speech-to-text dictation, AI-driven text polishing, and context-aware formatting.

---

## ✨ Features at a Glance

- **⚡ Real-Time Streaming Transcription**: Millisecond-latency live transcription via Gemini Live WebSocket bidirectional streaming (`models/gemini-3.5-transcribe-live`).
- **🛟 Floating Notched Lifebuoy Overlay**: Translucent dark frosted glass panel with 3-bounce edge-snapping collision physics, inactivity auto-shrink (56dp $\to$ 45dp), and dynamic Android 12+ Monet Aurora Glow bloom.
- **🧠 AI-Aware App Classifier**: Automatically senses the active foreground app and tailors transcription formatting:
  - **Social / Messaging** (*WhatsApp, Telegram, Messages, TikTok, X*): Fluid, conversational phrasing with natural punctuation.
  - **Work / Productivity** (*Gmail, Outlook, Docs, Notion, Slack*): Polished business grammar, proper capitalization, and automatic markdown bullet lists.
  - **AI Chat** (*ChatGPT, Claude, Grok, Gemini, DeepSeek*): Crisp instruction syntax with preserved coding and technical keywords.
- **✨ Multi-Model Polish Engine**: Instant one-tap transcript refining with automatic multi-model fallback (`gemini-3.5-flash-lite` $\to$ `gemini-3.1-flash-lite` $\to$ `gemini-2.5-flash`).
- **🛡️ Zero-Clobber Safe Clipboard**: Race-condition-free paste verification system for complex note-taking editors (Google Keep, Notion, Obsidian, OneNote) that never overwrites user clipboard data.
- **⌨️ Keyboard-Synced Visibility**: Floating lifebuoy bubble visibility strictly tracks keyboard/IME presence via an Accessibility Service overlay.

---

## 🏛️ Architecture & Modular Structure

VoxStream is built with clean MVVM architecture, unidirectional data flow (UDF), and strict separation of concerns.

```
com.example/
├── audio/                      # Hardware AudioRecord streaming & amplitude sampling
│   └── AudioRecorder.kt
├── data/                       # Local Room DB, repositories, and connection states
│   ├── AppLogRepository.kt
│   ├── ConnectionState.kt
│   ├── CustomVocabularyRepository.kt
│   └── HistoryRepository.kt
├── service/                    # Background Services & Accessibility Layer
│   ├── AppClassifier.kt        # AI-aware app categorization & prompt tailoring
│   ├── AppContextResolver.kt   # Hybrid high-traffic app whitelisting & tokenization
│   ├── FloatingBubbleManager.kt# Global session state & keyboard visibility
│   ├── FloatingBubbleService.kt# Slim foreground Service orchestrator
│   ├── VoxStreamAccessibilityService.kt # Text injection & verified paste pipeline
│   └── floating/               # Modular Service Helper Subsystem
│       ├── FloatingDictationSessionManager.kt # WebSocket & audio queue management
│       ├── FloatingHapticManager.kt           # Granular haptic feedback engine
│       ├── FloatingNotificationManager.kt     # Foreground Service notification channels
│       ├── FloatingOverlayLifecycleOwner.kt   # Standalone Compose LifecycleOwner
│       ├── FloatingOverlayWindowManager.kt    # WindowManager attachment & snap physics
│       └── FloatingPolishClient.kt            # Multi-model Gemini Polish REST client
├── ui/                         # Jetpack Compose UI
│   ├── VoiceTypingScreen.kt    # In-app dictation & diagnostics dashboard
│   ├── components/             # Reusable UI & settings bottom sheets
│   │   ├── FloatingDictationBubbleOverlay.kt  # Root floating overlay composable
│   │   ├── GlowStylesBottomSheet.kt           # 21 configurable visualizer glow styles
│   │   └── overlay/            # Modular Overlay UI Subsystem
│   │       ├── AuroraColorPalette.kt          # Monet dynamic color token resolver
│   │       ├── FloatingActionRow.kt           # Cancel | Polish | Complete button row
│   │       ├── FloatingCollapsedBubble.kt     # Idle-shrinking floating lifebuoy bubble
│   │       ├── FloatingDockedLifebuoy.kt      # Unclipped draggable lifebuoy ring
│   │       ├── FloatingGlowEffects.kt         # Multi-layer Aurora bloom & visualizers
│   │       ├── FloatingTranscriptBox.kt       # High-contrast scrolling text area
│   │       └── NotchedOverlayShape.kt         # Custom convex fillet notch geometry
│   ├── screens/                # App navigation destinations
│   │   ├── DictionaryScreen.kt # Custom vocabulary editor
│   │   └── HomeScreen.kt       # History, stats & settings hub
│   └── theme/                  # Material 3 typography, color schemes & shapes
│       ├── Color.kt
│       ├── Gradients.kt
│       ├── Shape.kt
│       ├── Theme.kt
│       └── Type.kt
└── websocket/                  # OkHttp Bidirectional WebSocket Client
    └── GeminiLiveWebSocketClient.kt
```

---

## 🚀 Getting Started & Build Instructions

### Prerequisites
- **Android Studio**: Ladybug (2024.2.1+) or newer
- **JDK**: Java 17 or Java 21
- **Android SDK**: `minSdk = 34`, `targetSdk = 35`, `compileSdk = 36`
- **Gemini API Key**: Obtain a key from [Google AI Studio](https://aistudio.google.com/)

### 1. Clone the Repository
```bash
git clone https://github.com/princeniithompson/VoxStream.git
cd VoxStream
```

### 2. Configure Your API Key
In Google AI Studio Build, configure your key in the **Secrets panel** under `GEMINI_API_KEY`, or add it to your root `.env` file:
```properties
GEMINI_API_KEY=AIzaSyYourGeminiApiKeyHere...
```

### 3. Build & Run
```bash
# Compile and run unit tests
./gradlew testDebugUnitTest

# Assemble Debug APK
./gradlew assembleDebug

# Assemble Release APK (Minified with R8 shrinker enabled)
./gradlew assembleRelease
```

---

## 🔒 Permissions & Security Model

VoxStream follows Android's least-privilege security principles:

| Permission | Purpose |
|---|---|
| `android.permission.RECORD_AUDIO` | Required to capture microphone audio during an active dictation session. |
| `android.permission.SYSTEM_ALERT_WINDOW` | Enables rendering the floating lifebuoy bubble over other applications. |
| `android.permission.BIND_ACCESSIBILITY_SERVICE` | Detects keyboard/IME presence and injects transcribed text into active editable input fields. |
| `android.permission.FOREGROUND_SERVICE` | Keeps the dictation stream alive when switching between applications. |
| `android.permission.FOREGROUND_SERVICE_MICROPHONE` | Android 14+ runtime requirement for background microphone streaming. |
| `android.permission.VIBRATE` | Provides haptic feedback during transcription start, sentence commits, and bubble snapping. |

---

## 🌐 Google Play Store Privacy & Data Safety Disclosures

When publishing VoxStream to Google Play, disclose the following data practices in the Google Play Console Data Safety section:

### 1. Audio Data Collection & Streaming
- **Data Type**: Voice or sound recordings (`Audio`).
- **Processing**: Ephemeral streaming only. Raw audio captured from the microphone is streamed directly over encrypted TLS 1.3/WSS to Google's Gemini Live API servers.
- **Storage**: **Zero Audio Retention**. Raw audio is never saved to device flash storage, nor is it stored permanently on external servers. Audio buffers are discarded from RAM immediately after chunk transmission.
- **User Control**: Audio recording is initiated **only** when the user explicitly taps the floating lifebuoy bubble or the in-app record button, and halts immediately when the user taps Complete, Cancel, or stops speaking.

### 2. Accessibility Service Usage (`BIND_ACCESSIBILITY_SERVICE`)
VoxStream utilizes the Android Accessibility API strictly for core assistive voice typing functionality:
- **Text Focus Detection**: Inspects the window hierarchy to detect when the user focuses an editable text field and opens the software keyboard.
- **Direct Text Injection**: Dispatches `ACTION_SET_TEXT` or `ACTION_PASTE` to insert dictated or polished text directly into the focused field without requiring manual clipboard copy-pasting.
- **No Personal Data Harvesting**: VoxStream does **not** log, harvest, or transmit user keystrokes, passwords, screen content, or sensitive personal data.

### 3. Local History & Cloud Polish
- **Local History**: Dictated transcripts are saved exclusively on the user's local device using an encrypted Room database.
- **Polish Feature**: When the user explicitly taps the **Polish ✨** button, the raw transcript string is sent to the Gemini REST API (`generateContent`) to remove verbal filler words and structure punctuation.

---

## 📄 License

```
Copyright 2026 Prince Nii Thompson & VoxStream Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
