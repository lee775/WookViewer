package com.wook.viewer.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 단일 오픈소스 라이브러리 고지 항목.
 * LGPL 의무: name + license + sourceUrl + copyright + (해당 시) replacement note 포함.
 */
private data class LibraryNotice(
    val name: String,
    val license: String,
    val copyright: String,
    val sourceUrl: String,
    val noteKr: String? = null
)

private val LIBRARIES = listOf(
    LibraryNotice(
        name = "LibreOffice (LibreOfficeKit)",
        license = "Mozilla Public License v2.0 / GNU LGPL v3",
        copyright = "© The Document Foundation and contributors",
        sourceUrl = "https://www.libreoffice.org/about-us/source-code/",
        noteKr = "이 앱은 LibreOffice 의 native 라이브러리(liblo-native-code.so)를 동적 링크 방식으로 " +
            "포함합니다 (LGPL §4 준수). 사용자는 위 사이트에서 LibreOffice 소스를 받아 " +
            "직접 빌드한 .so 로 APK 안의 동일 파일을 교체할 수 있습니다. " +
            "이 앱의 빌드 스크립트는 GitHub 에 공개되어 있습니다 — github.com/lee775/WookViewer"
    ),
    LibraryNotice(
        name = "Apache POI",
        license = "Apache License 2.0",
        copyright = "© The Apache Software Foundation",
        sourceUrl = "https://poi.apache.org/"
    ),
    LibraryNotice(
        name = "Apache XMLBeans",
        license = "Apache License 2.0",
        copyright = "© The Apache Software Foundation",
        sourceUrl = "https://xmlbeans.apache.org/"
    ),
    LibraryNotice(
        name = "PdfBox-Android",
        license = "Apache License 2.0",
        copyright = "© Tom Roush",
        sourceUrl = "https://github.com/TomRoush/PdfBox-Android"
    ),
    LibraryNotice(
        name = "hwplib",
        license = "Apache License 2.0",
        copyright = "© neolord0 (kr.dogfoot)",
        sourceUrl = "https://github.com/neolord0/hwplib"
    ),
    LibraryNotice(
        name = "hwpxlib",
        license = "Apache License 2.0",
        copyright = "© neolord0 (kr.dogfoot)",
        sourceUrl = "https://github.com/neolord0/hwpxlib"
    ),
    LibraryNotice(
        name = "hwpxlib_ext",
        license = "Apache License 2.0",
        copyright = "© neolord0 (kr.dogfoot)",
        sourceUrl = "https://github.com/neolord0/hwpxlib_ext"
    ),
    LibraryNotice(
        name = "Bouncy Castle",
        license = "MIT-style (Bouncy Castle License)",
        copyright = "© The Legion of the Bouncy Castle Inc.",
        sourceUrl = "https://www.bouncycastle.org/"
    ),
    LibraryNotice(
        name = "Kotlin / Coroutines",
        license = "Apache License 2.0",
        copyright = "© JetBrains s.r.o.",
        sourceUrl = "https://github.com/JetBrains/kotlin"
    ),
    LibraryNotice(
        name = "Jetpack Compose / Material 3",
        license = "Apache License 2.0",
        copyright = "© The Android Open Source Project / Google",
        sourceUrl = "https://developer.android.com/jetpack/compose"
    ),
    LibraryNotice(
        name = "Hilt (Dagger)",
        license = "Apache License 2.0",
        copyright = "© Google Inc.",
        sourceUrl = "https://dagger.dev/hilt/"
    ),
    LibraryNotice(
        name = "Room",
        license = "Apache License 2.0",
        copyright = "© The Android Open Source Project / Google",
        sourceUrl = "https://developer.android.com/training/data-storage/room"
    ),
    LibraryNotice(
        name = "Coil",
        license = "Apache License 2.0",
        copyright = "© Coil Contributors",
        sourceUrl = "https://github.com/coil-kt/coil"
    ),
    LibraryNotice(
        name = "Timber",
        license = "Apache License 2.0",
        copyright = "© Jake Wharton",
        sourceUrl = "https://github.com/JakeWharton/timber"
    ),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "오픈소스 라이선스",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 머리말 — 앱의 라이선스 입장 요약
            Text(
                text = "이 앱은 아래 오픈소스 소프트웨어를 사용합니다. " +
                    "각 라이브러리는 해당 라이선스에 따라 배포되며 원저작자에게 모든 권리가 귀속됩니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            LIBRARIES.forEach { lib ->
                LibraryNoticeItem(lib) {
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(lib.sourceUrl))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "각 라이선스의 전문은 위 소스 링크에서 확인할 수 있습니다. " +
                    "Apache 2.0 / LGPL v3 / MPL v2 / MIT 전문은 SPDX 사이트(spdx.org/licenses)에서도 열람 가능합니다.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun LibraryNoticeItem(lib: LibraryNotice, onOpenUrl: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenUrl)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lib.name,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = "소스 열기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = lib.license,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = lib.copyright,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
        if (lib.noteKr != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = lib.noteKr,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = lib.sourceUrl,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 11.sp
        )
    }
}

