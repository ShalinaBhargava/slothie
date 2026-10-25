package app.slothie.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothie.SlothieViewModel
import app.slothie.UiState
import app.slothie.ai.SlothBrain

@Composable
fun SettingsScreen(ui: UiState, vm: SlothieViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val brainState by vm.brain.state.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

        SlothCard {
            SectionTitle("Permissions")
            PermRow("Usage access", ui.hasUsageAccess, USAGE_SETTINGS)
            PermRow("Do Not Disturb access (for naps)", ui.hasDndAccess, DND_SETTINGS)
        }

        SlothCard {
            SectionTitle("History window")
            Dim("How many days back to learn from: ${ui.days}. Android keeps detailed events for roughly the last week or two.")
            Slider(value = ui.days.toFloat(), onValueChange = { }, onValueChangeFinished = { }, valueRange = 3f..14f, steps = 10, enabled = false)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (d in listOf(3, 7, 10, 14)) {
                    if (d == ui.days) Button(onClick = {}) { Text("$d days") } else OutlinedButton(onClick = { vm.setDays(d) }) { Text("$d days") }
                }
            }
        }

        SlothCard {
            SectionTitle("Nudges")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Dim("A notification 12 minutes before each risk hour, with one-tap nap.")
                Switch(checked = ui.nudgesEnabled, onCheckedChange = { vm.setNudges(it) })
            }
        }

        SlothCard {
            SectionTitle("Brain: Gemma 4 E2B, on this phone")
            Dim(
                when (val s = brainState) {
                    is SlothBrain.ModelState.Missing -> "Not on the phone yet."
                    is SlothBrain.ModelState.Downloading -> "Downloading, ${s.downloadedMb} of ${s.totalMb} MB."
                    is SlothBrain.ModelState.OnDisk -> "On the phone, not loaded."
                    is SlothBrain.ModelState.Loading -> "Loading."
                    is SlothBrain.ModelState.Ready -> "Loaded and running on ${s.backend}."
                    is SlothBrain.ModelState.Error -> s.message
                },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (brainState) {
                    is SlothBrain.ModelState.Missing, is SlothBrain.ModelState.Error -> Button(onClick = { vm.startDownload() }) { Text("Download (Wi-Fi)") }
                    is SlothBrain.ModelState.OnDisk -> Button(onClick = { vm.loadModel() }) { Text("Load now") }
                    else -> {}
                }
                if (brainState is SlothBrain.ModelState.OnDisk || brainState is SlothBrain.ModelState.Ready || brainState is SlothBrain.ModelState.Error) {
                    OutlinedButton(onClick = { vm.deleteModel() }) { Text("Delete model") }
                }
            }
            Dim("Developers: push the file instead of downloading.")
            Text(
                "adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/app.slothie/files/models/",
                fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SlothBrain.MODEL_PAGE))) }) { Text("Model card on Hugging Face") }
        }

        SlothCard {
            SectionTitle("About")
            Dim("Slothie reads Android's usage log, works out when you are most likely to reach for the phone, and lets an open-weight model running on the phone talk you into doing less. The numbers are computed in Kotlin; the model only ever gets to write words about them. Usage data never leaves the device.")
            Dim("Open source, MIT. Built for the Hacktoberfest 2026 Open-Source AI Challenge, week 3: Slow and Steady.")
            TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/ShalinaBhargava/slothie"))) }) { Text("Source on GitHub") }
        }
    }
}

@Composable
private fun PermRow(label: String, granted: Boolean, settingsAction: String) {
    val ctx = LocalContext.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Dim(if (granted) "Granted" else "Not granted")
        }
        if (!granted) TextButton(onClick = { ctx.startActivity(Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open settings") }
    }
}
