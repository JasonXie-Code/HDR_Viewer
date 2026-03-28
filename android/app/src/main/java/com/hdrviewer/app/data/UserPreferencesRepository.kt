package com.hdrviewer.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "hdr_user_prefs")

data class UserPrefsSnapshot(
    val defaultBrightnessPct: Float = 100f,
    val saturationBoostEnabled: Boolean = false,
    val keepScreenOn: Boolean = true,
    val sortOrder: GallerySortOrder = GallerySortOrder.NEWEST_FIRST,
    val gridMinSizeDp: Int = 108,
)

class UserPreferencesRepository(context: Context) {
    private val dataStore = context.applicationContext.userPrefsDataStore

    val prefsFlow: Flow<UserPrefsSnapshot> = dataStore.data.map { p ->
        UserPrefsSnapshot(
            defaultBrightnessPct = p[floatKey(KEY_DEFAULT_BRIGHTNESS)] ?: 100f,
            saturationBoostEnabled = p[booleanKey(KEY_SATURATION_BOOST)] ?: false,
            keepScreenOn = p[booleanKey(KEY_KEEP_SCREEN_ON)] ?: true,
            sortOrder = sortOrderFromString(p[stringKey(KEY_SORT_ORDER)]),
            gridMinSizeDp = (p[intKey(KEY_GRID_MIN_DP)] ?: 108).coerceIn(72, 200),
        )
    }

    suspend fun setDefaultBrightnessPct(value: Float) {
        dataStore.edit { it[floatKey(KEY_DEFAULT_BRIGHTNESS)] = value.coerceIn(0f, 200f) }
    }

    suspend fun setSaturationBoostEnabled(value: Boolean) {
        dataStore.edit { it[booleanKey(KEY_SATURATION_BOOST)] = value }
    }

    suspend fun setKeepScreenOn(value: Boolean) {
        dataStore.edit { it[booleanKey(KEY_KEEP_SCREEN_ON)] = value }
    }

    suspend fun setSortOrder(order: GallerySortOrder) {
        dataStore.edit { it[stringKey(KEY_SORT_ORDER)] = sortOrderToString(order) }
    }

    suspend fun setGridMinSizeDp(dp: Int) {
        dataStore.edit { it[intKey(KEY_GRID_MIN_DP)] = dp.coerceIn(72, 200) }
    }

    private companion object {
        const val KEY_DEFAULT_BRIGHTNESS = "default_brightness_pct"
        const val KEY_SATURATION_BOOST = "saturation_boost"
        const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        const val KEY_SORT_ORDER = "sort_order"
        const val KEY_GRID_MIN_DP = "grid_min_dp"

        fun floatKey(name: String) = floatPreferencesKey(name)
        fun booleanKey(name: String) = booleanPreferencesKey(name)
        fun stringKey(name: String) = stringPreferencesKey(name)
        fun intKey(name: String) = intPreferencesKey(name)

        fun sortOrderToString(o: GallerySortOrder): String = when (o) {
            GallerySortOrder.NEWEST_FIRST -> "newest"
            GallerySortOrder.OLDEST_FIRST -> "oldest"
            GallerySortOrder.NAME_ASC -> "name"
        }

        fun sortOrderFromString(s: String?): GallerySortOrder = when (s) {
            "oldest" -> GallerySortOrder.OLDEST_FIRST
            "name" -> GallerySortOrder.NAME_ASC
            else -> GallerySortOrder.NEWEST_FIRST
        }
    }
}
