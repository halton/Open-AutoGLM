# Data Model: VLM-Powered Android Agent

**Feature**: 001-vlm-android-agent
**Date**: 2025-12-13

## Entity Overview

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│    Task     │────▶│   Action    │────▶│ ActionResult│
└─────────────┘     └─────────────┘     └─────────────┘
       │                   │
       │                   │
       ▼                   ▼
┌─────────────┐     ┌─────────────┐
│ ScreenState │     │  AppContext │
└─────────────┘     └─────────────┘
       │
       │
       ▼
┌─────────────┐     ┌─────────────┐
│  UIElement  │     │ AppMapping  │
└─────────────┘     └─────────────┘

┌─────────────┐     ┌─────────────┐
│ UserPrefs   │     │ ModelConfig │
└─────────────┘     └─────────────┘
```

---

## Entity Definitions

### Task

Represents a user-initiated automation request.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | UUID | Unique identifier | Auto-generated |
| description | String | Natural language task description | Required, max 1000 chars |
| status | TaskStatus | Current task state | Enum: PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED |
| createdAt | Timestamp | When task was created | Auto-set |
| startedAt | Timestamp? | When execution began | Nullable |
| completedAt | Timestamp? | When task finished | Nullable |
| result | String? | Final result message | Nullable |
| errorMessage | String? | Error details if failed | Nullable |
| stepCount | Int | Number of steps executed | Default: 0 |
| maxSteps | Int | Maximum allowed steps | Default: 100 |
| inferenceMode | InferenceMode | Where inference runs | Enum: ON_DEVICE, CLOUD, AUTO |

**State Transitions**:
```
PENDING → RUNNING → COMPLETED
                  → FAILED
                  → CANCELLED
RUNNING → PAUSED → RUNNING
```

---

### Action

Represents a single UI interaction determined by the agent.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | UUID | Unique identifier | Auto-generated |
| taskId | UUID | Parent task reference | Foreign key |
| type | ActionType | Type of action | Enum (see below) |
| parameters | Map<String, Any> | Action-specific params | JSON serializable |
| timestamp | Timestamp | When action was executed | Auto-set |
| success | Boolean | Whether action succeeded | Required |
| thinking | String | Agent's reasoning | Max 2000 chars |
| screenStateBefore | UUID? | Screen state reference | Foreign key |

**ActionType Enum**:
- `TAP` - Single tap at coordinates
- `DOUBLE_TAP` - Double tap at coordinates
- `LONG_PRESS` - Long press at coordinates
- `SWIPE` - Swipe from start to end coordinates
- `TYPE` - Text input
- `BACK` - System back button
- `HOME` - System home button
- `LAUNCH` - Launch app by name
- `WAIT` - Wait for specified duration
- `TAKE_OVER` - Request user intervention
- `FINISH` - Complete task with message

**Parameter Examples**:
```kotlin
// TAP
{ "x": 540, "y": 1200, "relative": [500, 800] }

// TYPE
{ "text": "Hello world" }

// SWIPE
{ "startX": 540, "startY": 1500, "endX": 540, "endY": 500 }

// LAUNCH
{ "appName": "微信", "packageName": "com.tencent.mm" }

// WAIT
{ "durationMs": 2000 }
```

---

### ActionResult

The outcome of executing an action.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| actionId | UUID | Parent action reference | Foreign key |
| success | Boolean | Whether action succeeded | Required |
| shouldFinish | Boolean | Whether task should end | Default: false |
| message | String? | Result message | Nullable |
| requiresConfirmation | Boolean | User confirmation needed | Default: false |

---

### ScreenState

Captured state of the device screen at a point in time.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | UUID | Unique identifier | Auto-generated |
| taskId | UUID | Parent task reference | Foreign key |
| capturedAt | Timestamp | When captured | Auto-set |
| width | Int | Screen width in pixels | > 0 |
| height | Int | Screen height in pixels | > 0 |
| screenshotPath | String? | Path to screenshot file | Nullable (not stored in DB) |
| screenshotBase64 | String? | Base64 encoded image | Nullable (transient) |
| currentApp | String | Current foreground app | Required |
| currentPackage | String | Current package name | Required |
| uiElements | List<UIElement> | Parsed UI elements | May be empty |

---

### UIElement

A parsed UI element from the accessibility tree or VLM analysis.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | String | Element identifier | Auto-generated |
| type | String | Element type (button, text, etc.) | Required |
| text | String? | Visible text content | Nullable |
| contentDescription | String? | Accessibility description | Nullable |
| bounds | Rect | Element bounds on screen | Required |
| clickable | Boolean | Is element clickable | Default: false |
| scrollable | Boolean | Is element scrollable | Default: false |
| focused | Boolean | Is element focused | Default: false |
| enabled | Boolean | Is element enabled | Default: true |
| className | String? | Android class name | Nullable |

**Rect Structure**:
```kotlin
data class Rect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}
```

---

### AppContext

Runtime context for a specific app during task execution.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| packageName | String | Android package name | Primary key |
| appName | String | Display name | Required |
| currentScreen | String? | Identified screen name | Nullable |
| navigationPath | List<String> | Screens visited | May be empty |
| extractedData | Map<String, Any> | Data extracted from app | JSON serializable |
| lastAccessedAt | Timestamp | Last interaction time | Auto-updated |

---

### AppMapping

Configuration for a known app in the knowledge base.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| packageName | String | Android package name | Primary key |
| appName | String | Primary display name | Required |
| aliases | List<String> | Alternative names | May be empty |
| category | AppCategory | App category | Enum |
| iconPath | String? | Local icon path | Nullable |
| uiPatterns | Map<String, List<String>> | Known UI patterns | JSON serializable |
| prompts | Map<String, String> | Localized prompts (lang → text) | Required: zh, en |
| isUserDefined | Boolean | User-added vs built-in | Default: false |

**AppCategory Enum**:
- `SOCIAL` - Messaging and social apps
- `ECOMMERCE` - Shopping apps
- `TRAVEL` - Travel and booking apps
- `FOOD` - Food delivery apps
- `ENTERTAINMENT` - Video, music, games
- `PRODUCTIVITY` - Work and utility apps
- `FINANCE` - Banking and payment apps
- `SYSTEM` - System apps and settings
- `OTHER` - Uncategorized

---

### UserPreferences

User-configurable settings for the agent.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | String | User ID (single user) | Default: "default" |
| language | String | Preferred language | Default: "zh" |
| defaultInferenceMode | InferenceMode | Preferred inference mode | Default: AUTO |
| confirmSensitiveActions | Boolean | Confirm purchases, etc. | Default: true |
| enabledApps | Set<String> | Package names allowed | All by default |
| cloudApiEnabled | Boolean | Allow cloud inference | Default: true |
| maxStepsPerTask | Int | Default max steps | Default: 100 |
| defaultPaymentMethod | String? | Payment method hint | Nullable |
| defaultDeliveryAddress | String? | Address hint | Nullable |
| onDeviceModelPreference | String | Preferred on-device model | Default: "qwen2.5-vl-3b" |

---

### ModelConfig

Configuration for model inference.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| id | String | Config ID | Required |
| type | InferenceType | On-device or cloud | Enum: ON_DEVICE, CLOUD |
| modelName | String | Model identifier | Required |
| baseUrl | String? | API base URL (cloud) | Required for CLOUD |
| apiKey | String? | API key (stored encrypted) | Required for CLOUD |
| maxTokens | Int | Max response tokens | Default: 3000 |
| temperature | Float | Sampling temperature | Default: 0.0, range: 0.0-2.0 |
| topP | Float | Top-p sampling | Default: 0.85, range: 0.0-1.0 |
| frequencyPenalty | Float | Repetition penalty | Default: 0.2, range: -2.0-2.0 |
| isActive | Boolean | Is this config active | Default: true |

---

### ModelResponse

Response from model inference.

| Field | Type | Description | Constraints |
|-------|------|-------------|-------------|
| thinking | String | Agent's reasoning process | Max 2000 chars |
| action | String | Raw action string | Required |
| rawContent | String | Full model response | Required |
| inferenceTimeMs | Long | Inference duration | >= 0 |
| modelUsed | String | Which model was used | Required |
| tokenCount | Int? | Tokens consumed | Nullable |

---

## Validation Rules

### Task
- `description` must not be empty or whitespace-only
- `maxSteps` must be between 1 and 1000
- Cannot transition from COMPLETED/FAILED/CANCELLED to any other state

### Action
- Coordinate parameters must be within screen bounds (0 to width/height)
- `relative` coordinates must be in range 0-1000 (normalized)
- `TYPE` action must have non-null `text` parameter
- `LAUNCH` action must have valid `appName`

### AppMapping
- `packageName` must match Android package format: `[a-z][a-z0-9]*(\.[a-z][a-z0-9]*)+`
- Must have at least `zh` and `en` prompt entries

### UserPreferences
- `language` must be one of: "zh", "en"
- `maxStepsPerTask` must be between 1 and 1000

### ModelConfig
- Cloud configs must have non-empty `baseUrl` and `apiKey`
- `temperature` must be in range 0.0-2.0
- `topP` must be in range 0.0-1.0

---

## Storage Schema (Room/SQLite)

```sql
-- Tasks table
CREATE TABLE tasks (
    id TEXT PRIMARY KEY,
    description TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at INTEGER NOT NULL,
    started_at INTEGER,
    completed_at INTEGER,
    result TEXT,
    error_message TEXT,
    step_count INTEGER NOT NULL DEFAULT 0,
    max_steps INTEGER NOT NULL DEFAULT 100,
    inference_mode TEXT NOT NULL DEFAULT 'AUTO'
);

-- Actions table
CREATE TABLE actions (
    id TEXT PRIMARY KEY,
    task_id TEXT NOT NULL,
    type TEXT NOT NULL,
    parameters TEXT NOT NULL, -- JSON
    timestamp INTEGER NOT NULL,
    success INTEGER NOT NULL,
    thinking TEXT,
    screen_state_id TEXT,
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- App mappings table
CREATE TABLE app_mappings (
    package_name TEXT PRIMARY KEY,
    app_name TEXT NOT NULL,
    aliases TEXT NOT NULL, -- JSON array
    category TEXT NOT NULL,
    icon_path TEXT,
    ui_patterns TEXT, -- JSON
    prompts TEXT NOT NULL, -- JSON
    is_user_defined INTEGER NOT NULL DEFAULT 0
);

-- User preferences (single row)
CREATE TABLE user_preferences (
    id TEXT PRIMARY KEY DEFAULT 'default',
    language TEXT NOT NULL DEFAULT 'zh',
    default_inference_mode TEXT NOT NULL DEFAULT 'AUTO',
    confirm_sensitive_actions INTEGER NOT NULL DEFAULT 1,
    enabled_apps TEXT, -- JSON array, null = all
    cloud_api_enabled INTEGER NOT NULL DEFAULT 1,
    max_steps_per_task INTEGER NOT NULL DEFAULT 100,
    default_payment_method TEXT,
    default_delivery_address TEXT,
    on_device_model_preference TEXT NOT NULL DEFAULT 'qwen2.5-vl-3b'
);

-- Model configs table
CREATE TABLE model_configs (
    id TEXT PRIMARY KEY,
    type TEXT NOT NULL,
    model_name TEXT NOT NULL,
    base_url TEXT,
    api_key_encrypted TEXT, -- Encrypted via Android Keystore
    max_tokens INTEGER NOT NULL DEFAULT 3000,
    temperature REAL NOT NULL DEFAULT 0.0,
    top_p REAL NOT NULL DEFAULT 0.85,
    frequency_penalty REAL NOT NULL DEFAULT 0.2,
    is_active INTEGER NOT NULL DEFAULT 1
);

-- Indexes
CREATE INDEX idx_actions_task_id ON actions(task_id);
CREATE INDEX idx_tasks_status ON tasks(status);
CREATE INDEX idx_tasks_created_at ON tasks(created_at DESC);
```
