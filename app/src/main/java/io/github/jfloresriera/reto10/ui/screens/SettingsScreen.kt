@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.jfloresriera.reto10.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jfloresriera.reto10.AppViewModel
import io.github.jfloresriera.reto10.BuildConfig
import io.github.jfloresriera.reto10.data.ApiMode
import io.github.jfloresriera.reto10.ui.components.AppCard
import io.github.jfloresriera.reto10.ui.components.InfoNote
import io.github.jfloresriera.reto10.ui.components.ScreenBar
import io.github.jfloresriera.reto10.ui.components.SectionTitle
import io.github.jfloresriera.reto10.ui.theme.AppTheme
import io.github.jfloresriera.reto10.ui.theme.TextSoft

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(settings.token) }
    var visible by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Ajustes")
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                SectionTitle("Conexión con datos.gov.co")
                OutlinedTextField(
                    value = token,
                    onValueChange = {
                        token = it.trim()
                        vm.updateSettings { s -> s.copy(token = token) }
                    },
                    label = { Text("App token (Socrata)") },
                    singleLine = true,
                    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { visible = !visible }) { Text(if (visible) "Ocultar token" else "Mostrar token") }
                if (token.isBlank() && BuildConfig.DATOS_APP_TOKEN.isNotBlank()) {
                    Text(
                        "Se usará el token incluido al compilar la app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }

                Text(
                    "Versión de la API",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ApiMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.apiMode == mode,
                            onClick = { vm.updateSettings { it.copy(apiMode = mode) } },
                            label = { Text(mode.label) },
                        )
                    }
                }
                TextButton(onClick = { vm.loadSchema() }) { Text("Probar conexión") }
                if (ui.schemaLoading) Text("Conectando…", style = MaterialTheme.typography.bodySmall, color = TextSoft)
                ui.schemaError?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (!ui.schemaLoading && ui.schemaError == null && ui.columns.isNotEmpty()) {
                    Text(
                        "Conexión correcta: ${ui.columns.size} columnas leídas del dataset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                    )
                }
            }

            InfoNote(
                "La API v3 exige un app token. Créalo gratis en datos.gov.co: inicia sesión, abre tu perfil, " +
                    "«Configuración de desarrollador» y crea una «App Token». El modo Automático usa v3 si hay token " +
                    "y v2 si no lo hay (v2 funciona sin token, con menos peticiones permitidas).",
            )

            AppCard {
                SectionTitle("Apariencia")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppTheme.entries.forEach { theme ->
                        FilterChip(
                            selected = settings.theme == theme.id,
                            onClick = { vm.updateSettings { it.copy(theme = theme.id) } },
                            label = { Text(theme.label) },
                        )
                    }
                }
            }

            AppCard {
                SectionTitle("Acerca de")
                Text("EVA Explorer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Reto 10 · Desarrollo de aplicaciones para dispositivos móviles. Consume los servicios web " +
                        "del catálogo de datos abiertos de Colombia (datos.gov.co) y procesa el JSON con " +
                        "org.json del SDK de Android. Los límites municipales provienen de la capa oficial " +
                        "(septiembre 2026) incluida en el repositorio.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
