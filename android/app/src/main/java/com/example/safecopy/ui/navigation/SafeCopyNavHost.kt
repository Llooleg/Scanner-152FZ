package com.example.safecopy.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.example.safecopy.R
import com.example.safecopy.ui.copyflow.ExportScreen
import com.example.safecopy.ui.copyflow.MaskScreen
import com.example.safecopy.ui.copyflow.ScanScreen
import com.example.safecopy.ui.copyflow.WatermarkScreen
import com.example.safecopy.ui.documents.DocumentsRouteScreen
import com.example.safecopy.ui.journal.JournalScreen
import com.example.safecopy.ui.launch.LaunchRouteScreen
import com.example.safecopy.ui.lock.LockRouteScreen
import com.example.safecopy.ui.settings.SettingsRouteScreen
import kotlin.reflect.KClass

private enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    Documents(DocumentsRoute, DocumentsRoute::class, R.string.tab_documents, Icons.Outlined.Description),
    Journal(JournalRoute, JournalRoute::class, R.string.tab_journal, Icons.Outlined.History),
    Settings(SettingsRoute, SettingsRoute::class, R.string.tab_settings, Icons.Outlined.Settings),
}

@Composable
fun SafeCopyNavHost(
    isUnlocked: Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelTab.entries.any { currentDestination.isOn(it) }

    // Любой экран, кроме заставки и входа, доступен только в разблокированной сессии.
    // Срабатывает после восстановления процесса и при автоблокировке.
    val isPublicDestination = currentDestination == null ||
        currentDestination.hasRoute(LaunchRoute::class) ||
        currentDestination.hasRoute(LockRoute::class)
    LaunchedEffect(isUnlocked, isPublicDestination) {
        if (!isUnlocked && !isPublicDestination) {
            navController.navigate(LockRoute) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }
    }

    // Внешний Scaffold отвечает только за нижнюю навигацию. Отступ под неё «потребляется»,
    // чтобы Scaffold'ы экранов не добавляли инсет системной панели второй раз.
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(tab),
                            onClick = { navController.navigateToTab(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LaunchRoute,
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding),
        ) {
            composable<LaunchRoute> {
                LaunchRouteScreen(
                    onReady = {
                        navController.navigate(LockRoute) {
                            popUpTo(LaunchRoute) { inclusive = true }
                        }
                    },
                )
            }
            composable<LockRoute> {
                LockRouteScreen(
                    onUnlocked = {
                        navController.navigate(DocumentsRoute) {
                            popUpTo(LockRoute) { inclusive = true }
                        }
                    },
                )
            }

            composable<DocumentsRoute> {
                DocumentsRouteScreen(onScanClick = { navController.navigate(CopyFlowRoute) })
            }
            composable<JournalRoute> { JournalScreen() }
            composable<SettingsRoute> { SettingsRouteScreen() }

            navigation<CopyFlowRoute>(startDestination = ScanRoute) {
                composable<ScanRoute> {
                    ScanScreen(
                        onBack = { navController.popBackStack() },
                        onNext = { navController.navigate(MaskRoute) },
                    )
                }
                composable<MaskRoute> {
                    MaskScreen(
                        onBack = { navController.popBackStack() },
                        onNext = { navController.navigate(WatermarkRoute) },
                    )
                }
                composable<WatermarkRoute> {
                    WatermarkScreen(
                        onBack = { navController.popBackStack() },
                        onNext = { navController.navigate(ExportRoute) },
                    )
                }
                composable<ExportRoute> {
                    ExportScreen(
                        onBack = { navController.popBackStack() },
                        // Весь конвейер убирается из стека: «Назад» не должен возвращать к готовой копии.
                        onDone = { navController.popBackStack(CopyFlowRoute, inclusive = true) },
                    )
                }
            }
        }
    }
}

private fun NavDestination?.isOn(tab: TopLevelTab): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true

private fun NavHostController.navigateToTab(tab: TopLevelTab) {
    navigate(tab.route) {
        // Корень стека после входа — «Документы» (стартовые Launch и Lock из стека уже удалены).
        popUpTo(DocumentsRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
