package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wook.viewer.R

@Composable
fun LimitationsDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        title = { Text(stringResource(R.string.limitations_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.limitations_dialog_intro))
                Spacer(Modifier.height(4.dp))
                LimitationBullet(stringResource(R.string.limitations_item_table))
                LimitationBullet(stringResource(R.string.limitations_item_image))
                LimitationBullet(stringResource(R.string.limitations_item_format))
                LimitationBullet(stringResource(R.string.limitations_item_pagination))
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.limitations_item_recommend),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun LimitationBullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text("• ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
