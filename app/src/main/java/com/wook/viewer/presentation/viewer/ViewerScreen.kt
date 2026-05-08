package com.wook.viewer.presentation.viewer

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.wook.viewer.presentation.viewer.components.BitmapPage
import com.wook.viewer.presentation.viewer.components.LimitationsDialog
import com.wook.viewer.presentation.viewer.components.RenderingNoticeBanner
import com.wook.viewer.presentation.viewer.components.TextPage
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun ViewerScreen(
    uri: Uri?,
    onBack: () -> Unit,
    vm: ViewerViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()

    var noticeDismissed by rememberSaveable(state.document?.uri?.toString() ?: "") {
        mutableStateOf(false)
    }
    var showLimitationsDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uri) {
        if (uri != null) vm.load(uri)
    }

    val isTextFormat = state.document?.format?.fidelity == RenderingFidelity.TEXT_ONLY
    val showBanner = isTextFormat && !noticeDismissed && state.error == null

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
                PageIndicator(state.currentIndex, state.pageCount)
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
                    .background(Color(0xFF202225)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    state.loading -> LoadingView(state.document?.format)
                    state.error != null -> ErrorView(state.error!!, state.document)
                    state.pageCount > 0 -> PageContent(
                        pageCount = state.pageCount,
                        initialIndex = state.currentIndex,
                        isTextFormat = isTextFormat,
                        onPageChanged = vm::onPageChanged,
                        loadBitmap = vm::renderBitmap,
                        loadText = vm::getPageText
                    )
                }
            }
        }
    }

    if (showLimitationsDialog) {
        LimitationsDialog(onDismiss = { showLimitationsDialog = false })
    }
}

/**
 * 페이지 컨테이너 — HorizontalPager로 좌우 스와이프, 각 페이지는 lazy 렌더.
 *
 * - TEXT_ONLY: SelectionContainer + Text → 길게 눌러서 선택, 복사
 * - 기타(PDF): 비트맵 + 핀치/더블탭 줌
 */
@Composable
private fun PageContent(
    pageCount: Int,
    initialIndex: Int,
    isTextFormat: Boolean,
    onPageChanged: (Int) -> Unit,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?,
    loadText: suspend (index: Int) -> String?
) {
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)),
        pageCount = { pageCount }
    )

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { onPageChanged(it) }
    }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { pageIndex ->
        if (isTextFormat) {
            TextPagerItem(pageIndex = pageIndex, loadText = loadText)
        } else {
            BitmapPagerItem(pageIndex = pageIndex, loadBitmap = loadBitmap)
        }
    }
}

@Composable
private fun BitmapPagerItem(
    pageIndex: Int,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?
) {
    var widthPx by remember { mutableIntStateOf(0) }
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, widthPx) {
        if (widthPx > 0) {
            bitmap = loadBitmap(pageIndex, widthPx)
        }
    }

    BitmapPage(
        bitmap = bitmap,
        onWidthChanged = { widthPx = it }
    )
}

@Composable
private fun TextPagerItem(
    pageIndex: Int,
    loadText: suspend (index: Int) -> String?
) {
    var text by remember(pageIndex) { mutableStateOf<String?>(null) }

    LaunchedEffect(pageIndex) {
        text = loadText(pageIndex) ?: ""
    }

    TextPage(text = text)
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
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
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
                color = Color(0xFFFFB74D),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
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
    }
    val rawMessage = cause?.message ?: error.message ?: "(null)"
    sb.append("message:\n").append(rawMessage)
    return sb.toString()
}

@Composable
private fun PageIndicator(currentIndex: Int, pageCount: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.page_indicator, currentIndex + 1, pageCount),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
