package app.slothie

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.slothie.ui.AppsScreen
import app.slothie.ui.HoursScreen
import app.slothie.ui.SettingsScreen
import app.slothie.ui.SlothieTheme
import app.slothie.ui.TodayScreen

class MainActivity : ComponentActivity() {
    private val vm: SlothieViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SlothieTheme {
                val ui by vm.ui.collectAsStateWithLifecycle()
                var tab by rememberSaveable { mutableIntStateOf(0) }
                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Rounded.Bedtime, null) }, label = { Text("Today") })
                            NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Rounded.Schedule, null) }, label = { Text("Hours") })
                            NavigationBarItem(tab == 2, { tab = 2 }, { Icon(Icons.Rounded.Apps, null) }, label = { Text("Apps") })
                            NavigationBarItem(tab == 3, { tab = 3 }, { Icon(Icons.Rounded.Settings, null) }, label = { Text("Settings") })
                        }
                    },
                ) { pad ->
                    val m = Modifier.padding(pad)
                    when (tab) {
                        0 -> TodayScreen(ui, vm, m, openSettings = { tab = 3 })
                        1 -> HoursScreen(ui, vm, m)
                        2 -> AppsScreen(ui, vm, m)
                        else -> SettingsScreen(ui, vm, m)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
