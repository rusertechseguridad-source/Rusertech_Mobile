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
        org.osmdroid.config.Configuration.getInstance().load(applicationContext, android.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext))
        // B2 (tanda 6): UA propio también para los TILES de OSM (misma
        // política de uso que Nominatim — sin UA identificable pueden bloquear).
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "Rusertech-Mobile/1.0"
        enableEdgeToEdge()
        setContent { RusertechTheme { Surface(Modifier.fillMaxSize(), color = DeepSpaceTop) { RusertechNavHost() } } }
    }
}
