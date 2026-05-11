package com.wook.viewer.presentation.viewer

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.wook.viewer.presentation.theme.DarkSurface
import com.wook.viewer.presentation.theme.TextOnDark
import com.wook.viewer.presentation.theme.TextOnDarkMuted
import com.wook.viewer.domain.model.PageElement
import com.wook.viewer.presentation.viewer.components.BitmapPageInline
import com.wook.viewer.presentation.viewer.components.BookmarksSheet
import com.wook.viewer.presentation.viewer.components.LimitationsDialog
import com.wook.viewer.presentation.viewer.components.PasswordPromptDialog
import com.wook.viewer.presentation.viewer.components.RenderingNoticeBanner
import com.wook.viewer.presentation.viewer.components.SearchBar
import com.wook.viewer.presentation.viewer.components.ShareSheet
import com.wook.viewer.presentation.viewer.components.SheetTabs
import com.wook.viewer.presentation.viewer.components.TextPageInline
import com.wook.viewer.presentation.viewer.components.shareOriginalDocument
import com.wook.viewer.presentation.viewer.components.sharePlainText
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

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
    var showBookmarksSheet by rememberSaveable { mutableStateOf(false) }
    var showShareSheet by rememberSaveable { mutableStateOf(false) }
    // 비밀번호 다이얼로그 — 사용자가 취소를 눌러서 닫았는지 추적해 다시 열리는 걸 막음
    var passwordDialogDismissed by rememberSaveable(state.document?.uri?.toString() ?: "") {
        mutableStateOf(false)
    }

    LaunchedEffect(uri) {
        if (uri != null) vm.load(uri)
    }

    val format = state.document?.format
    val isTextFormat = format?.fidelity == RenderingFidelity.TEXT_ONLY
    // PDF 텍스트 모드: 비트맵 대신 텍스트로 표시 → 선택/복사 가능
    val isPdfTextMode = format == DocumentFormat.PDF && state.pdfViewMode == PdfViewMode.TEXT
    val isTextDisplay = isTextFormat || isPdfTextMode
    val showBanner = isTextFormat && !noticeDismissed && state.error == null && !state.searchActive

    val isDarkBitmap = !isTextDisplay
    val bgColor = if (isDarkBitmap) DarkBg else MaterialTheme.colorScheme.background
    val barColor = if (isDarkBitmap) DarkSurface else MaterialTheme.colorScheme.surface
    val textColor = if (isDarkBitmap) TextOnDark else MaterialTheme.colorScheme.onSurface
    val mutedColor = if (isDarkBitmap) TextOnDarkMuted else MaterialTheme.colorScheme.onSurfaceVariant
    val iconBg = if (isDarkBitmap) DarkBg else MaterialTheme.colorScheme.surfaceVariant

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        if (state.searchActive) {
            SearchBar(
                query = state.searchQuery,
                matchCount = state.searchMatches.size,
                currentMatchIndex = state.currentMatchIndex,
                searching = state.searching,
                onQueryChange = vm::onSearchQueryChange,
                onPrev = vm::goToPrevMatch,
                onNext = vm::goToNextMatch,
                onClose = { vm.setSearchActive(false) }
            )
        } else {
            ViewerTopBar(
                doc = state.document,
                pageCount = state.pageCount,
                searchSupported = vm.isSearchSupported,
                shareSupported = state.document != null && state.error == null,
                bookmarked = state.currentPageBookmarked,
                bookmarkCount = state.bookmarks.size,
                showPdfModeToggle = format == DocumentFormat.PDF,
                pdfInTextMode = isPdfTextMode,
                barColor = barColor,
                textColor = textColor,
                mutedColor = mutedColor,
                iconBg = iconBg,
                onBack = onBack,
                onSearch = { vm.setSearchActive(true) },
                onShare = { showShareSheet = true },
                onToggleBookmark = vm::toggleCurrentBookmark,
                onShowBookmarks = { showBookmarksSheet = true },
                onTogglePdfMode = vm::togglePdfViewMode
            )
        }

        if (showBanner) {
            RenderingNoticeBanner(
                onMoreInfo = { showLimitationsDialog = true },
                onDismiss = { noticeDismissed = true }
            )
        }

        // XLSX 등 섹션 라벨이 있는 포맷은 상단에 시트 탭 표시
        val labels = state.sectionLabels
        if (labels != null && labels.size > 1) {
            SheetTabs(
                labels = labels,
                selectedIndex = state.currentIndex,
                onSelect = vm::selectSection
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
                state.loading -> LoadingView(state.document?.format, isDarkBitmap)
                state.error != null -> ErrorView(state.error!!, state.document, isDarkBitmap)
                state.pageCount > 0 -> PageContent(
                    pageCount = state.pageCount,
                    currentIndex = state.currentIndex,
                    isTextFormat = isTextDisplay,
                    onPageChanged = vm::onPageChanged,
                    loadBitmap = vm::renderBitmap,
                    loadText = vm::getPageText,
                    loadImages = vm::getPageImages,
                    loadElements = vm::getPageElements,
                    matchesForPage = vm::matchesForPage,
                    activeMatchRange = vm::activeMatchRange,
                    pageBgColor = bgColor
                )
            }
        }

        if (state.pageCount > 0 && state.error == null) {
            PageIndicator(
                currentIndex = state.currentIndex,
                pageCount = state.pageCount,
                isDarkScreen = isDarkBitmap
            )
        }
    }

    if (showLimitationsDialog) {
        LimitationsDialog(onDismiss = { showLimitationsDialog = false })
    }
    if (showBookmarksSheet) {
        BookmarksSheet(
            bookmarks = state.bookmarks,
            pageCount = state.pageCount,
            onJumpTo = vm::jumpToPage,
            onRemove = vm::removeBookmarkAt,
            onDismiss = { showBookmarksSheet = false }
        )
    }

    val pwError = state.error as? DocumentError.PasswordProtected
    if (pwError != null && !passwordDialogDismissed) {
        PasswordPromptDialog(
            wrongPasswordAttempt = pwError.wrongPassword,
            canUnlock = format == DocumentFormat.PDF,
            onSubmit = { vm.unlockWithPassword(it) },
            onDismiss = { passwordDialogDismissed = true }
        )
    }

    if (showShareSheet) {
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()
        val doc = state.document
        ShareSheet(
            canShareText = doc != null && vm.isSearchSupported,
            onShareOriginal = {
                if (doc != null) {
                    // 실제 MIME 우선 — content provider가 제공하는 게 가장 정확
                    val mime = ctx.contentResolver.getType(doc.uri)
                        ?: doc.format.mimeTypes.firstOrNull()
                        ?: "application/octet-stream"
                    shareOriginalDocument(
                        context = ctx,
                        uri = doc.uri,
                        mimeType = mime,
                        subject = doc.displayName
                    )
                }
            },
            onSharePageText = {
                if (doc != null) {
                    scope.launch {
                        val text = vm.getPageText(state.currentIndex)?.trim().orEmpty()
                        if (text.isEmpty()) {
                            Toast.makeText(
                                ctx,
                                ctx.getString(R.string.share_page_text_empty),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            sharePlainText(
                                context = ctx,
                                text = text,
                                subject = doc.displayName
                            )
                        }
                    }
                }
            },
            onDismiss = { showShareSheet = false }
        )
    }
}

@Composable
private fun ViewerTopBar(
    doc: Document?,
    pageCount: Int,
    searchSupported: Boolean,
    shareSupported: Boolean,
    bookmarked: Boolean,
    bookmarkCount: Int,
    showPdfModeToggle: Boolean,
    pdfInTextMode: Boolean,
    barColor: Color,
    textColor: Color,
    mutedColor: Color,
    iconBg: Color,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onShare: () -> Unit,
    onToggleBookmark: () -> Unit,
    onShowBookmarks: () -> Unit,
    onTogglePdfMode: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarIconButton(iconBg = iconBg, onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                tint = textColor
            )
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
        if (showPdfModeToggle) {
            TopBarIconButton(iconBg = iconBg, onClick = onTogglePdfMode) {
                Icon(
                    imageVector = if (pdfInTextMode) Icons.Filled.Image else Icons.Filled.TextFields,
                    contentDescription = if (pdfInTextMode) "비트맵 모드" else "텍스트 모드",
                    tint = textColor
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        if (searchSupported) {
            TopBarIconButton(iconBg = iconBg, onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = "검색", tint = textColor)
            }
            Spacer(Modifier.width(4.dp))
        }
        if (shareSupported) {
            TopBarIconButton(iconBg = iconBg, onClick = onShare) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = stringResource(R.string.action_share),
                    tint = textColor
                )
            }
            Spacer(Modifier.width(4.dp))
        }
        TopBarIconButton(iconBg = iconBg, onClick = onToggleBookmark) {
            Icon(
                imageVector = if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                contentDescription = if (bookmarked) "북마크 해제" else "북마크",
                tint = if (bookmarked) MaterialTheme.colorScheme.primary else textColor
            )
        }
        if (bookmarkCount > 0) {
            Spacer(Modifier.width(4.dp))
            TopBarIconButton(iconBg = iconBg, onClick = onShowBookmarks) {
                Icon(Icons.Filled.Bookmarks, contentDescription = "북마크 목록", tint = textColor)
            }
        }
    }
}

@Composable
private fun TopBarIconButton(
    iconBg: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(iconBg),
        contentAlignment = Alignment.Center
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { content() }
    }
}

@Composable
private fun PageContent(
    pageCount: Int,
    currentIndex: Int,
    isTextFormat: Boolean,
    onPageChanged: (Int) -> Unit,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?,
    loadText: suspend (index: Int) -> String?,
    loadImages: suspend (index: Int) -> List<Bitmap>,
    loadElements: suspend (index: Int) -> List<PageElement>?,
    matchesForPage: (Int) -> List<IntRange>,
    activeMatchRange: (Int) -> IntRange?,
    pageBgColor: Color
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = currentIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
    )

    // 화면 중앙에 가장 가까운 페이지를 "현재 페이지"로 추적
    val visibleCenterIndex by androidx.compose.runtime.derivedStateOf {
        val info = listState.layoutInfo
        val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2) - viewportCenter)
        }?.index ?: listState.firstVisibleItemIndex
    }

    LaunchedEffect(listState) {
        snapshotFlow { visibleCenterIndex }
            .distinctUntilChanged()
            .collect { onPageChanged(it) }
    }

    // VM이 currentIndex를 강제로 바꾼 경우 (검색/북마크 jump) → 그 페이지로 스크롤
    LaunchedEffect(currentIndex) {
        if (visibleCenterIndex != currentIndex) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(pageCount, key = { it }) { pageIndex ->
            if (isTextFormat) {
                TextItem(
                    pageIndex = pageIndex,
                    loadText = loadText,
                    loadImages = loadImages,
                    loadElements = loadElements,
                    matches = matchesForPage(pageIndex),
                    activeMatch = activeMatchRange(pageIndex)
                )
            } else {
                BitmapItem(pageIndex = pageIndex, loadBitmap = loadBitmap)
            }
        }
        item("bottom_spacer") {
            Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(pageBgColor))
        }
    }
}

@Composable
private fun BitmapItem(
    pageIndex: Int,
    loadBitmap: suspend (index: Int, widthPx: Int) -> Bitmap?
) {
    var widthPx by remember(pageIndex) { mutableIntStateOf(0) }
    var bitmap by remember(pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(pageIndex, widthPx) {
        if (widthPx > 0) bitmap = loadBitmap(pageIndex, widthPx)
    }
    BitmapPageInline(bitmap = bitmap, onWidthChanged = { widthPx = it })
}

@Composable
private fun TextItem(
    pageIndex: Int,
    loadText: suspend (index: Int) -> String?,
    loadImages: suspend (index: Int) -> List<Bitmap>,
    loadElements: suspend (index: Int) -> List<PageElement>?,
    matches: List<IntRange>,
    activeMatch: IntRange?
) {
    var text by remember(pageIndex) { mutableStateOf<String?>(null) }
    var images by remember(pageIndex) { mutableStateOf<List<Bitmap>>(emptyList()) }
    var elements by remember(pageIndex) { mutableStateOf<List<PageElement>?>(null) }
    LaunchedEffect(pageIndex) {
        elements = loadElements(pageIndex)
        // 인라인 요소가 있으면 text/images 폴백은 불필요
        if (elements.isNullOrEmpty()) {
            text = loadText(pageIndex) ?: ""
            images = loadImages(pageIndex)
        } else {
            text = ""
            images = emptyList()
        }
    }
    TextPageInline(
        text = text,
        matches = matches,
        activeMatch = activeMatch,
        images = images,
        elements = elements
    )
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
            DocumentFormat.DOCX, DocumentFormat.PPTX, DocumentFormat.XLSX ->
                stringResource(R.string.loading_office)
            else -> stringResource(R.string.loading_generic)
        }
        Text(
            msg,
            color = if (isDarkScreen) TextOnDark else MaterialTheme.colorScheme.onSurface,
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
            color = if (isDarkScreen) TextOnDark else MaterialTheme.colorScheme.onSurface,
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
    val barBg = if (isDarkScreen) DarkSurface else MaterialTheme.colorScheme.surface
    val pillBg = if (isDarkScreen) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant
    val pillFg = if (isDarkScreen) TextOnDark else MaterialTheme.colorScheme.onSurface

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
