package com.muzrc.klipsync

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.muzrc.klipsync.ui.EditorScreen
import com.muzrc.klipsync.ui.theme.KlipSyncTheme
import com.muzrc.klipsync.viewmodel.EditorViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            KlipSyncTheme {
                val editorViewModel: EditorViewModel = viewModel()
                EditorScreen(viewModel = editorViewModel)
            }
        }
    }
}
