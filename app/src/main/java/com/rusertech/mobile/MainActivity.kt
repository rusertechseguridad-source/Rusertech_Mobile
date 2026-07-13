package com.rusertech.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.rusertech.mobile.ui.navigation.RusertechNavHost
import com.rusertech.mobile.ui.theme.DeepSpaceTop
import com.rusertech.mobile.ui.theme.RusertechTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { RusertechTheme { Surface(Modifier.fillMaxSize(), color = DeepSpaceTop) { RusertechNavHost() } } }
    }
}
