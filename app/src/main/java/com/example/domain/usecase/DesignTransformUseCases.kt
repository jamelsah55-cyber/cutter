package com.example.domain.usecase

import com.example.domain.model.AlignmentAction
import com.example.domain.model.CuttingMatConfig
import com.example.domain.model.DesignElement
import java.util.UUID

/**
 * Pure domain transformation use-cases for CAD design elements:
 * alignment, Z-order stacking, lock toggling, and step-and-repeat sticker array cloning.
 */
class DesignTransformUseCases {

    fun alignSelection(
        elements: List<DesignElement>,
        selectedIds: Set<String>,
        matConfig: CuttingMatConfig,
        action: AlignmentAction
    ): List<DesignElement> {
        val targets = elements.filter { it.id in selectedIds && !it.isLocked }
        if (targets.isEmpty()) return elements

        if (action == AlignmentAction.DISTRIBUTE_HORIZONTAL || action == AlignmentAction.DISTRIBUTE_VERTICAL) {
            if (targets.size < 3) return elements
            val posById = mutableMapOf<String, Pair<Float, Float>>()
            if (action == AlignmentAction.DISTRIBUTE_HORIZONTAL) {
                val sorted = targets.sortedBy { it.xMm }
                val first = sorted.first()
                val last = sorted.last()
                val totalSpan = (last.xMm + last.widthMm) - first.xMm
                val sumWidths = sorted.sumOf { it.widthMm.toDouble() }.toFloat()
                val gap = (totalSpan - sumWidths) / (sorted.size - 1)
                var cursorX = first.xMm
                sorted.forEach { el ->
                    posById[el.id] = cursorX.coerceIn(0f, (matConfig.widthMm - el.widthMm).coerceAtLeast(0f)) to el.yMm
                    cursorX += el.widthMm + gap
                }
            } else {
                val sorted = targets.sortedBy { it.yMm }
                val first = sorted.first()
                val last = sorted.last()
                val totalSpan = (last.yMm + last.heightMm) - first.yMm
                val sumHeights = sorted.sumOf { it.heightMm.toDouble() }.toFloat()
                val gap = (totalSpan - sumHeights) / (sorted.size - 1)
                var cursorY = first.yMm
                sorted.forEach { el ->
                    posById[el.id] = el.xMm to cursorY.coerceIn(0f, (matConfig.heightMm - el.heightMm).coerceAtLeast(0f))
                    cursorY += el.heightMm + gap
                }
            }
            return elements.map { el ->
                val np = posById[el.id]
                if (np != null) el.copy(xMm = np.first, yMm = np.second) else el
            }
        }

        val refMinX: Float
        val refMinY: Float
        val refMaxX: Float
        val refMaxY: Float

        if (targets.size == 1 || action == AlignmentAction.CENTER_MAT) {
            refMinX = 0f
            refMinY = 0f
            refMaxX = matConfig.widthMm
            refMaxY = matConfig.heightMm
        } else {
            refMinX = targets.minOf { it.xMm }
            refMinY = targets.minOf { it.yMm }
            refMaxX = targets.maxOf { it.xMm + it.widthMm }
            refMaxY = targets.maxOf { it.yMm + it.heightMm }
        }

        val refCenterX = (refMinX + refMaxX) / 2f
        val refCenterY = (refMinY + refMaxY) / 2f

        return elements.map { el ->
            if (el.id !in selectedIds || el.isLocked) {
                el
            } else {
                var nx = el.xMm
                var ny = el.yMm
                when (action) {
                    AlignmentAction.ALIGN_LEFT -> nx = refMinX
                    AlignmentAction.CENTER_HORIZONTAL -> nx = refCenterX - el.widthMm / 2f
                    AlignmentAction.ALIGN_RIGHT -> nx = refMaxX - el.widthMm
                    AlignmentAction.ALIGN_TOP -> ny = refMinY
                    AlignmentAction.CENTER_VERTICAL -> ny = refCenterY - el.heightMm / 2f
                    AlignmentAction.ALIGN_BOTTOM -> ny = refMaxY - el.heightMm
                    AlignmentAction.CENTER_MAT -> {
                        if (targets.size == 1) {
                            nx = refCenterX - el.widthMm / 2f
                            ny = refCenterY - el.heightMm / 2f
                        } else {
                            val groupMinX = targets.minOf { it.xMm }
                            val groupMinY = targets.minOf { it.yMm }
                            val groupW = targets.maxOf { it.xMm + it.widthMm } - groupMinX
                            val groupH = targets.maxOf { it.yMm + it.heightMm } - groupMinY
                            val dx = (matConfig.widthMm - groupW) / 2f - groupMinX
                            val dy = (matConfig.heightMm - groupH) / 2f - groupMinY
                            nx = el.xMm + dx
                            ny = el.yMm + dy
                        }
                    }
                    AlignmentAction.DISTRIBUTE_HORIZONTAL,
                    AlignmentAction.DISTRIBUTE_VERTICAL -> Unit
                }
                el.copy(
                    xMm = nx.coerceIn(0f, (matConfig.widthMm - el.widthMm).coerceAtLeast(0f)),
                    yMm = ny.coerceIn(0f, (matConfig.heightMm - el.heightMm).coerceAtLeast(0f))
                )
            }
        }
    }

    fun bringSelectionToFront(
        elements: List<DesignElement>,
        selectedIds: Set<String>
    ): List<DesignElement> {
        if (selectedIds.isEmpty()) return elements
        val unselected = elements.filterNot { it.id in selectedIds }
        val selected = elements.filter { it.id in selectedIds }
        return unselected + selected
    }

    fun sendSelectionToBack(
        elements: List<DesignElement>,
        selectedIds: Set<String>
    ): List<DesignElement> {
        if (selectedIds.isEmpty()) return elements
        val selected = elements.filter { it.id in selectedIds }
        val unselected = elements.filterNot { it.id in selectedIds }
        return selected + unselected
    }

    fun toggleLockSelection(
        elements: List<DesignElement>,
        selectedIds: Set<String>
    ): Pair<List<DesignElement>, Boolean> {
        val targets = elements.filter { it.id in selectedIds }
        if (targets.isEmpty()) return elements to false
        val nextLocked = targets.any { !it.isLocked }
        val updated = elements.map { el ->
            if (el.id in selectedIds) el.copy(isLocked = nextLocked) else el
        }
        return updated to nextLocked
    }

    /**
     * Step-and-Repeat Sticker Matrix generator: replicates the source element into a rows x cols grid.
     */
    fun generateArrayCopies(
        source: DesignElement,
        rows: Int,
        cols: Int,
        gapXMm: Float,
        gapYMm: Float,
        matConfig: CuttingMatConfig
    ): List<DesignElement> {
        val rCount = rows.coerceIn(1, 10)
        val cCount = cols.coerceIn(1, 10)
        val created = mutableListOf<DesignElement>()

        for (r in 0 until rCount) {
            for (c in 0 until cCount) {
                if (r == 0 && c == 0) continue
                val nx = source.xMm + c * (source.widthMm + gapXMm)
                val ny = source.yMm + r * (source.heightMm + gapYMm)
                if (nx + source.widthMm <= matConfig.widthMm + 0.5f &&
                    ny + source.heightMm <= matConfig.heightMm + 0.5f
                ) {
                    created.add(
                        source.copy(
                            id = UUID.randomUUID().toString(),
                            name = "${source.name} (${r + 1},${c + 1})",
                            xMm = nx.coerceAtLeast(0f),
                            yMm = ny.coerceAtLeast(0f),
                            groupId = null,
                            isLocked = false
                        )
                    )
                }
            }
        }
        return created
    }
}
