@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.jfloresriera.reto10.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.jfloresriera.reto10.AppViewModel
import io.github.jfloresriera.reto10.data.Agg
import io.github.jfloresriera.reto10.data.MapConfig
import io.github.jfloresriera.reto10.data.MunicipalityLayer
import io.github.jfloresriera.reto10.data.PRESETS
import io.github.jfloresriera.reto10.domain.Choropleth
import io.github.jfloresriera.reto10.ui.components.AppCard
import io.github.jfloresriera.reto10.ui.components.DropdownField
import io.github.jfloresriera.reto10.ui.components.InfoNote
import io.github.jfloresriera.reto10.ui.components.PrimaryButton
import io.github.jfloresriera.reto10.ui.theme.AppTheme
import io.github.jfloresriera.reto10.ui.theme.BrandDark
import io.github.jfloresriera.reto10.ui.theme.ErrorRed
import io.github.jfloresriera.reto10.ui.theme.TextSoft
import io.github.jfloresriera.reto10.ui.theme.brandOf
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import java.net.URI

private const val SRC = "mun-src"
private const val FILL = "mun-fill"
private const val LINE = "mun-line"
private const val SEL = "mun-sel"

/** Estilo mínimo: solo un fondo liso. El mapa funciona sin servidor de teselas ni conexión. */
private const val STYLE_JSON =
    """{"version":8,"name":"plano","sources":{},"layers":[{"id":"fondo","type":"background","paint":{"background-color":"#EAF1F6"}}]}"""

@Composable
fun MapScreen(vm: AppViewModel, onOpenResults: () -> Unit) {
    val context = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember { MapView(context) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var showConfig by remember { mutableStateOf(false) }

    // El MapView necesita recibir el ciclo de vida de la actividad.
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (lifecycleOwner.lifecycle.currentState != Lifecycle.State.DESTROYED) {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    // Configuración inicial del mapa y de las capas de municipios.
    LaunchedEffect(mapView) {
        mapView.getMapAsync { m ->
            map = m
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.setMinZoomPreference(3.0)
            m.cameraPosition = CameraPosition.Builder().target(LatLng(4.6, -74.1)).zoom(4.8).build()

            m.setStyle(Style.Builder().fromJson(STYLE_JSON)) { style ->
                if (MunicipalityLayer.exists(context)) {
                    style.addSource(GeoJsonSource(SRC, URI("asset://${MunicipalityLayer.ASSET}")))
                    style.addLayer(
                        FillLayer(FILL, SRC).withProperties(
                            PropertyFactory.fillColor("#E0E0E0"),
                            PropertyFactory.fillOpacity(0.95f),
                        ),
                    )
                    style.addLayer(
                        LineLayer(LINE, SRC).withProperties(
                            PropertyFactory.lineColor("#FFFFFF"),
                            PropertyFactory.lineWidth(0.4f),
                        ),
                    )
                    style.addLayer(
                        LineLayer(SEL, SRC).withProperties(
                            PropertyFactory.lineColor("#111111"),
                            PropertyFactory.lineWidth(2.5f),
                        ).withFilter(Expression.eq(Expression.get("cod"), Expression.literal(""))),
                    )
                }
                styleReady = true
            }

            m.addOnMapClickListener { latLng ->
                val point = m.projection.toScreenLocation(latLng)
                val feature = m.queryRenderedFeatures(point, FILL).firstOrNull()
                val code = feature?.getStringProperty("cod")
                if (feature != null && code != null) {
                    vm.selectMunicipality(
                        code,
                        feature.getStringProperty("nombre") ?: code,
                        feature.getStringProperty("depto") ?: "",
                    )
                    true
                } else {
                    vm.clearSelection()
                    false
                }
            }
        }
    }

    // Coloración coroplética según los valores agregados por municipio.
    val palette = remember(settings.theme) { brandOf(AppTheme.from(settings.theme)).ramp.map { it.toArgb() } }
    val choropleth = remember(ui.mapValues, palette) { Choropleth.build(ui.mapValues, palette) }

    LaunchedEffect(styleReady, choropleth) {
        if (!styleReady) return@LaunchedEffect
        val fill = map?.style?.getLayer(FILL) as? FillLayer ?: return@LaunchedEffect
        val expression = choropleth.expression
        fill.setProperties(
            PropertyFactory.fillColor(
                if (expression != null) Expression.raw(expression) else Expression.color(Choropleth.NO_DATA),
            ),
        )
    }

    // Contorno del municipio seleccionado.
    LaunchedEffect(styleReady, ui.selected) {
        if (!styleReady) return@LaunchedEffect
        val line = map?.style?.getLayer(SEL) as? LineLayer ?: return@LaunchedEffect
        line.setFilter(Expression.eq(Expression.get("cod"), Expression.literal(ui.selected?.code ?: "")))
    }

    val datasetTitle = PRESETS.firstOrNull { it.id == settings.datasetId }?.title ?: settings.datasetId
    val valueLabel = ui.columns.firstOrNull { it.fieldName == ui.mapConfig.valueCol }?.label

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        // Encabezado
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(datasetTitle, style = MaterialTheme.typography.titleSmall)
                        val what = when {
                            ui.mapConfig.agg == Agg.COUNT -> "Conteo de registros"
                            valueLabel != null -> "${ui.mapConfig.agg.label} de $valueLabel"
                            else -> "Elige la variable a colorear"
                        }
                        Text(what, style = MaterialTheme.typography.bodySmall, color = TextSoft, maxLines = 2)
                    }
                    TextButton(onClick = { showConfig = true }, enabled = ui.columns.isNotEmpty()) { Text("Ajustar") }
                }
                if (ui.schemaLoading || ui.mapLoading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp), color = BrandDark)
                }
                ui.schemaError?.let {
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { vm.loadSchema() }) { Text("Reintentar") }
                }
                ui.mapError?.let {
                    Text(it, color = ErrorRed, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                    TextButton(onClick = { vm.loadMap() }) { Text("Reintentar") }
                }
                if (ui.mapValues.isNotEmpty()) {
                    val extra = if (ui.mapMissing > 0) " · ${ui.mapMissing} sin polígono en la capa" else ""
                    Text(
                        "${ui.mapValues.size} municipios con datos$extra",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSoft,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (!ui.layerAvailable) {
                InfoNote(
                    "Falta la capa de municipios (assets/municipios.geojson). Genérala con tools/shp_a_geojson.py " +
                        "y vuelve a compilar; mientras tanto el mapa se ve sin polígonos.",
                )
            }
        }

        // Leyenda o municipio seleccionado
        val selected = ui.selected
        if (selected != null) {
            AppCard(Modifier.align(Alignment.BottomCenter).padding(12.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(selected.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${selected.depto.ifBlank { "Colombia" }} · cód. ${selected.code}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSoft,
                        )
                        val v = ui.mapValues[selected.code]
                        Text(
                            if (v != null) "${ui.mapConfig.agg.label}: ${Choropleth.format(v)}"
                            else "Sin datos para este municipio con los filtros actuales.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    IconButton(onClick = { vm.clearSelection() }) { Icon(Icons.Filled.Close, "Cerrar") }
                }
                Spacer(Modifier.size(10.dp))
                PrimaryButton("Ver registros de este municipio", onClick = {
                    vm.viewRecords()
                    onOpenResults()
                })
            }
        } else if (choropleth.legend.isNotEmpty()) {
            AppCard(Modifier.align(Alignment.BottomStart).padding(12.dp).width(180.dp)) {
                choropleth.legend.forEach { item ->
                    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(item.color)))
                        Spacer(Modifier.width(8.dp))
                        Text(item.label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(Color(Choropleth.NO_DATA)))
                    Spacer(Modifier.width(8.dp))
                    Text("Sin dato", style = MaterialTheme.typography.bodySmall, color = TextSoft)
                }
            }
        }
    }

    if (showConfig) {
        MapConfigDialog(
            columns = ui.columns,
            current = ui.mapConfig,
            onDismiss = { showConfig = false },
            onApply = {
                showConfig = false
                vm.setMapConfig(it)
            },
        )
    }
}

@Composable
private fun MapConfigDialog(
    columns: List<io.github.jfloresriera.reto10.data.DataColumn>,
    current: MapConfig,
    onDismiss: () -> Unit,
    onApply: (MapConfig) -> Unit,
) {
    var code by remember { mutableStateOf(current.codeCol) }
    var value by remember { mutableStateOf(current.valueCol) }
    var agg by remember { mutableStateOf(current.agg) }
    val numeric = columns.filter { it.isNumeric }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar el mapa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownField(
                    label = "Columna con el código DANE del municipio",
                    valueText = columns.firstOrNull { it.fieldName == code }?.label ?: "Elegir…",
                    options = columns,
                    optionText = { it.label },
                    onSelect = { code = it.fieldName },
                )
                DropdownField(
                    label = "Variable a colorear",
                    valueText = numeric.firstOrNull { it.fieldName == value }?.label ?: "Elegir…",
                    options = numeric,
                    optionText = { it.label },
                    onSelect = { value = it.fieldName },
                    enabled = agg != Agg.COUNT,
                )
                DropdownField(
                    label = "Operación por municipio",
                    valueText = agg.label,
                    options = Agg.entries.toList(),
                    optionText = { it.label },
                    onSelect = { agg = it },
                )
            }
        },
        confirmButton = { TextButton(onClick = { onApply(MapConfig(code, value, agg)) }) { Text("Aplicar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
