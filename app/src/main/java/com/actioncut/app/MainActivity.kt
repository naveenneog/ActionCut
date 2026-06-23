package com.actioncut.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.actioncut.app.navigation.ActionCutNavHost
import com.actioncut.core.designsystem.theme.ActionCutTheme
import dagger.hilt.android.AndroidEntryPoint

/** Single-activity host. All screens are Compose destinations under [ActionCutNavHost]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ActionCutTheme {
                ActionCutNavHost()
            }
        }
    }
}
