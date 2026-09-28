package com.failureludo.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.failureludo.ui.screens.RulesDialog
import com.failureludo.ui.screens.HomeSettingsDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.failureludo.ui.screens.GameBoardScreen
import com.failureludo.ui.screens.GameSetupScreen
import com.failureludo.ui.screens.HistoryScreen
import com.failureludo.ui.screens.HomeScreen
import com.failureludo.ui.screens.WinScreen
import com.failureludo.viewmodel.GameViewModel
import com.failureludo.BuildConfig
import com.failureludo.ui.screens.AuthScreen
import com.failureludo.ui.screens.OnlineLobbyScreen
import com.failureludo.ui.screens.WaitingRoomScreen
import com.failureludo.ui.screens.OnlineGameBoardScreen
import com.failureludo.viewmodel.AuthState
import com.failureludo.viewmodel.AuthViewModel

@Composable
fun AppNavigation(navController: NavHostController) {
    val gameViewModel: GameViewModel = viewModel()
    val isSessionRestored by gameViewModel.isSessionRestored.collectAsState()
    var showRules by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showRules) RulesDialog { showRules = false }
    if (showSettings) HomeSettingsDialog(gameViewModel) { showSettings = false }

    val gameState by gameViewModel.gameState.collectAsState()
    val hasActiveGame = gameViewModel.hasActiveGame

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onNewGame     = { navController.navigate(Screen.Setup.route) },
                onResume      = { navController.navigate(Screen.Game.route) },
                onHistory     = { navController.navigate(Screen.History.route) },
                hasActiveGame     = hasActiveGame,
                onRules = { showRules = true },
                onSettings = { showSettings = true },
                onPlayOnline = if (BuildConfig.DEBUG) {
                    { navController.navigate(Screen.Auth.route) }
                } else null,
                isSessionRestored = isSessionRestored,
                resumeSummary = gameState?.players?.filter { it.isActive }?.joinToString(" · ") { it.name }.orEmpty()
            )
        }

        // Firebase-backed ViewModels belong to online destinations so local play
        // never waits for an authenticated session.
        if (BuildConfig.DEBUG) {
            composable(Screen.Auth.route) {
                val authViewModel: AuthViewModel = viewModel()
                val authState by authViewModel.authState.collectAsState()
                LaunchedEffect(authState) {
                    if (authState is AuthState.SignedIn) {
                        navController.navigate(Screen.OnlineLobby.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
                AuthScreen(authViewModel, onBack = { navController.popBackStack() })
            }

            composable(Screen.OnlineLobby.route) {
                OnlineLobbyScreen(
                    viewModel = viewModel(),
                    onBack = { navController.popBackStack() },
                    onRoomReady = { navController.navigate(Screen.WaitingRoom.route(it.id)) }
                )
            }

            composable(Screen.WaitingRoom.route) { entry ->
                val roomId = requireNotNull(entry.arguments?.getString("roomId"))
                WaitingRoomScreen(
                    roomId = roomId,
                    viewModel = viewModel(),
                    onGameStarting = {
                        navController.navigate(Screen.OnlineGame.route(it)) {
                            popUpTo(Screen.OnlineLobby.route)
                        }
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Screen.OnlineGame.route) { entry ->
                val roomId = requireNotNull(entry.arguments?.getString("roomId"))
                OnlineGameBoardScreen(
                    roomId = roomId,
                    viewModel = viewModel(),
                    onGameOver = { navController.popBackStack(Screen.OnlineLobby.route, false) },
                    onQuit = { navController.popBackStack(Screen.OnlineLobby.route, false) }
                )
            }
        }

        composable(Screen.History.route) {
            HistoryScreen(
                viewModel  = gameViewModel,
                onBack     = { navController.popBackStack() },
                onOpenGame = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.History.route)
                    }
                }
            )
        }

        composable(Screen.Setup.route) {
            GameSetupScreen(
                viewModel   = gameViewModel,
                onStartGame = {
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Game.route) {
            GameBoardScreen(
                viewModel  = gameViewModel,
                onGameOver = {
                    navController.navigate(Screen.Win.route) {
                        popUpTo(Screen.Game.route) { inclusive = true }
                    }
                },
                onQuit = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Win.route) {
            WinScreen(
                viewModel   = gameViewModel,
                onPlayAgain = {
                    gameViewModel.replayWithSameSetup()
                    navController.navigate(Screen.Game.route) {
                        popUpTo(Screen.Home.route)
                    }
                },
                onMainMenu = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }
    }
}
