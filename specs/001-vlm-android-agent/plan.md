# Implementation Plan: Voice Support for Task Creation

**Branch**: `001-vlm-android-agent` | **Date**: 2025-12-20 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-vlm-android-agent/spec.md` + User request: "Add the voice support to add create task"

**Note**: This plan extends the existing VLM Android Agent with voice input capabilities for task creation.

## Summary

Add speech-to-text voice input as an alternative method for creating tasks in the Android agent application. Users will be able to tap a microphone button, speak their task description naturally, and have it transcribed and submitted to the agent - complementing the existing text input field. This leverages Android's built-in `SpeechRecognizer` API for on-device/cloud speech recognition with multi-language support (Chinese and English).

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android app)
**Primary Dependencies**: Android SpeechRecognizer API (built-in), Jetpack Compose, existing OkHttp/Retrofit for cloud alternatives
**Storage**: Room Database (existing - no new tables needed for voice)
**Testing**: Android Instrumented Tests, JUnit with Mockk
**Target Platform**: Android 10+ (API 29+)
**Project Type**: Mobile Android application
**Performance Goals**: Voice transcription latency < 2 seconds for typical task descriptions (10-30 words)
**Constraints**: On-device recognition preferred (privacy), cloud fallback optional, support both Chinese and English speech
**Scale/Scope**: Single input method addition, affects TaskScreen + TaskViewModel primarily

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Code Quality & Architecture** | PASS | Voice input is a new input layer; agent logic (PhoneAgent), ADB operations, model client remain unchanged. Follows module separation. |
| **Module Separation** | PASS | Voice handling isolated to UI layer (TaskScreen + TaskViewModel). Does not modify core agent or ADB operations. |
| **OpenAI-Compatible API** | N/A | Voice feature is Android-local; does not affect model API format. |
| **Configuration-Driven** | PASS | Will add configurable settings for speech recognition (language, continuous listening, cloud fallback). |
| **Type Hints** | N/A | Kotlin uses static typing with explicit type annotations. |
| **Error Handling** | PASS | Will implement graceful degradation for permission denial, no microphone, recognition failures. |
| **II. Documentation Standards** | PASS | Will update README files with voice input instructions in both Chinese and English. |
| **Bilingual Requirement** | PASS | Voice feature supports both languages; documentation will be bilingual. |
| **III. Simplicity & Maintainability** | PASS | Using Android's built-in SpeechRecognizer (no external dependencies). Minimal code addition. |
| **YAGNI** | PASS | Implementing only core voice input - no advanced features (voice commands, wake words) unless requested. |
| **Single Responsibility** | PASS | New VoiceInputManager class handles speech recognition; TaskViewModel coordinates. |
| **Minimal Dependencies** | PASS | Using Android platform APIs only - no new external dependencies. |

## Project Structure

### Documentation (this feature)

```text
specs/001-vlm-android-agent/
├── plan.md              # This file
├── research.md          # Phase 0 output - voice recognition research
├── data-model.md        # Phase 1 output - voice-related data structures
├── quickstart.md        # Phase 1 output - voice input setup guide
├── contracts/           # Phase 1 output - API contracts
│   └── voice-input-states.md
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
android/app/src/main/
├── AndroidManifest.xml                          # MODIFY: Add RECORD_AUDIO permission
├── java/com/openautoglm/agent/
│   ├── ui/
│   │   ├── screens/
│   │   │   └── TaskScreen.kt                    # MODIFY: Add voice input UI
│   │   ├── viewmodels/
│   │   │   └── TaskViewModel.kt                 # MODIFY: Add voice state management
│   │   └── components/
│   │       └── VoiceInputButton.kt              # NEW: Microphone button component
│   ├── voice/                                    # NEW: Voice input module
│   │   ├── VoiceInputManager.kt                 # NEW: SpeechRecognizer wrapper
│   │   ├── VoiceInputState.kt                   # NEW: Voice UI state sealed class
│   │   └── PermissionHandler.kt                 # NEW: RECORD_AUDIO permission handling
│   └── data/entities/
│       └── Enums.kt                              # MODIFY: Add InputMethod enum if tracking
└── res/
    ├── drawable/
    │   ├── ic_mic.xml                           # NEW: Microphone icon
    │   └── ic_mic_off.xml                       # NEW: Microphone off/disabled icon
    └── values/
        └── strings.xml                          # MODIFY: Add voice-related strings

android/app/src/test/
└── java/com/openautoglm/agent/voice/
    └── VoiceInputManagerTest.kt                 # NEW: Unit tests for voice logic

android/app/src/androidTest/
└── java/com/openautoglm/agent/ui/
    └── VoiceInputUITest.kt                      # NEW: UI tests for voice input
```

**Structure Decision**: Mobile application structure. New `voice/` package isolates speech recognition logic from UI. Follows existing pattern where specialized functionality has its own package (e.g., `accessibility/`, `inference/`, `agent/`).

## Complexity Tracking

No constitution violations requiring justification. Using platform APIs keeps complexity minimal.

---

## Phase 0: Research Summary

### Research Task 1: Android Speech Recognition Best Practices

**Decision**: Use Android's built-in `SpeechRecognizer` API

**Rationale**:
- Built into Android platform - no external dependencies (Constitution III: Minimal Dependencies)
- Works offline on Android 11+ with downloaded language packs
- Supports both Chinese and English (Constitution II: Bilingual)
- Integrates with Google's speech recognition services for accuracy
- Familiar UI patterns for Android users

**Alternatives Considered**:
| Alternative | Rejected Because |
|-------------|------------------|
| Google Cloud Speech-to-Text | Requires API key management, network dependency, cost per request |
| OpenAI Whisper (local) | Large model size (1GB+), requires ONNX runtime, complex integration |
| Vosk (on-device) | Additional 40MB+ dependency, less accurate than Google's recognizer |
| Azure Speech Services | Vendor lock-in, network dependency, requires Azure account |

### Research Task 2: Permission Handling Best Practices

**Decision**: Runtime permission request with graceful fallback

**Rationale**:
- `RECORD_AUDIO` is a dangerous permission requiring runtime request (Android 6+)
- Show rationale dialog explaining why voice input needs microphone
- Gracefully hide voice button if permission permanently denied
- Use `ActivityResultContracts.RequestPermission` (modern Jetpack approach)

**Implementation Pattern**:
```kotlin
val requestPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    if (isGranted) startListening() else showPermissionDeniedMessage()
}
```

### Research Task 3: Voice Input UI Patterns

**Decision**: Floating action button (FAB) style microphone icon within input field

**Rationale**:
- Matches Google Assistant and Google Search voice input patterns
- Users expect microphone button near text input
- Visual feedback (pulsing animation, waveform) during listening
- Press-and-hold or tap-to-toggle listening modes

**UI State Flow**:
```
Idle → (tap mic) → Listening → (speech detected) → Processing → (result) → Done → Idle
                           ↓                              ↓
                      (error/timeout)               (recognition failed)
                           ↓                              ↓
                        Error ─────────────────────────► Idle
```

### Research Task 4: Multi-language Speech Recognition

**Decision**: Auto-detect based on user's AgentConfig language preference

**Rationale**:
- Existing `AgentConfig` has `language: String` field ("en" or "zh")
- Map to Android locale: "en" → `Locale.US`, "zh" → `Locale.CHINESE`
- SpeechRecognizer supports language switching per request
- Fallback to device default locale if unsupported

**Configuration Mapping**:
```kotlin
val speechLocale = when (agentConfig.language) {
    "zh" -> Locale.CHINESE
    "en" -> Locale.US
    else -> Locale.getDefault()
}
```

---

## Phase 1: Design Artifacts

### Data Model

See [data-model.md](data-model.md) for complete entity definitions.

**New Entities**:

1. **VoiceInputState** (sealed class)
   - `Idle`, `Listening`, `Processing`, `Error(message)`, `Result(text)`

2. **VoiceInputConfig** (data class)
   - `enabled: Boolean`
   - `language: Locale`
   - `partialResults: Boolean`
   - `maxDuration: Long`

### API Contracts

See [contracts/](contracts/) directory.

**Internal Contracts**:

1. **VoiceInputManager interface**:
   ```kotlin
   interface VoiceInputManager {
       val state: StateFlow<VoiceInputState>
       fun startListening(locale: Locale)
       fun stopListening()
       fun cancelListening()
       val isAvailable: Boolean
   }
   ```

2. **ViewModel Extensions**:
   ```kotlin
   // TaskViewModel additions
   val voiceInputState: StateFlow<VoiceInputState>
   fun startVoiceInput()
   fun stopVoiceInput()
   fun cancelVoiceInput()
   ```

### Quickstart Guide

See [quickstart.md](quickstart.md) for user-facing setup instructions.

**Key Steps**:
1. Grant microphone permission when prompted
2. Tap microphone icon in task input field
3. Speak task description clearly
4. Review transcribed text and edit if needed
5. Tap "Start Task" to begin execution

---

## Post-Design Constitution Re-Check

| Principle | Status | Verification |
|-----------|--------|--------------|
| Module Separation | PASS | New `voice/` package is isolated; only TaskViewModel coordinates |
| Configuration-Driven | PASS | Language from AgentConfig; VoiceInputConfig for additional settings |
| Error Handling | PASS | VoiceInputState.Error provides actionable messages |
| Bilingual Documentation | PENDING | Will create in Phase 2 |
| Minimal Dependencies | PASS | Zero new external dependencies |
| Single Responsibility | PASS | VoiceInputManager handles recognition; ViewModel coordinates state |

---

## Implementation Scope Summary

**Files to Create** (5):
- `android/app/src/main/java/com/openautoglm/agent/voice/VoiceInputManager.kt`
- `android/app/src/main/java/com/openautoglm/agent/voice/VoiceInputState.kt`
- `android/app/src/main/java/com/openautoglm/agent/ui/components/VoiceInputButton.kt`
- `android/app/src/main/res/drawable/ic_mic.xml`
- `android/app/src/main/res/drawable/ic_mic_off.xml`

**Files to Modify** (4):
- `android/app/src/main/AndroidManifest.xml` - Add RECORD_AUDIO permission
- `android/app/src/main/java/com/openautoglm/agent/ui/screens/TaskScreen.kt` - Integrate voice UI
- `android/app/src/main/java/com/openautoglm/agent/ui/viewmodels/TaskViewModel.kt` - Voice state coordination
- `android/app/src/main/res/values/strings.xml` - Add voice-related strings

**No Changes Required**:
- PhoneAgent (agent execution logic unchanged)
- ActionExecutor (UI automation unchanged)
- CloudInference/OnDeviceInference (model inference unchanged)
- Database schema (no new tables)
- Accessibility service (unrelated to voice input)

---

## Next Steps

Run `/speckit.tasks` to generate the detailed task breakdown for implementation.
