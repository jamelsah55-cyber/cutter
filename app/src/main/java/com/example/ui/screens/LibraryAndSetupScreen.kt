package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.vector.StarterTemplates
import com.example.ui.CutterUiState
import com.example.ui.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun LibraryAndSetupScreen(
    state: CutterUiState,
    viewModel: MainViewModel,
    onOpenInStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewMaterialDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Hero Visual OTG Connection Guide Card
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_plotter_hero),
                    contentDescription = "توصيل ماكينة الكتر بالهاتف عبر USB OTG",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0xCC0E1318))
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = "دليل الربط السريع عبر USB OTG",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    Text(
                        text = "1. أوصل كابل OTG بالماكينة · 2. امنح إذن USB · 3. اختر HPGL أو GPGL وابدأ القص",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD2E2EE)
                    )
                }
            }
        }

        // Starter Templates Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("قوالب جاهزة للقص", style = MaterialTheme.typography.titleMedium)
            }
            StarterTemplates.templates.forEach { template ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(template.titleAr, style = MaterialTheme.typography.titleSmall)
                            Text(
                                template.subtitleAr,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "الخامة المقترحة: ${template.recommendedMaterialAr}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                viewModel.loadTemplate(template)
                                onOpenInStudio()
                            },
                            modifier = Modifier.testTag("load_template_${template.id}")
                        ) {
                            Text("فتح")
                        }
                    }
                }
            }
        }

        // Saved Projects Section
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("المشاريع المحفوظة", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "${state.savedProjects.size} مشروع",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.savedProjects.isEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "لا توجد مشاريع محفوظة بعد. من شاشة التصميم اضغط على أيقونة الحفظ لإضافة مشروعك هنا.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(14.dp)
                    )
                }
            } else {
                state.savedProjects.forEach { proj ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(proj.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${proj.elementCount} عناصر · ${proj.matWidthMm.toInt()}×${proj.matHeightMm.toInt()} مم",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Row {
                                TextButton(onClick = {
                                    viewModel.loadSavedProject(proj)
                                    onOpenInStudio()
                                }) {
                                    Text("فتح")
                                }
                                IconButton(onClick = { viewModel.deleteSavedProject(proj.id) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "حذف المشروع", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Material Presets Manager
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                    Text("مكتبة الخامات ومعايير السكين", style = MaterialTheme.typography.titleMedium)
                }
                FilledTonalButton(
                    onClick = { showNewMaterialDialog = true },
                    modifier = Modifier.testTag("add_custom_material_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("إضافة خامة")
                }
            }

            state.materialPresets.forEach { mat ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(mat.nameAr, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${mat.categoryAr} · ${mat.forceGrams}g · ${mat.speedMmPerSec}mm/s · ${mat.passes}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!mat.isBuiltIn) {
                            IconButton(onClick = { viewModel.deleteCustomMaterial(mat.id) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "حذف الخامة", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Mat Size & Studio Appearance Settings
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("أبعاد بساط القص والماكينة", style = MaterialTheme.typography.titleMedium)
                }

                val matSizes = listOf(
                    Triple("30×30 سم", 300f, 300f),
                    Triple("60×40 سم", 600f, 400f),
                    Triple("120×60 سم (رول)", 1200f, 600f)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    matSizes.forEach { (label, w, h) ->
                        FilterChip(
                            selected = state.matConfig.widthMm == w && state.matConfig.heightMm == h,
                            onClick = { viewModel.updateMatDimensions(w, h) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("mat_size_${w.toInt()}x${h.toInt()}")
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            if (state.darkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = null
                        )
                        Text("الوضع الداكن للاستوديو", style = MaterialTheme.typography.bodyLarge)
                    }
                    Switch(
                        checked = state.darkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme() },
                        modifier = Modifier.testTag("dark_theme_switch")
                    )
                }
            }
        }

        // Cut History Log
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("سجل عمليات القص الأخيرة", style = MaterialTheme.typography.titleMedium)
                }
                if (state.cutHistory.isNotEmpty()) {
                    TextButton(onClick = { viewModel.clearCutHistory() }) {
                        Text("مسح السجل")
                    }
                }
            }
            if (state.cutHistory.isEmpty()) {
                Text(
                    text = "لم يتم تسجيل عمليات قص عبر OTG بعد.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val fmt = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
                state.cutHistory.forEach { job ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(job.jobName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${job.materialName} · ${job.protocolName} · ${(job.totalCutLengthMm / 1000f).let { String.format(Locale.US, "%.2f", it) }} م",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                fmt.format(Date(job.timestamp)),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showNewMaterialDialog) {
        AddCustomMaterialDialog(
            onDismiss = { showNewMaterialDialog = false },
            onSave = { name, cat, force, speed, passes ->
                viewModel.saveCustomMaterialPreset(name, cat, force, speed, passes)
                showNewMaterialDialog = false
            }
        )
    }
}

@Composable
private fun AddCustomMaterialDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, Int, Int, Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("خامة خاصة") }
    var force by remember { mutableFloatStateOf(120f) }
    var speed by remember { mutableFloatStateOf(200f) }
    var passes by remember { mutableIntStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("إضافة إعداد خامة مخصص") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الخامة (مثال: فينيل مطفي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("التصنيف") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("الضغط: ${force.roundToInt()} غرام", style = MaterialTheme.typography.labelLarge)
                Slider(value = force, onValueChange = { force = it }, valueRange = 10f..450f)
                Text("السرعة: ${speed.roundToInt()} مم/ث", style = MaterialTheme.typography.labelLarge)
                Slider(value = speed, onValueChange = { speed = it }, valueRange = 20f..600f)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("التكرارات:")
                    listOf(1, 2, 3).forEach { p ->
                        FilterChip(selected = passes == p, onClick = { passes = p }, label = { Text("${p}×") })
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(name, category, force.roundToInt(), speed.roundToInt(), passes) }) {
                Text("حفظ", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}
