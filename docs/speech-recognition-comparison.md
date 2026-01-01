# Speech Recognition Comparison for Android

Last updated: 2026-01-01

This document compares speech recognition options for the Open-AutoGLM Android agent, with focus on Chinese and English language support.

---

## Local (On-Device) Options

| Feature | **Whisper.cpp** | **Vosk** | **SenseVoice** | **Android Built-in (SODA)** |
|---------|-----------------|----------|----------------|----------------------------|
| **Chinese Accuracy** | Good | Good | Excellent (optimized) | Good |
| **English Accuracy** | Excellent | Good | Good | Excellent |
| **Model Size (Small)** | 466 MB | 50 MB | ~200 MB | Pre-installed |
| **Memory Usage** | ~850 MB | ~200 MB | ~300 MB | Shared system |
| **Speed** | Medium | Fast | 15x faster than Whisper | Fast |
| **Android Support** | Native | Native | Requires porting | Built-in |
| **Offline** | Yes | Yes | Yes | Yes |
| **Languages** | 99+ | 20+ | 50+ (strong CJK) | Many |
| **License** | MIT | Apache 2.0 | Apache 2.0 | Proprietary |
| **GitHub** | [ggerganov/whisper.cpp](https://github.com/ggerganov/whisper.cpp) | [alphacep/vosk-api](https://github.com/alphacep/vosk-api) | [FunAudioLLM/SenseVoice](https://github.com/FunAudioLLM/SenseVoice) | N/A |

### Whisper.cpp Details

- High-performance C/C++ port of OpenAI's Whisper
- Optimized for Apple Silicon (Metal, Core ML, ARM NEON)
- GPU acceleration (NVIDIA CUDA, Vulkan, OpenVINO)
- Integer quantization for reduced memory usage
- Android example available at `examples/whisper.android`

**Model Sizes:**
| Model | Disk Size | Memory Usage |
|-------|-----------|--------------|
| tiny | 75 MiB | ~273 MB |
| base | 142 MiB | ~388 MB |
| small | 466 MiB | ~852 MB |
| medium | 1.5 GiB | ~2.1 GB |
| large | 2.9 GiB | ~3.9 GB |

### Vosk Details

- Lightweight speech recognition toolkit
- Small model size (~50 MB)
- Official Android SDK available
- Supports 20+ languages including Chinese
- Low resource usage, suitable for mobile devices

### SenseVoice Details

- Alibaba's speech foundation model
- Multiple capabilities: ASR, language ID, emotion recognition, audio event detection
- Specifically optimized for Chinese and Cantonese
- Outperforms Whisper on Chinese benchmarks (AISHELL-1, AISHELL-2, Wenetspeech)
- Non-autoregressive architecture: 70ms to process 10 seconds of audio
- No official Android support yet (requires porting)

---

## Cloud (Remote) Options

| Feature | **Google Cloud STT** | **Azure Speech** | **OpenAI Whisper API** | **Android SpeechRecognizer** |
|---------|---------------------|------------------|----------------------|------------------------------|
| **Chinese Accuracy** | Excellent | Excellent | Good | Good |
| **English Accuracy** | Excellent | Excellent | Excellent | Excellent |
| **Pricing** | $0.006/15sec | $1/audio hour | $0.006/min | Free (via Google) |
| **Latency** | Low | Low | Medium | Low |
| **Streaming** | Yes | Yes | No | Yes |
| **Integration** | REST/SDK | REST/SDK | REST | Built-in Android API |
| **Privacy** | Cloud-processed | Cloud-processed | Cloud-processed | Cloud-processed |

### Google Cloud Speech-to-Text
- Industry-leading accuracy
- Real-time streaming support
- 125+ languages
- Automatic punctuation
- Speaker diarization available

### Azure Speech Services
- Competitive accuracy
- Real-time and batch transcription
- Custom speech models available
- Integration with Azure ecosystem

### OpenAI Whisper API
- Based on Whisper large model
- Simple REST API
- No streaming support
- Good multilingual performance

### Android SpeechRecognizer (Current Implementation)
- Uses Google Speech Services on most devices
- Free, no API key required
- Built-in, no additional dependencies
- Real-time streaming with partial results
- Some devices have offline mode (SODA) but with session management issues

---

## Implementation Effort

| Option | Effort Level | Notes |
|--------|--------------|-------|
| Keep current (Android SpeechRecognizer) | None | Already implemented and working |
| Vosk | Low | Official Android SDK, straightforward integration |
| Whisper.cpp | Medium | Has Android example, requires JNI integration |
| SenseVoice | High | Need to port to Android, no official mobile support |
| Google Cloud STT | Medium | REST integration, requires API key and billing setup |
| Azure Speech | Medium | REST integration, requires Azure subscription |
| OpenAI Whisper API | Low-Medium | Simple REST API, no streaming |

---

## Recommendations

### Best for Chinese Recognition
**SenseVoice** - Specifically optimized for Chinese/Cantonese, 15x faster than Whisper, outperforms Whisper on Chinese benchmarks. However, requires porting effort.

### Best Balance (Multi-language + Easy Integration)
**Whisper.cpp** - Excellent across 99 languages, mature Android support with examples, active community, MIT license.

### Smallest Footprint
**Vosk** - Only 50MB model, low memory usage, official Android SDK, good for resource-constrained devices.

### Simplest (No Changes)
**Keep Android SpeechRecognizer** - Already working, no additional dependencies, free Google backend.

---

## Current Implementation

The Open-AutoGLM app currently uses Android's built-in `SpeechRecognizer` API with:
- Online recognition via Google Speech Services
- Offline mode disabled (`preferOffline = false`) due to SODA session management issues
- Chinese locale support (`zh-CN`) with extended silence timeout (3000ms)
- Fallback to partial results when final results are empty

See implementation in:
- `android/app/src/main/java/com/openautoglm/agent/voice/VoiceInputManager.kt`
- `android/app/src/main/java/com/openautoglm/agent/voice/VoiceInputConfig.kt`

---

## Future Considerations

1. **Privacy**: On-device recognition (Whisper.cpp, Vosk) provides better privacy
2. **Offline capability**: Local models work without network
3. **Cost**: Cloud APIs have ongoing costs; local models are free after initial setup
4. **Accuracy vs Size tradeoff**: Larger models = better accuracy but more resources
5. **Chinese optimization**: SenseVoice is the best option when Android support matures

---

## References

- [OpenAI Whisper](https://github.com/openai/whisper)
- [Whisper.cpp](https://github.com/ggerganov/whisper.cpp)
- [Vosk Speech Recognition](https://github.com/alphacep/vosk-api)
- [SenseVoice](https://github.com/FunAudioLLM/SenseVoice)
- [Google Cloud Speech-to-Text](https://cloud.google.com/speech-to-text)
- [Azure Speech Services](https://azure.microsoft.com/en-us/products/ai-services/speech-services)
- [OpenAI Whisper API](https://platform.openai.com/docs/guides/speech-to-text)
