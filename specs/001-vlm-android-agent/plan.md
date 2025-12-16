# Implementation Plan: VLM-Powered Android Agent

**Branch**: `001-vlm-android-agent` | **Date**: 2025-12-13 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-vlm-android-agent/spec.md`

## Summary

Build a native Android agent application powered by vision-language models (VLMs) that automates complex, multi-step tasks across apps. The system uses a hybrid inference architecture (on-device for privacy/speed, cloud for complex tasks), leverages AccessibilityService for universal app control, and ports proven agent logic from the existing Python Open-AutoGLM codebase to Kotlin.

## Technical Context

**Language/Version**: Kotlin 1.9+ (Android app), Python 3.11+ (existing agent logic reference)
**Primary Dependencies**:
- Android: Kotlin Coroutines, Jetpack Compose, MLC-LLM, llama.cpp (Android bindings)
- Cloud APIs: OpenAI-compatible clients for AutoGLM-Phone-9B, Qwen2.5-VL-72B
- Python (existing): openai, Pillow

**Storage**: Android SharedPreferences/DataStore for user preferences, SQLite/Room for task history and app mappings
**Testing**: JUnit 5, Espresso (Android), pytest (Python agent logic)
**Target Platform**: Android 10+ (API 29+)
**Project Type**: Mobile (Android native app) + existing Python agent logic to port

**Performance Goals**:
- On-device inference: <3s per action decision for simple tasks
- Cloud inference: <5s per action decision for complex tasks
- Task completion: 85% success rate without user intervention
- UI recognition: 95% accuracy for standard UI elements

**Constraints**:
- On-device model memory: <2GB RAM (Qwen2.5-VL-3B AWQ) or <1GB RAM (MobileVLM-V2-1.7B)
- Battery-conscious: Prefer on-device for frequent/privacy-sensitive tasks
- Accessibility permissions required for universal app control
- Some apps may block AccessibilityService detection

**Scale/Scope**:
- 50+ pre-configured popular apps in knowledge base
- Support for 8 user story categories (P1-P3)
- Extensible app mapping system for new apps

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Module Separation** | PASS | Android app follows separation: UI (Compose), Agent logic, Accessibility control, Model inference, App knowledge base |
| **I. OpenAI-Compatible API** | PASS | Cloud model clients maintain OpenAI API compatibility; existing `ModelClient` pattern reused |
| **I. Configuration-Driven** | PASS | App mappings in config files; model routing rules configurable |
| **I. Type Hints** | PASS (Android) | Kotlin is strongly typed; Python code maintains type hints |
| **I. Error Handling** | PASS | Bilingual error messages; graceful degradation from cloud to on-device |
| **II. Bilingual Docs** | PASS | README updates in both Chinese and English |
| **II. API Documentation** | PASS | Public APIs documented with KDoc/docstrings |
| **III. YAGNI** | PASS | MVP focuses on core automation; no speculative features |
| **III. Single Responsibility** | PASS | Each component has clear single purpose |
| **III. Minimal Dependencies** | PASS | Using established Android/ML libraries only |
| **III. Readable Over Clever** | PASS | Straightforward Kotlin patterns |

**Pre-Design Gate**: PASS - No violations

## Project Structure

### Documentation (this feature)

```text
specs/001-vlm-android-agent/
├── plan.md              # This file
├── research.md          # Phase 0: Technology decisions
├── data-model.md        # Phase 1: Entity definitions
├── quickstart.md        # Phase 1: Setup guide
├── contracts/           # Phase 1: API contracts
│   ├── agent-service.yaml      # Internal agent API
│   └── model-client.yaml       # Model inference API
└── tasks.md             # Phase 2 output (via /speckit.tasks)
```

### Source Code (repository root)

```text
# Existing Python agent (reference for porting)
phone_agent/
├── __init__.py
├── agent.py             # Main PhoneAgent class (port to Kotlin)
├── actions/
│   ├── __init__.py
│   └── handler.py       # Action handlers (port to Kotlin)
├── adb/
│   ├── __init__.py
│   ├── connection.py
│   ├── device.py        # Device control (replace with AccessibilityService)
│   ├── input.py
│   └── screenshot.py    # Screenshot capture (replace with MediaProjection)
├── config/
│   ├── __init__.py
│   ├── apps.py          # App package mappings (port to Kotlin)
│   ├── prompts.py       # System prompts (port to Kotlin)
│   ├── prompts_en.py
│   ├── prompts_zh.py
│   └── i18n.py
└── model/
    ├── __init__.py
    └── client.py        # Model client (port to Kotlin)

# New Android app
android/
├── app/
│   ├── src/main/
│   │   ├── java/com/openautoglm/agent/
│   │   │   ├── AutoGLMApplication.kt
│   │   │   ├── ui/                      # Jetpack Compose UI
│   │   │   │   ├── MainActivity.kt
│   │   │   │   ├── screens/
│   │   │   │   │   ├── TaskScreen.kt
│   │   │   │   │   ├── HistoryScreen.kt
│   │   │   │   │   └── SettingsScreen.kt
│   │   │   │   └── components/
│   │   │   ├── agent/                   # Core agent logic
│   │   │   │   ├── PhoneAgent.kt
│   │   │   │   ├── TaskPlanner.kt
│   │   │   │   └── AgentConfig.kt
│   │   │   ├── actions/                 # Action handlers
│   │   │   │   ├── ActionHandler.kt
│   │   │   │   └── ActionResult.kt
│   │   │   ├── accessibility/           # AccessibilityService
│   │   │   │   ├── AutoGLMAccessibilityService.kt
│   │   │   │   └── UIElementParser.kt
│   │   │   ├── inference/               # Model inference
│   │   │   │   ├── InferenceRouter.kt
│   │   │   │   ├── OnDeviceInference.kt
│   │   │   │   └── CloudInference.kt
│   │   │   ├── model/                   # Model clients
│   │   │   │   ├── ModelClient.kt
│   │   │   │   ├── ModelConfig.kt
│   │   │   │   └── ModelResponse.kt
│   │   │   ├── knowledge/               # App knowledge base
│   │   │   │   ├── AppRegistry.kt
│   │   │   │   └── AppMappings.kt
│   │   │   └── data/                    # Data layer
│   │   │       ├── TaskRepository.kt
│   │   │       ├── entities/
│   │   │       └── dao/
│   │   ├── res/
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── gradle/
└── build.gradle.kts

# Tests
tests/                   # Python tests (existing)
android/app/src/test/    # Kotlin unit tests
android/app/src/androidTest/  # Android instrumentation tests
```

**Structure Decision**: Mobile (Android) + existing Python reference. The Android app is a new module at repository root (`android/`). The existing `phone_agent/` Python code serves as the reference implementation for porting agent logic. This maintains backward compatibility with the Python-based ADB workflow while adding native Android capabilities.

## Complexity Tracking

| Deviation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Hybrid on-device/cloud inference | Privacy + battery optimization | Cloud-only would drain battery and expose private data; on-device-only insufficient for complex multi-step tasks |
| Two inference backends (MLC-LLM + llama.cpp) | GPU vs CPU fallback | Single backend would fail on devices without GPU support |
| Port from Python to Kotlin | Native Android performance + accessibility | Python on Android is impractical; native required for AccessibilityService |
