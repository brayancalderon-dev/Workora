package com.workora.app.presentation.jobs

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.presentation.jobs.components.HiringRoomJobCard
import com.workora.app.presentation.jobs.components.JobDetailModal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    modifier: Modifier = Modifier,
    onNavigateToCompanies: () -> Unit = {},
    viewModel: HiringRoomViewModel = hiltViewModel()
) {
    val searchStatus = viewModel.searchStatus.collectAsState().value
    val userSearched = viewModel.isSearchExecuted.collectAsState().value
    val stats = viewModel.stats.collectAsState().value
    val filteredJobs = viewModel.filteredJobs.collectAsState().value
    val selectedSort = viewModel.selectedSort.collectAsState().value
    val searchQuery = viewModel.searchQuery.collectAsState().value
    val selectedJobDetail = viewModel.selectedJobForDetail.collectAsState().value
    val selectedDays = viewModel.selectedDaysFilter.collectAsState().value

    var showHelpDialog by remember { mutableStateOf(false) }

    val daysOptions = listOf(
        0 to "Cualquier fecha",
        1 to "Hace 1 día",
        3 to "Hace 3 días",
        7 to "Hace 7 días",
        14 to "Hace 14 días"
    )

    val sortOptions = listOf(
        "Mayor compatibilidad",
        "Más recientes",
        "Empresa"
    )

    // Modal view for job detail
    if (selectedJobDetail != null) {
        JobDetailModal(
            job = selectedJobDetail,
            onDismiss = { viewModel.closeJobDetail() },
            onToggleFavorite = { viewModel.toggleFavorite(selectedJobDetail) },
            onDismissJob = { viewModel.dismissJob(selectedJobDetail) }
        )
    }

    // Modal Help Dialog matching GmailScreen style (opened when ? is clicked)
    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White)
                ) {
                    Text("Entendido", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.HelpOutline, contentDescription = null, tint = PrimaryBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("¿Cómo funciona HiringRoom?", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = PrimaryBlue)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "1. Gestor de Empresas:\nToca 'Empresas' arriba para elegir tus empresas de interés (ej. La Fabril, Banco Bolivariano, Multicines, BBVA) y activar su suscripción.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "2. Búsqueda y evaluación:\nAl presionar 'Buscar oportunidades', Workora compara la información del CV disponible con las vacantes guardadas.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "3. Postulación Directa:\nRevisa el nivel de compatibilidad y presiona 'Postularme' para abrir la oferta oficial específica sin rodeos.",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                }
            },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color.White
        )
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FA)),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // 1. Clean Compact Header Section with ? Help Icon matching GmailScreen style
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = PrimaryBlue,
                        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp)
                    )
                    .padding(top = 36.dp, bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "HiringRoom",
                                color = Color.White,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF6C63FF)
                            ) {
                                Text(
                                    text = "Workora AI",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Búsqueda de empleos por coincidencia de CV",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ? Help Icon Button matching GmailScreen style
                        IconButton(onClick = { showHelpDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.HelpOutline,
                                contentDescription = "Ayuda",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Prominent Empresas Manager Card Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PrimaryBlue),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                    .clickable { onNavigateToCompanies() }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Business,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Gestor de Empresas Suscritas",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Configura tus empresas de interés para monitorear",
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.2f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text("Configurar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 3. Action & Search Controls Section
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                // "Buscar Oportunidades" Animated Button
                Button(
                    onClick = { viewModel.startSearch() },
                    enabled = searchStatus !is SearchStatus.Searching,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    AnimatedContent(
                        targetState = searchStatus,
                        label = "search_status"
                    ) { status ->
                        when (status) {
                            is SearchStatus.Searching -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = status.stepMessage,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                }
                            }
                            else -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Search,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (userSearched) "Buscar oportunidades de nuevo" else "Buscar oportunidades",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("Buscar por palabra clave o puesto...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.Gray) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryBlue,
                        unfocusedTextColor = PrimaryBlue,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Jobs Count, Days Filter and Sort Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (userSearched) "${filteredJobs.size} vacantes encontradas" else "Listo para buscar",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Days Filter Menu Pill
                        var daysMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F0FE),
                                modifier = Modifier.clickable { daysMenuExpanded = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Outlined.DateRange, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    val currentDaysLabel = daysOptions.find { it.first == selectedDays }?.second ?: "Fechas"
                                    Text(
                                        text = currentDaysLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        maxLines = 1
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = daysMenuExpanded,
                                onDismissRequest = { daysMenuExpanded = false }
                            ) {
                                daysOptions.forEach { (days, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setDaysFilter(days)
                                            daysMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Sort Menu Pill
                        var sortMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFE8F0FE),
                                modifier = Modifier.clickable { sortMenuExpanded = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = selectedSort,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PrimaryBlue,
                                        maxLines = 1
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = sortMenuExpanded,
                                onDismissRequest = { sortMenuExpanded = false }
                            ) {
                                sortOptions.forEach { sort ->
                                    DropdownMenuItem(
                                        text = { Text(sort, fontSize = 12.sp) },
                                        onClick = {
                                            viewModel.setSort(sort)
                                            sortMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Initial Unsearched State OR Vacancies List items / Empty State
        if (!userSearched) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null,
                            tint = PrimaryBlue,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Búsqueda lista",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Presiona 'Buscar oportunidades' para evaluar las vacantes disponibles frente al texto extraído de tu Hoja de Vida.",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        } else if (filteredJobs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.SearchOff,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No encontramos vacantes que coincidan con tu búsqueda en las empresas suscritas.",
                            fontSize = 14.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { onNavigateToCompanies() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Gestionar empresas suscritas", color = Color.White)
                        }
                    }
                }
            }
        } else {
            items(
                items = filteredJobs,
                key = { it.id }
            ) { job ->
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    HiringRoomJobCard(
                        job = job,
                        onClick = { viewModel.openJobDetail(job) },
                        onToggleFavorite = { viewModel.toggleFavorite(job) },
                        onDismissJob = { viewModel.dismissJob(job) }
                    )
                }
            }
        }
    }
}
