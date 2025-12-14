package com.openautoglm.agent.actions.handlers

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.openautoglm.agent.actions.BaseActionHandler
import com.openautoglm.agent.actions.ExecutionResult
import com.openautoglm.agent.data.AgentRepository
import com.openautoglm.agent.data.entities.Action
import com.openautoglm.agent.data.entities.ActionType
import com.openautoglm.agent.knowledge.AppKnowledgeBase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Handler for launching applications.
 *
 * Opens apps by package name or app name using the AppKnowledgeBase
 * for name resolution. Faster than navigating from home screen.
 *
 * Supports multiple input formats:
 * - Package name: "com.android.settings"
 * - App name: "Settings", "WeChat", "微信"
 * - Aliases: "wechat", "Weixin"
 */
class LaunchActionHandler(
    private val context: Context,
    private val appKnowledgeBase: AppKnowledgeBase,
    private val repository: AgentRepository
) : BaseActionHandler() {

    companion object {
        private const val POST_LAUNCH_DELAY_MS = 1000L
    }

    override fun canHandle(action: Action): Boolean {
        return action.type == ActionType.LAUNCH
    }

    override fun getDescription(): String {
        return "Launches applications by package name or app name"
    }

    override suspend fun executeInternal(action: Action): ExecutionResult {
        // Extract app identifier
        val appIdentifier = extractAppIdentifier(action)
            ?: return ExecutionResult.Failure(
                error = "No app specified in action parameters",
                errorCode = "INVALID_PARAMETERS",
                isRetryable = false
            )

        // Resolve to package name
        val packageName = resolvePackageName(appIdentifier)
            ?: return ExecutionResult.Failure(
                error = "Could not find app: $appIdentifier",
                errorCode = "APP_NOT_FOUND",
                isRetryable = false
            )

        // Check if app is installed
        if (!isAppInstalled(packageName)) {
            return ExecutionResult.RequiresIntervention(
                message = "App not installed: $appIdentifier ($packageName)",
                reason = com.openautoglm.agent.actions.InterventionReason.APP_NOT_INSTALLED
            )
        }

        // Launch the app
        val success = launchApp(packageName)

        if (!success) {
            return ExecutionResult.Failure(
                error = "Failed to launch app: $packageName",
                errorCode = "LAUNCH_FAILED",
                isRetryable = true
            )
        }

        // Wait for app to start
        delay(POST_LAUNCH_DELAY_MS)

        return ExecutionResult.Success(
            message = "Launched app: $appIdentifier",
            data = mapOf(
                "packageName" to packageName,
                "appIdentifier" to appIdentifier
            )
        )
    }

    /**
     * Extracts app identifier from action parameters.
     *
     * Supports parameter names: "app", "package", "name"
     */
    private fun extractAppIdentifier(action: Action): String? {
        val params = action.parameters

        // Try "app" parameter (most common)
        (params["app"] as? String)?.let { return it }

        // Try "package" parameter
        (params["package"] as? String)?.let { return it }

        // Try "name" parameter
        (params["name"] as? String)?.let { return it }

        return null
    }

    /**
     * Resolves app name/alias to package name.
     */
    private suspend fun resolvePackageName(identifier: String): String? {
        // If it's already a package name (contains dots), use it directly
        if (identifier.contains('.')) {
            return identifier
        }

        // Search app knowledge base
        val matchingApps = appKnowledgeBase.searchApps(identifier)
        if (matchingApps.isNotEmpty()) {
            return matchingApps.first().packageName
        }

        // Try exact name match (case-insensitive)
        val normalizedId = identifier.lowercase()
        val allApps = try {
            repository.getAllAppMappings().first()
        } catch (e: Exception) {
            emptyList()
        }

        for (app in allApps) {
            if (app.appName.lowercase() == normalizedId ||
                app.aliases.any { it.lowercase() == normalizedId }) {
                return app.packageName
            }
        }

        return null
    }

    /**
     * Checks if an app is installed.
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Launches an app by package name.
     */
    private fun launchApp(packageName: String): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return false

            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
