package app.slothie.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.slothie.SlothieViewModel
import app.slothie.UiState
import app.slothie.usage.hourLabel
import app.slothie.usage.hourRangeLabel
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HoursScreen(ui: UiState, vm: SlothieViewModel, modifier: Modifier = Modifier) {
    val p = ui.profile
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hours", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (p == null) {
            if (!ui.hasUsageAccess) PermissionCard("Usage access needed", "Grant it on the Today tab to see your hours.", "Allow usage access", USAGE_SETTINGS)
            return@Column
        }
        var selected by remember { mutableIntStateOf(p.riskHours.firstOrNull()?.hour ?: p.currentHour) }
        val riskSet = p.riskHours.map { it.hour }.toSet()

        SlothCard {
            SectionTitle("Chance you are on the phone, by hour")
            Dim("Share of the last ${p.days} days with 5+ minutes of use in that hour. Tap a bar.")
            Canvas(
                Modifier.fillMaxWidth().height(160.dp).pointerInput(p) {
                    detectTapGestures { pos -> selected = (pos.x / (size.width / 24f)).toInt().coerceIn(0, 23) }
                },
            ) {
                val bw = size.width / 24f
                val labelH = 18.dp.toPx()
                val chartH = size.height - labelH
                for (h in 0 until 24) {
                    val v = p.hourProbability[h].toFloat()
                    val barH = chartH * v
                    val color = when {
                        h == selected -> Palette.text
                        h in riskSet -> Palette.ochre
                        else -> lerp(Palette.mossDim, Palette.moss, v)
                    }
                    drawRoundRect(color, Offset(h * bw + bw * 0.15f, chartH - barH), Size(bw * 0.7f, barH.coerceAtLeast(2f)), CornerRadius(3f))
                }
                drawLine(Palette.textDim.copy(alpha = 0.4f), Offset(0f, chartH), Offset(size.width, chartH), 1f)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (l in listOf("12 AM", "6 AM", "12 PM", "6 PM", "11 PM")) Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SlothCard {
            Text(hourRangeLabel(selected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${(p.hourProbability[selected] * 100).toInt()}% of days, about ${p.avgMinutesByHour[selected].toInt()} min when it happens")
            val apps = p.topAppsByHour[selected] ?: emptyList()
            if (apps.isEmpty()) Dim("Nothing recorded in this hour.")
            for ((pkg, mins) in apps) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(vm.catalog.label(pkg))
                    Dim("${(mins / p.days).toInt()} min/day")
                }
            }
            if (selected in riskSet) Text("One of your risk hours. I will nudge you ${12} minutes before.", color = Palette.ochre)
        }

        SlothCard {
            SectionTitle("The last ${p.days} days, hour by hour")
            Dim("Brighter means more minutes. Rows are days, oldest at the top.")
            val maxMin = p.minutes.maxOf { row -> row.maxOrNull() ?: 0.0 }.coerceAtLeast(1.0)
            Canvas(Modifier.fillMaxWidth().height((p.days * 22 + 4).dp)) {
                val cw = size.width / 24f
                val ch = size.height / p.days
                for (d in 0 until p.days) for (h in 0 until 24) {
                    val v = (p.minutes[d][h] / maxMin).toFloat().coerceIn(0f, 1f)
                    val c = if (v == 0f) Palette.surfaceHigh else lerp(Palette.mossDim, Palette.ochre, v)
                    drawRoundRect(c, Offset(h * cw + 1f, d * ch + 1f), Size(cw - 2f, ch - 2f), CornerRadius(3f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Dim(p.dates.first().dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) + " " + p.dates.first().dayOfMonth)
                Dim("today")
            }
        }

        SlothCard {
            SectionTitle("Risk hours")
            if (p.riskHours.isEmpty()) Dim("None yet.")
            for (r in p.riskHours) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(hourLabel(r.hour), fontWeight = FontWeight.SemiBold)
                    Dim("${(r.probability * 100).toInt()}% of days, ~${r.avgMinutes.toInt()} min")
                }
            }
        }
    }
}
