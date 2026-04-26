package br.com.jotdown.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.jotdown.JotdownApplication
import br.com.jotdown.ui.screens.library.LibraryScreen
import br.com.jotdown.ui.screens.reader.ReaderScreen
import br.com.jotdown.ui.screens.splash.SplashScreen
import br.com.jotdown.ui.viewmodel.LibraryViewModel
import br.com.jotdown.ui.viewmodel.LibraryViewModelFactory
import br.com.jotdown.ui.viewmodel.ReaderViewModel
import br.com.jotdown.ui.viewmodel.ReaderViewModelFactory

@Composable
fun JotdownApp() {
    val navController = rememberNavController()
    val context       = LocalContext.current
    val repository    = (context.applicationContext as JotdownApplication).repository

    NavHost(
        navController    = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onTimeout = {
                navController.navigate(Screen.Library.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Library.route) {
            val vm: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(repository))
            LibraryScreen(viewModel = vm, onOpenDocument = { docId -> navController.navigate(Screen.Reader.createRoute(docId)) })
        }

        composable(Screen.Reader.route) { backStack ->
            val documentId = backStack.arguments?.getString("documentId") ?: return@composable
            val vm: ReaderViewModel = viewModel(factory = ReaderViewModelFactory(repository, documentId))
            ReaderScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }
    }
}

