package io.github.jfloresriera.reto10

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jfloresriera.reto10.ui.screens.MapScreen
import io.github.jfloresriera.reto10.ui.screens.QueryScreen
import io.github.jfloresriera.reto10.ui.screens.ResultsScreen
import io.github.jfloresriera.reto10.ui.screens.SettingsScreen
import io.github.jfloresriera.reto10.ui.theme.AppBg
import io.github.jfloresriera.reto10.ui.theme.AppTheme
import io.github.jfloresriera.reto10.ui.theme.BrandDark
import io.github.jfloresriera.reto10.ui.theme.BrandSoft
import io.github.jfloresriera.reto10.ui.theme.EvaTheme
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Debe ejecutarse antes de crear cualquier MapView.
        MapLibre.getInstance(this)

        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            EvaTheme(AppTheme.from(settings.theme)) {
                val bar = BrandDark.toArgb()
                SideEffect {
                    window.statusBarColor = bar
                }
                EvaApp(vm)
            }
        }
    }
}

private enum class Screen(val label: String, val icon: ImageVector) {
    MAP("Mapa", Icons.Outlined.Map),
    QUERY("Consultar", Icons.Outlined.Search),
    RESULTS("Resultados", Icons.Outlined.TableChart),
    SETTINGS("Ajustes", Icons.Outlined.Settings),
}

@Composable
private fun EvaApp(vm: AppViewModel) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    val screen = Screen.entries[index]
    val go = { target: Screen -> index = target.ordinal }

    Scaffold(
        containerColor = AppBg,
        bottomBar = {
            NavigationBar(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface) {
                Screen.entries.forEach { s ->
                    NavigationBarItem(
                        selected = s == screen,
                        onClick = { go(s) },
                        icon = { Icon(s.icon, contentDescription = s.label) },
                        label = { Text(s.label) },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = BrandSoft,
                            selectedIconColor = BrandDark,
                            selectedTextColor = BrandDark,
                        ),
                    )
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            // El mapa se mantiene creado siempre: así no se vuelve a cargar la capa al cambiar de pestaña.
            MapScreen(vm, onOpenResults = { go(Screen.RESULTS) })

            if (screen != Screen.MAP) {
                Surface(
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {},
                    color = AppBg,
                ) {
                    when (screen) {
                        Screen.QUERY -> QueryScreen(vm, onRun = { go(Screen.RESULTS) })
                        Screen.RESULTS -> ResultsScreen(vm, onBackToQuery = { go(Screen.QUERY) })
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.MAP -> Unit
                    }
                }
            }
        }
    }
}
