package com.wook.viewer.presentation.viewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wook.viewer.R
import androidx.compose.ui.res.stringResource
import com.wook.viewer.presentation.theme.BannerAccent
import com.wook.viewer.presentation.theme.BannerBg
import com.wook.viewer.presentation.theme.BannerFg

@Composable
fun RenderingNoticeBanner(
    onMoreInfo: (() -> Unit)?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String = stringResource(R.string.notice_text_only_short)
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(BannerBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = BannerAccent,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = message,
            color = BannerFg,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
        if (onMoreInfo != null) {
            Text(
                text = stringResource(R.string.action_more_info),
                color = BannerAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable(onClick = onMoreInfo)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
            Spacer(Modifier.width(2.dp))
        }
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = stringResource(R.string.action_dismiss),
            tint = BannerAccent,
            modifier = Modifier
                .size(18.dp)
                .clickable(onClick = onDismiss)
        )
    }
}
