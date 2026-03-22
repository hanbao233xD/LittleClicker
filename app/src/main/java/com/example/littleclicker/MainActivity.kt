package com.example.littleclicker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.littleclicker.autoclick.AutoClickCoordinator
import com.example.littleclicker.ui.AboutScreen
import com.example.littleclicker.ui.AutoClickScreen
import com.example.littleclicker.ui.ScriptManageScreen
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MiuixTheme(colors = colors) {
                AppRoot()
            }
        }
    }
}

private enum class MainTab(val route: String, val title: String) {
    AUTO_CLICK("auto_click", "自动点击"),
    SCRIPTS("scripts", "脚本管理"),
    ABOUT("about", "关于"),
}

@Composable
private fun AppRoot() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val tabs = listOf(MainTab.AUTO_CLICK, MainTab.SCRIPTS, MainTab.ABOUT)
    val navBackStackEntry = navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry.value?.destination

    LaunchedEffect(Unit) {
        AutoClickCoordinator.initialize(context)
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    val selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true
                    val icon = when (tab) {
                        MainTab.AUTO_CLICK -> Icons.Default.Home
                        MainTab.SCRIPTS -> Icons.AutoMirrored.Filled.List
                        MainTab.ABOUT -> Icons.Default.Info
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = icon,
                        label = tab.title,
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = MainTab.AUTO_CLICK.route,
            modifier = Modifier
        ) {
            composable(MainTab.AUTO_CLICK.route) {
                AutoClickScreen(innerPadding)
            }
            composable(MainTab.SCRIPTS.route) {
                ScriptManageScreen(innerPadding)
            }
            composable(MainTab.ABOUT.route) {
                AboutScreen(innerPadding)
            }
        }
    }
}
