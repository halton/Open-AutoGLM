# Quickstart Guide: VLM-Powered Android Agent

**Feature**: 001-vlm-android-agent
**Date**: 2025-12-13

## Prerequisites

### Development Environment

- **Android Studio**: Arctic Fox (2020.3.1) or later
- **JDK**: 17 or later
- **Kotlin**: 1.9+
- **Android SDK**: API 29+ (Android 10+)
- **NDK**: Required for on-device inference (MLC-LLM/llama.cpp)

### Test Device

- Android 10+ device or emulator
- **Recommended**: Physical device with 6GB+ RAM for on-device inference
- **Minimum**: 4GB RAM (cloud-only mode)

### Cloud API Keys (Optional)

For cloud inference fallback:
- BigModel API key (for AutoGLM-Phone-9B)
- Alibaba Cloud DashScope key (for Qwen2.5-VL-72B)

---

## Project Setup

### 1. Clone Repository

```bash
git clone https://github.com/zai-org/Open-AutoGLM.git
cd Open-AutoGLM
git checkout 001-vlm-android-agent
```

### 2. Open in Android Studio

1. Open Android Studio
2. Select "Open" and navigate to `Open-AutoGLM/android`
3. Wait for Gradle sync to complete

### 3. Configure API Keys (Cloud Inference)

Create `android/app/local.properties` if not exists:

```properties
# BigModel API (AutoGLM-Phone-9B)
BIGMODEL_API_KEY=your_api_key_here
BIGMODEL_BASE_URL=https://open.bigmodel.cn/api/paas/v4

# Alibaba Cloud DashScope (Qwen2.5-VL-72B)
DASHSCOPE_API_KEY=your_api_key_here
DASHSCOPE_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

**Note**: These are optional. The app works without cloud keys (on-device only mode).

### 4. Download On-Device Models (First Run)

The app will prompt to download models on first launch, or you can pre-download:

```bash
# Qwen2.5-VL-3B (AWQ quantized, ~2GB)
# Download location will be shown in app settings

# MobileVLM-V2-1.7B (lighter alternative, ~1GB)
# Download location will be shown in app settings
```

---

## Build and Run

### Debug Build

```bash
cd android
./gradlew assembleDebug
```

Install on device:

```bash
./gradlew installDebug
```

### Release Build

```bash
./gradlew assembleRelease
```

---

## First Run Configuration

### 1. Grant Permissions

When launching the app for the first time:

1. **Accessibility Service**: Required for UI automation
   - Settings → Accessibility → AutoGLM → Enable
   - Grant "Full control of device"

2. **Screen Capture**: Required for screenshots
   - Grant when prompted on first task

3. **Overlay Permission**: Optional, for floating UI
   - Settings → Apps → AutoGLM → Display over other apps

### 2. Configure Inference Mode

In app Settings:

| Mode | Description | When to Use |
|------|-------------|-------------|
| **Auto** (default) | Routes based on task complexity | Recommended |
| **On-Device Only** | Never uses cloud | Privacy-sensitive / offline |
| **Cloud Only** | Always uses cloud | Testing / accuracy |

### 3. Select On-Device Model

If using on-device inference:

- **Qwen2.5-VL-3B**: Best accuracy, requires 3GB+ free RAM
- **MobileVLM-V2-1.7B**: Faster, lower RAM, slightly less accurate

---

## Running Your First Task

### Example: Price Comparison

1. Open AutoGLM app
2. Type or speak: "Find the best price for AirPods Pro across Taobao, JD, and Amazon"
3. Watch the agent navigate apps and collect prices
4. Review comparison and select purchase option

### Example: Food Order

1. Open AutoGLM app
2. Type: "Order a pepperoni pizza from Domino's on Meituan"
3. Agent navigates to Meituan → Domino's → selects pizza → adds to cart
4. Confirm order when prompted

### Example: Message Sending

1. Open AutoGLM app
2. Type: "Send WeChat message to Mom saying I'll be home late"
3. Agent opens WeChat → finds Mom → composes message
4. Review and confirm before sending

---

## Troubleshooting

### Accessibility Service Not Working

```
Symptom: "Please enable Accessibility Service" keeps appearing
Solution:
1. Go to Settings → Accessibility
2. Find AutoGLM and toggle OFF then ON
3. If still failing, restart device
```

### Model Download Failed

```
Symptom: Model download stuck or fails
Solution:
1. Check internet connection
2. Ensure sufficient storage (need ~3GB free)
3. Try downloading on Wi-Fi
4. Manual download: see app Settings → Model Management
```

### App Not Found in Knowledge Base

```
Symptom: "App not recognized" for an app you want to use
Solution:
1. Go to Settings → App Mappings
2. Tap "Add Custom App"
3. Enter app name and package name
4. Package name format: com.example.app (find in Play Store URL)
```

### Cloud Inference Errors

```
Symptom: "Cloud inference failed" or timeout
Solution:
1. Check API key configuration in settings
2. Verify internet connection
3. Try switching to on-device mode
4. Check API rate limits (BigModel/DashScope dashboard)
```

### High Battery Drain

```
Symptom: Battery draining quickly during agent use
Solution:
1. Switch to Auto or On-Device mode (cloud uses more battery)
2. Reduce max steps per task in Settings
3. Close app when not in use (accessibility service runs in background)
```

---

## Development Workflow

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumentation tests (requires device/emulator)
./gradlew connectedAndroidTest
```

### Code Style

```bash
# Format Kotlin code
./gradlew ktlintFormat

# Check style
./gradlew ktlintCheck
```

### Building Documentation

```bash
# Generate KDoc
./gradlew dokkaHtml
```

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                        UI Layer                               │
│  (Jetpack Compose: TaskScreen, HistoryScreen, SettingsScreen)│
└─────────────────────────┬────────────────────────────────────┘
                          │
┌─────────────────────────▼────────────────────────────────────┐
│                      Agent Layer                              │
│  (PhoneAgent, TaskPlanner, ActionHandler)                    │
└─────────────┬───────────────────────────────┬────────────────┘
              │                               │
┌─────────────▼────────────┐    ┌────────────▼─────────────────┐
│    Inference Layer        │    │    Accessibility Layer       │
│  (InferenceRouter,        │    │  (AutoGLMAccessibilityService,│
│   OnDeviceInference,      │    │   UIElementParser,            │
│   CloudInference)         │    │   MediaProjection)            │
└───────────────────────────┘    └──────────────────────────────┘
              │
┌─────────────▼────────────────────────────────────────────────┐
│                      Data Layer                               │
│  (TaskRepository, AppRegistry, Room DB, SharedPreferences)   │
└──────────────────────────────────────────────────────────────┘
```

---

## Next Steps

1. **Extend App Knowledge Base**: Add mappings for apps you use frequently
2. **Customize Prompts**: Modify system prompts in Settings for better results
3. **Contribute**: See CONTRIBUTING.md for how to add features
4. **Report Issues**: GitHub Issues for bugs and feature requests

---

## Related Documentation

- [Feature Specification](./spec.md)
- [Implementation Plan](./plan.md)
- [Data Model](./data-model.md)
- [API Contracts](./contracts/)
- [Research Notes](./research.md)

---

## Voice Input Feature (Added 2025-12-20)

The AutoGLM agent now supports voice input for creating tasks. Speak your task description naturally instead of typing.

### Enabling Voice Input

#### 1. Grant Microphone Permission

When you first tap the microphone button:

1. Android will prompt: "Allow AutoGLM to record audio?"
2. Tap **"Allow"** to enable voice input
3. If you deny, voice input will be disabled (text input still works)

**If you accidentally denied permission:**
1. Go to Settings → Apps → AutoGLM → Permissions
2. Tap "Microphone" and select "Allow"

#### 2. Using Voice Input

1. Open the AutoGLM app
2. On the Task screen, tap the **microphone icon** (🎤) next to the text field
3. A listening indicator appears - start speaking your task
4. Speak clearly: "Find the best price for iPhone 15 on Taobao and JD"
5. When you finish speaking, the transcription appears
6. Review and edit the text if needed
7. Tap **"Start Task"** to begin execution

### Voice Input Tips

**For Best Results:**
- Speak at a normal pace, clearly
- Reduce background noise if possible
- Keep descriptions under 30 seconds
- Include specific details: app names, product names, contacts

**Language Support:**
- Voice input language follows your agent language setting
- Settings → Language → Chinese (中文) or English
- Both Chinese and English speech are supported

**Examples of Voice Commands:**

| Say This | Agent Does |
|----------|------------|
| "帮我在淘宝上找一个便宜的iPhone手机壳" | Opens Taobao, searches for iPhone cases, compares prices |
| "Send a message to John on WhatsApp" | Opens WhatsApp, finds John, composes message |
| "Order a pizza from Domino's on Meituan" | Opens Meituan, finds Domino's, adds pizza to cart |
| "Check my delivery status on JD" | Opens JD, navigates to order tracking |

### Troubleshooting Voice Input

**Voice button is grayed out:**
- Check microphone permission in device settings
- Some devices require restarting the app after granting permission

**"No speech detected" error:**
- Make sure you're speaking into the microphone
- Check that your microphone isn't blocked or muted
- Try speaking louder or closer to the device

**Poor transcription accuracy:**
- Switch to a quieter environment
- Speak more slowly and clearly
- Verify the language setting matches your spoken language

**Voice input not available:**
- Some devices may not support SpeechRecognizer
- Ensure Google app is installed (provides speech services)
- Try updating Google app from Play Store

### Privacy Note

Voice recognition uses Android's built-in speech recognition service:
- On Android 11+, speech can be processed on-device (no network)
- Audio is not stored by AutoGLM - only the transcribed text
- You can enable "Prefer Offline" in Settings for maximum privacy
