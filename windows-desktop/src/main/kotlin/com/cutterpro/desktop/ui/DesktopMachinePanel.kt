package com.cutterpro.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cutterpro.desktop.model.CutExecutionStatus
import com.cutterpro.desktop.model.DefaultMaterialPresets
import com.cutterpro.desktop.model.DesktopTransportType
import com.cutterpro.desktop.model.PlotterProtocol
import com.cutterpro.desktop.state.DesktopStudioController
import com.cutterpro.desktop.state.DesktopUiState
import kotlin.math.roundToInt

@Composable
fun DesktopMachinePanel(
    state: DesktopUiState,
    controller: DesktopStudioController,
    onExportCutFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    var wifiHost by remember { mutableStateOf("192.168.1.50") }
    var wifiPort by remember { mutableStateOf("9100") }
    var jogStepMm by remember { mutableStateOf(10f) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Transport Card (USB COM via jSerialComm or Wi-Fi TCP)
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("اتصال الماكينة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    if (state.connectedLabel != null) {
                        Text(state.connectedLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("غير متصل", style = MaterialTheme.typography.labelSmall, color = Color(0xFF94A3B8))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    DesktopTransportType.entries.forEach { t ->
                        FilterChip(
                            selected = state.transportType == t,
                            onClick = { controller.selectTransport(t) },
                            label = { Text(if (t == DesktopTransportType.SERIAL_COM) "USB / COM" else "Wi-Fi", maxLines = 1) },
                            leadingIcon = {
                                Icon(
                                    if (t == DesktopTransportType.SERIAL_COM) Icons.Default.Cable else Icons.Default.Wifi,
                                    contentDescription = t.labelAr,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (state.transportType == DesktopTransportType.SERIAL_COM) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("منافذ COM المتاحة (${state.serialPorts.size}):", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { controller.transport.refreshSerialPorts() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "تحديث المنافذ", modifier = Modifier.size(18.dp))
                        }
                    }

                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(9600, 19200, 38400, 115200).forEach { baud ->
                            FilterChip(
                                selected = state.baudRate == baud,
                                onClick = { controller.selectBaudRate(baud) },
                                label = { Text("$baud") }
                            )
                        }
                    }

                    if (state.serialPorts.isEmpty()) {
                        Text(
                            "لا توجد منافذ COM متصلة حالياً. وصّل كابل البلوتر ثم اضغط تحديث.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.serialPorts.forEach { port ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .clickable { controller.transport.connectSerial(port.systemPortName, state.baudRate) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(port.descriptiveName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("ربط", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(
                            value = wifiHost,
                            onValueChange = { wifiHost = it },
                            label = { Text("IP") },
                            singleLine = true,
                            modifier = Modifier.weight(1.6f)
                        )
                        OutlinedTextField(
                            value = wifiPort,
                            onValueChange = { wifiPort = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(0.9f)
                        )
                    }
                    Button(
                        onClick = { controller.transport.connectWifi(wifiHost, wifiPort.toIntOrNull() ?: 9100) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اتصال بالشبكة")
                    }
                }

                if (state.connectedLabel != null) {
                    OutlinedButton(onClick = { controller.transport.disconnect() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("قطع الاتصال")
                    }
                }
            }
        }

        // Cut-by-Color Vinyl Spooler
        val colorGroups = remember(state.elements) {
            state.elements.filter { it.isVisible }.map { it.fillColorHex ?: it.strokeColorHex }.distinct()
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("القص حسب اللون (Vinyl Spooler)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = state.matConfig.cutColorFilterHex == null,
                        onClick = { controller.updateCutColorFilter(null) },
                        label = { Text("كل الألوان") }
                    )
                    colorGroups.forEach { hex ->
                        val active = state.matConfig.cutColorFilterHex == hex
                        FilterChip(
                            selected = active,
                            onClick = { controller.updateCutColorFilter(if (active) null else hex) },
                            label = {
                                val count = state.elements.count { it.isVisible && (it.fillColorHex ?: it.strokeColorHex) == hex }
                                Text("$count عنصر")
                            },
                            leadingIcon = {
                                Box(Modifier.size(14.dp).clip(CircleShape).background(Color(hex)).border(1.dp, Color.White, CircleShape))
                            }
                        )
                    }
                }
            }
        }

        // Protocol & Material Settings
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("البروتوكول والخامة", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlotterProtocol.entries.forEach { p ->
                        FilterChip(selected = state.protocol == p, onClick = { controller.selectProtocol(p) }, label = { Text(p.displayName) })
                    }
                }
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DefaultMaterialPresets.all.forEach { m ->
                        FilterChip(
                            selected = state.selectedMaterial.nameAr == m.nameAr,
                            onClick = { controller.selectMaterial(m) },
                            label = { Text(m.nameAr) }
                        )
                    }
                }
                Text("قوة الشفرة: ${state.selectedMaterial.forceGrams} جم", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.selectedMaterial.forceGrams.toFloat(),
                    onValueChange = { controller.updateMaterialParams(force = it.roundToInt()) },
                    valueRange = 20f..400f
                )
                Text("سرعة القص: ${state.selectedMaterial.speedMmPerSec} مم/ث", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = state.selectedMaterial.speedMmPerSec.toFloat(),
                    onValueChange = { controller.updateMaterialParams(speed = it.roundToInt()) },
                    valueRange = 20f..600f
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("إطار التفريغ (Weeding Box)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = state.matConfig.weedingBoxEnabled, onCheckedChange = { controller.toggleWeeding() })
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("تحسين ترتيب المسار (${state.cutPlan.savedTravelPercent}% وفر)", style = MaterialTheme.typography.bodySmall)
                    Switch(checked = state.matConfig.optimizeCutOrder, onCheckedChange = { controller.toggleOptimizeOrder() })
                }
                Text("Overcut: ${String.format("%.1f", state.matConfig.overcutMm)} مم", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = state.matConfig.overcutMm,
                    onValueChange = { controller.updateOvercut(it) },
                    valueRange = 0f..1.2f
                )
            }
        }

        // Jog Control & Actions
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("تحريك الرأس وتنفيذ القص", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildJogCommand(state.protocol, -jogStepMm, 0f)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "يسار")
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildJogCommand(state.protocol, 0f, -jogStepMm)) }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "أعلى")
                        }
                        IconButton(onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildHomeCommand(state.protocol)) }) {
                            Icon(Icons.Default.Home, contentDescription = "نقطة البداية")
                        }
                        IconButton(onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildJogCommand(state.protocol, 0f, jogStepMm)) }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "أسفل")
                        }
                    }
                    IconButton(onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildJogCommand(state.protocol, jogStepMm, 0f)) }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "يمين")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildSetOriginCommand(state.protocol)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.MyLocation, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نقطة صفر", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { controller.transport.sendImmediate(controller.commandGenerator.buildTestSquareCommand(state.protocol, state.selectedMaterial)) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.CropSquare, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مربع تجربة", maxLines = 1)
                    }
                }

                Text(
                    "مسافة القص: ${state.cutPlan.cutDistanceMm.roundToInt()} مم · ${state.cutPlan.commands.size} أمر",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (state.cutStatus != CutExecutionStatus.IDLE) {
                    LinearProgressIndicator(progress = { state.cutProgress }, modifier = Modifier.fillMaxWidth())
                    Text("${state.cutStatus.labelAr} (${(state.cutProgress * 100).roundToInt()}%)", style = MaterialTheme.typography.labelSmall)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { controller.transport.startRealCut(state.cutPlan, state.protocol, state.selectedMaterial) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ابدأ القص")
                    }
                    OutlinedButton(
                        onClick = { controller.transport.startDryRun(state.cutPlan) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("محاكاة")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    if (state.cutStatus == CutExecutionStatus.CUTTING || state.cutStatus == CutExecutionStatus.PAUSED) {
                        OutlinedButton(onClick = { controller.transport.togglePause() }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (state.cutStatus == CutExecutionStatus.PAUSED) "استئناف" else "إيقاف مؤقت")
                        }
                    }
                    Button(
                        onClick = { controller.transport.emergencyStop(state.protocol) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("طوارئ")
                    }
                }

                OutlinedButton(onClick = onExportCutFile, modifier = Modifier.fillMaxWidth()) {
                    Text("حفظ ملف قص (${state.protocol.fileExtension.uppercase()})")
                }
            }
        }

        // Serial Console
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0F18))) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("سجل الأوامر (Serial Monitor)", style = MaterialTheme.typography.labelMedium, color = Color(0xFF94A3B8))
                    TextButton(onClick = { controller.transport.clearLogs() }) { Text("مسح") }
                }
                if (state.serialLogs.isEmpty()) {
                    Text("لا توجد أوامر مرسلة بعد.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF64748B))
                } else {
                    state.serialLogs.takeLast(12).forEach { log ->
                        Text(
                            "[${log.direction}] ${log.message}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (log.direction) {
                                "ERR" -> Color(0xFFF87171)
                                "SYS" -> Color(0xFF38BDF8)
                                else -> Color(0xFF34D399)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        HorizontalDivider()
        Text(
            "كتر برو Desktop v1.0 · تطوير: جميل السيد احمد",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
