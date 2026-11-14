package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DesignElement
import com.example.domain.model.VectorBooleanOp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Modular Material 3 bottom sheet for advanced vector path manipulation:
 * - Millimeter-scale Curve & Node Simplification (Douglas-Peucker) with live node estimate
 * - Chaikin Curve Smoothing & Open-Curve Auto-Closing
 * - SignMaster Boolean Shaping (Weld / Punch Out / Intersect)
 * - Sticker Contour Offset (Die-cut outer border expansion) & Flip transforms
 */
@Composable
fun PathToolsBottomSheetContent(
    selectedElements: List<DesignElement>,
    pathStats: Pair<Int, Int>,
    previewSimplifiedCount: (Float) -> Int,
    onApplySimplify: (Float) -> Unit,
    onSmoothCurves: () -> Unit,
    onCloseOpenCurves: () -> Unit,
    onBooleanOp: (VectorBooleanOp) -> Unit,
    onCreateContourOffset: (Float) -> Unit,
    onToggleMirrorHorizontal: () -> Unit,
    onToggleMirrorVertical: () -> Unit,
    onCloseSheet: () -> Unit
) {
    var toleranceMm by remember { mutableFloatStateOf(0.8f) }
    var offsetMm by remember { mutableFloatStateOf(4.0f) }

    val (totalNodes, openCurvesCount) = pathStats
    val estimatedAfter = remember(selectedElements, toleranceMm) {
        previewSimplifiedCount(toleranceMm)
    }
    val reductionPct = remember(totalNodes, estimatedAfter) {
        if (totalNodes > 0) (((totalNodes - estimatedAfter).coerceAtLeast(0).toFloat() / totalNodes) * 100f).roundToInt()
        else 0
    }
    val single = selectedElements.singleOrNull()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Gesture, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "أدوات المنحنيات وتطوير التصميم",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "$totalNodes عقدة · $openCurvesCount مفتوح",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // 1. Simplify Curves & Nodes Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Compress,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("تبسيط المنحنيات والعقد", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = String.format(Locale.US, "%.1f مم", toleranceMm),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Slider(
                    value = toleranceMm,
                    onValueChange = { toleranceMm = it },
                    valueRange = 0.2f..4.0f,
                    modifier = Modifier.testTag("simplify_tolerance_slider")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "المتوقع: $totalNodes ← $estimatedAfter عقدة (تقليل $reductionPct%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { onApplySimplify(toleranceMm) },
                        modifier = Modifier.testTag("apply_simplify_button")
                    ) {
                        Text("تبسيط العقد")
                    }
                }
            }
        }

        // 2. Smooth & Close Curves Card
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("تحسين وإغلاق المنحنيات", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onSmoothCurves,
                        modifier = Modifier.weight(1f).testTag("sheet_smooth_curves_button")
                    ) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("تنعيم الزوايا", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    FilledTonalButton(
                        onClick = onCloseOpenCurves,
                        modifier = Modifier.weight(1f).testTag("sheet_close_curves_button")
                    ) {
                        Icon(Icons.Default.Loop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (openCurvesCount > 0) "إغلاق ($openCurvesCount)" else "إغلاق المنحنيات",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 2b. SignMaster Boolean Shaping (Weld, Punch Out, Intersect)
        if (selectedElements.size >= 2) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Layers,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("التشكيل المنطقي (SignMaster Shaping)", style = MaterialTheme.typography.titleMedium)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onBooleanOp(VectorBooleanOp.UNION) },
                            modifier = Modifier.weight(1f).testTag("shaping_weld_button")
                        ) {
                            Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("لحام", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onBooleanOp(VectorBooleanOp.DIFFERENCE) },
                            modifier = Modifier.weight(1f).testTag("punch_out_button")
                        ) {
                            Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("تفريغ", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onBooleanOp(VectorBooleanOp.INTERSECT) },
                            modifier = Modifier.weight(1f).testTag("intersect_button")
                        ) {
                            Text("تقاطع", maxLines = 1)
                        }
                    }
                }
            }
        }

        // 3. Design Pro Upgrades: Sticker Contour Offset + Flip
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.CropFree,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text("حد خارجي للملصقات (Offset)", style = MaterialTheme.typography.titleMedium)
                    }
                    Text(
                        text = "+${String.format(Locale.US, "%.1f", offsetMm)} مم",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Slider(
                    value = offsetMm,
                    onValueChange = { offsetMm = it },
                    valueRange = 1.5f..12.0f,
                    modifier = Modifier.testTag("contour_offset_slider")
                )

                Button(
                    onClick = { onCreateContourOffset(offsetMm) },
                    enabled = single != null,
                    modifier = Modifier.fillMaxWidth().testTag("create_contour_offset_button")
                ) {
                    Text(
                        text = if (single != null) "إنشاء حد قص خارجي للملصق"
                        else "حدد عنصراً واحداً لإنشاء حد خارجي"
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onToggleMirrorHorizontal,
                        modifier = Modifier.weight(1f).testTag("sheet_flip_horizontal_button"),
                        colors = if (single?.mirrorHorizontal == true) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.Flip, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("عكس أفقي (HTV)", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    OutlinedButton(
                        onClick = onToggleMirrorVertical,
                        modifier = Modifier.weight(1f).testTag("sheet_flip_vertical_button"),
                        colors = if (single?.mirrorVertical == true) {
                            ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                        } else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Icon(Icons.Default.SwapVert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("عكس عمودي", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }

        Button(
            onClick = onCloseSheet,
            modifier = Modifier.fillMaxWidth().testTag("close_path_tools_button")
        ) {
            Text("تم", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}
