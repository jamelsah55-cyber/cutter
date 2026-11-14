package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.DesignElement
import kotlin.math.roundToInt

/**
 * SignMaster-style Object Manager / Layers Inspector sheet.
 * Displays top-most layer first with live fill/stroke badge, dimensions, visibility toggle,
 * lock toggle, Z-order reordering, and click-to-select.
 */
@Composable
fun ObjectManagerSheetContent(
    elements: List<DesignElement>,
    selectedIds: Set<String>,
    onSelectElement: (String) -> Unit,
    onToggleVisibility: (String) -> Unit,
    onToggleLock: (String) -> Unit,
    onMoveForward: (String) -> Unit,
    onMoveBackward: (String) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                Icon(Icons.Default.Layers, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = "مدير العناصر والطبقات (Object Manager)",
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }

        if (elements.isEmpty()) {
            Text(
                text = "لا توجد عناصر على الحصيرة حالياً.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val reversed = elements.asReversed()
            reversed.forEachIndexed { revIdx, el ->
                val isSelected = el.id in selectedIds
                val canMoveForward = revIdx > 0
                val canMoveBackward = revIdx < reversed.lastIndex
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectElement(el.id) }
                        .testTag("layer_item_${el.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(el.fillColorHex?.let { Color(it) } ?: Color.Transparent)
                                .border(2.5.dp, Color(el.strokeColorHex), CircleShape)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = el.name,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${el.widthMm.roundToInt()}×${el.heightMm.roundToInt()} مم" +
                                    if (!el.isVisible) " · مخفي" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onMoveForward(el.id) },
                            enabled = canMoveForward
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "رفع الطبقة")
                        }
                        IconButton(
                            onClick = { onMoveBackward(el.id) },
                            enabled = canMoveBackward
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "خفض الطبقة")
                        }
                        IconButton(
                            onClick = { onToggleVisibility(el.id) },
                            modifier = Modifier.testTag("layer_visibility_${el.id}")
                        ) {
                            Icon(
                                if (el.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = if (el.isVisible) "إخفاء" else "إظهار",
                                tint = if (el.isVisible) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { onToggleLock(el.id) },
                            modifier = Modifier.testTag("layer_lock_${el.id}")
                        ) {
                            Icon(
                                if (el.isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = if (el.isLocked) "فك القفل" else "قفل",
                                tint = if (el.isLocked) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().testTag("close_object_manager_button")
        ) {
            Text("إغلاق", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
    }
}
