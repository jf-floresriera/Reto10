@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.jfloresriera.reto10.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jfloresriera.reto10.AppViewModel
import io.github.jfloresriera.reto10.data.DataColumn
import io.github.jfloresriera.reto10.ui.components.AppCard
import io.github.jfloresriera.reto10.ui.components.InfoNote
import io.github.jfloresriera.reto10.ui.components.ScreenBar
import io.github.jfloresriera.reto10.ui.theme.BrandDark
import io.github.jfloresriera.reto10.ui.theme.ErrorRed
import io.github.jfloresriera.reto10.ui.theme.TextSoft

@Composable
fun ResultsScreen(vm: AppViewModel, onBackToQuery: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        ScreenBar("Resultados")
        when {
            !ui.queried && !ui.rowsLoading -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    InfoNote("Aún no hay resultados. Arma tu consulta en la pestaña «Consultar» o toca un municipio en el mapa.")
                    TextButton(onClick = onBackToQuery) { Text("Ir a Consultar") }
                }
            }

            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val count = ui.rows.size
                        Text(
                            if (ui.rowsLoading && count == 0) "Consultando…"
                            else "$count ${if (count == 1) "registro cargado" else "registros cargados"}" +
                                if (ui.hasMore) " (hay más)" else "",
                            style = MaterialTheme.typography.titleSmall,
                            color = BrandDark,
                        )
                        ui.municipality?.let { m ->
                            FilterChip(
                                selected = true,
                                onClick = { vm.clearMunicipality(); vm.runQuery() },
                                label = { Text("Municipio: ${m.name} (${m.code}) ✕") },
                            )
                        }
                        if (ui.filters.isNotEmpty()) {
                            Text(
                                ui.filters.joinToString("  ·  ") { "${it.column.label} ${it.op.label} ${it.value}" },
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSoft,
                            )
                        }
                    }
                }

                ui.rowsError?.let { error ->
                    item {
                        Column {
                            Text(error, color = ErrorRed, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { vm.runQuery() }) { Text("Reintentar") }
                        }
                    }
                }

                if (ui.queried && !ui.rowsLoading && ui.rows.isEmpty() && ui.rowsError == null) {
                    item { InfoNote("La consulta no devolvió registros. Prueba con otros filtros.") }
                }

                itemsIndexed(ui.rows) { index, row -> RowCard(index, row, ui.columns) }

                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when {
                            ui.rowsLoading -> CircularProgressIndicator(color = BrandDark)
                            ui.hasMore -> TextButton(onClick = { vm.loadMore() }) { Text("Cargar más") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowCard(index: Int, row: Map<String, String>, columns: List<DataColumn>) {
    var open by remember { mutableStateOf(false) }
    val labels = remember(columns) { columns.associate { it.fieldName to it.label } }
    val entries = row.entries.toList()
    val preview = if (open) entries else entries.take(4)

    AppCard(Modifier.clickable { open = !open }) {
        Text("#${index + 1}", style = MaterialTheme.typography.bodySmall, color = TextSoft)
        preview.forEach { (key, value) ->
            Row(Modifier.padding(vertical = 3.dp)) {
                Text(
                    labels[key] ?: key,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSoft,
                    modifier = Modifier.weight(0.42f).padding(end = 8.dp),
                )
                Text(
                    value.ifBlank { "—" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(0.58f),
                )
            }
        }
        if (entries.size > 4) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Text(
                if (open) "Ocultar columnas" else "Ver las ${entries.size} columnas",
                style = MaterialTheme.typography.labelLarge,
                color = BrandDark,
            )
        }
    }
}
