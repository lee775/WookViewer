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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.wook.viewer.R
import com.wook.viewer.app.BuildInfo
import com.wook.viewer.domain.error.DocumentError
import com.wook.viewer.domain.model.Document
import com.wook.viewer.domain.model.DocumentFormat
import com.wook.viewer.domain.model.RenderingFidelity
import com.wook.viewer.presentation.theme.DarkBg
import com.wook.viewer.presentation.theme.DarkElevated
import com.wook.viewer.presentation.theme.DarkSurface
import com.wook.viewer.presentation.theme.LightBg
import com.wook.viewer.presentation.theme.LightSurface
import com.wook.viewer.presentation.theme.LightSurfaceAlt
import com.wook.viewer.presentation.theme.TextOnDark
import com.wook.viewer.presentation.theme.TextOnDarkMuted
import com.wook.viewer.presentation.theme.TextPrimary
import com.wook.viewer.presentation.theme.TextSecondary
import com.wook.viewer.presentation.viewer.components.BitmapPage
import com.wook.viewer.presentation.viewer.components.LimitationsDialog
import com.wook.viewer.presentation.viewer.components.RenderingNoticeBanner
import com.wook.viewer.presentation.viewer.components.TextPage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 욱뷰어 v0.5 뷰어 — 콘텐츠가 비트맵/이미지면 다크 테마, 텍스트면 라이트 테마.
 */
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

    val isDarkScreen = !isTextFormat  // 비트맵(PDF/이미지) → 다크
    val bgColor = if (isDarkScreen) DarkBg else LightBg
    val barColor = if (isDarkScreen) DarkSurface else LightSurface
    val textColor = if (isDarkScreen) TextOnDark else TextPrimary
    val mutedColor = if (isDarkScreen) TextOnDarkMuted else TextSecondary

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        ViewerTopBar(
            doc = state.document,
            pageCount = state.pageCount,
            barColor = barColor,
            textColor = textColor,
            mutedColor = mutedColor,
            iconBg = if (isDarkScreen) DarkBg else LightSurfaceAlt,
            onBack = onBack
        )

        if (showBanner) {
            RenderingNoticeBanner(
                onMoreInfo = { showLimitationsDialog = true },
                onDismiss = { noticeDismissed = true }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            when {
                state.loading -> LoadingView(state.document?.format, isDarkScreen)
                state.error != null -> ErrorView(state.error!!, state.document, isDarkScreen)
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

        if (state.pageCount > 0 && state.error == null) {
            PageIndicator(
                currentIndex = state.currentIndex,
                pageCount = state.pageCount,
                isDarkScreen = isDarkScreen
            )
        }
    }

    if (showLimitationsDialog) {
        LimitationsDialog(onDismiss = { showLimitationsDialog = false })
    }
}

@Composable
private fun ViewerTopBar(
    doc: Document?,
    pageCount: Int,
    barColor: Color,
    textColor: Color,
    mutedColor: Color,
    iconBg: Color,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                    tint = textColor
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
            Text(
                text = doc?.displayName ?: stringResource(R.string.title_viewer),
                color = textColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            if (doc != null && pageCount > 0) {
                Text(
                    text = "${doc.format.displayName} · ${pageCount} 페이지",
                    color = mutedColor,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}

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
        if (widthPx > 0) bitmap = loadBitmap(pageIndex, widthPx)
    }

    BitmapPage(bitmap = bitmap, onWidthChanged = { widthPx = it })
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
private fun LoadingView(format: DocumentFormat?, isDarkScreen: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator(color = if (isDarkScreen) TextOnDark else MaterialTheme.colorScheme.primary)
        val msg = when (format) {
            DocumentFormat.HWP -> stringResource(R.string.loading_hwp)
            DocumentFormat.DOCX, DocumentFormat.PPTX, DocumentFormat.XLSX -> stringResource(R.string.loading_office)
            else -> stringResource(R.string.loading_generic)
        }
        Text(
            msg,
            color = if (isDarkScreen) TextOnDark else TextPrimary,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun ErrorView(error: DocumentError, doc: Document?, isDarkScreen: Boolean) {
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
            color = if (isDarkScreen) TextOnDark else TextPrimary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp
        )
        if (debugInfo != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = debugInfo,
                color = Color(0xFFFFB74D),
                fontSize = 11.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

private fun buildDebugInfo(error: DocumentError): String {
    val sb = StringBuilder("[debug]\n")
    sb.append("type: ").append(error::class.java.simpleName).append('\n')
    val cause = error.cause
    if (cause != null) sb.append("cause: ").append(cause::class.java.simpleName).append('\n')
    val rawMessage = cause?.message ?: error.message ?: "(null)"
    sb.append("message:\n").append(rawMessage)
    return sb.toString()
}

@Composable
private fun PageIndicator(
    currentIndex: Int,
    pageCount: Int,
    isDarkScreen: Boolean
) {
    val barBg = if (isDarkScreen) DarkSurface else LightSurface
    val pillBg = if (isDarkScreen) DarkElevated else LightSurfaceAlt
    val pillFg = if (isDarkScreen) TextOnDark else TextPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(pillBg)
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "${currentIndex + 1} / $pageCount",
                color = pillFg,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
