package br.com.jotdown.ui

sealed class Screen(val route: String) {
    object Splash  : Screen("splash")
    object Library : Screen("library")
    object Reader  : Screen("reader")
    object Settings : Screen("settings")
}

