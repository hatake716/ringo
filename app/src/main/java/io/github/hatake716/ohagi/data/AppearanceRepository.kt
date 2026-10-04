package io.github.hatake716.ohagi.data

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 外観設定と壁紙由来の色。
 *
 * - ガラスの透明度: iOS 27 の「クリア〜着色」スライダー相当。0 = クリア、1 = 着色。
 * - 壁紙の主色: iOS のフォルダやDockが壁紙に合わせて色づく表現に使う。
 *   `getWallpaperColors` は権限不要で、壁紙の画素そのものは読まない。
 *
 * 設定ファイルの読み込み・壁紙サービスへの登録はどちらもディスクI/OとBinderを伴うので、
 * メインスレッドをブロックしないよう [start] でIOスレッドから行う。
 * 保存されたテーマを読み終わってからUIを構成し、誤った配色の一瞬の表示を防ぐ。
 */
class AppearanceRepository(context: Context) {

    private val appContext = context.applicationContext

    @Volatile
    private var prefs: SharedPreferences? = null

    private val _glassTint = MutableStateFlow(DEFAULT_GLASS_TINT)
    val glassTint: StateFlow<Float> = _glassTint.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Hold the first composition until the saved mode is available: no wrong-theme flash.
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private var themeTouched = false
    private var persistenceScope: CoroutineScope? = null

    /** 壁紙の主色(ARGB)。取得できない端末・API 26 では null。 */
    private val _wallpaperPrimaryArgb = MutableStateFlow<Int?>(null)
    val wallpaperPrimaryArgb: StateFlow<Int?> = _wallpaperPrimaryArgb.asStateFlow()
    private val _wallpaperSupportsDarkText = MutableStateFlow(false)
    val wallpaperSupportsDarkText: StateFlow<Boolean> = _wallpaperSupportsDarkText.asStateFlow()

    @Volatile
    private var tintTouched = false
    private var started = false

    /** スライダー操作中の即時反映。永続化は [persistGlassTint] で操作完了時に1回だけ行う。 */
    fun previewGlassTint(value: Float) {
        tintTouched = true
        _glassTint.value = value.coerceIn(0f, 1f)
    }

    fun persistGlassTint() {
        val value = _glassTint.value
        val target = prefs ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        target.edit { putFloat(KEY_GLASS_TINT, value) }
    }

    fun setThemeMode(mode: ThemeMode) {
        synchronized(this) {
            themeTouched = true
            _themeMode.value = mode
        }
        persistenceScope?.launch(Dispatchers.IO) {
            val target = prefs ?: appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            synchronized(this@AppearanceRepository) {
                // Read the latest selection so rapid taps cannot persist an older mode last.
                target.edit { putString(KEY_THEME_MODE, _themeMode.value.preferenceValue) }
            }
        }
    }

    fun start(scope: CoroutineScope) {
        if (started) return
        started = true
        persistenceScope = scope
        scope.launch(Dispatchers.IO) {
            val loaded = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = loaded
            // 読み込み中にスライダーを動かしていたら、その値を優先する。
            if (!tintTouched) {
                _glassTint.value = loaded.getFloat(KEY_GLASS_TINT, DEFAULT_GLASS_TINT).coerceIn(0f, 1f)
            }
            synchronized(this@AppearanceRepository) {
                if (!themeTouched) {
                    _themeMode.value = ThemeMode.fromPreference(loaded.getString(KEY_THEME_MODE, null))
                }
                _loaded.value = true
            }
            watchWallpaperColors()
        }
    }

    private fun watchWallpaperColors() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return
        val manager = runCatching { WallpaperManager.getInstance(appContext) }.getOrNull() ?: return
        val colors = runCatching { manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) }
            .onFailure { Log.w(TAG, "壁紙の色を取得できませんでした", it) }
            .getOrNull()
        _wallpaperPrimaryArgb.value = colors?.primaryColor?.toArgb()
        _wallpaperSupportsDarkText.value = supportsDarkText(colors)
        val listener = WallpaperManager.OnColorsChangedListener { changed: WallpaperColors?, which: Int ->
            if (which and WallpaperManager.FLAG_SYSTEM != 0) {
                _wallpaperPrimaryArgb.value = changed?.primaryColor?.toArgb()
                _wallpaperSupportsDarkText.value = supportsDarkText(changed)
            }
        }
        runCatching {
            manager.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        }.onFailure { Log.w(TAG, "壁紙の色の変化を監視できません", it) }
    }

    private fun supportsDarkText(colors: WallpaperColors?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1 || colors == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        } else {
            colors.primaryColor.luminance() > 0.6f
        }
    }

    companion object {
        const val DEFAULT_GLASS_TINT = 0.45f
        private const val PREFS_NAME = "appearance"
        private const val KEY_GLASS_TINT = "glass_tint"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val TAG = "AppearanceRepository"
    }
}
