package io.github.jfloresriera.reto10.data

import android.content.Context

/** Preferencias simples. El app token de Socrata identifica la aplicación; no es una contraseña. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("eva_prefs", Context.MODE_PRIVATE)

    fun load(): Settings {
        val default = Settings()
        return Settings(
            token = sp.getString("token", "") ?: "",
            apiMode = runCatching { ApiMode.valueOf(sp.getString("api", "AUTO") ?: "AUTO") }.getOrDefault(ApiMode.AUTO),
            theme = sp.getString("theme", default.theme) ?: default.theme,
            datasetId = sp.getString("dataset", default.datasetId) ?: default.datasetId,
        )
    }

    fun save(s: Settings) {
        sp.edit()
            .putString("token", s.token)
            .putString("api", s.apiMode.name)
            .putString("theme", s.theme)
            .putString("dataset", s.datasetId)
            .apply()
    }
}
