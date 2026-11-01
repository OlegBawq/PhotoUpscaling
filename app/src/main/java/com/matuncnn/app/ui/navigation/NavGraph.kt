package com.matuncnn.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val titleRes: Int, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    data object Home : Screen("home", com.matuncnn.app.R.string.nav_home, Icons.Filled.Image, Icons.Outlined.Image)
    data object Settings : Screen("settings", com.matuncnn.app.R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings)

    companion object {
        val items = listOf(Home, Settings)
    }
}
