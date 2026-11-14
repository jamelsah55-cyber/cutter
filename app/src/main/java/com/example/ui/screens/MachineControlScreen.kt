package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.usb.ConnectionTransport
import com.example.data.usb.CutExecutionStatus
import com.example.data.usb.MachineConnectionState
import com.example.domain.model.PlotterProtocol
import com.example.ui.CutterUiState
import com.example.ui.MainViewModel
import com.example.ui.components.CuttingMatCanvas
import com.example.ui.theme.CutEmerald
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

@Composable
fun MachineControlScreen(
    state: CutterUiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var jogStepMm by remember { mutableFloatStateOf(10f) }
    var showSerialConsole by remember { mutableStateOf(false) }

    val exportCutFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                val ext = state.serialConfig.protocol.fileExtension
                val text = viewModel.buildExportContent(ext)
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                viewModel.showMessage("تم تصدير ملف القص بصيغة .$ext")
            } catch (_: Exception) {
                viewModel.showMessage("تعذر حفظ ملف القص")
            }
        }
    }

    val exportSvgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/svg+xml")
    ) { uri ->
        if (uri != null) {
            try {
                val text = viewModel.buildExportContent("svg")
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
                viewModel.showMessage("تم تصدير التصميم بصيغة SVG")
            } catch (_: Exception) {
                viewModel.showMessage("تعذر تصدير ملف SVG")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Multi-Transport Hardware Connection Card (USB OTG/CDC, Wi-Fi, Bluetooth)
        MachineConnectionCard(
            state = state,
            onSelectTransport = { viewModel.selectTransport(it) },
            onRefreshUsb = { viewModel.refreshUsbDevices() },
            onConnectUsb = { id -> viewModel.connectUsbDevice(id) },
            onConnectWifi = { host, port -> viewModel.connectWifi(host, port) },
            hasBluetoothPermissions = { viewModel.hasBluetoothPermissions() },
            onRefreshBluetooth = { viewModel.refreshBluetoothDevices() },
            onConnectBluetooth = { addr -> viewModel.connectBluetoothDevice(addr) },
            onDisconnect = { viewModel.disconnectMachine() },
            onSelectProtocol = { viewModel.updateProtocol(it) },
            onSelectBaudRate = { viewModel.updateBaudRate(it) },
            onShowMessage = { viewModel.showMessage(it) }
        )

        // 2. Material Preset & Blade Force/Speed Tuning Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إعدادات السكين والخامة",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${state.selectedMaterial.forceGrams}g · ${state.selectedMaterial.speedMmPerSec} mm/s · ${state.selectedMaterial.passes}×",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.materialPresets.forEach { preset ->
                        FilterChip(
                            selected = preset.nameAr == state.selectedMaterial.nameAr,
                            onClick = { viewModel.selectMaterialPreset(preset) },
                            label = { Text(preset.nameAr) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ضغط السكين (Force)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${state.selectedMaterial.forceGrams} غرام",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = state.selectedMaterial.forceGrams.toFloat(),
                        onValueChange = { viewModel.updateActiveCutParameters(forceGrams = it.roundToInt()) },
                        valueRange = 10f..450f,
                        modifier = Modifier.testTag("blade_force_slider")
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("سرعة القص (Speed)", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${state.selectedMaterial.speedMmPerSec} مم/ث",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = state.selectedMaterial.speedMmPerSec.toFloat(),
                        onValueChange = { viewModel.updateActiveCutParameters(speedMmSec = it.roundToInt()) },
                        valueRange = 20f..600f,
                        modifier = Modifier.testTag("blade_speed_slider")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("عدد التكرارات (Passes):", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { passCount ->
                            FilterChip(
                                selected = state.selectedMaterial.passes == passCount,
                                onClick = { viewModel.updateActiveCutParameters(passes = passCount) },
                                label = { Text("${passCount}×") }
                            )
                        }
                    }
                }
            }
        }

        // 3. Jog Head & Origin Calibration Pad + Live Path Preview
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تحريك الرأس ونقطة الأصل", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1f, 10f, 50f).forEach { step ->
                            FilterChip(
                                selected = jogStepMm == step,
                                onClick = { jogStepMm = step },
                                label = { Text("${step.toInt()}mm") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Directional Jog D-Pad
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLow,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(10.dp)
                    ) {
                        FilledTonalIconButton(
                            onClick = { viewModel.sendJog(0f, -jogStepMm) },
                            modifier = Modifier.size(48.dp).testTag("jog_up_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "تحريك للأعلى")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalIconButton(
                                onClick = { viewModel.sendJog(-jogStepMm, 0f) },
                                modifier = Modifier.size(48.dp).testTag("jog_left_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "تحريك لليسار")
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.sendHome() },
                                modifier = Modifier.size(48.dp).testTag("jog_home_button")
                            ) {
                                Icon(Icons.Default.Home, contentDescription = "العودة للأصل")
                            }
                            FilledTonalIconButton(
                                onClick = { viewModel.sendJog(jogStepMm, 0f) },
                                modifier = Modifier.size(48.dp).testTag("jog_right_button")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "تحريك لليمين")
                            }
                        }
                        FilledTonalIconButton(
                            onClick = { viewModel.sendJog(0f, jogStepMm) },
                            modifier = Modifier.size(48.dp).testTag("jog_down_button")
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "تحريك للأسفل")
                        }
                    }

                    // Origin & Test Cut Quick Buttons
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.sendSetOrigin() },
                            modifier = Modifier.fillMaxWidth().testTag("set_origin_button")
                        ) {
                            Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("تعيين نقطة الأصل", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { viewModel.sendTestSquare() },
                            modifier = Modifier.fillMaxWidth().testTag("test_square_button")
                        ) {
                            Icon(Icons.Default.CropSquare, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("قص اختبار 20×20", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = { viewModel.sendFrameBoundingBoxTest() },
                            enabled = state.elements.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().testTag("test_frame_button")
                        ) {
                            Icon(Icons.Default.CropFree, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("فحص حدود الإطار (بدون قص)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        ) {
                            CuttingMatCanvas(
                                elements = state.elements,
                                selectedElementId = null,
                                matConfig = state.matConfig,
                                geometryEngine = viewModel.geometryEngine,
                                simulatedHeadMm = state.simulatedHeadMm,
                                zoomScale = 1f,
                                panOffset = Offset.Zero,
                                onZoomPanChange = { _, _ -> },
                                onSelectElement = {},
                                onDragElementDelta = { _, _, _, _ -> },
                                interactive = false,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // 4. Cut Execution & Export Control Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("إرسال مهمة القص", style = MaterialTheme.typography.titleMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = state.cutStatus.labelAr,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                // Telemetry Summary Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TelemetryMiniCard(
                        label = "طول القص",
                        value = "${(state.cutPlan.cutDistanceMm / 10f).roundToInt() / 100f} م",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniCard(
                        label = if (state.matConfig.optimizeCutOrder && state.cutPlan.savedTravelPercent > 0)
                            "توفير الحركة" else "الأوامر",
                        value = if (state.matConfig.optimizeCutOrder && state.cutPlan.savedTravelPercent > 0)
                            "${state.cutPlan.savedTravelPercent}%" else "${state.cutPlan.commands.size}",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMiniCard(
                        label = "الزمن المتوقع",
                        value = "${state.cutPlan.estimatedSeconds} ث",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Pro Cut Optimization Panel (Path Sort, Overcut Compensation, Cut Selected)
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text("تحسينات القص الاحترافية", style = MaterialTheme.typography.labelLarge)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تحسين مسار الرأس الذكي", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = "ترتيب المسارات للأقرب (${(state.cutPlan.travelDistanceMm / 10f).roundToInt() / 100f} م انتقال)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = state.matConfig.optimizeCutOrder,
                                onCheckedChange = { viewModel.toggleOptimizeCutOrder() },
                                modifier = Modifier.testTag("optimize_cut_switch")
                            )
                        }

                        if (state.selectedElements.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("قص المحدد فقط (${state.selectedElements.size})", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "إرسال العناصر المحددة على اللوحة دون البقية",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = state.matConfig.cutOnlySelected,
                                    onCheckedChange = { viewModel.toggleCutOnlySelected() },
                                    modifier = Modifier.testTag("cut_selected_switch")
                                )
                            }
                        }

                        // SignMaster Vinyl Spooler Cut-by-Color Isolation
                        val visibleColorGroups = remember(state.elements) {
                            state.elements
                                .filter { it.isVisible }
                                .groupBy { el -> el.fillColorHex ?: el.strokeColorHex }
                                .map { (hex, list) -> hex to list.size }
                        }
                        if (visibleColorGroups.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "القص حسب اللون (SignMaster Vinyl Spooler):",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val visibleCount = state.elements.count { it.isVisible }
                                    FilterChip(
                                        selected = state.matConfig.cutColorFilterHex == null,
                                        onClick = { viewModel.updateCutColorFilter(null) },
                                        label = { Text("الكل ($visibleCount)") },
                                        modifier = Modifier.testTag("cut_by_color_all_chip")
                                    )
                                    visibleColorGroups.forEachIndexed { idx, (hex, count) ->
                                        val isActive = state.matConfig.cutColorFilterHex == hex
                                        FilterChip(
                                            selected = isActive,
                                            onClick = {
                                                viewModel.updateCutColorFilter(if (isActive) null else hex)
                                            },
                                            leadingIcon = {
                                                Box(
                                                    modifier = Modifier
                                                        .size(14.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(hex))
                                                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                                )
                                            },
                                            label = { Text("رول ($count)") },
                                            modifier = Modifier.testTag("cut_by_color_chip_$idx")
                                        )
                                    }
                                }
                            }
                        }

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("تمديد الإغلاق (Overcut)", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = String.format("%.1f مم · سن %.2f", state.matConfig.overcutMm, state.selectedMaterial.bladeOffsetMm),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = state.matConfig.overcutMm,
                                onValueChange = { viewModel.updateOvercutMm(it) },
                                valueRange = 0f..1.5f,
                                steps = 14,
                                modifier = Modifier.testTag("overcut_slider")
                            )
                        }
                    }
                }

                if (state.cutStatus == CutExecutionStatus.CUTTING ||
                    state.cutStatus == CutExecutionStatus.PAUSED ||
                    state.cutStatus == CutExecutionStatus.DRY_RUN
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { state.cutProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text(
                            text = "التقدم: ${state.cutSentCommands} / ${state.cutTotalCommands} (${(state.cutProgress * 100).roundToInt()}%)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                val isCuttingOrDryRun = state.cutStatus == CutExecutionStatus.CUTTING ||
                    state.cutStatus == CutExecutionStatus.PAUSED ||
                    state.cutStatus == CutExecutionStatus.DRY_RUN

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.startRealOtgCut() },
                        enabled = !isCuttingOrDryRun && state.elements.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(50.dp)
                            .testTag("start_otg_cut_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("بدء القص الآن", fontWeight = FontWeight.Bold)
                    }

                    FilledTonalButton(
                        onClick = { viewModel.startVisualDryRun() },
                        enabled = !isCuttingOrDryRun && state.elements.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("dry_run_preview_button")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("معاينة المسار")
                    }
                }

                AnimatedVisibility(visible = isCuttingOrDryRun) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.togglePauseCut() },
                            modifier = Modifier.weight(1f).height(48.dp).testTag("pause_cut_button")
                        ) {
                            Icon(Icons.Default.Pause, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.cutStatus == CutExecutionStatus.PAUSED) "استئناف" else "إيقاف مؤقت")
                        }
                        Button(
                            onClick = { viewModel.emergencyStop() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            ),
                            modifier = Modifier.weight(1f).height(48.dp).testTag("emergency_stop_button")
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("إيقاف طارئ", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Export Cut Files (.plt / .gcode / .svg)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val ext = state.serialConfig.protocol.fileExtension
                            exportCutFileLauncher.launch("cutter_job.$ext")
                        },
                        enabled = state.elements.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("export_cut_file_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير .${state.serialConfig.protocol.fileExtension}")
                    }
                    OutlinedButton(
                        onClick = { exportSvgLauncher.launch("cutter_design.svg") },
                        enabled = state.elements.isNotEmpty(),
                        modifier = Modifier.weight(1f).testTag("export_svg_file_button")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تصدير SVG")
                    }
                }
            }
        }

        // 5. Expandable OTG Serial Monitor Console
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(18.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Terminal, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("سجل أوامر الاتصال التسلسلي", style = MaterialTheme.typography.titleSmall)
                    }
                    Row {
                        if (showSerialConsole && state.serialLogs.isNotEmpty()) {
                            TextButton(onClick = { viewModel.clearSerialLogs() }) {
                                Text("مسح")
                            }
                        }
                        IconButton(
                            onClick = { showSerialConsole = !showSerialConsole },
                            modifier = Modifier.testTag("toggle_serial_console_button")
                        ) {
                            Icon(
                                if (showSerialConsole) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "فتح أو إغلاق السجل"
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = showSerialConsole) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerLowest,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (state.serialLogs.isEmpty()) {
                            Text(
                                text = "لا توجد أوامر مرسلة بعد. عند توصيل الماكينة ستظهر أوامر HPGL/GPGL/GRBL هنا.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            state.serialLogs.takeLast(18).forEach { entry ->
                                val prefixColor = when (entry.direction) {
                                    "TX" -> MaterialTheme.colorScheme.primary
                                    "RX" -> CutEmerald
                                    else -> MaterialTheme.colorScheme.secondary
                                }
                                Text(
                                    text = "[${entry.direction}] ${entry.message}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                    color = prefixColor
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MachineConnectionCard(
    state: CutterUiState,
    onSelectTransport: (ConnectionTransport) -> Unit,
    onRefreshUsb: () -> Unit,
    onConnectUsb: (Int) -> Unit,
    onConnectWifi: (String, Int) -> Unit,
    hasBluetoothPermissions: () -> Boolean,
    onRefreshBluetooth: () -> Unit,
    onConnectBluetooth: (String) -> Unit,
    onDisconnect: () -> Unit,
    onSelectProtocol: (PlotterProtocol) -> Unit,
    onSelectBaudRate: (Int) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val isConnected = state.connectionState is MachineConnectionState.Connected
    var wifiHostInput by remember { mutableStateOf(state.serialConfig.wifiHost) }
    var wifiPortInput by remember { mutableStateOf(state.serialConfig.wifiPort.toString()) }

    LaunchedEffect(state.serialConfig.wifiHost, state.serialConfig.wifiPort) {
        if (wifiHostInput.isBlank()) wifiHostInput = state.serialConfig.wifiHost
        if (wifiPortInput.isBlank()) wifiPortInput = state.serialConfig.wifiPort.toString()
    }

    val btPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results.values.all { it }
        if (granted) {
            onRefreshBluetooth()
        } else {
            onShowMessage("يلزم منح إذن البلوتوث لعرض والاتصال بماكينات البلوتر المقترنة.")
        }
    }

    fun requestOrRefreshBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !hasBluetoothPermissions()) {
            btPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            )
        } else {
            onRefreshBluetooth()
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header + Status Dot + Disconnect Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(
                                when (state.connectionState) {
                                    is MachineConnectionState.Connected -> CutEmerald
                                    is MachineConnectionState.Error -> MaterialTheme.colorScheme.error
                                    is MachineConnectionState.Connecting -> MaterialTheme.colorScheme.secondary
                                    MachineConnectionState.Disconnected -> MaterialTheme.colorScheme.outline
                                }
                            )
                    )
                    Column {
                        Text("ربط الماكينة والاتصال", style = MaterialTheme.typography.titleMedium)
                        val statusText = when (val cs = state.connectionState) {
                            is MachineConnectionState.Connected ->
                                "متصل (${cs.transport.labelAr}): ${cs.deviceName} · ${cs.detailsAr}"
                            is MachineConnectionState.Connecting ->
                                cs.labelAr
                            is MachineConnectionState.Error ->
                                cs.messageAr
                            MachineConnectionState.Disconnected ->
                                when (state.selectedTransport) {
                                    ConnectionTransport.USB_OTG_CDC ->
                                        if (state.isUsbHostSupported) "جاهز — أوصل كابل USB OTG / CDC واضغط فحص"
                                        else "هذا الهاتف لا يدعم وضع USB Host OTG"
                                    ConnectionTransport.WIFI_TCP ->
                                        "أدخل عنوان IP ورقم المنفذ للماكينة ثم اضغط اتصال"
                                    ConnectionTransport.BLUETOOTH_SPP ->
                                        if (state.isBluetoothSupported) "اختر ماكينة مقترنة عبر البلوتوث للاتصال"
                                        else "البلوتوث غير مدعوم على هذا الجهاز"
                                }
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isConnected) CutEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isConnected) {
                    OutlinedButton(
                        onClick = onDisconnect,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("otg_disconnect_button")
                    ) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("فصل")
                    }
                }
            }

            // Transport Mode Switcher (USB OTG/CDC | Wi-Fi | Bluetooth)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("وسيلة الاتصال:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ConnectionTransport.entries.forEach { transport ->
                        val icon = when (transport) {
                            ConnectionTransport.USB_OTG_CDC -> Icons.Default.Cable
                            ConnectionTransport.WIFI_TCP -> Icons.Default.Wifi
                            ConnectionTransport.BLUETOOTH_SPP -> Icons.Default.Bluetooth
                        }
                        FilterChip(
                            selected = state.selectedTransport == transport,
                            onClick = {
                                onSelectTransport(transport)
                                if (transport == ConnectionTransport.BLUETOOTH_SPP) {
                                    requestOrRefreshBluetooth()
                                }
                            },
                            leadingIcon = {
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            label = { Text(transport.labelAr) },
                            modifier = Modifier.testTag("transport_chip_${transport.name.lowercase()}")
                        )
                    }
                }
            }

            // Transport-specific connection panel when not connected
            if (!isConnected) {
                when (state.selectedTransport) {
                    ConnectionTransport.USB_OTG_CDC -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أجهزة USB OTG / CDC المتصلة:",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FilledTonalButton(
                                onClick = onRefreshUsb,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("otg_scan_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("فحص USB")
                            }
                        }

                        if (state.attachedDevices.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Cable,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "لم يتم اكتشاف ماكينة عبر USB OTG / CDC حالياً. أدخل الكابل وشغّل الماكينة ثم اضغط فحص USB.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.attachedDevices.forEach { device ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = device.productName,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${device.chipFamily.labelAr} · ${device.vidPidFormatted}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Button(
                                                onClick = { onConnectUsb(device.deviceId) },
                                                modifier = Modifier.testTag("connect_usb_device_${device.deviceId}")
                                            ) {
                                                Text(if (device.hasPermission) "اتصال" else "منح إذن واتصال")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ConnectionTransport.WIFI_TCP -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = wifiHostInput,
                                    onValueChange = { wifiHostInput = it },
                                    label = { Text("عنوان IP للماكينة") },
                                    placeholder = { Text("192.168.1.100") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    modifier = Modifier
                                        .weight(1.7f)
                                        .testTag("wifi_host_input")
                                )
                                OutlinedTextField(
                                    value = wifiPortInput,
                                    onValueChange = { text ->
                                        wifiPortInput = text.filter { it.isDigit() }.take(5)
                                    },
                                    label = { Text("المنفذ") },
                                    placeholder = { Text("9100") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("wifi_port_input")
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(9100, 23, 8080).forEach { presetPort ->
                                        FilterChip(
                                            selected = wifiPortInput == presetPort.toString(),
                                            onClick = { wifiPortInput = presetPort.toString() },
                                            label = { Text("$presetPort") }
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        val parsedPort = wifiPortInput.toIntOrNull() ?: 9100
                                        onConnectWifi(wifiHostInput, parsedPort)
                                    },
                                    modifier = Modifier.testTag("connect_wifi_button")
                                ) {
                                    Icon(Icons.Default.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("اتصال Wi-Fi")
                                }
                            }
                        }
                    }

                    ConnectionTransport.BLUETOOTH_SPP -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "أجهزة البلوتوث المقترنة (SPP):",
                                style = MaterialTheme.typography.labelLarge
                            )
                            FilledTonalButton(
                                onClick = { requestOrRefreshBluetooth() },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("bluetooth_scan_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("فحص البلوتوث")
                            }
                        }

                        if (!state.isBluetoothSupported) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "هذا الجهاز لا يحتوي على محول بلوتوث.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                )
                            }
                        } else if (state.pairedBluetoothDevices.isEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Bluetooth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "لا توجد أجهزة مقترنة ظاهرة حالياً. قم بإقران الماكينة من إعدادات بلوتوث الهاتف ثم اضغط فحص البلوتوث.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.pairedBluetoothDevices.forEachIndexed { idx, btDevice ->
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = btDevice.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = btDevice.address,
                                                    style = MaterialTheme.typography.labelSmall.copy(
                                                        fontFamily = JetBrainsMonoFontFamily
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Button(
                                                onClick = { onConnectBluetooth(btDevice.address) },
                                                modifier = Modifier.testTag("connect_bt_device_$idx")
                                            ) {
                                                Text("اتصال")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Protocol & Baud Rate selectors
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("بروتوكول الماكينة:", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PlotterProtocol.entries.forEach { proto ->
                        FilterChip(
                            selected = state.serialConfig.protocol == proto,
                            onClick = { onSelectProtocol(proto) },
                            label = { Text(proto.displayName) },
                            leadingIcon = if (state.serialConfig.protocol == proto) {
                                { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            modifier = Modifier.testTag("protocol_chip_${proto.name.lowercase()}")
                        )
                    }
                }
                Text(
                    text = state.serialConfig.protocol.subtitleAr,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (state.selectedTransport == ConnectionTransport.USB_OTG_CDC) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("سرعة النقل التسلسلي (Baud Rate):", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(9600, 19200, 38400, 57600, 115200).forEach { baud ->
                            FilterChip(
                                selected = state.serialConfig.baudRate == baud,
                                onClick = { onSelectBaudRate(baud) },
                                label = { Text("$baud") },
                                modifier = Modifier.testTag("baud_chip_$baud")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMiniCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
