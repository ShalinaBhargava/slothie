package app.slothie.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothie.SlothieViewModel
import app.slothie.UiState
import app.slothie.ai.SlothBrain
import app.slothie.usage.hourLabel
import app.slothie.usage.minutesLabel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(ui: UiState, vm: SlothieViewModel, modifier: Modifier = Modifier, openSettings: () -> Unit) {
    val brainState by vm.brain.state.collectAsStateWithLifecycle()

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Mascot
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Sloth(ui.mood, Modifier.size(220.dp))
        }
        Text(
            when (ui.mood) {
                Mood.Sleeping -> "Barely touched the phone. I could get used to this."
                Mood.Chill -> "About your usual pace. No rush, no fuss."
                Mood.Peeking -> "One eye open. A slippery hour is close."
                Mood.Alarmed -> "You are well past your usual. Put me down for a bit?"
            },
            style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        if (!ui.hasUsageAccess) {
            PermissionCard(
                "Let me see the phone's own usage log",
                "Android already keeps a log of which app was in front, and when. Slothie reads that log on the phone and nowhere else. Nothing is uploaded, ever.",
                "Allow usage access", USAGE_SETTINGS,
            )
            return@Column
        }

        val p = ui.profile ?: return@Column

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Today", minutesLabel(p.todayMinutes), "usually ${minutesLabel(p.baselineSameTime)} by now")
            StatTile("Pickups", "${p.pickupsToday}", "usually ${p.avgPickups.toInt()} a day")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Longest break", minutesLabel(p.longestBreakTodayMin), "off the phone today")
            StatTile("Usual day", minutesLabel(p.avgDailyMinutes), "over ${p.days} days")
        }

        // Risk hours
        SlothCard {
            SectionTitle("When you are most likely on your phone")
            if (p.riskHours.isEmpty()) {
                Dim("Not enough history yet. Give it a day or two.")
            }
            for (r in p.riskHours) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(hourLabel(r.hour), fontWeight = FontWeight.SemiBold)
                        Text("${(r.probability * 100).toInt()}% of days, ~${r.avgMinutes.toInt()} min", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                        Box(Modifier.fillMaxWidth(r.probability.toFloat().coerceIn(0.02f, 1f)).height(8.dp).background(Palette.ochre))
                    }
                    if (r.topApps.isNotEmpty()) Dim(r.topApps.joinToString(", ") { vm.catalog.label(it.first) })
                }
            }
        }

        // Nap
        SlothCard {
            SectionTitle("Nap mode")
            if (ui.nappingUntil > 0L) {
                val t = Instant.ofEpochMilli(ui.nappingUntil).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a"))
                Dim("Holding Do Not Disturb until $t.")
                OutlinedButton(onClick = { vm.endNap() }) { Text("End nap") }
            } else {
                Dim(
                    if (ui.hasDndAccess) "I turn on Do Not Disturb and let go by myself. Zero effort for you."
                    else "Needs Do Not Disturb access once. Tap a nap and I will ask.",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.napNow(30) }) { Text("Nap 30 min") }
                    FilledTonalButton(onClick = { vm.napNow(60) }) { Text("Nap 60 min") }
                }
            }
        }

        // The sloth's note
        SlothCard {
            SectionTitle("A word from Slothie")
            when (val s = brainState) {
                is SlothBrain.ModelState.Missing -> {
                    Dim("My brain is a 2.6 GB open-weight model (Gemma 4 E2B, Apache 2.0). It downloads once and then runs on this phone with no internet.")
                    Button(onClick = { vm.startDownload() }) { Text("Download brain on Wi-Fi") }
                    TextButton(onClick = openSettings) { Text("Or push a model file from a computer") }
                }
                is SlothBrain.ModelState.Downloading -> {
                    Dim("Downloading: ${s.downloadedMb} of ${s.totalMb} MB")
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                }
                is SlothBrain.ModelState.OnDisk -> {
                    Dim("Brain is on the phone. First load takes a few seconds.")
                    Button(onClick = { vm.askSloth() }) { Text("Wake Slothie up") }
                }
                is SlothBrain.ModelState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Dim("Stretching, yawning, loading Gemma...")
                }
                is SlothBrain.ModelState.Error -> {
                    Text(s.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { vm.loadModel() }) { Text("Try again") }
                }
                is SlothBrain.ModelState.Ready -> {
                    if (ui.note.isEmpty() && !ui.noteStreaming) {
                        Dim("Running on this phone (${s.backend}). Ask me what I make of your week.")
                    }
                    if (ui.note.isNotEmpty()) Text(ui.note, style = MaterialTheme.typography.bodyLarge)
                    if (ui.noteStreaming) LinearProgressIndicator(Modifier.fillMaxWidth())
                    if (ui.noteError != null) Text(ui.noteError, color = MaterialTheme.colorScheme.error)
                    Button(onClick = { vm.askSloth() }, enabled = !ui.noteStreaming) {
                        Text(if (ui.note.isEmpty()) "Ask Slothie" else "Ask again")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
