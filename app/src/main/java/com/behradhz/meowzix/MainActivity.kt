package com.behradhz.meowzix

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.behradhz.meowzix.ui.MeowzixApp
import com.behradhz.meowzix.ui.theme.MeowzixTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MeowzixTheme {
                MeowzixApp()
            }
        }
    }
}
