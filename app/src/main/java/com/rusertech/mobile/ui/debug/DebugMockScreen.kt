package com.rusertech.mobile.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rusertech.mobile.data.local.prefs.MockAuthMode
import com.rusertech.mobile.data.local.prefs.UserPreferences
import com.rusertech.mobile.ui.theme.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugMockViewModel @Inject constructor(
    private val prefs: UserPreferences
) : ViewModel() {
    val currentMode: StateFlow<MockAuthMode> = prefs.mockAuthMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MockAuthMode.SUCCESS)

    fun setMode(mode: MockAuthMode) {
        viewModelScope.launch { prefs.setMockAuthMode(mode) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugMockScreen(
    onBack: () -> Unit,
    viewModel: DebugMockViewModel = hiltViewModel()
) {
    val currentMode by viewModel.currentMode.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mock Mode Settings", fontSize = 18.sp, color = TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = TextPrimary) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepSpaceTop)
            )
        },
        containerColor = DeepSpaceBottom
    ) { padding ->
        LazyColumn(contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    "Selecciona el comportamiento del Login MOCK:",
                    color = TextSecondary,
                    modifier = Modifier.padding(16.dp)
                )
            }
            items(MockAuthMode.values()) { mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setMode(mode) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentMode == mode,
                        onClick = { viewModel.setMode(mode) },
                        colors = RadioButtonDefaults.colors(selectedColor = TechGlowCyan, unselectedColor = TextMuted)
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = mode.name,
                        color = if (currentMode == mode) TechGlowCyan else TextPrimary,
                        fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}
