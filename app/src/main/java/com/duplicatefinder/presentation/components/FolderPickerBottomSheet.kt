package com.duplicatefinder.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.duplicatefinder.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerBottomSheet(
    sheetState: SheetState,
    availableFolders: List<String>,
    currentSelection: Set<String>,
    onApply: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedFolders by remember(currentSelection) {
        mutableStateOf(currentSelection)
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
                text = stringResource(R.string.scan_select_folders_title),
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (availableFolders.isEmpty()) {
                Text(
                    text = stringResource(R.string.scan_no_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                ) {
                    items(availableFolders) { folder ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = folder in selectedFolders,
                                onCheckedChange = { checked ->
                                    selectedFolders = if (checked) {
                                        selectedFolders + folder
                                    } else {
                                        selectedFolders - folder
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = folder,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedFolders = emptySet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scan_clear_selection))
                }

                OutlinedButton(
                    onClick = { selectedFolders = availableFolders.toSet() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.scan_select_all))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onApply(selectedFolders) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.scan_apply_folders))
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}
