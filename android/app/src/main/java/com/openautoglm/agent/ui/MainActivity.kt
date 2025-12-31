package com.openautoglm.agent.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openautoglm.agent.R
import com.openautoglm.agent.accessibility.ScreenCaptureManager
import com.openautoglm.agent.agent.TaskRedoHandler
import com.openautoglm.agent.ui.screens.HistoryScreen
import com.openautoglm.agent.ui.screens.ModelDownloadScreen
import com.openautoglm.agent.ui.screens.SettingsScreen
import com.openautoglm.agent.ui.screens.TaskScreen
import com.openautoglm.agent.ui.theme.AutoGLMTheme

/**
 * Main activity for the AutoGLM Android Agent application.
 *
 * Provides a bottom navigation bar with three main sections:
 * - Tasks: Create and manage automation tasks
 * - History: View completed task history
 * - Settings: Configure API keys, models, and preferences
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_OVERLAY_PERMISSION = 1001

        @Volatile
        private var screenCapturePermissionGranted = false

        @Volatile
        private var overlayPermissionGranted = false

        fun isScreenCapturePermissionGranted() = screenCapturePermissionGranted
        fun isOverlayPermissionGranted() = overlayPermissionGranted
    }

    private val screenCaptureManager by lazy {
        ScreenCaptureManager.getInstance(this)
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val success = screenCaptureManager.startCapture(result.resultCode, result.data!!)
            if (success) {
                screenCapturePermissionGranted = true
                Toast.makeText(this, "Screen capture permission granted", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Screen capture started successfully")
            } else {
                screenCapturePermissionGranted = false
                Toast.makeText(this, "Failed to start screen capture", Toast.LENGTH_LONG).show()
                Log.e(TAG, "Failed to start screen capture")
            }
        } else {
            screenCapturePermissionGranted = false
            Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_LONG).show()
            Log.w(TAG, "Screen capture permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check and request overlay permission first
        checkOverlayPermission()

        // Request screen capture permission on startup
        requestScreenCapturePermission()

        setContent {
            AutoGLMTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    private fun checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                overlayPermissionGranted = true
                Log.i(TAG, "Overlay permission already granted")
            } else {
                overlayPermissionGranted = false
                Log.w(TAG, "Overlay permission not granted, requesting...")
                requestOverlayPermission()
            }
        } else {
            // Pre-M devices don't need runtime permission
            overlayPermissionGranted = true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            @Suppress("DEPRECATION")
            startActivityForResult(intent, REQUEST_OVERLAY_PERMISSION)
            Toast.makeText(
                this,
                "Please enable overlay permission for floating status display",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                overlayPermissionGranted = Settings.canDrawOverlays(this)
                if (overlayPermissionGranted) {
                    Log.i(TAG, "Overlay permission granted")
                    Toast.makeText(this, "Overlay permission granted", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Overlay permission denied")
                    Toast.makeText(this, "Overlay permission denied - floating status will not work", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun requestScreenCapturePermission() {
        // Start foreground service required for MediaProjection on Android 14+
        val serviceIntent = Intent(this, com.openautoglm.agent.service.ScreenCaptureService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(captureIntent)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (screenCapturePermissionGranted) {
            screenCaptureManager.stopCapture()
            // Stop the foreground service
            stopService(Intent(this, com.openautoglm.agent.service.ScreenCaptureService::class.java))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableStateOf(Screen.Tasks) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AutoGLM Agent") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        NavigationHost(
            navController = navController,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

@Composable
fun BottomNavigationBar(
    navController: NavHostController,
    selectedTab: Screen,
    onTabSelected: (Screen) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar {
        Screen.values().forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(id = screen.iconRes),
                        contentDescription = screen.route
                    )
                },
                label = { Text(stringResource(id = screen.labelRes)) },
                selected = currentRoute == screen.route,
                onClick = {
                    if (currentRoute != screen.route) {
                        onTabSelected(screen)
                        navController.navigate(screen.route) {
                            // Pop up to the start destination to avoid building up a large stack
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            // Avoid multiple copies of the same destination
                            launchSingleTop = true
                            // Restore state when reselecting a previously selected item
                            restoreState = true
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun NavigationHost(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    // State to hold pending redo task description
    var pendingRedoTask by remember { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Screen.Tasks.route,
        modifier = modifier
    ) {
        composable(Screen.Tasks.route) {
            // Pass and consume pending redo task
            val redoTask = pendingRedoTask
            pendingRedoTask = null
            TaskScreen(initialTaskDescription = redoTask)
        }
        composable(Screen.History.route) {
            HistoryScreen(
                onRedoTask = { redoInfo ->
                    // Store the task description and navigate to Tasks screen
                    pendingRedoTask = redoInfo.description
                    navController.navigate(Screen.Tasks.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = false // Don't restore state to ensure fresh TaskScreen
                    }
                }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateToModelDownload = {
                    navController.navigate("model_download")
                }
            )
        }
        composable("model_download") {
            ModelDownloadScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

/**
 * Navigation destinations for the app.
 */
enum class Screen(
    val route: String,
    val iconRes: Int,
    val labelRes: Int
) {
    Tasks(
        route = "tasks",
        iconRes = android.R.drawable.ic_menu_add,
        labelRes = R.string.tab_tasks
    ),
    History(
        route = "history",
        iconRes = android.R.drawable.ic_menu_recent_history,
        labelRes = R.string.tab_history
    ),
    Settings(
        route = "settings",
        iconRes = android.R.drawable.ic_menu_preferences,
        labelRes = R.string.tab_settings
    )
}
