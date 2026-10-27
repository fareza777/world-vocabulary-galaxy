package com.verbilume.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavType
import com.verbilume.app.data.AppGraph
import com.verbilume.app.data.AppSettings
import com.verbilume.app.data.PracticeMode
import com.verbilume.app.ui.theme.LocalVerbiColors
import com.verbilume.app.ui.Routes
import com.verbilume.app.ui.theme.ManropeFamily

@Composable
fun MainShell(
    graph: AppGraph,
    settings: AppSettings,
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isMain = route == null || route in Routes.mainDestinations
    val isGalaxy = route == Routes.GALAXY

    LaunchedEffect(pendingRoute) {
        if (!pendingRoute.isNullOrBlank()) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }

    fun goTab(target: String) {
        navController.navigate(target) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(140)) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    graph = graph,
                    settings = settings,
                    onSearch = { navController.navigate(Routes.SEARCH) },
                    onWord = { navController.navigate(Routes.word(it)) },
                    onAtlas = { goTab(Routes.ATLAS) },
                    onGalaxy = { goTab(Routes.GALAXY) },
                    onPractice = { goTab(Routes.PRACTICE) },
                    onLearn = { navController.navigate(Routes.LEARN) },
                    onDaily = { navController.navigate(Routes.DAILY) },
                    onStats = { navController.navigate(Routes.STATS) },
                    onTopic = { navController.navigate(Routes.topic(it)) },
                    onProfile = { goTab(Routes.PROFILE) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }
            composable(Routes.ATLAS) {
                AtlasScreen(
                    graph = graph,
                    onWord = { navController.navigate(Routes.word(it)) },
                    onTopic = { navController.navigate(Routes.topic(it)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.GALAXY) {
                GalaxyScreen(
                    graph = graph,
                    onWord = { navController.navigate(Routes.word(it)) },
                    onSearch = { navController.navigate(Routes.SEARCH) },
                )
            }
            composable(Routes.PRACTICE) {
                PracticeScreen(
                    graph = graph,
                    settings = settings,
                    onLearn = { navController.navigate(Routes.LEARN) },
                    onDaily = { navController.navigate(Routes.DAILY) },
                    onFlashcards = { navController.navigate(Routes.FLASHCARDS) },
                    onQuiz = { navController.navigate(Routes.quiz(it)) },
                    onMatch = { navController.navigate(Routes.MATCH) },
                    onTyping = { navController.navigate(Routes.TYPING) },
                )
            }
            composable(Routes.LEARN) {
                LearnScreen(graph, settings, onClose = { navController.popBackStack() })
            }
            composable(Routes.FLASHCARDS) {
                FlashcardScreen(graph, settings, onClose = { navController.popBackStack() })
            }
            composable(Routes.MATCH) {
                MatchScreen(graph, settings, onClose = { navController.popBackStack() })
            }
            composable(Routes.TYPING) {
                TypingScreen(graph, settings, onClose = { navController.popBackStack() })
            }
            composable(Routes.DAILY) {
                QuizScreen(graph, settings, PracticeMode.DAILY, onClose = { navController.popBackStack() })
            }
            composable(
                Routes.QUIZ,
                arguments = listOf(navArgument("mode") { type = NavType.StringType }),
            ) { entry ->
                val mode = when (entry.arguments?.getString("mode")) {
                    "picture" -> PracticeMode.PICTURE
                    "listen" -> PracticeMode.LISTEN
                    "spelling" -> PracticeMode.SPELLING
                    "recall" -> PracticeMode.RECALL
                    else -> PracticeMode.MEANING
                }
                QuizScreen(graph, settings, mode, onClose = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    graph = graph,
                    settings = settings,
                    onStats = { navController.navigate(Routes.STATS) },
                    onAchievements = { navController.navigate(Routes.ACHIEVEMENTS) },
                    onBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                    onTyping = { navController.navigate(Routes.TYPING) },
                    onOffline = { navController.navigate(Routes.OFFLINE) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onAbout = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                    onAtlas = { goTab(Routes.ATLAS) },
                    onTyping = { navController.navigate(Routes.TYPING) },
                )
            }
            composable(Routes.ACHIEVEMENTS) {
                AchievementsScreen(graph, onBack = { navController.popBackStack() })
            }
            composable(Routes.BOOKMARKS) {
                BookmarksScreen(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                    onWord = { navController.navigate(Routes.word(it)) },
                    onFlashcards = { navController.navigate(Routes.FLASHCARDS) },
                    onAtlas = { goTab(Routes.ATLAS) },
                )
            }
            composable(Routes.OFFLINE) {
                OfflineScreen(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                    onBrowse = { goTab(Routes.ATLAS) },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(graph, settings, onBack = { navController.popBackStack() })
            }
            composable(Routes.ABOUT) {
                AboutScreen(
                    graph = graph,
                    onBack = { navController.popBackStack() },
                    onPrivacy = { navController.navigate(Routes.PRIVACY) },
                    onLicenses = { navController.navigate(Routes.LICENSES) },
                )
            }
            composable(Routes.PRIVACY) {
                PrivacyScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.LICENSES) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.SEARCH) {
                SearchScreen(
                    graph = graph,
                    settings = settings,
                    onBack = { navController.popBackStack() },
                    onWord = { navController.navigate(Routes.word(it)) },
                )
            }
            composable(
                Routes.WORD,
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { entry ->
                WordScreen(
                    graph = graph,
                    settings = settings,
                    wordId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onWord = { navController.navigate(Routes.word(it)) },
                    onReview = { navController.navigate(Routes.LEARN) },
                )
            }
            composable(
                Routes.TOPIC,
                arguments = listOf(navArgument("index") { type = NavType.IntType }),
            ) { entry ->
                TopicScreen(
                    graph = graph,
                    settings = settings,
                    topicIndex = entry.arguments?.getInt("index") ?: 0,
                    onBack = { navController.popBackStack() },
                    onWord = { navController.navigate(Routes.word(it)) },
                )
            }
        }

        AnimatedVisibility(
            visible = isMain,
            enter = slideInVertically(tween(260)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(200)) { it } + fadeOut(tween(120)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            VerbiBottomBar(
                currentRoute = route ?: Routes.HOME,
                dark = isGalaxy,
                onSelect = ::goTab,
            )
        }
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabItem(Routes.HOME, "Today", Icons.Rounded.WbSunny),
    TabItem(Routes.ATLAS, "Atlas", Icons.Rounded.GridView),
    TabItem(Routes.GALAXY, "Galaxy", Icons.Rounded.AutoAwesome),
    TabItem(Routes.PRACTICE, "Practice", Icons.Rounded.School),
    TabItem(Routes.PROFILE, "You", Icons.Rounded.Person),
)

@Composable
private fun VerbiBottomBar(
    currentRoute: String,
    dark: Boolean,
    onSelect: (String) -> Unit,
) {
    val colors = LocalVerbiColors.current
    val background = if (dark) Color(0xE60C1A25) else colors.raised.copy(alpha = 0.97f)
    val border = if (dark) Color(0xFFF2E8D7).copy(alpha = 0.12f) else colors.line
    val activeText = if (dark) Color(0xFFF2E8D7) else colors.paper
    val idle = if (dark) Color(0xFFF2E8D7).copy(alpha = 0.55f) else colors.inkFaint
    val active = if (dark) Color(0xFFE0B86A) else colors.ink

    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(26.dp))
            .padding(horizontal = 6.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tabs.forEach { tab ->
            val selected = currentRoute == tab.route
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(19.dp))
                    .clickable { if (!selected) onSelect(tab.route) }
                    .padding(vertical = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .then(
                            if (selected) Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(active.copy(alpha = 0.13f))
                                .padding(horizontal = 13.dp, vertical = 4.dp)
                            else Modifier.padding(horizontal = 13.dp, vertical = 4.dp)
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = tab.label,
                        tint = if (selected) active else idle,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    tab.label,
                    fontFamily = ManropeFamily,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 12.sp,
                    letterSpacing = 0.2.sp,
                    color = if (selected) if (dark) activeText else colors.ink else idle,
                )
            }
        }
    }
}
