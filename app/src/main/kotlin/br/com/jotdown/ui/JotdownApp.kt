package br.com.jotdown.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import br.com.jotdown.JotdownApplication
import br.com.jotdown.ui.screens.library.LibraryScreen
import br.com.jotdown.ui.screens.reader.ReaderScreen
import br.com.jotdown.ui.screens.reader.ReaderTabsBar
import br.com.jotdown.ui.screens.splash.SplashScreen
import br.com.jotdown.ui.viewmodel.LibraryViewModel
import br.com.jotdown.ui.viewmodel.LibraryViewModelFactory
import br.com.jotdown.ui.viewmodel.ReaderTabsViewModel
import br.com.jotdown.ui.viewmodel.ReaderTabsViewModelFactory
import br.com.jotdown.ui.viewmodel.ReaderViewModel
import br.com.jotdown.ui.viewmodel.ReaderViewModelFactory

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun JotdownApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as JotdownApplication
    val repository = app.repository
    val tabsViewModel: ReaderTabsViewModel = viewModel(
        factory = ReaderTabsViewModelFactory(context.applicationContext as android.app.Application, repository)
    )
    val tabs by tabsViewModel.tabs.collectAsState()
    val activeDocumentId by tabsViewModel.activeDocumentId.collectAsState()
    val isPro by app.billingProvider.isPro.collectAsState()

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(320)) + fadeIn(tween(320)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 3 }, animationSpec = tween(320)) + fadeOut(tween(200)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 3 }, animationSpec = tween(320)) + fadeIn(tween(320)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(320)) + fadeOut(tween(200)) },
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(onTimeout = {
                navController.navigate(Screen.Library.route) {
                    popUpTo(Screen.Splash.route) { inclusive = true }
                }
            })
        }

        composable(Screen.Library.route) {
            val vm: LibraryViewModel = viewModel(factory = LibraryViewModelFactory(repository, app))
            LibraryScreen(
                viewModel = vm,
                onOpenDocument = { documentId ->
                    val allowMultiple = app.billingProvider.isBillingSupported && isPro
                    tabsViewModel.openDocument(documentId, allowMultiple)
                    if (navController.currentDestination?.route != Screen.Reader.route) {
                        navController.navigate(Screen.Reader.route) { launchSingleTop = true }
                    }
                },
                onOpenSettings = { navController.navigate(Screen.Settings.route) }
            )
        }

        composable(Screen.Reader.route) {
            val documentId = activeDocumentId
            if (documentId == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
                return@composable
            }

            var isFullscreen by remember { mutableStateOf(false) }
            val prefs = remember { context.getSharedPreferences("pdf_prefs", android.content.Context.MODE_PRIVATE) }
            val showTabsInFullscreen = prefs.getBoolean("tabs_in_fullscreen", false)

            val tabOwner = remember(documentId) {
                object : ViewModelStoreOwner {
                    override val viewModelStore = ViewModelStore()
                }
            }
            DisposableEffect(tabOwner) {
                onDispose { tabOwner.viewModelStore.clear() }
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalViewModelStoreOwner provides tabOwner) {
                key(documentId) {
                    val vm: ReaderViewModel = viewModel(
                        factory = ReaderViewModelFactory(repository, documentId, app.dictionaryRepository)
                    )
                    Column {
                        if (app.billingProvider.isBillingSupported) {
                            AnimatedVisibility(
                                visible = !isFullscreen || showTabsInFullscreen,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                ReaderTabsBar(
                                    tabs = tabs,
                                    activeDocumentId = documentId,
                                    isPro = isPro,
                                    onSelect = tabsViewModel::select,
                                    onClose = { id ->
                                        if (tabsViewModel.close(id)) navController.popBackStack()
                                    },
                                    onAddTab = {
                                        if (isPro) {
                                            navController.popBackStack(Screen.Library.route, false)
                                        } else {
                                            navController.navigate(Screen.Settings.route)
                                        }
                                    }
                                )
                            }
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            ReaderScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onFullscreenChanged = { isFullscreen = it }
                            )
                        }
                    }
                }
            }
        }

        composable(Screen.Settings.route) {
            val vm: br.com.jotdown.ui.viewmodel.SettingsViewModel = viewModel(
                factory = br.com.jotdown.ui.viewmodel.SettingsViewModelFactory(app)
            )
            br.com.jotdown.ui.screens.settings.SettingsScreen(
                viewModel = vm,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
