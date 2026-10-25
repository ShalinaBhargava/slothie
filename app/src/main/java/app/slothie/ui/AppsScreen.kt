package app.slothie.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothie.SlothieViewModel
import app.slothie.UiState
import app.slothie.ai.SlothBrain
import app.slothie.apps.AppInfo
import app.slothie.apps.Category

@Composable
fun AppsScreen(ui: UiState, vm: SlothieViewModel, modifier: Modifier = Modifier) {
    val brainState by vm.brain.state.collectAsStateWithLifecycle()
    LazyColumn(modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Apps", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        item {
            SlothCard {
                SectionTitle("Let Slothie sort them")
                Dim("Gemma reads the app names on the phone and files each one as Feed, Video, Game, Chat, Tool, Work or Health. Flip any it gets wrong; your choice always wins.")
                val ready = brainState is SlothBrain.ModelState.Ready || brainState is SlothBrain.ModelState.OnDisk
                Button(onClick = { vm.sortApps() }, enabled = ready && !ui.sorting) {
                    Text(if (ui.sorting) "Sorting..." else "Sort with Gemma")
                }
                if (ui.sorting) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (!ready) Dim("Download or load the model on the Today tab first.")
                if (ui.sortMessage != null) Dim(ui.sortMessage)
            }
        }
        if (ui.apps.isEmpty()) item { Dim("No usage recorded yet.") }
        items(ui.apps, key = { it.first.pkg }) { (info, minsPerDay) ->
            AppRow(info, minsPerDay, vm)
        }
    }
}

@Composable
private fun AppRow(info: AppInfo, minsPerDay: Double, vm: SlothieViewModel) {
    val icon = remember(info.pkg) { vm.catalog.icon(info.pkg)?.toBitmap(96, 96)?.asImageBitmap() }
    var menu by remember { mutableStateOf(false) }
    SlothCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Box(Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(info.label, fontWeight = FontWeight.SemiBold)
                Dim("${minsPerDay.toInt()} min/day" + if (info.modelSet && !info.userSet) " · sorted by Gemma" else "")
            }
            Column(horizontalAlignment = Alignment.End) {
                Box {
                    AssistChip(onClick = { menu = true }, label = { Text(info.category.label) })
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        for (c in Category.entries) DropdownMenuItem(text = { Text(c.label) }, onClick = { menu = false; vm.setCategory(info.pkg, c) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("distraction", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = info.distraction, onCheckedChange = { vm.setDistraction(info.pkg, it) })
                }
            }
        }
    }
}
