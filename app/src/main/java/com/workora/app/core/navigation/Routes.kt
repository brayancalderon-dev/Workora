package com.workora.app.core.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed class AppRoute {
    @Serializable
    data object Dashboard : AppRoute()
    
    @Serializable
    data object Jobs : AppRoute()
    
    @Serializable
    data object Applications : AppRoute()
    
    @Serializable
    data object Profile : AppRoute()

    @Serializable
    data object Termux : AppRoute()
    
    @Serializable
    data object Gmail : AppRoute()

    @Serializable
    data object GmailSettings : AppRoute()

    @Serializable
    data object HiringRoomCompanies : AppRoute()
}