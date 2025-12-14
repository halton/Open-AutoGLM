package com.openautoglm.agent.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import com.openautoglm.agent.ui.screens.HistoryScreen
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    NavHost(
        navController = navController,
        startDestination = Screen.Tasks.route,
        modifier = modifier
    ) {
        composable(Screen.Tasks.route) {
            TaskScreen()
        }
        composable(Screen.History.route) {
            HistoryScreen()
        }
        composable(Screen.Settings.route) {
            SettingsScreen()
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
        labelRes = android.R.string.ok  // TODO: Add proper string resources
    ),
    History(
        route = "history",
        iconRes = android.R.drawable.ic_menu_recent_history,
        labelRes = android.R.string.ok  // TODO: Add proper string resources
    ),
    Settings(
        route = "settings",
        iconRes = android.R.drawable.ic_menu_preferences,
        labelRes = android.R.string.ok  // TODO: Add proper string resources
    )
}
