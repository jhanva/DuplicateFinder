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
import com.duplicatefinder.presentation.components.ReviewEmptyState
import com.duplicatefinder.presentation.components.ReviewNoFilterMatchesContent
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
    val samsungGalleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.onExternalEditorResult()
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

    uiState.pendingExternalEditIntent?.let { intent ->
        LaunchedEffect(intent) {
            viewModel.onExternalEditorLaunchConsumed()
            samsungGalleryLauncher.launch(intent)
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
                                imageVector = if (uiState.isPaused) {
                                    Icons.Default.PlayArrow
                                } else {
                                    Icons.Default.Pause
                                },
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

                uiState.isApplyingBatch -> {
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
                        onOpenInSamsungGallery = viewModel::openCurrentInSamsungGallery,
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
    onOpenInSamsungGallery: () -> Unit,
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
                    OverlayReviewSummaryContent(
                        keptCount = state.keptCount,
                        movedToTrashCount = state.movedToTrashCount,
                        editedInGalleryCount = state.editedInGalleryCount,
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
                            reviewedCount = state.reviewedCount,
                            totalCount = state.totalCount,
                            pendingBatchCount = state.pendingBatchCount,
                            isPaused = state.isPaused,
                            samsungGalleryDisabledReason = state.samsungGalleryDisabledReason,
                            onKeep = onKeep,
                            onMarkForTrash = onMarkForTrash,
                            onApplyBatch = onApplyBatch,
                            onOpenInSamsungGallery = onOpenInSamsungGallery
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
    reviewedCount: Int,
    totalCount: Int,
    pendingBatchCount: Int,
    isPaused: Boolean,
    samsungGalleryDisabledReason: String?,
    onKeep: () -> Unit,
    onMarkForTrash: () -> Unit,
    onApplyBatch: () -> Unit,
    onOpenInSamsungGallery: () -> Unit
) {
    val isSamsungGalleryEnabled = !isPaused && samsungGalleryDisabledReason == null

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
            onClick = onOpenInSamsungGallery,
            enabled = isSamsungGalleryEnabled,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.AutoFixHigh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.overlay_open_in_samsung_gallery))
        }

        if (samsungGalleryDisabledReason != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = samsungGalleryDisabledReason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
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

@Composable
private fun OverlayReviewSummaryContent(
    keptCount: Int,
    movedToTrashCount: Int,
    editedInGalleryCount: Int,
    pendingBatchCount: Int,
    onApplyBatch: () -> Unit,
    onOpenTrash: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.overlay_review_complete_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.overlay_summary_kept, keptCount),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.overlay_summary_moved, movedToTrashCount),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.overlay_summary_edited, editedInGalleryCount),
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.overlay_summary_pending, pendingBatchCount),
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(modifier = Modifier.height(20.dp))

        if (pendingBatchCount > 0) {
            Button(
                onClick = onApplyBatch,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.quality_apply_final_batch))
            }

            Spacer(modifier = Modifier.height(12.dp))
        }

        OutlinedButton(
            onClick = onOpenTrash,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.quality_open_trash))
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.nav_home))
        }
    }
}
