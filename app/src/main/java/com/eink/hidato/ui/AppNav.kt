package com.eink.hidato.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.eink.hidato.ui.game.GameScreen
import com.eink.hidato.ui.menu.MenuScreen
import com.eink.hidato.ui.menu.MenuViewModel

/** 应用导航：主界面 <-> 游戏界面（参数经路由传递给 GameViewModel 的 SavedStateHandle） */
@Composable
fun AppNav(viewModel: MenuViewModel = hiltViewModel()) {
    val navController = rememberNavController()
    val tutorialCompleted by viewModel.tutorialCompleted.collectAsState()
    val tutorialError by viewModel.tutorialError.collectAsState()
    if (tutorialCompleted == null) return

    NavHost(navController = navController, startDestination = "menu") {
        composable("menu") {
            MenuScreen(
                viewModel = viewModel,
                onStartGame = { rows, cols, difficulty ->
                    navController.navigate("game/${GameStartMode.NEW}/$rows/$cols/${difficulty.name}")
                },
                onContinueGame = {
                    navController.navigate("game/${GameStartMode.CONTINUE}/0/0/NONE")
                },
            )
        }
        composable(
            route = "game/{mode}/{rows}/{cols}/{difficulty}",
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("rows") { type = NavType.IntType; defaultValue = 8 },
                navArgument("cols") { type = NavType.IntType; defaultValue = 8 },
                navArgument("difficulty") { type = NavType.StringType; defaultValue = "MEDIUM" },
            ),
        ) {
            GameScreen(
                onFinished = { navController.popBackStack("menu", inclusive = false) },
                onBack = { navController.popBackStack() },
            )
        }
    }
    if (tutorialCompleted == false) {
        TutorialDialog(onFinish = viewModel::completeTutorial, error = tutorialError)
    }
}

object GameStartMode {
    const val NEW = "new"
    const val CONTINUE = "continue"
}
