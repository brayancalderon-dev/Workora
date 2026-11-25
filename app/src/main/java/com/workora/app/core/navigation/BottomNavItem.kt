package com.workora.app.core.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: AppRoute
) {
    data object Dashboard : BottomNavItem(
        title = "Inicio",
        icon = Icons.Default.Home,
        route = AppRoute.Dashboard
    )
    
    data object Jobs : BottomNavItem(
        title = "Vacantes",
        icon = Icons.Default.Search,
        route = AppRoute.Jobs
    )
    
    data object Applications : BottomNavItem(
        title = "Postulaciones",
        icon = Icons.AutoMirrored.Filled.List,
        route = AppRoute.Applications
    )
    
    data object Profile : BottomNavItem(
        title = "Perfil",
        icon = Icons.Default.Person,
        route = AppRoute.Profile
    )
}

val bottomNavItems = listOf(
    BottomNavItem.Dashboard,
    BottomNavItem.Jobs,
    BottomNavItem.Applications,
    BottomNavItem.Profile
)