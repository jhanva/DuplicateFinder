package com.duplicatefinder.data.repository

import com.duplicatefinder.domain.model.DetectionStage
import com.duplicatefinder.domain.model.OverlayKind
import com.duplicatefinder.domain.model.OverlayRegion
import com.duplicatefinder.domain.repository.OverlayDetectorOutputFormat
import kotlin.math.max
import kotlin.math.min

internal object OverlayOnnxPostProcessor {

    fun decodeDetectorOutput(
        values: FloatArray,
        shape: LongArray,
        width: Int,
        height: Int,
        outputFormat: OverlayDetectorOutputFormat,
        confidenceThreshold: Float,
        minRegionAreaRatio: Float
    ): List<OverlayRegion> {
        if (width <= 0 || height <= 0 || values.isEmpty()) return emptyList()
        val regions = when (outputFormat) {
            OverlayDetectorOutputFormat.HEATMAP -> decodeHeatmap(
                values = values,
                shape = shape,
                confidenceThreshold = confidenceThreshold,
                minRegionAreaRatio = minRegionAreaRatio
            )

            OverlayDetectorOutputFormat.BOXES_NORMALIZED -> decodeBoxes(
                values = values,
                confidenceThreshold = confidenceThreshold,
                minRegionAreaRatio = minRegionAreaRatio
            )
        }
        return mergeRegions(regions).sortedByDescending { it.confidence }
    }

    fun buildAnalysisResult(regions: List<OverlayRegion>): OverlayAnalysisResult {
        return buildAnalysisResult(
            regions = regions,
            stage = DetectionStage.STAGE_2_REFINED
        )
    }

    fun buildAnalysisResult(
        regions: List<OverlayRegion>,
        stage: DetectionStage
    ): OverlayAnalysisResult {
        if (regions.isEmpty()) {
            return OverlayAnalysisResult(
                preliminaryScore = 0f,
                refinedScore = 0f,
                overlayCoverageRatio = 0f,
                regions = emptyList(),
                maskConfidence = 0f,
                overlayKinds = setOf(OverlayKind.UNKNOWN),
                stage = stage
            )
        }

        val kinds = regions.map { it.kind }.toSet().ifEmpty { setOf(OverlayKind.UNKNOWN) }
        val coverage = regions.sumOf { region ->
            ((region.right - region.left) * (region.bottom - region.top)).toDouble()
        }.toFloat().coerceIn(0f, MAX_COVERAGE)
        val maskConfidence = regions.map { it.confidence }.average().toFloat().coerceIn(0f, 1f)
        val preliminaryScore = (
            (maskConfidence * 0.72f) +
                (coverage * 0.55f) +
                (min(regions.size, 3) * 0.05f)
            ).coerceIn(0f, 1f)
        val refinedScore = if (stage == DetectionStage.STAGE_2_REFINED) {
            (
                preliminaryScore +
                    if (OverlayKind.CAPTION in kinds || OverlayKind.HANDLE in kinds) 0.08f else 0f +
                    if (coverage in 0.02f..0.4f) 0.05f else -0.05f
                ).coerceIn(0f, 1f)
        } else {
            preliminaryScore.coerceIn(0f, 1f)
        }

        return OverlayAnalysisResult(
            preliminaryScore = preliminaryScore,
            refinedScore = refinedScore,
            overlayCoverageRatio = coverage,
            regions = regions.sortedByDescending { it.confidence },
            maskConfidence = maskConfidence,
            overlayKinds = kinds,
            stage = stage
        )
    }

    fun decodeMask(
        values: FloatArray,
        width: Int,
        height: Int,
        threshold: Float,
        minRegionAreaRatio: Float
    ): List<OverlayRegion> {
        if (width <= 0 || height <= 0 || values.size < width * height) return emptyList()
        return decodeHeatmapGrid(
            values = values,
            gridWidth = width,
            gridHeight = height,
            confidenceThreshold = threshold,
            minRegionAreaRatio = minRegionAreaRatio
        )
    }

    fun buildMask(
        width: Int,
        height: Int,
        regions: List<OverlayRegion>
    ): FloatArray {
        if (width <= 0 || height <= 0 || regions.isEmpty()) return FloatArray(width.coerceAtLeast(0) * height.coerceAtLeast(0))
        val mask = FloatArray(width * height)
        regions.forEach { region ->
            val left = (region.left.coerceIn(0f, 1f) * width).toInt().coerceIn(0, width - 1)
            val top = (region.top.coerceIn(0f, 1f) * height).toInt().coerceIn(0, height - 1)
            val right = (region.right.coerceIn(0f, 1f) * width).toInt().coerceIn(left + 1, width)
            val bottom = (region.bottom.coerceIn(0f, 1f) * height).toInt().coerceIn(top + 1, height)
            for (y in top until bottom) {
                for (x in left until right) {
                    mask[(y * width) + x] = 1f
                }
            }
        }
        return mask
    }

    private fun decodeBoxes(
        values: FloatArray,
        confidenceThreshold: Float,
        minRegionAreaRatio: Float
    ): List<OverlayRegion> {
        val stride = when {
            values.size % 6 == 0 -> 6
            values.size % 5 == 0 -> 5
            else -> return emptyList()
        }
        val regions = mutableListOf<OverlayRegion>()
        for (offset in values.indices step stride) {
            if (offset + 4 >= values.size) break
            val left = values[offset].coerceIn(0f, 1f)
            val top = values[offset + 1].coerceIn(0f, 1f)
            val right = values[offset + 2].coerceIn(left, 1f)
            val bottom = values[offset + 3].coerceIn(top, 1f)
            val confidence = values[offset + 4].coerceIn(0f, 1f)
            if (confidence < confidenceThreshold) continue
            val area = (right - left) * (bottom - top)
            if (area < minRegionAreaRatio) continue
            val kind = if (stride >= 6) {
                values[offset + 5].toInt().toOverlayKind()
            } else {
                classifyKind(left, top, right, bottom)
            }
            regions += OverlayRegion(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                confidence = confidence,
                kind = kind
            )
        }
        return regions
    }

    private fun decodeHeatmap(
        values: FloatArray,
        shape: LongArray,
        confidenceThreshold: Float,
        minRegionAreaRatio: Float
    ): List<OverlayRegion> {
        val heatmapHeight = shape.getOrNull(shape.size - 2)?.toInt() ?: return emptyList()
        val heatmapWidth = shape.getOrNull(shape.size - 1)?.toInt() ?: return emptyList()
        if (heatmapHeight <= 0 || heatmapWidth <= 0 || values.size < heatmapWidth * heatmapHeight) {
            return emptyList()
        }

        return decodeHeatmapGrid(
            values = values,
            gridWidth = heatmapWidth,
            gridHeight = heatmapHeight,
            confidenceThreshold = confidenceThreshold,
            minRegionAreaRatio = minRegionAreaRatio
        )
    }

    private fun decodeHeatmapGrid(
        values: FloatArray,
        gridWidth: Int,
        gridHeight: Int,
        confidenceThreshold: Float,
        minRegionAreaRatio: Float
    ): List<OverlayRegion> {
        val visited = BooleanArray(gridWidth * gridHeight)
        val regions = mutableListOf<OverlayRegion>()
        for (index in 0 until (gridWidth * gridHeight)) {
            if (visited[index] || values[index] < confidenceThreshold) continue
            val component = collectHeatmapComponent(
                start = index,
                width = gridWidth,
                height = gridHeight,
                values = values,
                visited = visited,
                threshold = confidenceThreshold
            )
            val minX = component.minOfOrNull { it % gridWidth } ?: continue
            val maxX = component.maxOfOrNull { it % gridWidth } ?: continue
            val minY = component.minOfOrNull { it / gridWidth } ?: continue
            val maxY = component.maxOfOrNull { it / gridWidth } ?: continue
            val confidence = component.map { values[it].coerceIn(0f, 1f) }.average().toFloat()
            val left = (minX.toFloat() / gridWidth.toFloat()).coerceIn(0f, 1f)
            val top = (minY.toFloat() / gridHeight.toFloat()).coerceIn(0f, 1f)
            val right = ((maxX + 1).toFloat() / gridWidth.toFloat()).coerceIn(left, 1f)
            val bottom = ((maxY + 1).toFloat() / gridHeight.toFloat()).coerceIn(top, 1f)
            val area = (right - left) * (bottom - top)
            if (area < minRegionAreaRatio) continue
            regions += OverlayRegion(
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                confidence = confidence,
                kind = classifyKind(left, top, right, bottom)
            )
        }
        return regions
    }

    private fun collectHeatmapComponent(
        start: Int,
        width: Int,
        height: Int,
        values: FloatArray,
        visited: BooleanArray,
        threshold: Float
    ): List<Int> {
        val queue = ArrayDeque<Int>()
        val component = mutableListOf<Int>()
        queue.add(start)
        visited[start] = true

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            component += current
            val x = current % width
            val y = current / width
            for (nextY in max(0, y - 1)..min(height - 1, y + 1)) {
                for (nextX in max(0, x - 1)..min(width - 1, x + 1)) {
                    val nextIndex = (nextY * width) + nextX
                    if (visited[nextIndex] || values[nextIndex] < threshold) continue
                    visited[nextIndex] = true
                    queue.add(nextIndex)
                }
            }
        }

        return component
    }

    private fun mergeRegions(regions: List<OverlayRegion>): List<OverlayRegion> {
        if (regions.size <= 1) return regions
        val pending = regions.sortedByDescending { it.confidence }.toMutableList()
        val merged = mutableListOf<OverlayRegion>()
        while (pending.isNotEmpty()) {
            val base = pending.removeAt(0)
            val overlapping = pending.filter { overlapRatio(base, it) >= MERGE_THRESHOLD }
            pending.removeAll(overlapping)
            merged += overlapping.fold(base) { acc, region ->
                OverlayRegion(
                    left = min(acc.left, region.left),
                    top = min(acc.top, region.top),
                    right = max(acc.right, region.right),
                    bottom = max(acc.bottom, region.bottom),
                    confidence = max(acc.confidence, region.confidence),
                    kind = if (acc.confidence >= region.confidence) acc.kind else region.kind
                )
            }
        }
        return merged
    }

    private fun overlapRatio(first: OverlayRegion, second: OverlayRegion): Float {
        val left = max(first.left, second.left)
        val top = max(first.top, second.top)
        val right = min(first.right, second.right)
        val bottom = min(first.bottom, second.bottom)
        if (right <= left || bottom <= top) return 0f
        val intersection = (right - left) * (bottom - top)
        val firstArea = (first.right - first.left) * (first.bottom - first.top)
        val secondArea = (second.right - second.left) * (second.bottom - second.top)
        val union = firstArea + secondArea - intersection
        if (union <= 0f) return 0f
        return intersection / union
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

    private fun Int.toOverlayKind(): OverlayKind {
        return OverlayKind.entries.getOrNull(this) ?: OverlayKind.UNKNOWN
    }

    private const val MAX_COVERAGE = 0.45f
    private const val MERGE_THRESHOLD = 0.45f
}
