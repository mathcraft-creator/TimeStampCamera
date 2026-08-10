package com.mathcraft.timestampcamera

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // 펼쳐진 섹션의 id. null 이면 전부 접힌 상태.
    var expandedSection by remember { mutableStateOf<String?>(null) }
    fun toggle(id: String) {
        expandedSection = if (expandedSection == id) null else id
    }

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
        ExpandableSection("각인 항목", expandedSection == "items", onToggle = { toggle("items") }) {
            ToggleRow("날짜 · 시간", config.showDateTime) { onChange(config.copy(showDateTime = it)) }
            ToggleRow("GPS 좌표 (위도·경도)", config.showGps) { onChange(config.copy(showGps = it)) }
            ToggleRow("주소 (좌표 → 한글 주소)", config.showAddress) { onChange(config.copy(showAddress = it)) }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = config.customText,
                onValueChange = { onChange(config.copy(customText = it)) },
                label = { Text("메모 / 학원명 (선택)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        // 2) 사진 비율
        ExpandableSection("사진 비율", expandedSection == "ratio", onToggle = { toggle("ratio") }) {
            ChipRow {
                StampAspectRatio.values().forEach { ratio ->
                    FilterChip(
                        selected = config.aspectRatio == ratio,
                        onClick = { onChange(config.copy(aspectRatio = ratio)) },
                        label = { Text(ratio.label) }
                    )
                }
            }
        }

        // 3) 날짜 형식
        ExpandableSection("날짜 형식", expandedSection == "dateFormat", onToggle = { toggle("dateFormat") }) {
            ChipRow {
                StampTemplate.values().forEach { temp ->
                    FilterChip(
                        selected = config.template == temp,
                        onClick = { onChange(config.copy(template = temp)) },
                        label = { Text(temp.label) }
                    )
                }
            }
        }

        // 4) 각인 위치
        ExpandableSection("각인 위치", expandedSection == "position", onToggle = { toggle("position") }) {
            ChipRow {
                StampPosition.values().forEach { pos ->
                    FilterChip(
                        selected = config.position == pos,
                        onClick = { onChange(config.copy(position = pos)) },
                        label = { Text(pos.label) }
                    )
                }
            }
        }

        // 5) 글자 크기
        ExpandableSection("글자 크기 (${config.fontSize} PT)", expandedSection == "fontSize", onToggle = { toggle("fontSize") }) {
            Slider(
                value = config.fontSize.toFloat(),
                onValueChange = { onChange(config.copy(fontSize = it.roundToInt())) },
                valueRange = 20f..150f,
                steps = 12,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        // 6) 글자 색
        ExpandableSection("글자 색", expandedSection == "textColor", onToggle = { toggle("textColor") }) {
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
        }

        // 7) 글꼴
        ExpandableSection("글꼴", expandedSection == "font", onToggle = { toggle("font") }) {
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

        // 8) 로고 설정
        ExpandableSection("로고 설정 (Butterfly)", expandedSection == "logo", onToggle = { toggle("logo") }) {
            ToggleRow("로고 표시", config.showLogo) { onChange(config.copy(showLogo = it)) }

            if (config.showLogo) {
                Text("로고 색상", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ChipRow {
                    StampLogoColor.values().forEach { color ->
                        FilterChip(
                            selected = config.logoColor == color,
                            onClick = { onChange(config.copy(logoColor = color)) },
                            label = { Text(color.label) }
                        )
                    }
                }

                Text("로고 위치", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ChipRow {
                    LogoPosition.values().forEach { pos ->
                        FilterChip(
                            selected = config.logoPosition == pos,
                            onClick = { onChange(config.copy(logoPosition = pos)) },
                            label = { Text(pos.label) }
                        )
                    }
                }

                Text("로고 크기 (${config.logoSize})", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                Slider(
                    value = config.logoSize.toFloat(),
                    onValueChange = { onChange(config.copy(logoSize = it.roundToInt())) },
                    valueRange = 30f..100f,
                    steps = 7,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Text("로고 글꼴", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ChipRow {
                    StampFontOption.values().forEach { f ->
                        FilterChip(
                            selected = config.logoFont == f,
                            onClick = { onChange(config.copy(logoFont = f)) },
                            label = { Text(f.label) }
                        )
                    }
                }
            }
        }

        // 9) 테두리 설정
        ExpandableSection("테두리 설정", expandedSection == "border", onToggle = { toggle("border") }) {
            ChipRow {
                StampBorder.values().forEach { b ->
                    FilterChip(
                        selected = config.border == b,
                        onClick = { onChange(config.copy(border = b)) },
                        label = { Text(b.label) }
                    )
                }
            }

            if (config.border != StampBorder.NONE) {
                Text("테두리 굵기", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                Slider(
                    value = config.borderThickness.toFloat(),
                    onValueChange = { onChange(config.copy(borderThickness = it.roundToInt())) },
                    valueRange = 4f..40f,
                    steps = 8,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Text("테두리 색상", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp))
                ChipRow {
                    StampBorderColor.values().forEach { c ->
                        val selected = config.borderColor == c.color
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
                                .clickable { onChange(config.copy(borderColor = c.color)) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 클릭하면 펼쳐지고, 다시 클릭하면 접히는 설정 섹션.
 * 제목 행이 항상 보이는 "메뉴 리스트" 역할을 하고, 그 아래 상세 설정은 펼쳐졌을 때만 보인다.
 */
@Composable
private fun ExpandableSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = Color(0xFFBBDEFB),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "접기" else "펼치기",
                tint = Color.White
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                content()
            }
        }
        HorizontalDivider(color = Color(0xFF2A2A2A), thickness = 1.dp)
    }
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
