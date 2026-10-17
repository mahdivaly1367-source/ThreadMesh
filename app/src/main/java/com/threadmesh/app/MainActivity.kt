package com.threadmesh.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.threadmesh.app.data.local.AppDatabase
import com.threadmesh.app.data.repository.ThreadMeshRepository
import com.threadmesh.app.ui.screens.*
import com.threadmesh.app.ui.theme.ThreadMeshTheme
import com.threadmesh.app.ui.viewmodel.ThreadMeshViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Threads : Screen("threads", "گفتگوها", Icons.Default.Chat)
    data object Recent : Screen("recent", "تغییرات جدید", Icons.Default.Update)
    data object Status : Screen("status", "ارتباط", Icons.Default.WifiOff)
    data object Settings : Screen("settings", "تنظیمات", Icons.Default.Settings)
}

import com.threadmesh.app.transport.GoogleNearbyTransport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = ThreadMeshRepository(database.threadMeshDao(), database.outboxDao())
        val transport = try {
            GoogleNearbyTransport(applicationContext)
        } catch (_: Exception) {
            null
        }

        val viewModel = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ThreadMeshViewModel(repository, transport) as T
            }
        })[ThreadMeshViewModel::class.java]

        setContent {
            ThreadMeshTheme {
                MainAppNav(viewModel)
            }
        }
    }
}

@Composable
fun MainAppNav(viewModel: ThreadMeshViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomNavItems = listOf(
        Screen.Threads,
        Screen.Recent,
        Screen.Status,
        Screen.Settings
    )

    val showBottomBar = bottomNavItems.any { it.route == currentRoute }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        NavigationBarItem(
                            icon = { Icon(screen.icon, contentDescription = screen.title) },
                            label = { Text(screen.title) },
                            selected = currentRoute == screen.route,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Threads.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Threads.route) {
                ThreadListScreen(
                    viewModel = viewModel,
                    onThreadClick = { threadId ->
                        navController.navigate("thread_detail/$threadId")
                    },
                    onCreateThreadClick = {
                        navController.navigate("create_topic")
                    }
                )
            }

            composable(Screen.Recent.route) {
                RecentChangesScreen(
                    viewModel = viewModel,
                    onThreadClick = { threadId ->
                        navController.navigate("thread_detail/$threadId")
                    }
                )
            }

            composable(Screen.Status.route) {
                ConnectionStatusScreen(viewModel = viewModel)
            }

            composable(Screen.Settings.route) {
                SettingsScreen(viewModel = viewModel)
            }

            composable(
                route = "thread_detail/{threadId}",
                arguments = listOf(navArgument("threadId") { type = NavType.StringType })
            ) { backStackEntry ->
                val threadId = backStackEntry.arguments?.getString("threadId") ?: ""
                ThreadDetailScreen(
                    threadId = threadId,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onReplyClick = { targetEventId, preview ->
                        val encodedPreview = URLEncoder.encode(preview, StandardCharsets.UTF_8.toString())
                        navController.navigate("reply/$targetEventId/$encodedPreview")
                    },
                    onStanceChangeClick = { targetEventId, targetPreview ->
                        val encodedPreview = URLEncoder.encode(targetPreview, StandardCharsets.UTF_8.toString())
                        navController.navigate("stance_change/$targetEventId/$encodedPreview")
                    }
                )
            }

            composable("create_topic") {
                CreatePostScreen(
                    parentEventId = null,
                    parentPreview = null,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }

            composable(
                route = "reply/{parentId}/{preview}",
                arguments = listOf(
                    navArgument("parentId") { type = NavType.StringType },
                    navArgument("preview") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val parentId = backStackEntry.arguments?.getString("parentId") ?: ""
                val rawPreview = backStackEntry.arguments?.getString("preview") ?: ""
                val preview = runCatching { URLDecoder.decode(rawPreview, StandardCharsets.UTF_8.toString()) }.getOrDefault("")
                CreatePostScreen(
                    parentEventId = parentId,
                    parentPreview = preview,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }

            composable(
                route = "stance_change/{targetId}/{preview}",
                arguments = listOf(
                    navArgument("targetId") { type = NavType.StringType },
                    navArgument("preview") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val targetId = backStackEntry.arguments?.getString("targetId") ?: ""
                val rawPreview = backStackEntry.arguments?.getString("preview") ?: ""
                val preview = runCatching { URLDecoder.decode(rawPreview, StandardCharsets.UTF_8.toString()) }.getOrDefault("")
                StanceChangeScreen(
                    targetEventId = targetId,
                    targetTextPreview = preview,
                    viewModel = viewModel,
                    onBackClick = { navController.popBackStack() },
                    onSuccess = { navController.popBackStack() }
                )
            }
        }
    }
}
