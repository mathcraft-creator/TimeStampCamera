package com.mathcraft.timestampcamera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    config: StampConfig,
    onChange: (StampConfig) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp)
    ) {
        // 상단 바
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로", tint = Color.White)
            }
            Text("각인 설정", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // 1) 각인 항목
        SectionTitle("각인 항목")
        ToggleRow("날짜 · 시간", config.showDateTime) { onChange(config.copy(showDateTime = it)) }
        ToggleRow("GPS 좌표 (위도·경도)", config.showGps) { onChange(config.copy(showGps = it)) }
        ToggleRow("주소 (좌표 → 한글 주소)", config.showAddress) { onChange(config.copy(showAddress = it)) }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = config.customText,
            onValueChange = { onChange(config.copy(customText = it)) },
            label = { Text("메모 / 학원명 (선택)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        // 2) 각인 위치
        SectionTitle("각인 위치")
        ChipRow {
            StampPosition.values().forEach { pos ->
                FilterChip(
                    selected = config.position == pos,
                    onClick = { onChange(config.copy(position = pos)) },
                    label = { Text(pos.label) }
                )
            }
        }

        // 3) 글자 크기
        SectionTitle("글자 크기  (${config.sizePercent}%)")
        Slider(
            value = config.sizePercent.toFloat(),
            onValueChange = { onChange(config.copy(sizePercent = it.roundToInt())) },
            valueRange = 2f..8f,
            steps = 5,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // 4) 글자 색
        SectionTitle("글자 색")
        ChipRow {
            StampColorOption.values().forEach { c ->
                val selected = config.color == c
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(c.color))
                        .border(
                            width = if (selected) 4.dp else 1.dp,
                            color = if (selected) Color(0xFF2196F3) else Color.Gray,
                            shape = CircleShape
                        )
                        .clickable { onChange(config.copy(color = c)) }
                )
            }
        }

        // 5) 글꼴
        SectionTitle("글꼴")
        ChipRow {
            StampFontOption.values().forEach { f ->
                FilterChip(
                    selected = config.font == f,
                    onClick = { onChange(config.copy(font = f)) },
                    label = { Text(f.label) }
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFBBDEFB),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        content()
    }
}
