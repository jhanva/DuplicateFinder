package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OverlayAnalysisResult(
    val preliminaryScore: Float,
    val refinedScore: Float,
    val overlayCoverageRatio: Float,
    val regions: List<OverlayRegion>,
    val maskConfidence: Float,
    val overlayKinds: Set<OverlayKind>,
    val stage: DetectionStage
)

internal object OverlayImageAnalysis {

    fun analyze(
        pixels: IntArray,
        width: Int,
        height: Int
    ): OverlayAnalysisResult {
        if (width < MIN_DIMENSION || height < MIN_DIMENSION || pixels.size != width * height) {
            return emptyResult()
        }

        val luminance = FloatArray(pixels.size)
        val saturation = FloatArray(pixels.size)
        for (index in pixels.indices) {
            luminance[index] = luminanceOf(pixels[index])
            saturation[index] = saturationOf(pixels[index])
        }

        val cellSize = max(MIN_CELL_SIZE, min(width, height) / CELL_DIVISOR)
        val gridWidth = (width + cellSize - 1) / cellSize
        val gridHeight = (height + cellSize - 1) / cellSize
        val candidateCounts = IntArray(gridWidth * gridHeight)
        val edgeSums = FloatArray(gridWidth * gridHeight)
        val contrastSums = FloatArray(gridWidth * gridHeight)

        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val index = (y * width) + x
                val localMean = localMean(luminance, width, height, x, y)
                val edgeStrength = edgeStrength(luminance, width, index)
                val contrast = abs(luminance[index] - localMean)
                val neutralTextTone = saturation[index] <= SATURATION_THRESHOLD ||
                    luminance[index] <= DARK_TONE_THRESHOLD ||
                    luminance[index] >= LIGHT_TONE_THRESHOLD
                val candidate = (neutralTextTone && contrast >= CONTRAST_THRESHOLD && edgeStrength >= EDGE_THRESHOLD) ||
                    edgeStrength >= STRONG_EDGE_THRESHOLD

                if (!candidate) continue

                val cellX = x / cellSize
                val cellY = y / cellSize
                val cellIndex = (cellY * gridWidth) + cellX
                candidateCounts[cellIndex] += 1
                edgeSums[cellIndex] += edgeStrength
                contrastSums[cellIndex] += contrast
            }
        }

        val selected = BooleanArray(gridWidth * gridHeight)
        val cellAreas = IntArray(gridWidth * gridHeight)
        for (cellY in 0 until gridHeight) {
            for (cellX in 0 until gridWidth) {
                val cellIndex = (cellY * gridWidth) + cellX
                val cellWidth = min(cellSize, width - (cellX * cellSize))
                val cellHeight = min(cellSize, height - (cellY * cellSize))
                val area = max(1, cellWidth * cellHeight)
                cellAreas[cellIndex] = area

                val candidateRatio = candidateCounts[cellIndex].toFloat() / area.toFloat()
                if (candidateRatio <= 0f) continue

                val averageEdge = edgeSums[cellIndex] / max(1, candidateCounts[cellIndex]).toFloat()
                val averageContrast = contrastSums[cellIndex] / max(1, candidateCounts[cellIndex]).toFloat()
                val borderPriority = borderPriority(cellX, cellY, gridWidth, gridHeight)
                val threshold = if (borderPriority > 1f) BORDER_CELL_THRESHOLD else CELL_THRESHOLD

                if (candidateRatio >= threshold &&
                    averageEdge >= EDGE_THRESHOLD &&
                    averageContrast >= (CONTRAST_THRESHOLD * 0.75f)
                ) {
                    selected[cellIndex] = true
                }
            }
        }

        dilateSelectedCells(selected, gridWidth, gridHeight)

        val regions = mutableListOf<OverlayRegion>()
        val visited = BooleanArray(selected.size)
        for (cellIndex in selected.indices) {
            if (!selected[cellIndex] || visited[cellIndex]) continue
            val component = collectComponent(
                start = cellIndex,
                selected = selected,
                visited = visited,
                gridWidth = gridWidth,
                gridHeight = gridHeight
            )
            buildRegion(
                component = component,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                width = width,
                height = height,
                cellSize = cellSize,
                candidateCounts = candidateCounts,
                cellAreas = cellAreas,
                edgeSums = edgeSums,
                contrastSums = contrastSums
            )?.let(regions::add)
        }

        val finalRegions = if (regions.isNotEmpty()) {
            regions
        } else {
            fallbackRegion(
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                width = width,
                height = height,
                cellSize = cellSize,
                candidateCounts = candidateCounts,
                cellAreas = cellAreas,
                edgeSums = edgeSums,
                contrastSums = contrastSums
            )?.let(::listOf).orEmpty()
        }

        if (finalRegions.isEmpty()) {
            return emptyResult()
        }

        val kinds = finalRegions.map { it.kind }.toSet().ifEmpty { setOf(OverlayKind.UNKNOWN) }
        val coverage = finalRegions.sumOf { regionArea(it, width, height).toDouble() }
            .toFloat()
            .coerceIn(0f, MAX_COVERAGE)
        val maskConfidence = finalRegions.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        val preliminaryScore = (
            (maskConfidence * 0.72f) +
                (coverage * 0.55f) +
                (min(finalRegions.size, 3) * 0.05f)
            ).coerceIn(0f, 1f)
        val refinedScore = (
            preliminaryScore +
                if (OverlayKind.CAPTION in kinds || OverlayKind.HANDLE in kinds) 0.08f else 0f +
                if (coverage in 0.02f..0.4f) 0.05f else -0.05f
            ).coerceIn(0f, 1f)

        return OverlayAnalysisResult(
            preliminaryScore = preliminaryScore,
            refinedScore = refinedScore,
            overlayCoverageRatio = coverage,
            regions = finalRegions.sortedByDescending { it.confidence },
            maskConfidence = maskConfidence,
            overlayKinds = kinds,
            stage = DetectionStage.STAGE_2_REFINED
        )
    }

    fun cleanOverlay(
        pixels: IntArray,
        width: Int,
        height: Int,
        regions: List<OverlayRegion>
    ): IntArray {
        if (pixels.size != width * height || regions.isEmpty()) return pixels.copyOf()

        val mask = BooleanArray(pixels.size)
        regions.forEach { region ->
            val rect = region.toPixelRect(width, height) ?: return@forEach
            for (y in rect.top until rect.bottom) {
                for (x in rect.left until rect.right) {
                    mask[(y * width) + x] = true
                }
            }
        }

        val cleaned = pixels.copyOf()
        for (index in cleaned.indices) {
            if (!mask[index]) continue
            val x = index % width
            val y = index / width
            cleaned[index] = sampleFromSurroundings(
                pixels = cleaned,
                mask = mask,
                width = width,
                height = height,
                x = x,
                y = y
            )
        }

        repeat(SMOOTHING_PASSES) {
            val snapshot = cleaned.copyOf()
            for (index in snapshot.indices) {
                if (!mask[index]) continue
                val x = index % width
                val y = index / width
                cleaned[index] = smoothMaskedPixel(
                    pixels = snapshot,
                    mask = mask,
                    width = width,
                    height = height,
                    x = x,
                    y = y
                )
            }
        }

        return cleaned
    }

    private fun buildRegion(
        component: List<Int>,
        gridWidth: Int,
        gridHeight: Int,
        width: Int,
        height: Int,
        cellSize: Int,
        candidateCounts: IntArray,
        cellAreas: IntArray,
        edgeSums: FloatArray,
        contrastSums: FloatArray
    ): OverlayRegion? {
        var minCellX = Int.MAX_VALUE
        var minCellY = Int.MAX_VALUE
        var maxCellX = Int.MIN_VALUE
        var maxCellY = Int.MIN_VALUE
        var weightedDensity = 0f
        var weightedEdge = 0f
        var weightedContrast = 0f

        component.forEach { cellIndex ->
            val cellX = cellIndex % gridWidth
            val cellY = cellIndex / gridWidth
            minCellX = min(minCellX, cellX)
            minCellY = min(minCellY, cellY)
            maxCellX = max(maxCellX, cellX)
            maxCellY = max(maxCellY, cellY)

            val count = candidateCounts[cellIndex]
            if (count <= 0) return@forEach
            val area = max(1, cellAreas[cellIndex])
            val density = count.toFloat() / area.toFloat()
            weightedDensity += density
            weightedEdge += edgeSums[cellIndex] / count.toFloat()
            weightedContrast += contrastSums[cellIndex] / count.toFloat()
        }

        val componentSize = max(1, component.size)
        val averageDensity = weightedDensity / componentSize.toFloat()
        val averageEdge = weightedEdge / componentSize.toFloat()
        val averageContrast = weightedContrast / componentSize.toFloat()

        val left = ((minCellX * cellSize).toFloat() / width.toFloat()).coerceIn(0f, 1f)
        val top = ((minCellY * cellSize).toFloat() / height.toFloat()).coerceIn(0f, 1f)
        val right = (((maxCellX + 1) * cellSize).toFloat() / width.toFloat()).coerceIn(left, 1f)
        val bottom = (((maxCellY + 1) * cellSize).toFloat() / height.toFloat()).coerceIn(top, 1f)

        val coverage = ((right - left) * (bottom - top)).coerceIn(0f, 1f)
        if (coverage < MIN_REGION_COVERAGE || coverage > MAX_REGION_COVERAGE) {
            return null
        }

        val kind = classifyKind(left, top, right, bottom)
        val confidence = (
            0.22f +
                (averageDensity * 1.8f) +
                (averageEdge / 110f) +
                (averageContrast / 140f) +
                (borderPriorityForRegion(left, top, right, bottom) * 0.08f)
            ).coerceIn(0f, 1f)

        return OverlayRegion(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            confidence = confidence,
            kind = kind
        )
    }

    private fun fallbackRegion(
        gridWidth: Int,
        gridHeight: Int,
        width: Int,
        height: Int,
        cellSize: Int,
        candidateCounts: IntArray,
        cellAreas: IntArray,
        edgeSums: FloatArray,
        contrastSums: FloatArray
    ): OverlayRegion? {
        var bestIndex = -1
        var bestScore = 0f
        for (cellIndex in candidateCounts.indices) {
            val count = candidateCounts[cellIndex]
            if (count <= 0) continue
            val density = count.toFloat() / max(1, cellAreas[cellIndex]).toFloat()
            val edge = edgeSums[cellIndex] / count.toFloat()
            val contrast = contrastSums[cellIndex] / count.toFloat()
            val score = density + (edge / 180f) + (contrast / 220f)
            if (score > bestScore) {
                bestScore = score
                bestIndex = cellIndex
            }
        }

        if (bestIndex < 0 || bestScore < FALLBACK_SCORE_THRESHOLD) {
            return null
        }

        val cellX = bestIndex % gridWidth
        val cellY = bestIndex / gridWidth
        val component = buildList {
            for (y in max(0, cellY - 1) .. min(gridHeight - 1, cellY + 1)) {
                for (x in max(0, cellX - 1) .. min(gridWidth - 1, cellX + 1)) {
                    add((y * gridWidth) + x)
                }
            }
        }
        return buildRegion(
            component = component,
            gridWidth = gridWidth,
            gridHeight = gridHeight,
            width = width,
            height = height,
            cellSize = cellSize,
            candidateCounts = candidateCounts,
            cellAreas = cellAreas,
            edgeSums = edgeSums,
            contrastSums = contrastSums
        )
    }

    private fun collectComponent(
        start: Int,
        selected: BooleanArray,
        visited: BooleanArray,
        gridWidth: Int,
        gridHeight: Int
    ): List<Int> {
        val queue = ArrayDeque<Int>()
        val component = mutableListOf<Int>()
        queue.add(start)
        visited[start] = true

        while (queue.isNotEmpty()) {
            val cellIndex = queue.removeFirst()
            component += cellIndex
            val x = cellIndex % gridWidth
            val y = cellIndex / gridWidth

            for (nextY in max(0, y - 1) .. min(gridHeight - 1, y + 1)) {
                for (nextX in max(0, x - 1) .. min(gridWidth - 1, x + 1)) {
                    val nextIndex = (nextY * gridWidth) + nextX
                    if (!selected[nextIndex] || visited[nextIndex]) continue
                    visited[nextIndex] = true
                    queue.add(nextIndex)
                }
            }
        }

        return component
    }

    private fun dilateSelectedCells(selected: BooleanArray, gridWidth: Int, gridHeight: Int) {
        val snapshot = selected.copyOf()
        for (index in snapshot.indices) {
            if (snapshot[index]) continue
            val x = index % gridWidth
            val y = index / gridWidth
            var neighbors = 0
            for (nextY in max(0, y - 1) .. min(gridHeight - 1, y + 1)) {
                for (nextX in max(0, x - 1) .. min(gridWidth - 1, x + 1)) {
                    if (snapshot[(nextY * gridWidth) + nextX]) {
                        neighbors += 1
                    }
                }
            }
            if (neighbors >= DILATION_NEIGHBORS) {
                selected[index] = true
            }
        }
    }

    private fun localMean(
        luminance: FloatArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Float {
        var sum = 0f
        var count = 0
        for (nextY in max(0, y - 1) .. min(height - 1, y + 1)) {
            for (nextX in max(0, x - 1) .. min(width - 1, x + 1)) {
                sum += luminance[(nextY * width) + nextX]
                count += 1
            }
        }
        return sum / max(1, count).toFloat()
    }

    private fun edgeStrength(luminance: FloatArray, width: Int, index: Int): Float {
        return max(
            max(abs(luminance[index] - luminance[index - 1]), abs(luminance[index] - luminance[index + 1])),
            max(abs(luminance[index] - luminance[index - width]), abs(luminance[index] - luminance[index + width]))
        )
    }

    private fun borderPriority(cellX: Int, cellY: Int, gridWidth: Int, gridHeight: Int): Float {
        val nearHorizontalBorder = cellY <= gridHeight / 5 || cellY >= (gridHeight * 4) / 5
        val nearVerticalBorder = cellX <= gridWidth / 6 || cellX >= (gridWidth * 5) / 6
        return if (nearHorizontalBorder || nearVerticalBorder) 1.2f else 1f
    }

    private fun borderPriorityForRegion(left: Float, top: Float, right: Float, bottom: Float): Float {
        return when {
            bottom >= 0.68f || top <= 0.2f -> 1.2f
            left <= 0.12f || right >= 0.88f -> 1.1f
            else -> 1f
        }
    }

    private fun classifyKind(left: Float, top: Float, right: Float, bottom: Float): OverlayKind {
        val width = right - left
        val height = bottom - top
        val aspect = if (height <= 0f) width else width / height

        return when {
            bottom >= 0.68f && aspect >= 2.2f -> OverlayKind.CAPTION
            top <= 0.22f && right >= 0.65f && aspect >= 2.0f -> OverlayKind.HANDLE
            (left <= 0.18f || right >= 0.82f) && aspect in 0.7f..2.1f && (width * height) <= 0.08f -> OverlayKind.LOGO
            aspect < 1.2f && (width * height) <= 0.04f -> OverlayKind.SIGNATURE
            else -> OverlayKind.TEXT
        }
    }

    private fun sampleFromSurroundings(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Int {
        var red = 0f
        var green = 0f
        var blue = 0f
        var weightSum = 0f

        for ((dx, dy) in DIRECTIONS) {
            var distance = 1
            while (distance <= MAX_SAMPLE_RADIUS) {
                val sampleX = x + (dx * distance)
                val sampleY = y + (dy * distance)
                if (sampleX !in 0 until width || sampleY !in 0 until height) {
                    break
                }
                val sampleIndex = (sampleY * width) + sampleX
                if (!mask[sampleIndex]) {
                    val weight = 1f / distance.toFloat()
                    red += redOf(pixels[sampleIndex]) * weight
                    green += greenOf(pixels[sampleIndex]) * weight
                    blue += blueOf(pixels[sampleIndex]) * weight
                    weightSum += weight
                    break
                }
                distance += 1
            }
        }

        return if (weightSum > 0f) {
            argb(
                red = (red / weightSum).toInt(),
                green = (green / weightSum).toInt(),
                blue = (blue / weightSum).toInt()
            )
        } else {
            pixels[(y * width) + x]
        }
    }

    private fun smoothMaskedPixel(
        pixels: IntArray,
        mask: BooleanArray,
        width: Int,
        height: Int,
        x: Int,
        y: Int
    ): Int {
        var red = 0
        var green = 0
        var blue = 0
        var count = 0
        for (nextY in max(0, y - 1) .. min(height - 1, y + 1)) {
            for (nextX in max(0, x - 1) .. min(width - 1, x + 1)) {
                val index = (nextY * width) + nextX
                if (mask[index] || nextX != x || nextY != y) {
                    red += redOf(pixels[index])
                    green += greenOf(pixels[index])
                    blue += blueOf(pixels[index])
                    count += 1
                }
            }
        }
        return argb(
            red = red / max(1, count),
            green = green / max(1, count),
            blue = blue / max(1, count)
        )
    }

    private fun OverlayRegion.toPixelRect(width: Int, height: Int): PixelRect? {
        val leftPx = (left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
        val topPx = (top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
        val rightPx = (right.coerceIn(0f, 1f) * width).toInt().coerceIn(leftPx + 1, width)
        val bottomPx = (bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(topPx + 1, height)
        if (rightPx - leftPx < 2 || bottomPx - topPx < 2) return null
        return PixelRect(leftPx, topPx, rightPx, bottomPx)
    }

    private fun regionArea(region: OverlayRegion, width: Int, height: Int): Float {
        val rect = region.toPixelRect(width, height) ?: return 0f
        return (rect.width * rect.height).toFloat() / (width * height).toFloat()
    }

    private fun emptyResult() = OverlayAnalysisResult(
        preliminaryScore = 0f,
        refinedScore = 0f,
        overlayCoverageRatio = 0f,
        regions = emptyList(),
        maskConfidence = 0f,
        overlayKinds = setOf(OverlayKind.UNKNOWN),
        stage = DetectionStage.STAGE_1_CANDIDATE
    )

    private fun luminanceOf(color: Int): Float {
        return (redOf(color) * 0.2126f) + (greenOf(color) * 0.7152f) + (blueOf(color) * 0.0722f)
    }

    private fun saturationOf(color: Int): Float {
        val red = redOf(color) / 255f
        val green = greenOf(color) / 255f
        val blue = blueOf(color) / 255f
        val max = max(red, max(green, blue))
        val min = min(red, min(green, blue))
        if (max <= 0f) return 0f
        return (max - min) / max
    }

    private fun argb(red: Int, green: Int, blue: Int): Int {
        return (255 shl 24) or
            (red.coerceIn(0, 255) shl 16) or
            (green.coerceIn(0, 255) shl 8) or
            blue.coerceIn(0, 255)
    }

    private fun redOf(color: Int): Int = (color shr 16) and 0xFF
    private fun greenOf(color: Int): Int = (color shr 8) and 0xFF
    private fun blueOf(color: Int): Int = color and 0xFF

    private data class PixelRect(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int
    ) {
        val width: Int
            get() = right - left

        val height: Int
            get() = bottom - top
    }

    private val DIRECTIONS = listOf(
        -1 to 0,
        1 to 0,
        0 to -1,
        0 to 1,
        -1 to -1,
        1 to -1,
        -1 to 1,
        1 to 1
    )

    private const val MIN_DIMENSION = 16
    private const val MIN_CELL_SIZE = 4
    private const val CELL_DIVISOR = 48
    private const val CONTRAST_THRESHOLD = 24f
    private const val EDGE_THRESHOLD = 28f
    private const val STRONG_EDGE_THRESHOLD = 48f
    private const val SATURATION_THRESHOLD = 0.38f
    private const val DARK_TONE_THRESHOLD = 36f
    private const val LIGHT_TONE_THRESHOLD = 222f
    private const val CELL_THRESHOLD = 0.12f
    private const val BORDER_CELL_THRESHOLD = 0.08f
    private const val MIN_REGION_COVERAGE = 0.004f
    private const val MAX_REGION_COVERAGE = 0.55f
    private const val MAX_COVERAGE = 0.45f
    private const val FALLBACK_SCORE_THRESHOLD = 0.22f
    private const val DILATION_NEIGHBORS = 3
    private const val MAX_SAMPLE_RADIUS = 18
    private const val SMOOTHING_PASSES = 3
}
