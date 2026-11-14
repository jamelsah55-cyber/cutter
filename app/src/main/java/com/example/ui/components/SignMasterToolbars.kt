package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DesignElement
import com.example.domain.model.VinylColorPalettes
import com.example.ui.theme.JetBrainsMonoFontFamily
import kotlin.math.roundToInt

/**
 * SignMaster-inspired live coordinate & view inspector ribbon at top of canvas:
 * Displays active X, Y, Width, Height, Angle readouts (tappable to open numeric inspector),
 * Wireframe/Solid toggle, and Object Manager launcher.
 */
@Composable
fun SignMasterTopPropertyRibbon(
    selectedElements: List<DesignElement>,
    totalElementsCount: Int,
    isWireframeMode: Boolean,
    onToggleWireframe: () -> Unit,
    onOpenObjectManager: () -> Unit,
    onOpenNumericInspector: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (selectedElements.isNotEmpty()) {
                val minX = selectedElements.minOf { it.xMm }.roundToInt()
                val minY = selectedElements.minOf { it.yMm }.roundToInt()
                val maxX = selectedElements.maxOf { it.xMm + it.widthMm }
                val maxY = selectedElements.maxOf { it.yMm + it.heightMm }
                val wMm = (maxX - selectedElements.minOf { it.xMm }).roundToInt()
                val hMm = (maxY - selectedElements.minOf { it.yMm }).roundToInt()
                val single = selectedElements.singleOrNull()

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = single != null, onClick = onOpenNumericInspector)
                        .testTag("signmaster_coords_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelemetryCoordTag("X", "$minX")
                        TelemetryCoordTag("Y", "$minY")
                        TelemetryCoordTag("W", "${wMm}mm")
                        TelemetryCoordTag("H", "${hMm}mm")
                        if (single != null) {
                            TelemetryCoordTag("∠", "${single.rotationDeg.roundToInt()}°")
                            Icon(
                                Icons.Default.Tune,
                                contentDescription = "تعديل الأبعاد",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "SignMaster CAD · حدد عنصراً للمعاينة",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            FilterChip(
                selected = isWireframeMode,
                onClick = onToggleWireframe,
                label = { Text(if (isWireframeMode) "خطوط القص" else "معاينة ممتلئة") },
                leadingIcon = {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                modifier = Modifier.testTag("wireframe_toggle_chip")
            )

            FilterChip(
                selected = false,
                onClick = onOpenObjectManager,
                label = { Text("الطبقات ($totalElementsCount)") },
                leadingIcon = {
                    Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                },
                modifier = Modifier.testTag("object_manager_button")
            )
        }
    }
}

@Composable
private fun TelemetryCoordTag(prefix: String, value: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = "$prefix:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.SemiBold
            ),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * Classic SignMaster vertical left/start docked CAD Toolbox rail:
 * Select, Node Edit, Text, Shapes, Freehand, Trace Image, Offset Border, Import SVG.
 */
@Composable
fun SignMasterSideToolbox(
    isSelectMode: Boolean,
    isNodeEditMode: Boolean,
    isFreehandMode: Boolean,
    hasSingleSelection: Boolean,
    onSelectTool: () -> Unit,
    onNodeEditTool: () -> Unit,
    onAddText: () -> Unit,
    onAddShape: () -> Unit,
    onToggleFreehand: () -> Unit,
    onTraceImage: () -> Unit,
    onOpenPathTools: () -> Unit,
    onImportSvg: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 3.dp,
        modifier = modifier
            .width(58.dp)
            .fillMaxHeight()
            .testTag("signmaster_side_toolbox")
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            SideToolboxItem(
                icon = Icons.Default.NearMe,
                label = "تحديد",
                selected = isSelectMode,
                onClick = onSelectTool,
                modifier = Modifier.testTag("side_tool_select")
            )
            SideToolboxItem(
                icon = Icons.Default.Edit,
                label = "العقد",
                selected = isNodeEditMode,
                enabled = hasSingleSelection,
                onClick = onNodeEditTool,
                modifier = Modifier.testTag("side_tool_node_edit")
            )
            SideToolboxItem(
                icon = Icons.Default.TextFields,
                label = "نص",
                onClick = onAddText,
                modifier = Modifier.testTag("add_text_button")
            )
            SideToolboxItem(
                icon = Icons.Default.Category,
                label = "أشكال",
                onClick = onAddShape,
                modifier = Modifier.testTag("add_shape_button")
            )
            SideToolboxItem(
                icon = Icons.Default.Draw,
                label = "رسم",
                selected = isFreehandMode,
                onClick = onToggleFreehand,
                modifier = Modifier.testTag("side_tool_freehand")
            )
            SideToolboxItem(
                icon = Icons.Default.ImageSearch,
                label = "تتبع",
                onClick = onTraceImage,
                modifier = Modifier.testTag("trace_image_button")
            )
            SideToolboxItem(
                icon = Icons.Default.CropFree,
                label = "حد/قص",
                enabled = hasSingleSelection,
                onClick = onOpenPathTools,
                modifier = Modifier.testTag("side_tool_offset")
            )
            SideToolboxItem(
                icon = Icons.Default.FileOpen,
                label = "SVG",
                onClick = onImportSvg,
                modifier = Modifier.testTag("import_svg_button")
            )
        }
    }
}

@Composable
private fun SideToolboxItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true
) {
    val bgColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 5.dp, horizontal = 2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Classic SignMaster docked vinyl swatch bar anchored directly beneath the canvas.
 * Supports 1-tap switching between Fill Color and Cut-Line Stroke Color modes.
 */
@Composable
fun SignMasterBottomColorStrip(
    selectedElements: List<DesignElement>,
    applyToStroke: Boolean,
    onToggleMode: () -> Unit,
    onSelectFill: (Long?) -> Unit,
    onSelectStroke: (Long) -> Unit,
    onOpenFullPalette: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasSelection = selectedElements.isNotEmpty()
    val activeFill = selectedElements.firstOrNull()?.fillColorHex
    val activeStroke = selectedElements.firstOrNull()?.strokeColorHex

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("signmaster_color_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Mode Switch: Fill vs Cut Line
            Surface(
                color = if (applyToStroke) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onToggleMode)
                    .testTag("color_strip_mode_toggle")
            ) {
                Text(
                    text = if (applyToStroke) "خط القص" else "تعبئة",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (applyToStroke) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }

            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!applyToStroke) {
                    val isNoneSelected = hasSelection && activeFill == null
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                width = if (isNoneSelected) 2.2.dp else 1.dp,
                                color = if (isNoneSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = hasSelection) { onSelectFill(null) }
                            .testTag("color_strip_none_swatch")
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "بدون تعبئة",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                val swatches = if (applyToStroke) VinylColorPalettes.cutLineSwatches else VinylColorPalettes.fillSwatches
                swatches.forEachIndexed { idx, swatch ->
                    val swatchColor = Color(swatch.hex)
                    val isSel = hasSelection && if (applyToStroke) activeStroke == swatch.hex else activeFill == swatch.hex
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (applyToStroke) MaterialTheme.colorScheme.surfaceContainerLowest else swatchColor)
                            .border(
                                width = if (isSel) 3.dp else if (applyToStroke) 2.5.dp else 1.dp,
                                color = if (isSel) MaterialTheme.colorScheme.primary else if (applyToStroke) swatchColor else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable(enabled = hasSelection) {
                                if (applyToStroke) onSelectStroke(swatch.hex) else onSelectFill(swatch.hex)
                            }
                            .testTag("color_strip_swatch_$idx")
                    ) {
                        if (applyToStroke) {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(swatchColor)
                            )
                        }
                        if (isSel) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = swatch.labelAr,
                                tint = if (swatchColor.luminance() > 0.55f) Color.Black else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            IconButton(
                onClick = onOpenFullPalette,
                enabled = hasSelection,
                modifier = Modifier.testTag("open_full_palette_button")
            ) {
                Icon(Icons.Default.Palette, contentDescription = "لوح الألوان الكامل")
            }
        }
    }
}
