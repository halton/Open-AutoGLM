package com.openautoglm.agent.agent

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Handles app authentication state and re-authentication flows.
 *
 * Features:
 * - Tracks authentication state per app
 * - Detects login screens using VLM
 * - Requests user intervention for re-authentication
 * - Caches authentication status
 *
 * @param context Application context
 */
class AuthenticationHandler(
    private val context: Context
) {
    companion object {
        private const val TAG = "AuthenticationHandler"
        private const val PREFS_NAME = "auth_state"
        private const val KEY_LAST_AUTH_TIME_PREFIX = "last_auth_"
        private const val KEY_AUTH_FAILED_PREFIX = "auth_failed_"

        // Authentication expiry: 24 hours
        private const val AUTH_EXPIRY_MS = 24 * 60 * 60 * 1000L

        // Common login screen indicators
        private val LOGIN_INDICATORS_ZH = listOf(
            "登录", "请登录", "登录账号", "手机登录", "验证码登录",
            "账号密码", "请输入密码", "忘记密码", "快捷登录",
            "微信登录", "支付宝登录", "扫码登录"
        )

        private val LOGIN_INDICATORS_EN = listOf(
            "login", "sign in", "log in", "enter password", "forgot password",
            "sign in with", "continue with", "verification code"
        )
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Apps that need re-authentication
    private val _pendingReauthApps = MutableStateFlow<Set<String>>(emptySet())
    val pendingReauthApps: StateFlow<Set<String>> = _pendingReauthApps.asStateFlow()

    // Current authentication requests
    private val _authenticationRequests = MutableStateFlow<List<AuthRequest>>(emptyList())
    val authenticationRequests: StateFlow<List<AuthRequest>> = _authenticationRequests.asStateFlow()

    /**
     * Checks if an app needs re-authentication.
     *
     * @param appPackage The app package name
     * @return True if re-authentication is needed
     */
    fun needsReauthentication(appPackage: String): Boolean {
        // Check if explicitly marked as failed
        if (prefs.getBoolean("$KEY_AUTH_FAILED_PREFIX$appPackage", false)) {
            Log.d(TAG, "$appPackage marked as auth failed")
            return true
        }

        // Check if authentication has expired
        val lastAuthTime = prefs.getLong("$KEY_LAST_AUTH_TIME_PREFIX$appPackage", 0L)
        if (lastAuthTime > 0) {
            val elapsed = System.currentTimeMillis() - lastAuthTime
            if (elapsed > AUTH_EXPIRY_MS) {
                Log.d(TAG, "$appPackage auth expired (${elapsed / 1000 / 60} minutes old)")
                return true
            }
        }

        return _pendingReauthApps.value.contains(appPackage)
    }

    /**
     * Requests re-authentication for an app.
     *
     * @param appPackage The app package name
     */
    fun requestReauthentication(appPackage: String) {
        Log.i(TAG, "Requesting re-authentication for $appPackage")

        _pendingReauthApps.value = _pendingReauthApps.value + appPackage

        val request = AuthRequest(
            appPackage = appPackage,
            appName = getAppDisplayName(appPackage),
            timestamp = System.currentTimeMillis(),
            reason = AuthReason.SESSION_EXPIRED
        )

        _authenticationRequests.value = _authenticationRequests.value + request
    }

    /**
     * Marks an app as authenticated.
     *
     * @param appPackage The app package name
     */
    fun markAuthenticated(appPackage: String) {
        Log.i(TAG, "Marking $appPackage as authenticated")

        prefs.edit()
            .putLong("$KEY_LAST_AUTH_TIME_PREFIX$appPackage", System.currentTimeMillis())
            .putBoolean("$KEY_AUTH_FAILED_PREFIX$appPackage", false)
            .apply()

        _pendingReauthApps.value = _pendingReauthApps.value - appPackage
        _authenticationRequests.value = _authenticationRequests.value.filter {
            it.appPackage != appPackage
        }
    }

    /**
     * Marks an app authentication as failed.
     *
     * @param appPackage The app package name
     * @param reason The failure reason
     */
    fun markAuthFailed(appPackage: String, reason: AuthReason) {
        Log.w(TAG, "Marking $appPackage auth as failed: $reason")

        prefs.edit()
            .putBoolean("$KEY_AUTH_FAILED_PREFIX$appPackage", true)
            .apply()

        _pendingReauthApps.value = _pendingReauthApps.value + appPackage
    }

    /**
     * Detects if a screen is a login screen based on VLM analysis.
     *
     * @param screenContent Extracted text from the screen
     * @return True if this appears to be a login screen
     */
    fun isLoginScreen(screenContent: String): Boolean {
        val contentLower = screenContent.lowercase()

        // Check Chinese indicators
        for (indicator in LOGIN_INDICATORS_ZH) {
            if (contentLower.contains(indicator)) {
                Log.d(TAG, "Login screen detected (ZH): $indicator")
                return true
            }
        }

        // Check English indicators
        for (indicator in LOGIN_INDICATORS_EN) {
            if (contentLower.contains(indicator)) {
                Log.d(TAG, "Login screen detected (EN): $indicator")
                return true
            }
        }

        return false
    }

    /**
     * Detects if a screen shows authentication error.
     *
     * @param screenContent Extracted text from the screen
     * @return True if this appears to be an auth error
     */
    fun isAuthError(screenContent: String): Boolean {
        val errorIndicators = listOf(
            // Chinese
            "登录失效", "请重新登录", "会话过期", "身份验证失败",
            "账号异常", "需要验证", "安全验证",
            // English
            "session expired", "please log in again", "authentication failed",
            "account verification", "security check"
        )

        val contentLower = screenContent.lowercase()
        for (indicator in errorIndicators) {
            if (contentLower.contains(indicator)) {
                Log.d(TAG, "Auth error detected: $indicator")
                return true
            }
        }

        return false
    }

    /**
     * Gets pending authentication requests.
     */
    fun getPendingRequests(): List<AuthRequest> {
        return _authenticationRequests.value
    }

    /**
     * Dismisses an authentication request.
     *
     * @param appPackage The app package to dismiss
     */
    fun dismissRequest(appPackage: String) {
        _authenticationRequests.value = _authenticationRequests.value.filter {
            it.appPackage != appPackage
        }
    }

    /**
     * Clears all authentication state.
     */
    fun clearAll() {
        Log.i(TAG, "Clearing all authentication state")
        prefs.edit().clear().apply()
        _pendingReauthApps.value = emptySet()
        _authenticationRequests.value = emptyList()
    }

    /**
     * Gets display name for an app package.
     */
    private fun getAppDisplayName(appPackage: String): String {
        return LogisticsTracker.SUPPORTED_APPS[appPackage] ?: try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(appPackage, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            appPackage
        }
    }

    /**
     * Authentication request data.
     */
    data class AuthRequest(
        val appPackage: String,
        val appName: String,
        val timestamp: Long,
        val reason: AuthReason
    )

    /**
     * Reasons for authentication request.
     */
    enum class AuthReason {
        /** Session has expired */
        SESSION_EXPIRED,

        /** Login screen detected during automation */
        LOGIN_DETECTED,

        /** Authentication error from app */
        AUTH_ERROR,

        /** User explicitly logged out */
        USER_LOGOUT,

        /** First time access to app */
        FIRST_ACCESS
    }
}
