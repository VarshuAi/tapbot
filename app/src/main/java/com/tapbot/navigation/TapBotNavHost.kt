package com.tapbot.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.tapbot.TapBotApplication
import com.tapbot.feature.botdetail.BotDetailScreen
import com.tapbot.feature.botdetail.BotDetailViewModel
import com.tapbot.feature.catalog.CatalogScreen
import com.tapbot.feature.catalog.CatalogViewModel

@Composable
fun TapBotNavHost(
    app: TapBotApplication,
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Catalog.route,
        modifier = modifier
    ) {
        composable(route = Screen.Catalog.route) {
            val catalogViewModel: CatalogViewModel = viewModel(
                factory = CatalogViewModel.provideFactory(app.catalogApi)
            )
            CatalogScreen(
                viewModel = catalogViewModel,
                onBotClick = { botId ->
                    navController.navigate(Screen.BotDetail.createRoute(botId))
                }
            )
        }

        composable(
            route = Screen.BotDetail.route,
            arguments = listOf(
                navArgument("botId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val botId = backStackEntry.arguments?.getString("botId") ?: return@composable
            val botDetailViewModel: BotDetailViewModel = viewModel(
                factory = BotDetailViewModel.provideFactory(
                    botId = botId,
                    catalogApi = app.catalogApi,
                    credentialStore = app.credentialStore,
                    botServiceController = app.botServiceController,
                    logRepository = app.logRepository
                )
            )
            BotDetailScreen(
                viewModel = botDetailViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
