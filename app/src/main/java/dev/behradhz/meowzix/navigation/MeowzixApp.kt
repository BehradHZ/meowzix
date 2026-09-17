package dev.behradhz.meowzix.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.feature.nowplaying.NowPlayingRoute
import dev.behradhz.meowzix.feature.queue.QueueRoute
import dev.behradhz.meowzix.feature.telegramauth.TelegramAuthRoute

private const val LIBRARY_ROUTE = "library"
private const val NOW_PLAYING_ROUTE = "now-playing"
private const val QUEUE_ROUTE = "queue"
private const val TELEGRAM_AUTH_ROUTE = "telegram-auth"

@Composable
fun MeowzixApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LIBRARY_ROUTE,
    ) {
        composable(LIBRARY_ROUTE) {
            LibraryRoute(
                onOpenNowPlaying = {
                    navController.navigate(NOW_PLAYING_ROUTE) { launchSingleTop = true }
                },
                onOpenTelegram = {
                    navController.navigate(TELEGRAM_AUTH_ROUTE) { launchSingleTop = true }
                },
            )
        }
        composable(NOW_PLAYING_ROUTE) {
            NowPlayingRoute(
                onBack = navController::popBackStack,
                onOpenQueue = { navController.navigate(QUEUE_ROUTE) { launchSingleTop = true } },
            )
        }
        composable(QUEUE_ROUTE) {
            QueueRoute(onBack = navController::popBackStack)
        }
        composable(TELEGRAM_AUTH_ROUTE) {
            TelegramAuthRoute(onBack = navController::popBackStack)
        }
    }
}
