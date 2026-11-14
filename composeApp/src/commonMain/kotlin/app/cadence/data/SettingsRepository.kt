package app.cadence.data

import app.cadence.platform.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

class SettingsRepository(private val scope: CoroutineScope) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    init {
        restore()
    }

    private fun restore() {
        Platform.readState(KEY)?.let { raw ->
            runCatching { json.decodeFromString(AppSettings.serializer(), raw) }
                .onSuccess { _settings.value = it }
        }
    }

    fun updateSponsorBlock(transform: (SponsorBlockPreferences) -> SponsorBlockPreferences) {
        val next = _settings.value.copy(sponsorBlock = transform(_settings.value.sponsorBlock))
        _settings.value = next
        persist(next)
    }

    private fun persist(value: AppSettings) {
        scope.launch(Dispatchers.IO) {
            runCatching { Platform.writeState(KEY, json.encodeToString(AppSettings.serializer(), value)) }
        }
    }

    private companion object {
        const val KEY = "settings.v1"
    }
}
