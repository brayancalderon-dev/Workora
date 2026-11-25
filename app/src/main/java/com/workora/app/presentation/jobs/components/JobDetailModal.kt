package com.workora.app.presentation.jobs.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.workora.app.core.theme.PrimaryBlue
import com.workora.app.data.local.entity.HiringRoomJobEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDetailModal(
    job: HiringRoomJobEntity,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissJob: () -> Unit
) {
    val context = LocalContext.current
    val applyUri = Uri.parse(job.applyUrl)
    val applyHost = applyUri.host?.lowercase().orEmpty()
    val hasValidApplyUrl = applyUri.scheme == "https" &&
            (applyHost == "hiringroom.com" || applyHost.endsWith(".hiringroom.com")) &&
            applyUri.path.orEmpty().contains("get_vacancy", ignoreCase = true)
    val analysisUnavailable = job.aiConclusion.contains("no disponible", ignoreCase = true)
    val scrollState = rememberScrollState()

    val badgeColor = when {
        analysisUnavailable -> Color(0xFFE5E7EB)
        job.matchScore >= 85 -> Color(0xFFE8F5E9)
        job.matchScore >= 75 -> Color(0xFFE8F5E9)
        job.matchScore >= 40 -> Color(0xFFFFF3E0)
        else -> Color(0xFFFFEBEE)
    }

    val badgeTextColor = when {
        analysisUnavailable -> Color(0xFF4B5563)
        job.matchScore >= 75 -> Color(0xFF2E7D32)
        job.matchScore >= 40 -> Color(0xFFE65100)
        else -> Color(0xFFC62828)
    }

    val matchesList = job.aiMatches.split("|").map { it.trim() }.filter { it.isNotBlank() }
    val gapsList = job.aiGaps.split("|").map { it.trim() }.filter { it.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modal Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                ) {
                    if (job.companyLogoUrl != null) {
                        AsyncImage(
                            model = job.companyLogoUrl,
                            contentDescription = job.companyName,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryBlue)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(PrimaryBlue)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
                    }
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = badgeColor
                        ) {
                            Text(
                                text = when {
                                    analysisUnavailable -> "Análisis no disponible"
                                    job.matchScore >= 85 -> "${job.matchScore}% Excelente"
                                    job.matchScore >= 75 -> "${job.matchScore}% Alta compatibilidad"
                                    job.matchScore >= 40 -> "${job.matchScore}% Compatibilidad parcial"
                                    else -> "${job.matchScore}% Baja compatibilidad"
                                },
                                color = badgeTextColor,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    Text(
                        text = job.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlue
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${job.companyName} • ${job.location} • ${job.workMode}",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    if (job.salary != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "💰 Salario estimado: ${job.salary}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))

                    // AI Section Card
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.AutoAwesome,
                                    contentDescription = null,
                                    tint = PrimaryBlue
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Estimación local de compatibilidad",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "¿Por qué encajas?",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.DarkGray
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = job.aiWhyFit,
                                fontSize = 13.sp,
                                color = Color(0xFF374151),
                                lineHeight = 18.sp
                            )

                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "🟢 Coincidencias con tu CV:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                            matchesList.forEach { item ->
                                Text(
                                    text = "  • $item",
                                    fontSize = 12.sp,
                                    color = Color(0xFF1F2937)
                                )
                            }

                            if (gapsList.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = "🟠 Requisitos a considerar:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFFE65100)
                                )
                                gapsList.forEach { item ->
                                    Text(
                                        text = "  • $item",
                                        fontSize = 12.sp,
                                        color = Color(0xFF374151)
                                    )
                                }
                            }

                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "📊 Desglose por áreas:",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = PrimaryBlue
                            )
                            Spacer(Modifier.height(8.dp))

                            if (analysisUnavailable) {
                                Text("No se pudo extraer suficiente texto del CV para calcular los criterios.", fontSize = 12.sp, color = Color.Gray)
                            } else {
                                ScoreProgressBar(label = "Tecnologías & Stack", score = job.techScore)
                                ScoreProgressBar(label = "Experiencia Requerida", score = job.expScore)
                                ScoreProgressBar(label = "Funciones & Rol", score = job.funcScore)
                            }

                            Spacer(Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color.White
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Conclusión de compatibilidad",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PrimaryBlue
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = job.aiConclusion,
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Descripción de la oferta",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryBlue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = job.description,
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 19.sp
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Requisitos completos",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = PrimaryBlue
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = job.requirements,
                        fontSize = 13.sp,
                        color = Color.DarkGray,
                        lineHeight = 19.sp
                    )
                }

                // Modal Footer Action
                Surface(
                    tonalElevation = 8.dp,
                    color = Color.White
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (job.isFavorite) Icons.Default.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Favorito",
                                tint = if (job.isFavorite) Color.Red else Color.Gray
                            )
                        }
                        IconButton(onClick = onDismissJob) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Descartar",
                                tint = Color.Gray
                            )
                        }
                        Button(
                            enabled = hasValidApplyUrl,
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, applyUri)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = PrimaryBlue,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Launch, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (hasValidApplyUrl) "Ver vacante y postularme" else "Enlace no disponible", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreProgressBar(label: String, score: Int) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 11.sp, color = Color.DarkGray)
            Text("$score%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
        }
        Spacer(Modifier.height(2.dp))
        LinearProgressIndicator(
            progress = { score / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = if (score >= 80) Color(0xFF4CAF50) else Color(0xFFFF9800),
            trackColor = Color.LightGray.copy(alpha = 0.4f)
        )
    }
}
