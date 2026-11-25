package com.workora.app.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.workora.app.core.navigation.AppRoute
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.presentation.applications.ApplicationsScreen
import com.workora.app.presentation.components.BottomNavigationBar
import com.workora.app.presentation.dashboard.DashboardScreen
import com.workora.app.presentation.jobs.JobsScreen
import com.workora.app.presentation.profile.ProfileScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(320.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PrimaryBlue)
                        .padding(top = 48.dp, bottom = 24.dp, start = 24.dp, end = 24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.TrackChanges,
                        contentDescription = "Logo",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Workora",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    DrawerMenuItem(icon = Icons.Outlined.Home, label = "Inicio", selected = true) { 
                        scope.launch { 
                            drawerState.close() 
                            navController.navigate(AppRoute.Dashboard)
                        } 
                    }
                    DrawerMenuItem(icon = Icons.Outlined.Person, label = "Mi perfil profesional") { 
                        scope.launch { 
                            drawerState.close() 
                            navController.navigate(AppRoute.Profile)
                        } 
                    }
                    DrawerMenuItem(icon = Icons.Outlined.Business, label = "Hiring Room") { 
                        scope.launch { 
                            drawerState.close()
                            navController.navigate(AppRoute.Jobs)
                        } 
                    }
                    
                    DrawerMenuItem(icon = Icons.Outlined.Email, label = "Gmail") { 
                        scope.launch { 
                            drawerState.close()
                            navController.navigate(AppRoute.Gmail)
                        } 
                    }
                    
                    DrawerMenuItem(icon = Icons.AutoMirrored.Outlined.List, label = "Vacantes y evaluaciones") { scope.launch { drawerState.close() } }
                    DrawerMenuItem(icon = Icons.Outlined.CheckCircle, label = "Postulaciones") { scope.launch { drawerState.close() } }
                    DrawerMenuItem(icon = Icons.Outlined.BarChart, label = "Mi progreso") { scope.launch { drawerState.close() } }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    DrawerMenuItem(icon = Icons.Outlined.Link, label = "Integraciones (cuentas)") { scope.launch { drawerState.close() } }
                    DrawerMenuItem(icon = Icons.Outlined.CloudUpload, label = "Respaldos y restauración") { scope.launch { drawerState.close() } }
                    DrawerMenuItem(icon = Icons.Outlined.Settings, label = "Configuración") { scope.launch { drawerState.close() } }
                    
                    DrawerMenuItem(icon = Icons.Outlined.Terminal, label = "Bot Telegram (Termux)") { 
                        scope.launch { 
                            drawerState.close()
                            navController.navigate(AppRoute.Termux) 
                        } 
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    DrawerMenuItem(icon = Icons.Outlined.Info, label = "Ayuda") { scope.launch { drawerState.close() } }
                    DrawerMenuItem(icon = Icons.Outlined.Info, label = "Acerca de Workora") { scope.launch { drawerState.close() } }
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = { BottomNavigationBar(navController = navController) },
            containerColor = Color.White
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = AppRoute.Dashboard,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable<AppRoute.Dashboard> {
                    DashboardScreen(
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onNavigateToGmail = { navController.navigate(AppRoute.Gmail) },
                        onNavigateToProfile = { navController.navigate(AppRoute.Profile) },
                        onNavigateToJobs = { navController.navigate(AppRoute.Jobs) }
                    )
                }
                composable<AppRoute.Jobs> {
                    JobsScreen(
                        onNavigateToCompanies = { navController.navigate(AppRoute.HiringRoomCompanies) }
                    )
                }
                composable<AppRoute.HiringRoomCompanies> {
                    val hiltViewModel: com.workora.app.presentation.jobs.HiringRoomViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    com.workora.app.presentation.jobs.HiringRoomCompaniesScreen(
                        navController = navController,
                        viewModel = hiltViewModel
                    )
                }
                composable<AppRoute.Applications> { ApplicationsScreen() }
                composable<AppRoute.Profile> { 
                    ProfileScreen(navController = navController) 
                }
                composable<AppRoute.Termux> {
                    com.workora.app.presentation.termux.TermuxScreen(navController = navController)
                }
                composable<AppRoute.Gmail> {
                    com.workora.app.presentation.gmail.GmailScreen(navController = navController)
                }
                composable<AppRoute.GmailSettings> {
                    val gmailViewModel: com.workora.app.presentation.gmail.viewmodel.GmailViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    com.workora.app.presentation.gmail.GmailSettingsScreen(
                        navController = navController,
                        viewModel = gmailViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    val backgroundColor = if (selected) Color(0xFFF3E8FF) else Color.Transparent
    val contentColor = if (selected) Color(0xFF3B2E99) else Color(0xFF111827)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        if (!selected) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}