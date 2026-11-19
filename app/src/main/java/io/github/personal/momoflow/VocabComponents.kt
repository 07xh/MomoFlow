package io.github.personal.momoflow

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Slate = Color(0xFF0F172A)
private val SlateMuted = Color(0xFF64748B)
private val LightCanvas = Color(0xFFF8FAFC)

@Composable
fun StudyModeSwitch(
    mode: String,
    onSwitch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(5.dp, RoundedCornerShape(100.dp)),
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = 0.96f),
        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0))
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("exam" to "精听·笔试", "vocab" to "背单词").forEach { (id, title) ->
                val chosen = mode == id
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(100.dp))
                        .testTag("app-mode-" + id)
                        .background(if (chosen) Slate else Color.Transparent)
                        .clickable { onSwitch(id) }
                        .padding(horizontal = 13.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        title, fontSize = 12.sp,
                        color = if (chosen) Color.White else SlateMuted,
                        fontWeight = if (chosen) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun VocabGlassDock(
    active: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.shadow(16.dp, RoundedCornerShape(100.dp)),
        color = Color.White.copy(alpha = 0.95f),
        shape = RoundedCornerShape(100.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE4E8EF))
    ) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf("study" to "学习", "fluid" to "流体云",
                "verify" to "核验", "connections" to "连接").forEachIndexed { i, (key, label) ->
                val selected = key == active
                val icon = when (i) {
                    0 -> Icons.Outlined.Home
                    1 -> Icons.Outlined.MenuBook
                    2 -> Icons.Outlined.BarChart
                    else -> Icons.Outlined.AccountCircle
                }
                Row(
                    Modifier.clip(RoundedCornerShape(100.dp))
                        .testTag("vocab-tab-" + key)
                        .background(if (selected) Color(0xFFE9EFF6) else Color.Transparent)
                        .clickable { onSelect(key) }
                        .height(40.dp).padding(horizontal = if (selected) 11.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(icon, label, modifier = Modifier.size(19.dp),
                        tint = if (selected) Slate else SlateMuted)
                    if (selected) {
                        Text(label, color = Slate, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WordMetric(label: String, value: Int, suffix: String = "") {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = SlateMuted, fontSize = 11.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.toString(), color = Slate, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(suffix, fontSize = 10.sp, color = SlateMuted,
                modifier = Modifier.padding(start = 2.dp, bottom = 3.dp))
        }
    }
}

@Composable
fun WordSection(
    modifier: Modifier = Modifier,
    background: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        color = background,
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE9EDF2))
    ) {
        Column(Modifier.padding(19.dp), verticalArrangement = Arrangement.spacedBy(11.dp),
            content = content)
    }
}

fun memoryName(tier: Int): String = when (tier) {
    3 -> "牢固"
    2 -> "稳定"
    1 -> "模糊"
    else -> "快忘了"
}

fun memoryColor(tier: Int): Color = when (tier) {
    3 -> Color(0xFF18886A)
    2 -> Color(0xFF4A76A8)
    1 -> Color(0xFFA87C40)
    else -> Color(0xFFB8716D)
}
