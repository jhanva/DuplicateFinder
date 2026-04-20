package com.duplicatefinder.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.duplicatefinder.R

@Composable
fun ReviewEmptyState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ReviewNoFilterMatchesContent(
    filterEmptyTitle: String,
    filterEmptySubtitle: String,
    pendingBatchCount: Int,
    onApplyBatch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ReviewEmptyState(
            title = filterEmptyTitle,
            subtitle = filterEmptySubtitle
        )

        if (pendingBatchCount > 0) {
            Spacer(modifier = Modifier.height(16.dp))
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
        }
    }
}

@Composable
fun ReviewSummaryContent(
    completeTitle: String,
    keptText: String,
    movedText: String,
    pendingText: String,
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
            text = completeTitle,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = keptText,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = movedText,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = pendingText,
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
