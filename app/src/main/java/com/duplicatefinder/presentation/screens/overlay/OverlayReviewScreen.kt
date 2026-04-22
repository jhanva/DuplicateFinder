package com.duplicatefinder.presentation.screens.overlay

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.duplicatefinder.R
import com.duplicatefinder.domain.model.OverlayReviewItem
import com.duplicatefinder.domain.model.supportsOverlayCleaning
import com.duplicatefinder.presentation.components.ReviewEmptyState
import com.duplicatefinder.presentation.components.ReviewNoFilterMatchesContent
import com.duplicatefinder.presentation.components.ReviewSummaryContent
import com.duplicatefinder.presentation.components.ScanProgressIndicator
import com.duplicatefinder.util.extension.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverlayReviewScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: OverlayReviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onDeleteConfirmationResult(result.resultCode == Activity.RESULT_OK)
    }

    LaunchedEffect(Unit) {
        viewModel.startReview()
    }

    uiState.pendingDeleteIntentSender?.let { intentSender ->
        LaunchedEffect(intentSender) {
            deletePermissionLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build()
            )
        }
    }

    uiState.error?.let { message ->
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.overlay_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.nav_home)
                        )
                    }
                },
                actions = {
                    if (!uiState.isScanning && uiState.hasFilterMatches && !uiState.isReviewComplete) {
                        IconButton(
                            onClick = {
                                if (uiState.isPaused) {
                                    viewModel.resumeReview()
                                } else {
                                    viewModel.pauseReview()
                                }
                            }
                        ) {
                            Icon(
                                imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = if (uiState.isPaused) {
                                    stringResource(R.string.overlay_resume)
                                } else {
                                    stringResource(R.string.overlay_pause)
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            when {
                uiState.showFullScanProgress -> {
                    ScanProgressIndicator(
                        progress = uiState.scanProgress,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.isApplyingBatch || uiState.isGeneratingPreview -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.requiresFolderSelection -> {
                    ReviewEmptyState(
                        title = stringResource(R.string.scan_select_folders_required),
                        subtitle = stringResource(R.string.overlay_select_folders_message),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.hasNoResults -> {
                    ReviewEmptyState(
                        title = stringResource(R.string.overlay_no_results_title),
                        subtitle = stringResource(R.string.overlay_no_results_subtitle),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LoadedOverlayReviewState(
                        state = uiState,
                        onRangeChange = viewModel::updateReviewScoreRange,
                        onKeep = viewModel::keepCurrent,
                        onMarkForTrash = viewModel::markCurrentForTrash,
                        onApplyBatch = viewModel::applyBatchToTrash,
                        onGeneratePreview = viewModel::generatePreviewForCurrent,
                        onKeepCleaned = viewModel::keepCleanedPreview,
                        onDeleteAll = viewModel::deleteAllFromPreview,
                        onSkipPreview = viewModel::skipPreview,
                        onOpenTrash = onOpenTrash,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedOverlayReviewState(
    state: OverlayReviewUiState,
    onRangeChange: (Float, Float) -> Unit,
    onKeep: () -> Unit,
    onMarkForTrash: () -> Unit,
    onApplyBatch: () -> Unit,
    onGeneratePreview: () -> Unit,
    onKeepCleaned: () -> Unit,
    onDeleteAll: () -> Unit,
    onSkipPreview: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        OverlayFilterCard(
            minOverlayScore = state.minOverlayScore,
            maxOverlayScore = state.maxOverlayScore,
            matchingCount = state.totalCount,
            totalCount = state.overlayItems.size,
            onRangeChange = onRangeChange
        )

        if (state.showInlineScanProgress) {
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.scanProgress.progress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when {
                state.hasNoFilterMatches -> {
                    ReviewNoFilterMatchesContent(
                        filterEmptyTitle = stringResource(R.string.overlay_filter_empty_title),
                        filterEmptySubtitle = stringResource(R.string.overlay_filter_empty_subtitle),
                        pendingBatchCount = state.pendingBatchCount,
                        onApplyBatch = onApplyBatch,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.isReviewComplete -> {
                    ReviewSummaryContent(
                        completeTitle = stringResource(R.string.overlay_review_complete_title),
                        keptText = stringResource(R.string.overlay_summary_kept, state.keptCount),
                        movedText = stringResource(
                            R.string.overlay_summary_moved,
                            state.movedToTrashCount
                        ),
                        pendingText = stringResource(
                            R.string.overlay_summary_pending,
                            state.pendingBatchCount
                        ),
                        pendingBatchCount = state.pendingBatchCount,
                        onApplyBatch = onApplyBatch,
                        onOpenTrash = onOpenTrash,
                        onBack = onBack
                    )
                }

                else -> {
                    state.currentItem?.let { item ->
                        OverlayReviewContent(
                            item = item,
                            previewUri = state.previewState?.previewUri,
                            reviewedCount = state.reviewedCount,
                            totalCount = state.totalCount,
                            pendingBatchCount = state.pendingBatchCount,
                            isPaused = state.isPaused,
                            hasReadyPreview = state.hasReadyPreview,
                            onKeep = onKeep,
                            onMarkForTrash = onMarkForTrash,
                            onApplyBatch = onApplyBatch,
                            onGeneratePreview = onGeneratePreview,
                            onKeepCleaned = onKeepCleaned,
                            onDeleteAll = onDeleteAll,
                            onSkipPreview = onSkipPreview
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayFilterCard(
    minOverlayScore: Float,
    maxOverlayScore: Float,
    matchingCount: Int,
    totalCount: Int,
    onRangeChange: (Float, Float) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.overlay_filter_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.overlay_filter_description,
                            (minOverlayScore * 100).toInt(),
                            (maxOverlayScore * 100).toInt()
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(
                        R.string.overlay_filter_range_value,
                        (minOverlayScore * 100).toInt(),
                        (maxOverlayScore * 100).toInt()
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            RangeSlider(
                value = minOverlayScore..maxOverlayScore,
                onValueChange = { range ->
                    onRangeChange(range.start, range.endInclusive)
                },
                valueRange = 0f..1f
            )

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.overlay_filter_showing,
                    matchingCount,
                    totalCount
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun OverlayReviewContent(
    item: OverlayReviewItem,
    previewUri: android.net.Uri?,
    reviewedCount: Int,
    totalCount: Int,
    pendingBatchCount: Int,
    isPaused: Boolean,
    hasReadyPreview: Boolean,
    onKeep: () -> Unit,
    onMarkForTrash: () -> Unit,
    onApplyBatch: () -> Unit,
    onGeneratePreview: () -> Unit,
    onKeepCleaned: () -> Unit,
    onDeleteAll: () -> Unit,
    onSkipPreview: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.overlay_review_progress, reviewedCount + 1, totalCount),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.overlay_score_label,
                (item.rankScore * 100).toInt()
            ),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.overlay_kind_label,
                item.detection.overlayKinds.joinToString(", ") { it.name }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (hasReadyPreview && previewUri != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AsyncImage(
                    model = item.image.uri,
                    contentDescription = item.image.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f)
                )
                AsyncImage(
                    model = previewUri,
                    contentDescription = stringResource(R.string.overlay_preview_title),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            AsyncImage(
                model = item.image.uri,
                contentDescription = item.image.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = item.image.name,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Text(
            text = "${item.image.width}x${item.image.height} • ${item.image.size.formatFileSize()}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(
                R.string.overlay_coverage_label,
                (item.detection.overlayCoverageRatio * 100).toInt()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (pendingBatchCount > 0) {
            OutlinedButton(
                onClick = onApplyBatch,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.RestoreFromTrash,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.overlay_apply_batch, pendingBatchCount))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (hasReadyPreview) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSkipPreview,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.overlay_preview_skip))
                }

                OutlinedButton(
                    onClick = onDeleteAll,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.overlay_preview_delete_all))
                }

                Button(
                    onClick = onKeepCleaned,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.overlay_preview_keep_cleaned))
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onKeep,
                    enabled = !isPaused,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.quality_keep))
                }

                Button(
                    onClick = onMarkForTrash,
                    enabled = !isPaused,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.quality_mark_for_trash))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onGeneratePreview,
                enabled = !isPaused && item.image.supportsOverlayCleaning(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.overlay_remove_watermark))
            }
        }

        if (isPaused) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.overlay_paused_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
