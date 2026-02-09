package com.duplicatefinder.presentation.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.duplicatefinder.R

@Composable
fun ConfirmDeleteDialog(
    count: Int,
    isPermanent: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isPermanent) {
                    stringResource(R.string.dialog_delete_permanent_title)
                } else {
                    stringResource(R.string.dialog_delete_title)
                }
            )
        },
        text = {
            Text(
                text = if (isPermanent) {
                    stringResource(R.string.dialog_delete_permanent_message, count)
                } else {
                    stringResource(R.string.dialog_delete_message, count)
                }
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (isPermanent) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(
                    text = if (isPermanent) {
                        stringResource(R.string.dialog_delete_permanent_confirm)
                    } else {
                        stringResource(R.string.dialog_delete_confirm)
                    }
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun ConfirmRestoreDialog(
    count: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.dialog_restore_title))
        },
        text = {
            Text(text = stringResource(R.string.dialog_restore_message, count))
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.dialog_restore_confirm))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
