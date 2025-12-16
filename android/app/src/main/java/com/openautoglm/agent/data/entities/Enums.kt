package com.openautoglm.agent.data.entities

/**
 * Status of a task in its lifecycle.
 */
enum class TaskStatus {
    /** Task created but not started */
    PENDING,
    /** Task is currently executing */
    RUNNING,
    /** Task execution paused by user */
    PAUSED,
    /** Task completed successfully */
    COMPLETED,
    /** Task failed due to error */
    FAILED,
    /** Task cancelled by user */
    CANCELLED
}

/**
 * Mode for model inference - determines where computation runs.
 */
enum class InferenceMode {
    /** Run inference on-device using MLC-LLM or llama.cpp */
    ON_DEVICE,
    /** Run inference in cloud using AutoGLM or Qwen APIs */
    CLOUD,
    /** Automatically route based on task complexity, battery, and network */
    AUTO
}

/**
 * Type of action that can be performed by the agent.
 */
enum class ActionType {
    /** Single tap at coordinates */
    TAP,
    /** Double tap at coordinates */
    DOUBLE_TAP,
    /** Long press at coordinates */
    LONG_PRESS,
    /** Swipe from start to end coordinates */
    SWIPE,
    /** Text input */
    TYPE,
    /** System back button */
    BACK,
    /** System home button */
    HOME,
    /** Launch app by name */
    LAUNCH,
    /** Wait for specified duration */
    WAIT,
    /** Request user intervention */
    TAKE_OVER,
    /** Press Enter/IME action (submit search, send message, etc.) */
    ENTER,
    /** Complete task with message */
    FINISH
}

/**
 * Category of app for knowledge base organization.
 */
enum class AppCategory {
    /** Messaging and social apps (WeChat, WhatsApp) */
    SOCIAL,
    /** Shopping apps (Taobao, JD, Amazon) */
    ECOMMERCE,
    /** Travel and booking apps (12306, Ctrip) */
    TRAVEL,
    /** Food delivery apps (Meituan, Eleme) */
    FOOD,
    /** Video, music, games */
    ENTERTAINMENT,
    /** Work and utility apps */
    PRODUCTIVITY,
    /** Banking and payment apps */
    FINANCE,
    /** System apps and settings */
    SYSTEM,
    /** Uncategorized apps */
    OTHER
}

/**
 * Type of inference provider.
 */
enum class InferenceType {
    /** On-device inference via MLC-LLM or llama.cpp */
    ON_DEVICE,
    /** Cloud inference via OpenAI-compatible APIs */
    CLOUD
}
