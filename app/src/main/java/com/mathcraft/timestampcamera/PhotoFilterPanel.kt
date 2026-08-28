package com.mathcraft.timestampcamera

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val AccentPink = Color(0xFFFF80AB)

@Composable
fun PhotoFilterPanel(
    selected: PhotoFilterPreset,
    intensity: Int,
    onSelect: (PhotoFilterPreset) -> Unit,
    onIntensityChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.78f), RoundedCornerShape(18.dp))
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .clearAndSetSemantics { }
        )
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("강도", color = Color.White, fontSize = 12.sp)
                Slider(
                    value = intensity.toFloat(),
                    onValueChange = { onIntensityChange(it.roundToInt()) },
                    enabled = selected != PhotoFilterPreset.ORIGINAL,
                    valueRange = 0f..100f,
                    steps = 99,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("$intensity", color = Color.White, fontSize = 12.sp)
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PhotoFilterPreset.entries.forEach { preset ->
                    FilterThumbnail(
                        preset = preset,
                        selected = preset == selected,
                        intensity = if (preset == PhotoFilterPreset.ORIGINAL) 0 else intensity,
                        onClick = { onSelect(preset) }
                    )
                }
            }
        }
    }
}

@Composable
fun PhotoFilterButton(
    selected: PhotoFilterPreset,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(54.dp)
            .clip(CircleShape)
            .background(
                if (selected == PhotoFilterPreset.ORIGINAL) Color.White.copy(alpha = 0.85f)
                else AccentPink
            )
            .clickable(
                role = Role.Button,
                onClickLabel = if (expanded) "필터 닫기" else "필터 열기",
                onClick = onClick
            )
            .semantics {
                contentDescription = "필터"
                stateDescription = if (expanded) "열림" else "닫힘"
            },
        contentAlignment = Alignment.Center
    ) {
        Text("🎨필터", color = Color.Black, fontSize = 11.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun FilterThumbnail(
    preset: PhotoFilterPreset,
    selected: Boolean,
    intensity: Int,
    onClick: () -> Unit
) {
    val matrix = PhotoFilter.matrix(preset, intensity).array
    Column(
        Modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${preset.label} 필터"
                this.selected = selected
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(R.drawable.filter_thumbnail_sample),
            contentDescription = null,
            colorFilter = ColorFilter.colorMatrix(ColorMatrix(matrix)),
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) AccentPink else Color.Transparent,
                    shape = RoundedCornerShape(10.dp)
                )
        )
        Text(
            text = preset.label,
            color = if (selected) AccentPink else Color.White,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
