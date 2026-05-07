package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
fun RenderingNoticeBanner(
    onMoreInfo: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer
        )
        Text(
            text = stringResource(R.string.notice_text_only_short),
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onMoreInfo) {
            Text(stringResource(R.string.action_more_info))
        }
        IconButton(onClick = onDismiss) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.action_dismiss),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
        Spacer(Modifier.width(0.dp))
    }
}
