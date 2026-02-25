package com.duplicatefinder.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duplicatefinder.R
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.MatchType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    sheetState: SheetState,
    currentFilter: FilterCriteria,
    onApply: (FilterCriteria) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedMatchTypes by remember(currentFilter) {
        mutableStateOf(currentFilter.matchTypes.toSet())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = stringResource(R.string.filter_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.filter_match_type),
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MatchType.entries.forEach { matchType ->
                    FilterChip(
                        selected = matchType in selectedMatchTypes,
                        onClick = {
                            selectedMatchTypes = if (matchType in selectedMatchTypes) {
                                selectedMatchTypes - matchType
                            } else {
                                selectedMatchTypes + matchType
                            }
                        },
                        label = {
                            Text(
                                text = when (matchType) {
                                    MatchType.EXACT -> stringResource(R.string.filter_match_exact)
                                    MatchType.SIMILAR -> stringResource(R.string.filter_match_similar)
                                    MatchType.BOTH -> stringResource(R.string.filter_match_both)
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedMatchTypes = MatchType.entries.toSet()
                        onReset()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.filter_reset))
                }

                Button(
                    onClick = {
                        val typesToApply = if (selectedMatchTypes.isEmpty()) {
                            MatchType.entries.toSet()
                        } else {
                            selectedMatchTypes
                        }
                        onApply(
                            currentFilter.copy(
                                folders = emptyList(),
                                dateRange = null,
                                minSize = null,
                                maxSize = null,
                                mimeTypes = emptyList(),
                                matchTypes = typesToApply.toList()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.filter_apply))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
