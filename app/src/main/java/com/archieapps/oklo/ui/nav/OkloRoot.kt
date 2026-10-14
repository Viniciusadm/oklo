package com.archieapps.oklo.ui.nav

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.archieapps.oklo.R
import com.archieapps.oklo.ui.components.Hairline
import com.archieapps.oklo.ui.theme.Spacing
import com.archieapps.oklo.ui.theme.okloPalette
import com.archieapps.oklo.ui.screen.ai.AiCreateScreen
import com.archieapps.oklo.ui.screen.ai.AiReviewScreen
import com.archieapps.oklo.ui.screen.connect.ConnectScreen
import com.archieapps.oklo.ui.screen.deck.DeckScreen
import com.archieapps.oklo.ui.screen.editor.CardEditorScreen
import com.archieapps.oklo.ui.screen.home.HomeScreen
import com.archieapps.oklo.ui.screen.library.LibraryScreen
import com.archieapps.oklo.ui.screen.settings.SettingsScreen
import com.archieapps.oklo.ui.screen.settings.SettingsSection
import com.archieapps.oklo.ui.screen.settings.SettingsSectionScreen
import com.archieapps.oklo.ui.screen.stats.StatsScreen
import com.archieapps.oklo.ui.screen.study.StudyScreen

object Routes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val STATS = "stats?deckId={deckId}"
    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings/{section}"
    const val DECK = "deck/{deckId}"
    const val EDITOR = "editor?deckId={deckId}&cardId={cardId}"
    const val AI_CREATE = "ai/create?deckId={deckId}"
    const val AI_REVIEW = "ai/review"
    const val STUDY = "study?deckId={deckId}"
    const val CONNECT = "connect"

    fun stats(deckId: String? = null) = if (deckId == null) "stats" else "stats?deckId=$deckId"
    fun settingsSection(section: SettingsSection) = "settings/${section.key}"
    fun deck(id: String) = "deck/$id"
    fun newCard(deckId: String?) = if (deckId == null) "editor" else "editor?deckId=$deckId"
    fun editCard(cardId: String) = "editor?cardId=$cardId"
    fun aiCreate(deckId: String? = null) = if (deckId == null) "ai/create" else "ai/create?deckId=$deckId"
    fun study(deckId: String? = null) = if (deckId == null) "study" else "study?deckId=$deckId"
}

private data class TabItem(
    val route: String,
    val target: String,
    val label: String,
    @param:DrawableRes val icon: Int,
)

private val Tabs = listOf(
    TabItem(Routes.HOME, Routes.HOME, "Início", icon = R.drawable.ic_home),
    TabItem(Routes.LIBRARY, Routes.LIBRARY, "Biblioteca", icon = R.drawable.ic_library),
    TabItem(Routes.STATS, "stats", "Estatísticas", icon = R.drawable.ic_bar_chart),
)

class AppNavigator(private val controller: NavHostController) {
    fun back() {
        controller.popBackStack()
    }

    fun to(route: String) {
        controller.navigate(route) { launchSingleTop = true }
    }

    fun replace(route: String) {
        controller.navigate(route) {
            controller.currentBackStackEntry?.destination?.route?.let { current ->
                popUpTo(current) { inclusive = true }
            }
            launchSingleTop = true
        }
    }

    fun tab(route: String) {
        controller.navigate(route) {
            popUpTo(controller.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun home() {
        controller.navigate(Routes.HOME) {
            popUpTo(controller.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }
}

@Composable
fun OkloRoot() {
    val controller = rememberNavController()
    val navigator = AppNavigator(controller)
    val entry by controller.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val showBar = Tabs.any { it.route == currentRoute } && entry?.arguments?.getString("deckId") == null

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                OkloBottomBar(currentRoute = currentRoute, onSelect = { navigator.tab(it.target) })
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
            NavHost(
                navController = controller,
                startDestination = Routes.HOME,
                enterTransition = { fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 40 } },
                exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(120)) + slideOutVertically(tween(180)) { it / 40 } },
            ) {
                composable(Routes.HOME) { HomeScreen(navigator) }
                composable(Routes.LIBRARY) { LibraryScreen(navigator) }
                composable(
                    Routes.STATS,
                    arguments = listOf(navArgument("deckId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStack ->
                    StatsScreen(navigator, backStack.arguments?.getString("deckId"))
                }
                composable(Routes.SETTINGS) { SettingsScreen(navigator) }
                composable(
                    Routes.SETTINGS_SECTION,
                    arguments = listOf(navArgument("section") { type = NavType.StringType }),
                ) { backStack ->
                    SettingsSectionScreen(navigator, SettingsSection.of(backStack.arguments?.getString("section")))
                }
                composable(
                    Routes.DECK,
                    arguments = listOf(navArgument("deckId") { type = NavType.StringType }),
                ) { backStack ->
                    DeckScreen(navigator, backStack.arguments?.getString("deckId").orEmpty())
                }
                composable(
                    Routes.EDITOR,
                    arguments = listOf(
                        navArgument("deckId") { type = NavType.StringType; nullable = true; defaultValue = null },
                        navArgument("cardId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    ),
                ) { backStack ->
                    CardEditorScreen(
                        navigator,
                        deckId = backStack.arguments?.getString("deckId"),
                        cardId = backStack.arguments?.getString("cardId"),
                    )
                }
                composable(
                    Routes.AI_CREATE,
                    arguments = listOf(navArgument("deckId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStack ->
                    AiCreateScreen(navigator, backStack.arguments?.getString("deckId"))
                }
                composable(Routes.AI_REVIEW) { AiReviewScreen(navigator) }
                composable(
                    Routes.STUDY,
                    arguments = listOf(navArgument("deckId") { type = NavType.StringType; nullable = true; defaultValue = null }),
                ) { backStack ->
                    StudyScreen(navigator, backStack.arguments?.getString("deckId"))
                }
                composable(Routes.CONNECT) { ConnectScreen(navigator) }
            }
        }
    }
}

@Composable
private fun OkloBottomBar(currentRoute: String?, onSelect: (TabItem) -> Unit) {
    val palette = okloPalette
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Hairline()
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp)
                .selectableGroup(),
        ) {
            Tabs.forEach { tab ->
                val selected = currentRoute == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(tab.icon),
                        contentDescription = null,
                        tint = if (selected) palette.ink else palette.faint,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) palette.ink else palette.faint,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                    )
                }
            }
        }
    }
}
