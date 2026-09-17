package dev.behradhz.meowzix.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.behradhz.meowzix.feature.library.LibraryRoute

private const val LIBRARY_ROUTE = "library"

@Composable
fun MeowzixApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = LIBRARY_ROUTE,
    ) {
        composable(LIBRARY_ROUTE) {
            LibraryRoute()
        }
    }
}
