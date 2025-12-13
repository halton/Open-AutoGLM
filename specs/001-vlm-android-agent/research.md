# Research: VLM-Powered Android Agent

**Feature**: 001-vlm-android-agent
**Date**: 2025-12-13

## Executive Summary

This document captures technology decisions and research findings for building a native Android agent powered by vision-language models. All key unknowns from the Technical Context have been resolved.

---

## 1. On-Device Model Inference

### Decision: MLC-LLM (primary) + llama.cpp (fallback)

**Rationale**: MLC-LLM provides optimized GPU inference via OpenCL/Vulkan for modern Android devices, while llama.cpp offers CPU-based inference as a fallback for older devices or when GPU resources are unavailable.

**Alternatives Considered**:
| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| MLC-LLM only | Best GPU performance | No CPU fallback | Rejected - device compatibility |
| llama.cpp only | Universal CPU support | Slower inference | Rejected - performance |
| ONNX Runtime | Cross-platform | Limited VLM support | Rejected - model compatibility |
| TensorFlow Lite | Google ecosystem | Poor VLM model support | Rejected - no suitable models |

**Implementation Notes**:
- MLC-LLM: Use pre-compiled Android libraries from mlc-ai/mlc-llm releases
- llama.cpp: Use the official android bindings from ggerganov/llama.cpp
- Model selection at runtime based on device capabilities (GPU memory, SOC type)

---

## 2. On-Device Model Selection

### Decision: Qwen2.5-VL-3B (AWQ, ~2GB RAM) primary; MobileVLM-V2-1.7B (~1GB RAM) fallback

**Rationale**: Qwen2.5-VL-3B offers the best accuracy/size tradeoff for modern devices (6GB+ RAM). MobileVLM-V2-1.7B provides a lighter alternative for memory-constrained devices.

**Model Comparison**:
| Model | Size | RAM Required | Accuracy | Use Case |
|-------|------|--------------|----------|----------|
| Qwen2.5-VL-3B (AWQ) | ~2GB | ~3GB runtime | High | High-end devices (Snapdragon 8 Gen 2+) |
| MobileVLM-V2-1.7B | ~1GB | ~1.5GB runtime | Medium | Mid-range devices |
| Qwen2.5-VL-7B | ~4GB | ~6GB runtime | Very High | Tablets only (too large for phones) |

**Implementation Notes**:
- AWQ quantization for Qwen2.5-VL-3B reduces model size while maintaining accuracy
- GGUF format for llama.cpp compatibility
- Model files distributed via download on first launch (not bundled in APK)

---

## 3. Cloud Model APIs (Fallback)

### Decision: AutoGLM-Phone-9B (BigModel/ModelScope) + Qwen2.5-VL-72B (Alibaba Cloud)

**Rationale**: AutoGLM-Phone-9B is specifically trained for phone agent tasks with action format compatibility. Qwen2.5-VL-72B provides maximum capability for complex multi-step tasks requiring deep reasoning.

**API Configuration**:
| Model | Provider | Use Case | Latency | Cost |
|-------|----------|----------|---------|------|
| AutoGLM-Phone-9B | BigModel API / ModelScope | Standard tasks | ~2-3s | Low |
| Qwen2.5-VL-72B | Alibaba Cloud | Complex reasoning | ~3-5s | Higher |

**Implementation Notes**:
- Both APIs are OpenAI-compatible, reuse existing `ModelClient` pattern
- API key storage in Android Keystore for security
- Automatic fallback chain: on-device → AutoGLM-9B → Qwen2.5-VL-72B

---

## 4. Android UI Automation Approach

### Decision: AccessibilityService (primary) + MediaProjection API (screenshots)

**Rationale**: AccessibilityService provides universal app control without requiring app cooperation. MediaProjection API provides high-fidelity screenshots for VLM analysis.

**Alternatives Considered**:
| Option | Pros | Cons | Verdict |
|--------|------|------|---------|
| AccessibilityService | Universal, no root | Some apps block it | Selected - best coverage |
| UiAutomator | Official testing API | Requires debug mode | Rejected - not for production |
| Root + input injection | Full control | Security risk, limited devices | Rejected - too restrictive |
| App-specific integrations | Best reliability | Not scalable | Rejected - too narrow |

**Implementation Notes**:
- Accessibility Service for: UI tree reading, tap/swipe/type actions, app state detection
- MediaProjection for: screenshot capture (higher quality than accessibility screenshots)
- Handle apps that block accessibility: notify user, suggest manual intervention

---

## 5. Task Routing Logic

### Decision: Hybrid router with complexity scoring

**Rationale**: Route tasks to appropriate inference backend based on task complexity, privacy sensitivity, battery state, and network availability.

**Routing Rules**:
```
IF battery < 20% AND not_charging:
    route to CLOUD (preserve battery)
ELSE IF task contains sensitive_data_keywords:
    route to ON_DEVICE (privacy)
ELSE IF task_complexity_score > 0.7:
    route to CLOUD (accuracy)
ELSE IF network_unavailable:
    route to ON_DEVICE (availability)
ELSE:
    route to ON_DEVICE (default - speed + privacy)
```

**Complexity Scoring Factors**:
- Number of apps involved (1 app = low, 3+ apps = high)
- Task involves financial transactions (+0.3)
- Task requires multi-step reasoning (+0.2)
- Task involves text extraction only (-0.3)

---

## 6. App Knowledge Base Design

### Decision: JSON-based mappings with runtime extensibility

**Rationale**: Maintain compatibility with existing Python `phone_agent/config/apps.py` while allowing users to add custom app mappings.

**Data Structure**:
```json
{
  "appName": "微信",
  "packageName": "com.tencent.mm",
  "aliases": ["WeChat", "wechat"],
  "category": "messaging",
  "uiPatterns": {
    "homeScreen": ["chat_list", "contacts_tab"],
    "commonActions": ["send_message", "view_moments"]
  },
  "prompts": {
    "zh": "当前应用：微信",
    "en": "Current app: WeChat"
  }
}
```

**Implementation Notes**:
- Ship 50+ app mappings from existing `apps.py`
- Allow user-defined mappings stored in app's data directory
- UI patterns help agent recognize app state without full VLM inference

---

## 7. Error Handling Strategy

### Decision: Graceful degradation with user intervention points

**Rationale**: Following constitution's error handling principle, provide actionable bilingual messages and clear intervention points.

**Error Categories**:
| Error Type | Handling | User Communication |
|------------|----------|-------------------|
| Model inference failure | Fallback to next backend | "Switching to cloud inference..." |
| App not installed | Offer to install | "App not found. Install from Play Store?" |
| CAPTCHA/2FA detected | Pause for user | "Please complete verification manually" |
| Accessibility blocked | Guide user | "Enable accessibility in Settings > ..." |
| Network unavailable | Switch to on-device | "Offline mode - using local model" |
| Action failed | Retry with variation | "Retrying with alternative approach..." |

---

## 8. Security Considerations

### Decision: Never store credentials; use Android Keystore for API keys

**Rationale**: Agent must not compromise user security. Sensitive operations require user confirmation.

**Security Measures**:
- API keys stored in Android Keystore (hardware-backed on supported devices)
- No logging of sensitive text content (passwords, card numbers)
- Payment/login actions require explicit user confirmation
- Optional: Biometric confirmation for high-risk actions

---

## 9. Testing Strategy

### Decision: Unit tests + Mock inference + Limited device testing

**Rationale**: VLM-based automation is difficult to fully test automatically. Focus on unit testing core logic, mock inference responses, and manual validation on real devices.

**Test Coverage**:
| Component | Test Type | Coverage Target |
|-----------|-----------|-----------------|
| Action parsing | Unit test | 95% |
| Routing logic | Unit test | 90% |
| API clients | Integration (mocked) | 85% |
| Accessibility service | Instrumentation | 70% |
| Full task execution | Manual + recording | Key scenarios |

---

## Resolved Unknowns Summary

| Unknown | Resolution | Source |
|---------|------------|--------|
| On-device inference framework | MLC-LLM + llama.cpp | User input + research |
| On-device model | Qwen2.5-VL-3B AWQ | User input |
| Cloud model APIs | AutoGLM-Phone-9B, Qwen2.5-VL-72B | User input |
| UI automation method | AccessibilityService | User input |
| Task routing strategy | Complexity-based hybrid | Design decision |
| App knowledge format | JSON with existing Python port | Constitution compliance |

**All NEEDS CLARIFICATION items resolved. Ready for Phase 1.**
