package com.workora.app.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.domain.model.Profile
import com.workora.app.presentation.dashboard.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenDrawer: () -> Unit = {},
    onNavigateToGmail: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToJobs: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val scrollState = rememberScrollState()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val gmailSession by viewModel.gmailSession.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA))
            .verticalScroll(scrollState)
    ) {
        HeaderSection(onOpenDrawer = onOpenDrawer)
        
        Column(modifier = Modifier.padding(16.dp)) {
            GreetingSection()
            Spacer(modifier = Modifier.height(16.dp))
            ProfileSection(profile, onNavigateToProfile)
            Spacer(modifier = Modifier.height(16.dp))
            ConnectionsSection(
                isGmailConnected = gmailSession.isAuthorized,
                onNavigateToGmail = onNavigateToGmail,
                onNavigateToJobs = onNavigateToJobs
            )
            Spacer(modifier = Modifier.height(24.dp))
            ActivitySummarySection()
            Spacer(modifier = Modifier.height(24.dp))
            QuickActionsSection(onNavigateToProfile)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderSection(onOpenDrawer: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = PrimaryBlue,
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .padding(top = 48.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenDrawer) {
                    Icon(
                        imageVector = Icons.Outlined.Menu,
                        contentDescription = "Menú",
                        tint = Color.White
                    )
                }
                Icon(
                    imageVector = Icons.Default.TrackChanges,
                    contentDescription = "Logo",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Workora",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tu asistente de búsqueda y postulación",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp
                    )
                }
            }
            Row {
                IconButton(onClick = { /*TODO*/ }) {
                    BadgedBox(
                        badge = {
                            Badge(
                                containerColor = Color.Red,
                                modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                            ) { }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notificaciones",
                            tint = Color.White
                        )
                    }
                }
                IconButton(onClick = { /*TODO*/ }) { // Reemplazo a icono de Settings si quieres o Drawer Menu
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "Configuración",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun GreetingSection() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F0FE)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(
                text = "¡Hola!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
            Text(
                text = "Reanuda tu búsqueda de empleo",
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun ProfileSection(profile: Profile?, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFFF3E8FF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = Color(0xFF6C63FF)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tu perfil profesional",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Administra tu hoja de vida y tu perfil para analizar vacantes.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    lineHeight = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (profile != null) {
                    Text(
                        text = "CV: ${profile.pdfFileName ?: "Sin archivo"}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.DarkGray
                    )
                } else {
                    Text(
                        text = "Aún no has configurado tu perfil",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                }
            }
            if (profile != null) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Actualizado",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Ver Perfil",
                tint = Color.Gray
            )
        }
    }
}

@Composable
private fun ConnectionsSection(
    isGmailConnected: Boolean,
    onNavigateToGmail: () -> Unit,
    onNavigateToJobs: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionCard(
            modifier = Modifier.weight(1f),
            title = "Hiring Room",
            subtitle = "Busca empleos y analiza vacantes afines.",
            icon = Icons.Outlined.Business,
            isConnected = true,
            onClick = onNavigateToJobs
        )
        ConnectionCard(
            modifier = Modifier.weight(1f),
            title = "Gmail",
            subtitle = "Genera borradores de correo para postular.",
            icon = Icons.Outlined.Email,
            isConnected = isGmailConnected,
            onClick = onNavigateToGmail
        )
    }
}

@Composable
private fun ConnectionCard(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    isConnected: Boolean,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier
            .height(112.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8F0FE)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }

            Text(
                text = subtitle,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = Color.Gray,
                maxLines = 2
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF287A45) else Color(0xFFE65100),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isConnected) "Conectado" else "Desconectado",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isConnected) Color(0xFF287A45) else Color(0xFFE65100)
                )
            }
        }
    }
}

@Composable
private fun ActivitySummarySection() {
    Column {
        Text(
            text = "Resumen de actividad",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.WorkOutline,
                iconTint = PrimaryBlue,
                title = "Vacantes evaluadas",
                subtitle = "Consulta las ofertas que Workora ha analizado.",
                value = "12"
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Verified,
                iconTint = Color(0xFF4CAF50),
                title = "Alta compatibilidad",
                subtitle = "Revisa las vacantes que mejor coinciden con tu perfil.",
                value = "4"
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Drafts,
                iconTint = Color(0xFFFF9800),
                title = "Borradores en Gmail",
                subtitle = "Consulta los correos de postulación que has generado.",
                value = "3"
            )
            SummaryCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Send,
                iconTint = Color(0xFF6C63FF),
                title = "Postulaciones registradas",
                subtitle = "Lleva un seguimiento de las oportunidades a las que te has postulado.",
                value = "2"
            )
        }
    }
}

@Composable
private fun SummaryCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    value: String
) {
    Card(
        modifier = modifier.height(116.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = value,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }
            Column {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.DarkGray,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(onNavigateToProfile: () -> Unit) {
    Column {
        Text(
            text = "Acciones rápidas",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PrimaryBlue
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            QuickActionItem(
                icon = Icons.Outlined.UploadFile,
                iconTint = PrimaryBlue,
                title = "Actualizar\nCV",
                onClick = onNavigateToProfile
            )
            QuickActionItem(
                icon = Icons.Outlined.FormatListBulleted,
                iconTint = PrimaryBlue,
                title = "Ver\nvacantes"
            )
            QuickActionItem(
                icon = Icons.Outlined.Email,
                iconTint = Color(0xFFFF9800),
                title = "Borradores\nde Gmail"
            )
            QuickActionItem(
                icon = Icons.Outlined.BarChart,
                iconTint = PrimaryBlue,
                title = "Mi\nprogreso"
            )
        }
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    onClick: () -> Unit = {}
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.width(80.dp).clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontSize = 11.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp,
                color = Color.DarkGray
            )
        }
    }
}