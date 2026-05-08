package com.wook.viewer.presentation.filelist

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wook.viewer.R
import com.wook.viewer.app.BuildInfo
import com.wook.viewer.domain.model.RecentDocument
import java.text.DateFormat
import java.util.Date

@Composable
fun FileListScreen(
    onOpenDocument: (Uri) -> Unit,
    vm: FileListViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val event by vm.events.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) vm.onUriPicked(uri)
    }

    LaunchedEffect(event) {
        when (val e = event) {
            is FileListEvent.OpenDocument -> {
                onOpenDocument(e.uri)
                vm.consumeEvent()
            }
            is FileListEvent.ShowError -> {
                snackbar.showSnackbar(e.message)
                vm.consumeEvent()
            }
            null -> Unit
        }
    }

    val ctx = LocalContext.current
    val buildLabel = remember { BuildInfo.displayLabel(ctx) }

    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text(stringResource(R.string.title_recent))
                    Text(
                        text = buildLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            })
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text(stringResource(R.string.action_open_file)) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = { pickFile.launch(allMimeTypes()) }
            )
        }
    ) { padding ->
        if (state.recent.isEmpty()) {
            EmptyState(padding)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                    start = 8.dp,
                    end = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.recent, key = { it.uriString }) { item ->
                    RecentRow(item) { vm.onRecentClicked(item) }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(padding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.empty_recent),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RecentRow(item: RecentDocument, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Description, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(item.displayName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            Text(
                text = "${item.format.displayName} · ${formatDate(item.lastOpenedAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDate(ts: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ts))

private fun allMimeTypes(): Array<String> = arrayOf(
    // 문서
    "application/pdf",
    "application/msword",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "application/vnd.ms-powerpoint",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "application/x-hwp",
    "application/haansofthwp",
    "application/vnd.hancom.hwpx",
    // 텍스트 가족 (markdown / plain / csv / json / xml / yaml 등)
    "text/*",
    "application/json",
    "application/xml",
    "application/yaml",
    "application/toml",
    // 폴백
    "application/octet-stream"
)
