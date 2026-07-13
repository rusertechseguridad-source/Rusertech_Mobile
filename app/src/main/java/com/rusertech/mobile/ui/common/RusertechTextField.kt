package com.rusertech.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusertech.mobile.ui.theme.*

@Composable
fun RusertechTextField(
    value: String, onValueChange: (String) -> Unit,
    label: String, placeholder: String, error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    singleLine: Boolean = true,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = TextSecondary)
        Spacer(Modifier.height(6.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth()
                .background(SurfaceInput, RoundedCornerShape(10.dp))
                .border(0.5.dp, if (error != null) SOSRed else SurfaceBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary, fontWeight = FontWeight.Normal),
            cursorBrush = SolidColor(TechGlowCyan), singleLine = singleLine, maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            decorationBox = { inner -> if (value.isEmpty()) Text(placeholder, fontSize = 15.sp, color = TextMuted); inner() }
        )
        if (error != null) { Spacer(Modifier.height(4.dp)); Text(error, fontSize = 11.sp, color = SOSRed) }
    }
}
