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
import androidx.compose.ui.unit.dp
import com.duplicatefinder.domain.model.FilterCriteria
import com.duplicatefinder.domain.model.MatchType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FilterBottomSheet(
    sheetState: SheetState,
    currentFilter: FilterCriteria,
    availableFolders: List<String>,
    onApply: (FilterCriteria) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolders by remember(currentFilter) {
        mutableStateOf(currentFilter.folders.toSet())
    }
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
                text = "Filters",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Match Type",
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
                        label = { Text(matchType.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }

            if (availableFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Folders",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableFolders.take(10).forEach { folder ->
                        FilterChip(
                            selected = folder in selectedFolders,
                            onClick = {
                                selectedFolders = if (folder in selectedFolders) {
                                    selectedFolders - folder
                                } else {
                                    selectedFolders + folder
                                }
                            },
                            label = { Text(folder) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        selectedFolders = emptySet()
                        selectedMatchTypes = MatchType.entries.toSet()
                        onReset()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset")
                }

                Button(
                    onClick = {
                        onApply(
                            currentFilter.copy(
                                folders = selectedFolders.toList(),
                                matchTypes = selectedMatchTypes.toList()
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Apply")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
