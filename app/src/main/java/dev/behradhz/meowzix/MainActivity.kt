package dev.behradhz.meowzix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dagger.hilt.android.AndroidEntryPoint
import dev.behradhz.meowzix.feature.library.LibraryRoute
import dev.behradhz.meowzix.ui.theme.MeowzixTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MeowzixTheme {
                LibraryRoute()
            }
        }
    }
}
