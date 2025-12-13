# Tasks: VLM-Powered Android Agent

**Input**: Design documents from `/specs/001-vlm-android-agent/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Tests are NOT explicitly requested in the feature specification. Skipping test tasks per template guidelines.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Android app**: `android/app/src/main/java/com/openautoglm/agent/`
- **Resources**: `android/app/src/main/res/`
- **Existing Python reference**: `phone_agent/` (for porting logic)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and Android project structure

- [X] T001 Create Android project structure per plan.md in android/
- [X] T002 Initialize Kotlin project with build.gradle.kts and Jetpack Compose dependencies in android/app/build.gradle.kts
- [X] T003 [P] Configure ktlint for Kotlin code formatting in android/build.gradle.kts
- [X] T004 [P] Create AndroidManifest.xml with required permissions (ACCESSIBILITY, INTERNET, FOREGROUND_SERVICE) in android/app/src/main/AndroidManifest.xml
- [X] T005 [P] Setup Room database dependencies and configuration in android/app/build.gradle.kts
- [X] T006 Create AutoGLMApplication.kt application class in android/app/src/main/java/com/openautoglm/agent/AutoGLMApplication.kt

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**CRITICAL**: No user story work can begin until this phase is complete

### 2.1 Data Layer Foundation

- [X] T007 Create TaskStatus and InferenceMode enums in android/app/src/main/java/com/openautoglm/agent/data/entities/Enums.kt
- [X] T008 [P] Create Task entity with Room annotations in android/app/src/main/java/com/openautoglm/agent/data/entities/Task.kt
- [X] T009 [P] Create Action entity with Room annotations in android/app/src/main/java/com/openautoglm/agent/data/entities/Action.kt
- [X] T010 [P] Create ActionResult data class in android/app/src/main/java/com/openautoglm/agent/data/entities/ActionResult.kt
- [X] T011 [P] Create ScreenState data class in android/app/src/main/java/com/openautoglm/agent/data/entities/ScreenState.kt
- [X] T012 [P] Create UIElement data class with Rect in android/app/src/main/java/com/openautoglm/agent/data/entities/UIElement.kt
- [X] T013 [P] Create AppMapping entity with Room annotations in android/app/src/main/java/com/openautoglm/agent/data/entities/AppMapping.kt
- [X] T014 [P] Create UserPreferences entity in android/app/src/main/java/com/openautoglm/agent/data/entities/UserPreferences.kt
- [X] T015 [P] Create ModelConfig entity in android/app/src/main/java/com/openautoglm/agent/data/entities/ModelConfig.kt
- [X] T016 Create TaskDao with CRUD operations in android/app/src/main/java/com/openautoglm/agent/data/dao/TaskDao.kt
- [X] T017 [P] Create ActionDao in android/app/src/main/java/com/openautoglm/agent/data/dao/ActionDao.kt
- [X] T018 [P] Create AppMappingDao in android/app/src/main/java/com/openautoglm/agent/data/dao/AppMappingDao.kt
- [X] T019 [P] Create UserPreferencesDao in android/app/src/main/java/com/openautoglm/agent/data/dao/UserPreferencesDao.kt
- [X] T020 [P] Create ModelConfigDao in android/app/src/main/java/com/openautoglm/agent/data/dao/ModelConfigDao.kt
- [X] T021 Create AppDatabase with Room database definition in android/app/src/main/java/com/openautoglm/agent/data/AppDatabase.kt
- [X] T022 Create TaskRepository for data access abstraction in android/app/src/main/java/com/openautoglm/agent/data/AgentRepository.kt

### 2.2 Model Client Foundation (OpenAI-Compatible)

- [X] T023 Create ChatMessage data class (OpenAI format) in android/app/src/main/java/com/openautoglm/agent/model/ChatMessage.kt
- [X] T024 [P] Create ContentPart sealed class (text/image) in android/app/src/main/java/com/openautoglm/agent/model/ChatMessage.kt
- [X] T025 [P] Create ChatCompletionRequest data class in android/app/src/main/java/com/openautoglm/agent/model/ChatCompletionRequest.kt
- [X] T026 [P] Create ChatCompletionResponse data class in android/app/src/main/java/com/openautoglm/agent/model/ChatCompletionResponse.kt
- [X] T027 Create ModelResponse data class with thinking/action parsing in android/app/src/main/java/com/openautoglm/agent/model/ModelResponse.kt
- [X] T028 Create ModelClient interface in android/app/src/main/java/com/openautoglm/agent/model/ModelClient.kt

### 2.3 Accessibility Service Foundation

- [X] T029 Create AutoGLMAccessibilityService extending AccessibilityService in android/app/src/main/java/com/openautoglm/agent/accessibility/AutoGLMAccessibilityService.kt
- [X] T030 Create UIElementParser for parsing accessibility node tree in android/app/src/main/java/com/openautoglm/agent/accessibility/UIElementParser.kt
- [X] T031 Create ScreenCaptureManager using MediaProjection API in android/app/src/main/java/com/openautoglm/agent/accessibility/ScreenCaptureManager.kt

### 2.4 App Knowledge Base Foundation

- [X] T032 Port app mappings from phone_agent/config/apps.py to JSON format in android/app/src/main/assets/app_mappings.json
- [X] T033 Create AppRegistry for loading and querying app mappings in android/app/src/main/java/com/openautoglm/agent/knowledge/AppKnowledgeBase.kt
- [X] T034 Create AppMappings utility for app name resolution in android/app/src/main/java/com/openautoglm/agent/knowledge/DefaultAppMappings.kt

### 2.5 Inference Router Foundation

- [X] T035 Create InferenceRouter interface in android/app/src/main/java/com/openautoglm/agent/model/InferenceRouter.kt
- [X] T036 Create complexity scoring logic for task routing in android/app/src/main/java/com/openautoglm/agent/inference/ComplexityScorer.kt

### 2.6 Agent Core Foundation

- [X] T037 Port system prompts from phone_agent/config/prompts.py to Kotlin in android/app/src/main/java/com/openautoglm/agent/knowledge/PromptTemplates.kt
- [X] T038 [P] Port prompts_en.py to Kotlin in android/app/src/main/java/com/openautoglm/agent/knowledge/PromptTemplates.kt
- [X] T039 [P] Port prompts_zh.py to Kotlin in android/app/src/main/java/com/openautoglm/agent/knowledge/PromptTemplates.kt
- [X] T040 Create AgentConfig data class in android/app/src/main/java/com/openautoglm/agent/agent/AgentConfig.kt
- [X] T041 Create ActionHandler interface in android/app/src/main/java/com/openautoglm/agent/actions/ActionHandler.kt
- [X] T042 Create ActionParser for parsing model output to Action in android/app/src/main/java/com/openautoglm/agent/core/ResponseParser.kt

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Cross-App Price Comparison and Purchase (Priority: P1) MVP

**Goal**: Enable users to find the best price for a product across multiple e-commerce platforms and complete purchases

**Independent Test**: Ask agent "Find the best price for [product] across shopping apps and buy it"

### Cloud Inference Implementation (Required for P1)

- [X] T043 [US1] Implement CloudInference client using OkHttp/Retrofit in android/app/src/main/java/com/openautoglm/agent/inference/CloudInference.kt
- [X] T044 [US1] Add BigModel API configuration (AutoGLM-Phone-9B) in android/app/src/main/java/com/openautoglm/agent/inference/BigModelConfig.kt
- [X] T045 [P] [US1] Add DashScope API configuration (Qwen2.5-VL-72B) in android/app/src/main/java/com/openautoglm/agent/inference/DashScopeConfig.kt
- [X] T046 [US1] Implement API key secure storage using Android Keystore in android/app/src/main/java/com/openautoglm/agent/inference/SecureKeyStorage.kt

### Core Agent Implementation

- [ ] T047 [US1] Port PhoneAgent.run() and PhoneAgent.step() from phone_agent/agent.py to android/app/src/main/java/com/openautoglm/agent/agent/PhoneAgent.kt
- [ ] T048 [US1] Implement TaskPlanner for multi-step task orchestration in android/app/src/main/java/com/openautoglm/agent/agent/TaskPlanner.kt
- [ ] T049 [US1] Implement action execution via AccessibilityService (TAP, SWIPE, TYPE, BACK, HOME, LAUNCH) in android/app/src/main/java/com/openautoglm/agent/actions/ActionExecutor.kt

### Action Handlers

- [ ] T050 [P] [US1] Implement TapAction handler in android/app/src/main/java/com/openautoglm/agent/actions/handlers/TapAction.kt
- [ ] T051 [P] [US1] Implement SwipeAction handler in android/app/src/main/java/com/openautoglm/agent/actions/handlers/SwipeAction.kt
- [ ] T052 [P] [US1] Implement TypeAction handler in android/app/src/main/java/com/openautoglm/agent/actions/handlers/TypeAction.kt
- [ ] T053 [P] [US1] Implement LaunchAction handler in android/app/src/main/java/com/openautoglm/agent/actions/handlers/LaunchAction.kt
- [ ] T054 [P] [US1] Implement NavigationAction handler (BACK, HOME) in android/app/src/main/java/com/openautoglm/agent/actions/handlers/NavigationAction.kt

### E-commerce App Mappings

- [ ] T055 [P] [US1] Add Taobao app mapping with UI patterns in android/app/src/main/assets/app_mappings.json
- [ ] T056 [P] [US1] Add JD.com app mapping with UI patterns in android/app/src/main/assets/app_mappings.json
- [ ] T057 [P] [US1] Add Amazon app mapping with UI patterns in android/app/src/main/assets/app_mappings.json

### UI Implementation

- [ ] T058 [US1] Create MainActivity with Compose navigation in android/app/src/main/java/com/openautoglm/agent/ui/MainActivity.kt
- [ ] T059 [US1] Create TaskScreen for task input and progress display in android/app/src/main/java/com/openautoglm/agent/ui/screens/TaskScreen.kt
- [ ] T060 [P] [US1] Create TaskProgressComponent showing real-time agent status in android/app/src/main/java/com/openautoglm/agent/ui/components/TaskProgressComponent.kt
- [ ] T061 [P] [US1] Create PriceComparisonCard component for displaying results in android/app/src/main/java/com/openautoglm/agent/ui/components/PriceComparisonCard.kt
- [ ] T062 [US1] Create ConfirmationDialog for purchase confirmation (FR-007) in android/app/src/main/java/com/openautoglm/agent/ui/components/ConfirmationDialog.kt

### Error Handling for US1

- [ ] T063 [US1] Implement bilingual error messages (zh/en) for cloud inference failures in android/app/src/main/java/com/openautoglm/agent/error/InferenceErrorHandler.kt
- [ ] T064 [US1] Implement out-of-stock detection and handling in android/app/src/main/java/com/openautoglm/agent/agent/StockChecker.kt

**Checkpoint**: User Story 1 complete - price comparison and purchase flow functional

---

## Phase 4: User Story 2 - Food Ordering from Restaurants (Priority: P1)

**Goal**: Enable users to order food from restaurants through delivery apps using natural language

**Independent Test**: Ask agent "Order [food item] from [restaurant] on [delivery app]"

### Food Delivery App Mappings

- [ ] T065 [P] [US2] Add Meituan app mapping with UI patterns in android/app/src/main/assets/app_mappings.json
- [ ] T066 [P] [US2] Add Eleme app mapping with UI patterns in android/app/src/main/assets/app_mappings.json
- [ ] T067 [P] [US2] Add Uber Eats app mapping with UI patterns in android/app/src/main/assets/app_mappings.json

### Restaurant Detection and Menu Navigation

- [ ] T068 [US2] Implement restaurant search and selection logic in android/app/src/main/java/com/openautoglm/agent/agent/RestaurantFinder.kt
- [ ] T069 [US2] Implement menu item matching from natural language description in android/app/src/main/java/com/openautoglm/agent/agent/MenuMatcher.kt
- [ ] T070 [US2] Implement cart management actions (add to cart, view cart) in android/app/src/main/java/com/openautoglm/agent/actions/handlers/CartAction.kt

### Alternative Suggestions

- [ ] T071 [US2] Implement restaurant availability detection (closed/unavailable) in android/app/src/main/java/com/openautoglm/agent/agent/AvailabilityChecker.kt
- [ ] T072 [US2] Implement alternative restaurant suggestion logic in android/app/src/main/java/com/openautoglm/agent/agent/AlternativeSuggester.kt

### Order Confirmation UI

- [ ] T073 [US2] Create OrderConfirmationDialog with cart summary in android/app/src/main/java/com/openautoglm/agent/ui/components/OrderConfirmationDialog.kt

**Checkpoint**: User Story 2 complete - food ordering functional

---

## Phase 5: User Story 3 - Train Ticket Booking (Priority: P2)

**Goal**: Enable users to book train tickets with price and schedule comparison

**Independent Test**: Ask agent "Book a train from [origin] to [destination] on [date]"

### Travel App Mappings

- [ ] T074 [P] [US3] Add 12306 (China Railway) app mapping in android/app/src/main/assets/app_mappings.json
- [ ] T075 [P] [US3] Add Ctrip app mapping in android/app/src/main/assets/app_mappings.json
- [ ] T076 [P] [US3] Add Trip.com app mapping in android/app/src/main/assets/app_mappings.json

### Travel Booking Logic

- [ ] T077 [US3] Implement travel date/time parsing from natural language in android/app/src/main/java/com/openautoglm/agent/agent/TravelDateParser.kt
- [ ] T078 [US3] Implement train search and comparison logic in android/app/src/main/java/com/openautoglm/agent/agent/TrainSearcher.kt
- [ ] T079 [US3] Implement seat class selection handling in android/app/src/main/java/com/openautoglm/agent/agent/SeatSelector.kt
- [ ] T080 [US3] Implement passenger information handling (using saved data) in android/app/src/main/java/com/openautoglm/agent/agent/PassengerManager.kt

### Alternative Date Suggestions

- [ ] T081 [US3] Implement date availability detection and alternative suggestions in android/app/src/main/java/com/openautoglm/agent/agent/DateAlternativeSuggester.kt

### Travel Booking UI

- [ ] T082 [US3] Create TrainComparisonCard component in android/app/src/main/java/com/openautoglm/agent/ui/components/TrainComparisonCard.kt
- [ ] T083 [US3] Create BookingConfirmationDialog in android/app/src/main/java/com/openautoglm/agent/ui/components/BookingConfirmationDialog.kt

**Checkpoint**: User Story 3 complete - train booking functional

---

## Phase 6: User Story 4 - Text Extraction from Screenshots (Priority: P2)

**Goal**: Enable users to extract text from screenshots or images for further use

**Independent Test**: Share screenshot and ask "Extract the text from this image"

### On-Device Inference Implementation

- [ ] T084 [US4] Integrate MLC-LLM Android library for GPU inference in android/app/build.gradle.kts
- [ ] T085 [US4] Implement OnDeviceInference using MLC-LLM in android/app/src/main/java/com/openautoglm/agent/inference/OnDeviceInference.kt
- [ ] T086 [US4] Integrate llama.cpp Android bindings for CPU fallback in android/app/src/main/java/com/openautoglm/agent/inference/LlamaCppInference.kt
- [ ] T087 [US4] Implement model download manager for first-run model download in android/app/src/main/java/com/openautoglm/agent/inference/ModelDownloadManager.kt

### InferenceRouter Complete Implementation

- [ ] T088 [US4] Implement full InferenceRouter with on-device/cloud routing logic (per research.md) in android/app/src/main/java/com/openautoglm/agent/inference/InferenceRouterImpl.kt

### Text Extraction

- [ ] T089 [US4] Implement text extraction task type detection in android/app/src/main/java/com/openautoglm/agent/agent/TaskTypeDetector.kt
- [ ] T090 [US4] Implement text extraction result formatting (copyable) in android/app/src/main/java/com/openautoglm/agent/agent/TextExtractor.kt
- [ ] T091 [US4] Implement translation request handling (route to cloud for translation) in android/app/src/main/java/com/openautoglm/agent/agent/TranslationHandler.kt

### Text Extraction UI

- [ ] T092 [US4] Create TextExtractionResultScreen with copy functionality in android/app/src/main/java/com/openautoglm/agent/ui/screens/TextExtractionResultScreen.kt
- [ ] T093 [P] [US4] Create HandwritingConfidenceIndicator component in android/app/src/main/java/com/openautoglm/agent/ui/components/HandwritingConfidenceIndicator.kt

**Checkpoint**: User Story 4 complete - text extraction functional with on-device inference

---

## Phase 7: User Story 5 - Multi-App Logistics Tracking (Priority: P2)

**Goal**: Enable users to track packages across multiple shopping apps in a unified view

**Independent Test**: Ask "Show me all my pending deliveries"

### Logistics Data Model

- [ ] T094 [P] [US5] Create Shipment data class in android/app/src/main/java/com/openautoglm/agent/data/entities/Shipment.kt
- [ ] T095 [P] [US5] Create ShipmentDao in android/app/src/main/java/com/openautoglm/agent/data/dao/ShipmentDao.kt

### Logistics Tracking Logic

- [ ] T096 [US5] Implement multi-app logistics querying orchestration in android/app/src/main/java/com/openautoglm/agent/agent/LogisticsTracker.kt
- [ ] T097 [US5] Implement shipment data extraction from app screens in android/app/src/main/java/com/openautoglm/agent/agent/ShipmentExtractor.kt
- [ ] T098 [US5] Implement shipment status change detection in android/app/src/main/java/com/openautoglm/agent/agent/ShipmentStatusMonitor.kt

### Re-authentication Handling

- [ ] T099 [US5] Implement app re-authentication detection and user prompting in android/app/src/main/java/com/openautoglm/agent/agent/AuthenticationHandler.kt

### Logistics UI

- [ ] T100 [US5] Create ShipmentListScreen with consolidated deliveries view in android/app/src/main/java/com/openautoglm/agent/ui/screens/ShipmentListScreen.kt
- [ ] T101 [P] [US5] Create ShipmentCard component with status and ETA in android/app/src/main/java/com/openautoglm/agent/ui/components/ShipmentCard.kt

**Checkpoint**: User Story 5 complete - logistics tracking functional

---

## Phase 8: User Story 6 - Calendar and Appointment Management (Priority: P3)

**Goal**: Enable users to schedule appointments and manage calendar events via natural language

**Independent Test**: Ask "Schedule a meeting with [person] tomorrow at 3pm"

### Calendar App Integration

- [ ] T102 [P] [US6] Add Google Calendar app mapping in android/app/src/main/assets/app_mappings.json
- [ ] T103 [P] [US6] Add system Calendar app mapping in android/app/src/main/assets/app_mappings.json

### Calendar Logic

- [ ] T104 [US6] Implement calendar event parsing from natural language in android/app/src/main/java/com/openautoglm/agent/agent/CalendarEventParser.kt
- [ ] T105 [US6] Implement calendar conflict detection in android/app/src/main/java/com/openautoglm/agent/agent/CalendarConflictDetector.kt
- [ ] T106 [US6] Implement event modification handling in android/app/src/main/java/com/openautoglm/agent/agent/CalendarEventModifier.kt

### Calendar UI

- [ ] T107 [US6] Create EventConfirmationDialog with time conflict warnings in android/app/src/main/java/com/openautoglm/agent/ui/components/EventConfirmationDialog.kt

**Checkpoint**: User Story 6 complete - calendar management functional

---

## Phase 9: User Story 7 - Message Sending and Notification Response (Priority: P3)

**Goal**: Enable users to send messages across messaging apps and respond to notifications

**Independent Test**: Ask "Send a message to [contact] on [app] saying [message]"

### Messaging App Mappings

- [ ] T108 [P] [US7] Add WeChat app mapping with messaging UI patterns in android/app/src/main/assets/app_mappings.json
- [ ] T109 [P] [US7] Add WhatsApp app mapping in android/app/src/main/assets/app_mappings.json
- [ ] T110 [P] [US7] Add Telegram app mapping in android/app/src/main/assets/app_mappings.json

### Messaging Logic

- [ ] T111 [US7] Implement contact search and selection in android/app/src/main/java/com/openautoglm/agent/agent/ContactFinder.kt
- [ ] T112 [US7] Implement message composition and sending flow in android/app/src/main/java/com/openautoglm/agent/agent/MessageComposer.kt
- [ ] T113 [US7] Implement notification response handling via AccessibilityService in android/app/src/main/java/com/openautoglm/agent/agent/NotificationResponder.kt
- [ ] T114 [US7] Implement contact-not-found handling with app suggestions in android/app/src/main/java/com/openautoglm/agent/agent/ContactNotFoundHandler.kt

### Messaging UI

- [ ] T115 [US7] Create MessagePreviewDialog for send confirmation (FR-007) in android/app/src/main/java/com/openautoglm/agent/ui/components/MessagePreviewDialog.kt

**Checkpoint**: User Story 7 complete - messaging functional

---

## Phase 10: User Story 8 - Batch App Installation (Priority: P3)

**Goal**: Enable users to install and configure multiple apps from a list

**Independent Test**: Ask "Install these apps: [list]"

### App Store Integration

- [ ] T116 [P] [US8] Add Google Play Store app mapping in android/app/src/main/assets/app_mappings.json
- [ ] T117 [P] [US8] Add Huawei AppGallery app mapping in android/app/src/main/assets/app_mappings.json

### Batch Installation Logic

- [ ] T118 [US8] Implement batch app installation orchestration in android/app/src/main/java/com/openautoglm/agent/agent/BatchInstaller.kt
- [ ] T119 [US8] Implement permission grant handling based on user preferences in android/app/src/main/java/com/openautoglm/agent/agent/PermissionHandler.kt
- [ ] T120 [US8] Implement region availability detection and notification in android/app/src/main/java/com/openautoglm/agent/agent/RegionAvailabilityChecker.kt

### Installation Progress UI

- [ ] T121 [US8] Create BatchInstallProgressScreen with app-by-app status in android/app/src/main/java/com/openautoglm/agent/ui/screens/BatchInstallProgressScreen.kt
- [ ] T122 [P] [US8] Create AppInstallStatusCard component in android/app/src/main/java/com/openautoglm/agent/ui/components/AppInstallStatusCard.kt

**Checkpoint**: User Story 8 complete - batch installation functional

---

## Phase 11: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

### Settings and History UI

- [ ] T123 [P] Create SettingsScreen with inference mode, API keys, model selection in android/app/src/main/java/com/openautoglm/agent/ui/screens/SettingsScreen.kt
- [ ] T124 [P] Create HistoryScreen with task history list in android/app/src/main/java/com/openautoglm/agent/ui/screens/HistoryScreen.kt
- [ ] T125 [P] Create ModelManagementScreen for on-device model downloads in android/app/src/main/java/com/openautoglm/agent/ui/screens/ModelManagementScreen.kt
- [ ] T126 [P] Create AppMappingsScreen for custom app additions in android/app/src/main/java/com/openautoglm/agent/ui/screens/AppMappingsScreen.kt

### Edge Case Handling (FR compliance)

- [ ] T127 Implement app-not-installed detection with install prompt (Edge Case 1) in android/app/src/main/java/com/openautoglm/agent/agent/AppInstallChecker.kt
- [ ] T128 Implement CAPTCHA/2FA detection and user takeover request (Edge Case 2, FR-005) in android/app/src/main/java/com/openautoglm/agent/agent/CaptchaDetector.kt
- [ ] T129 Implement unexpected UI state handling (popups, ads, overlays) (Edge Case 3, FR-009) in android/app/src/main/java/com/openautoglm/agent/agent/UnexpectedUIHandler.kt
- [ ] T130 Implement network connectivity monitoring and retry logic (Edge Case 4) in android/app/src/main/java/com/openautoglm/agent/agent/NetworkMonitor.kt
- [ ] T131 Implement transaction failure handling (Edge Case 5) in android/app/src/main/java/com/openautoglm/agent/agent/TransactionFailureHandler.kt
- [ ] T132 Implement sensitive data filtering for logging (Edge Case 6, FR-011) in android/app/src/main/java/com/openautoglm/agent/logging/SensitiveDataFilter.kt

### Task State Management

- [ ] T133 Implement task pause/resume functionality (FR-006) in android/app/src/main/java/com/openautoglm/agent/agent/TaskStateManager.kt
- [ ] T134 Implement task cancellation handling (FR-010) in android/app/src/main/java/com/openautoglm/agent/agent/TaskCancellationHandler.kt

### Documentation Updates

- [ ] T135 [P] Update README.md with Android app installation instructions (bilingual - Chinese)
- [ ] T136 [P] Update README_en.md with Android app installation instructions (bilingual - English)

### Final Validation

- [ ] T137 Run quickstart.md validation scenarios on physical device
- [ ] T138 Verify 50+ app mappings are loaded correctly from app_mappings.json

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3-10)**: All depend on Foundational phase completion
  - US1 and US2 are both P1 - can proceed in parallel after Foundational
  - US3, US4, US5 are P2 - can start after Foundational, recommend after US1/US2
  - US6, US7, US8 are P3 - lower priority, can be done last
- **Polish (Phase 11)**: Can run in parallel with later user stories

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational - No dependencies on other stories
- **User Story 3 (P2)**: Can start after Foundational - No dependencies on other stories
- **User Story 4 (P2)**: Can start after Foundational - On-device inference can be used by all stories once complete
- **User Story 5 (P2)**: Can start after Foundational - No dependencies on other stories
- **User Story 6 (P3)**: Can start after Foundational - No dependencies on other stories
- **User Story 7 (P3)**: Can start after Foundational - No dependencies on other stories
- **User Story 8 (P3)**: Can start after Foundational - No dependencies on other stories

### Within Each User Story

- Models before services
- Services before action handlers
- Action handlers before UI components
- Core implementation before edge case handling

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, User Stories 1 and 2 (both P1) can start in parallel
- All app mapping additions within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: Foundational Phase

```bash
# Launch entity creation in parallel:
Task: "Create Task entity in android/.../data/entities/Task.kt"
Task: "Create Action entity in android/.../data/entities/Action.kt"
Task: "Create AppMapping entity in android/.../data/entities/AppMapping.kt"
Task: "Create UserPreferences entity in android/.../data/entities/UserPreferences.kt"
Task: "Create ModelConfig entity in android/.../data/entities/ModelConfig.kt"
```

## Parallel Example: User Story 1

```bash
# Launch action handlers in parallel:
Task: "Implement TapAction handler in android/.../actions/handlers/TapAction.kt"
Task: "Implement SwipeAction handler in android/.../actions/handlers/SwipeAction.kt"
Task: "Implement TypeAction handler in android/.../actions/handlers/TypeAction.kt"
Task: "Implement LaunchAction handler in android/.../actions/handlers/LaunchAction.kt"
Task: "Implement NavigationAction handler in android/.../actions/handlers/NavigationAction.kt"

# Launch app mappings in parallel:
Task: "Add Taobao app mapping in android/.../assets/app_mappings.json"
Task: "Add JD.com app mapping in android/.../assets/app_mappings.json"
Task: "Add Amazon app mapping in android/.../assets/app_mappings.json"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1 (Price Comparison)
4. **STOP and VALIDATE**: Test price comparison on real device
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. User Story 1 (Price Comparison) → Test → Deploy (MVP!)
3. User Story 2 (Food Ordering) → Test → Deploy (adds daily utility)
4. User Story 4 (Text Extraction) → Enables on-device inference for all stories
5. User Stories 3, 5 (Travel, Logistics) → Test → Deploy (P2 features)
6. User Stories 6, 7, 8 (Calendar, Messaging, Install) → Test → Deploy (P3 features)

### Suggested MVP Scope

**MVP = Phase 1 + Phase 2 + Phase 3 (User Story 1)**

Total MVP tasks: 64 tasks (T001-T064)
- Setup: 6 tasks
- Foundational: 36 tasks
- User Story 1: 22 tasks

---

## Summary

| Metric | Count |
|--------|-------|
| **Total Tasks** | 138 |
| **Setup Phase** | 6 tasks |
| **Foundational Phase** | 36 tasks |
| **User Story 1 (P1)** | 22 tasks |
| **User Story 2 (P1)** | 9 tasks |
| **User Story 3 (P2)** | 10 tasks |
| **User Story 4 (P2)** | 10 tasks |
| **User Story 5 (P2)** | 8 tasks |
| **User Story 6 (P3)** | 6 tasks |
| **User Story 7 (P3)** | 8 tasks |
| **User Story 8 (P3)** | 7 tasks |
| **Polish Phase** | 16 tasks |
| **Parallel Opportunities** | 45 tasks marked [P] |
| **MVP Tasks** | 64 tasks |

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
- All file paths assume android/ module structure from plan.md
