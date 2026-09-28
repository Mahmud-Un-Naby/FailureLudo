package com.failureludo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import com.failureludo.ui.navigation.Screen
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.rememberNavController
import com.failureludo.ui.navigation.AppNavigation
import com.failureludo.ui.theme.FailureLudoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            val entry by navController.currentBackStackEntryAsState()
            val isTabletop = entry == null || entry?.destination?.route in setOf(Screen.Home.route, Screen.Setup.route, Screen.Game.route)
            FailureLudoTheme(forceLightSystemBarIcons = isTabletop) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation(navController = navController)
                }
            }
        }
    }
}
