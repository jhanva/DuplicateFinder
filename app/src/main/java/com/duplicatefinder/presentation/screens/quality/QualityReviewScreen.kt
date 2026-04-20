package com.duplicatefinder.presentation.screens.quality

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
import com.duplicatefinder.domain.model.ImageQualityItem
import com.duplicatefinder.presentation.components.ReviewEmptyState
import com.duplicatefinder.presentation.components.ReviewNoFilterMatchesContent
import com.duplicatefinder.presentation.components.ReviewSummaryContent
import com.duplicatefinder.presentation.components.ScanProgressIndicator
import com.duplicatefinder.util.extension.formatFileSize
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityReviewScreen(
    onBack: () -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: QualityReviewViewModel = hiltViewModel()
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
                title = { Text(stringResource(R.string.quality_title)) },
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
                                imageVector = if (uiState.isPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
                                contentDescription = if (uiState.isPaused) {
                                    stringResource(R.string.quality_resume)
                                } else {
                                    stringResource(R.string.quality_pause)
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

                uiState.isApplyingBatch -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.requiresFolderSelection -> {
                    ReviewEmptyState(
                        title = stringResource(R.string.scan_select_folders_required),
                        subtitle = stringResource(R.string.quality_select_folders_message),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.hasNoResults -> {
                    ReviewEmptyState(
                        title = stringResource(R.string.quality_no_results_title),
                        subtitle = stringResource(R.string.quality_no_results_subtitle),
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LoadedReviewState(
                        state = uiState,
                        onRangeChange = viewModel::updateReviewScoreRange,
                        onKeep = { viewModel.keepCurrent() },
                        onMarkForTrash = { viewModel.markCurrentForTrash() },
                        onApplyBatch = { viewModel.applyBatchToTrash() },
                        onOpenTrash = onOpenTrash,
                        onBack = onBack
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadedReviewState(
    state: QualityReviewUiState,
    onRangeChange: (Int, Int) -> Unit,
    onKeep: () -> Unit,
    onMarkForTrash: () -> Unit,
    onApplyBatch: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        ReviewFilterCard(
            reviewScoreMin = state.reviewScoreMin,
            reviewScoreMax = state.reviewScoreMax,
            matchingCount = state.totalCount,
            totalCount = state.qualityItems.size,
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
                        filterEmptyTitle = stringResource(R.string.quality_filter_empty_title),
                        filterEmptySubtitle = stringResource(R.string.quality_filter_empty_subtitle),
                        pendingBatchCount = state.pendingBatchCount,
                        onApplyBatch = onApplyBatch,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                state.isReviewComplete -> {
                    ReviewSummaryContent(
                        completeTitle = stringResource(R.string.quality_review_complete_title),
                        keptText = stringResource(R.string.quality_summary_kept, state.keptCount),
                        movedText = stringResource(R.string.quality_summary_moved, state.movedToTrashCount),
                        pendingText = stringResource(R.string.quality_summary_pending, state.pendingBatchCount),
                        pendingBatchCount = state.pendingBatchCount,
                        onApplyBatch = onApplyBatch,
                        onOpenTrash = onOpenTrash,
                        onBack = onBack
                    )
                }

                else -> {
                    state.currentItem?.let { item ->
                        ReviewContent(
                            item = item,
                            reviewedCount = state.reviewedCount,
                            totalCount = state.totalCount,
                            pendingBatchCount = state.pendingBatchCount,
                            isPaused = state.isPaused,
                            onKeep = onKeep,
                            onMarkForTrash = onMarkForTrash,
                            onApplyBatch = onApplyBatch
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewFilterCard(
    reviewScoreMin: Int,
    reviewScoreMax: Int,
    matchingCount: Int,
    totalCount: Int,
    onRangeChange: (Int, Int) -> Unit
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
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.quality_filter_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(
                            R.string.quality_filter_description,
                            reviewScoreMin,
                            reviewScoreMax
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = stringResource(
                        R.string.quality_filter_range_value,
                        reviewScoreMin,
                        reviewScoreMax
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            RangeSlider(
                value = reviewScoreMin.toFloat()..reviewScoreMax.toFloat(),
                onValueChange = { range ->
                    onRangeChange(
                        range.start.roundToInt(),
                        range.endInclusive.roundToInt()
                    )
                },
                valueRange = 0f..100f,
                steps = 99
            )

            HorizontalDivider()

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.quality_filter_showing,
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
private fun ReviewContent(
    item: ImageQualityItem,
    reviewedCount: Int,
    totalCount: Int,
    pendingBatchCount: Int,
    isPaused: Boolean,
    onKeep: () -> Unit,
    onMarkForTrash: () -> Unit,
    onApplyBatch: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.quality_review_progress, reviewedCount + 1, totalCount),
            style = MaterialTheme.typography.titleMedium
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.quality_score_label, item.qualityScore.toInt()),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        AsyncImage(
            model = item.image.uri,
            contentDescription = item.image.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )

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
                R.string.quality_metrics_label,
                item.sharpness.toPercent(),
                item.detailDensity.toPercent(),
                item.blockiness.toPercent()
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
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
                Text(stringResource(R.string.quality_apply_batch, pendingBatchCount))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

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

        if (isPaused) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.quality_paused_message),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun Float.toPercent(): Int = (this.coerceIn(0f, 1f) * 100f).toInt()

