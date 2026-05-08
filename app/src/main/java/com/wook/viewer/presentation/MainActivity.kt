package com.wook.viewer.presentation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.wook.viewer.presentation.filelist.FileListScreen
import com.wook.viewer.presentation.theme.WookViewerTheme
import com.wook.viewer.presentation.viewer.ViewerScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val incoming: Uri? = intent?.takeIf { it.action == Intent.ACTION_VIEW }?.data

        setContent {
            WookViewerTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(initialUri = incoming)
                }
            }
        }
    }
}

private object Routes {
    const val LIST = "list"
    const val VIEWER = "viewer?uri={uri}"
    fun viewer(uri: Uri) = "viewer?uri=${Uri.encode(uri.toString())}"
}

@androidx.compose.runtime.Composable
private fun AppNavHost(initialUri: Uri?) {
    val nav = rememberNavController()
    var consumedInitial by remember { androidx.compose.runtime.mutableStateOf(false) }

    NavHost(navController = nav, startDestination = Routes.LIST) {
        composable(Routes.LIST) {
            // 외부 인텐트로 들어온 URI는 한 번만 자동 진입
            if (initialUri != null && !consumedInitial) {
                consumedInitial = true
                nav.navigate(Routes.viewer(initialUri))
            }
            FileListScreen(
                onOpenDocument = { uri -> nav.navigate(Routes.viewer(uri)) }
            )
        }
        composable(Routes.VIEWER) { backStack ->
            // Compose Navigation은 query argument를 자동으로 한 번 URL-decode하므로
            // 여기서 추가로 Uri.decode를 호출하면 안 됨 (이중 디코드 시 %3A → ':' 등으로
            // 문자열이 바뀌어 ContentResolver의 URI 권한 매칭이 실패한다 — v0.4.4까지 발생).
            val uriArg = backStack.arguments?.getString("uri")
            val uri = uriArg?.let { Uri.parse(it) }
            ViewerScreen(
                uri = uri,
                onBack = { nav.popBackStack() }
            )
        }
    }
}
