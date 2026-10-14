package com.archieapps.oklo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.archieapps.oklo.data.prefs.Appearance
import com.archieapps.oklo.ui.nav.OkloRoot
import com.archieapps.oklo.ui.theme.OkloTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as OkloApp).container
        if (savedInstanceState == null) {
            container.sync.requestSync()
            container.lessonAiFinisher.resumePending()
        }
        setContent {
            val appearance by container.preferences.appearance.collectAsStateWithLifecycle(initialValue = null as Appearance?)
            val current = appearance ?: return@setContent
            CompositionLocalProvider(LocalContainer provides container) {
                OkloTheme(mode = current.theme, textScale = current.textScale) {
                    OkloRoot()
                }
            }
        }
    }
}
