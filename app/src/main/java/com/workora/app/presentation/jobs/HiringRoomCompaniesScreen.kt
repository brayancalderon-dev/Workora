package com.workora.app.presentation.jobs

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Launch
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.data.remote.hiringroom.HiringRoomCompanySource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HiringRoomCompaniesScreen(
    navController: NavController,
    viewModel: HiringRoomViewModel
) {
    val context = LocalContext.current
    val subscribedSet = viewModel.subscribedCompanyIds.collectAsState().value
    val searchState = viewModel.companySearchState.collectAsState().value
    val customCompanies = viewModel.customCompanies.collectAsState().value
    var inputQuery by remember { mutableStateOf("") }
    var activeSearchTerm by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    val allCompanies = (HiringRoomCompanySource.verifiedCompanies + customCompanies).distinctBy { it.id }
    val subscribedCompanies = allCompanies.filter {
        subscribedSet.contains(it.id)
    }

    val cleanQuery = activeSearchTerm.trim().lowercase().replace(" ", "")

    val searchResult = when (searchState) {
        is HiringRoomViewModel.CompanySearchState.Found -> searchState.company to true
        is HiringRoomViewModel.CompanySearchState.Candidate -> searchState.company to false
        else -> null
    }
    val searchResults = searchResult?.let { listOf(it) }.orEmpty()
        .filterNot { (company, _) -> subscribedSet.contains(company.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Empresas en HiringRoom", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PrimaryBlue)
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(scrollState)
        ) {
            // Search Row at VERY TOP with explicit [ Buscar ] Button next to text input
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputQuery,
                    onValueChange = { 
                        inputQuery = it
                        if (it.isBlank()) activeSearchTerm = ""
                    },
                    placeholder = { Text("Nombre de empresa") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue) },
                    trailingIcon = {
                        if (inputQuery.isNotBlank()) {
                            IconButton(onClick = { 
                                inputQuery = ""
                                activeSearchTerm = ""
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar", tint = Color.Gray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PrimaryBlue,
                        unfocusedTextColor = PrimaryBlue,
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color.LightGray
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 56.dp)
                )

                Button(
                    onClick = { activeSearchTerm = inputQuery.trim(); viewModel.searchCompany(inputQuery) },
                    enabled = inputQuery.isNotBlank() && searchState !is HiringRoomViewModel.CompanySearchState.Searching,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrimaryBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(56.dp).widthIn(min = 76.dp, max = 104.dp)
                ) {
                    Text("Buscar", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Search Results Section (Only visible when user pressed Buscar and entered query)
            if (activeSearchTerm.isNotBlank()) {
                Text(
                    text = "🔍 Resultados para '$activeSearchTerm'",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
                Spacer(Modifier.height(8.dp))

                if (searchState is HiringRoomViewModel.CompanySearchState.Searching) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                        Spacer(Modifier.width(10.dp))
                        Text("Buscando en Internet y validando el portal de Hiring Room…", fontSize = 13.sp, color = Color.DarkGray)
                    }
                } else if (searchResults.isEmpty()) {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = when (val state = searchState) {
                                    is HiringRoomViewModel.CompanySearchState.ConnectionError -> state.message
                                    is HiringRoomViewModel.CompanySearchState.SearchError -> state.message
                                    is HiringRoomViewModel.CompanySearchState.NotFound -> state.message
                                    else -> "Presiona Buscar para consultar fuentes web y validar el portal de empleos."
                                },
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100),
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "Puedes corregir el nombre e intentar nuevamente. Un error de conexión no se interpreta como empresa inexistente.",
                                fontSize = 12.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                } else {
                    searchResults.forEach { (company, verified) ->
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = company.logoUrl,
                                        contentDescription = company.name,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PrimaryBlue)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = company.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = PrimaryBlue
                                        )
                                        Text(
                                            text = if (company.vacanciesCount > 0) "${company.sector} • ${company.vacanciesCount} vacantes" else company.sector,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = company.hiringRoomUrl,
                                            fontSize = 10.sp,
                                            color = Color(0xFF4B5563),
                                            maxLines = 2
                                        )
                                        Text(
                                            text = if (verified) "Portal verificado" else "Portal probable; revisa la URL antes de confirmar",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(company.hiringRoomUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(38.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Launch,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ver Vacantes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            if (verified) viewModel.toggleCompanySubscription(company.id)
                                            else {
                                                viewModel.confirmCandidatePortal(company)
                                                viewModel.toggleCompanySubscription(company.id)
                                            }
                                            activeSearchTerm = ""
                                            inputQuery = ""
                                            viewModel.clearCompanySearch()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrimaryBlue,
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(if (verified) "Suscribir" else "Confirmar y suscribir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // Main Section: Subscribed Companies List (Unsubscribed companies VANISH COMPLETELY)
            Text(
                text = "Empresas Suscritas (${subscribedCompanies.size})",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PrimaryBlue
            )
            Text(
                text = "Si te desunes de una empresa, desaparecerá inmediatamente de tu pantalla:",
                fontSize = 12.sp,
                color = Color.Gray
            )

            Spacer(Modifier.height(12.dp))

            if (subscribedCompanies.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Outlined.Business, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "No tienes empresas suscritas",
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Escribe el nombre de una empresa en el buscador de arriba y presiona 'Buscar' para suscribirte.",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            } else {
                subscribedCompanies.forEach { company ->
                    AnimatedVisibility(
                        visible = subscribedSet.contains(company.id),
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = company.logoUrl,
                                        contentDescription = company.name,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(PrimaryBlue)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = company.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                color = PrimaryBlue
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color(0xFFE8F5E9)
                                            ) {
                                                Text(
                                                    text = "✓ Suscrito",
                                                    color = Color(0xFF2E7D32),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = if (company.vacanciesCount > 0) "${company.sector} • ${company.vacanciesCount} vacantes" else company.sector,
                                            fontSize = 11.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }

                                Spacer(Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(company.hiringRoomUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(38.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryBlue)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Launch,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text("Ver Vacantes", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = { viewModel.toggleCompanySubscription(company.id) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFFFFEBEE),
                                            contentColor = Color(0xFFD32F2F)
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Desuscribir", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
