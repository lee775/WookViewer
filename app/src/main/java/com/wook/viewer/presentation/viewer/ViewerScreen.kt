package com.wook.viewer.presentation.viewer

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wook.viewer.R
import com.wook.viewer.app.BuildInfo
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.RenderingFidelity
import com.wook.viewer.presentation.viewer.components.LimitationsDialog
import com.wook.viewer.presentation.viewer.components.RenderingNoticeBanner

@Composable
fun ViewerScreen(
    uri: Uri?,
    onBack: () -> Unit,
    vm: ViewerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    var containerWidthPx by remember { mutableStateOf(0) }

    // 배너 표시 여부 — 사용자가 닫으면 현 세션에서 다시 안 뜸 (영속 X — v0.2 스코프)
    var noticeDismissed by rememberSaveable(state.document?.uri?.toString() ?: "") {
        mutableStateOf(false)
    }
    var showLimitationsDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uri, containerWidthPx) {
        if (uri != null && containerWidthPx > 0) {
            vm.load(uri, containerWidthPx)
        }
    }

    val showBanner = state.document?.format?.fidelity == RenderingFidelity.TEXT_ONLY &&
            !noticeDismissed && state.error == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = state.document?.displayName ?: stringResource(R.string.title_viewer),
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        bottomBar = {
            if (state.pageCount > 0 && state.error == null) {
                PageIndicator(
                    currentIndex = state.currentIndex,
                    pageCount = state.pageCount,
                    onPrev = { vm.goToPage(state.currentIndex - 1, containerWidthPx) },
                    onNext = { vm.goToPage(state.currentIndex + 1, containerWidthPx) }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showBanner) {
                RenderingNoticeBanner(
                    onMoreInfo = { showLimitationsDialog = true },
                    onDismiss = { noticeDismissed = true }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF202225))
                    .onSizeChanged { containerWidthPx = it.width },
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.loading -> LoadingView(state.document?.format)
                    state.error != null -> ErrorView(state.error!!, state.document)
                    state.pageBitmap != null -> ZoomablePage(
                        bitmap = state.pageBitmap!!.asImageBitmap()
                    )
                }
            }
        }
    }

    if (showLimitationsDialog) {
        LimitationsDialog(onDismiss = { showLimitationsDialog = false })
    }
}

@Composable
private fun LoadingView(format: DocumentFormat?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        val msg = when (format) {
            DocumentFormat.HWP -> stringResource(R.string.loading_hwp)
            DocumentFormat.DOCX, DocumentFormat.PPTX -> stringResource(R.string.loading_office)
            else -> stringResource(R.string.loading_generic)
        }
        Text(msg, color = Color.White, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ErrorView(error: DocumentError, doc: Document?) {
    val baseMsg = when (error) {
        is DocumentError.PasswordProtected -> stringResource(R.string.error_password)
        is DocumentError.Corrupted -> stringResource(R.string.error_corrupted)
        is DocumentError.IoError -> stringResource(R.string.error_io)
        is DocumentError.UnsupportedVariant -> stringResource(
            R.string.error_unsupported_variant,
            doc?.format?.displayName ?: error.variant
        )
        is DocumentError.Unknown -> stringResource(R.string.error_unknown)
    }

    val ctx = LocalContext.current
    val showDebug = remember { BuildInfo.isDebuggable(ctx) }
    val debugInfo = if (showDebug) buildDebugInfo(error) else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = baseMsg,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        if (debugInfo != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = debugInfo,
                color = Color(0xFFFFB74D),  // orange — 디버그 표시
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start
            )
        }
    }
}

private fun buildDebugInfo(error: DocumentError): String {
    val sb = StringBuilder("[debug]\n")
    sb.append("type: ").append(error::class.java.simpleName).append('\n')
    val cause = error.cause
    if (cause != null) {
        sb.append("cause: ").append(cause::class.java.simpleName).append('\n')
        sb.append("message: ").append(cause.message ?: "(null)")
    } else {
        sb.append("message: ").append(error.message ?: "(null)")
    }
    return sb.toString()
}

@Composable
private fun ZoomablePage(bitmap: androidx.compose.ui.graphics.ImageBitmap) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            )
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                }
            }
    )
}

@Composable
private fun PageIndicator(
    currentIndex: Int,
    pageCount: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onPrev, enabled = currentIndex > 0) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "이전 페이지")
        }
        Text(
            text = stringResource(R.string.page_indicator, currentIndex + 1, pageCount),
            style = MaterialTheme.typography.bodyMedium
        )
        IconButton(onClick = onNext, enabled = currentIndex < pageCount - 1) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "다음 페이지")
        }
    }
}
