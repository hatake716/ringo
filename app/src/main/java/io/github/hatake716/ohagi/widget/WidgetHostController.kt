package io.github.hatake716.ohagi.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.os.Process
import android.util.Log
import android.util.SizeF
import io.github.hatake716.ohagi.data.WidgetPlacement
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Android標準AppWidgetHostをランチャー全体で1つだけ管理する。 */
class WidgetHostController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    // ウィジェットページはページを往復するたびにカードを組み直すため、
    // getAppWidgetInfo(Binder)と loadLabel を ID ごとに一度だけ行う。
    // null(未バインド・プロバイダー不在)は保持せず、次回また問い合わせる。
    private val widgetInfoCache = ConcurrentHashMap<Int, AppWidgetProviderInfo>()
    private val widgetLabelCache = ConcurrentHashMap<Int, String>()
    private val host = ReleasableWidgetHost(appContext) { invalidateWidget(it) }
    // Pagerから一度外れた直後の再入場では、まだ生存しているRemoteViewsを再利用する。
    // AppWidgetHost自身も作成済みViewを強参照する。通常のページ往復では
    // ウィジェット内のスクロール位置も維持し、実メモリ圧迫時だけ参照を解放する。
    private val reusableViews = ConcurrentHashMap<Int, WeakReference<AppWidgetHostView>>()
    private val lastWidgetSizes = ConcurrentHashMap<Int, WidgetSize>()
    // createView / AndroidView.onReleaseはいずれもメインスレッドから呼ばれる。
    private val activeViews = Collections.newSetFromMap(
        IdentityHashMap<AppWidgetHostView, Boolean>(),
    )
    private var releaseWhenUnused = false

    // AppWidgetProviderInfo の寸法は取得時の密度で px 化され、ラベルは言語で変わる。
    private var cachedDensityDpi = 0
    private var cachedLocales: LocaleList? = null
    private var watchingPackages = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val packageName = intent.data?.schemeSpecificPart ?: return
            invalidatePackage(packageName)
        }
    }

    fun startListening() {
        watchPackageChanges()
        runCatching(host::startListening)
            .onFailure { Log.w(TAG, "ウィジェット更新の購読開始に失敗しました", it) }
    }

    fun stopListening() {
        runCatching(host::stopListening)
            .onFailure { Log.w(TAG, "ウィジェット更新の購読停止に失敗しました", it) }
    }

    fun allocateAppWidgetId(): Int = host.allocateAppWidgetId().also(::invalidateWidget)

    fun deleteAppWidgetId(appWidgetId: Int) {
        reusableViews.remove(appWidgetId)
        lastWidgetSizes.remove(appWidgetId)
        invalidateWidget(appWidgetId)
        runCatching { host.deleteAppWidgetId(appWidgetId) }
            .onFailure { Log.w(TAG, "ウィジェットIDの削除に失敗しました: $appWidgetId", it) }
    }

    /** バインド済みIDのプロバイダー情報。ID ごとにキャッシュする。 */
    fun appWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        dropCachesIfConfigurationChanged()
        widgetInfoCache[appWidgetId]?.let { return it }
        val info = runCatching { manager.getAppWidgetInfo(appWidgetId) }
            .onFailure { Log.w(TAG, "ウィジェット情報の取得に失敗しました: $appWidgetId", it) }
            .getOrNull()
            ?: return null
        widgetInfoCache[appWidgetId] = info
        return info
    }

    /** バインド済みIDのウィジェット名。ID ごとにキャッシュする。 */
    fun widgetLabel(appWidgetId: Int, info: AppWidgetProviderInfo): String {
        dropCachesIfConfigurationChanged()
        widgetLabelCache[appWidgetId]?.let { return it }
        return providerLabel(info).also { widgetLabelCache[appWidgetId] = it }
    }

    /**
     * ホーム画面に置けるインストール済みプロバイダー(順序は不定)。
     * 一覧表示には、ラベル取得と並べ替えをIOスレッドで行う [loadProviderEntries] を使う。
     */
    fun installedProviders(): List<AppWidgetProviderInfo> =
        manager.getInstalledProvidersForProfile(Process.myUserHandle())
            .filter(::isHomeScreenProvider)

    /**
     * ウィジェット選択シート用の一覧。列挙・ラベル取得・並べ替えをIOスレッドで行い、
     * ラベルはプロバイダーごと(アプリ名はパッケージごと)に一度だけ読む。
     */
    suspend fun loadProviderEntries(): List<WidgetProviderEntry> = withContext(Dispatchers.IO) {
        val providers = runCatching {
            manager.getInstalledProvidersForProfile(Process.myUserHandle())
        }.onFailure {
            Log.w(TAG, "ウィジェット一覧の取得に失敗しました", it)
        }.getOrDefault(emptyList())
        val appLabels = HashMap<String, String>()
        providers
            .filter(::isHomeScreenProvider)
            .map { info ->
                ensureActive()
                val appLabel = appLabels.getOrPut(info.provider.packageName) {
                    providerAppLabel(info)
                }
                val widgetLabel = providerLabel(info)
                SortableEntry(
                    entry = WidgetProviderEntry(info, appLabel, widgetLabel),
                    appKey = appLabel.lowercase(),
                    widgetKey = widgetLabel.lowercase(),
                )
            }
            .sortedWith(compareBy(SortableEntry::appKey, SortableEntry::widgetKey))
            .map(SortableEntry::entry)
    }

    private data class SortableEntry(
        val entry: WidgetProviderEntry,
        val appKey: String,
        val widgetKey: String,
    )

    private fun isHomeScreenProvider(info: AppWidgetProviderInfo): Boolean =
        info.widgetCategory == 0 ||
            info.widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0

    private fun providerLabel(info: AppWidgetProviderInfo): String =
        runCatching { info.loadLabel(appContext.packageManager) }
            .getOrNull()
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?: info.provider.shortClassName.substringAfterLast('.')

    private fun providerAppLabel(info: AppWidgetProviderInfo): String =
        runCatching {
            val applicationInfo = appContext.packageManager.getApplicationInfo(
                info.provider.packageName,
                0,
            )
            appContext.packageManager.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(info.provider.packageName)

    fun bindIfAllowed(
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
        options: Bundle,
    ): Boolean = runCatching {
        manager.bindAppWidgetIdIfAllowed(
            appWidgetId,
            info.profile,
            info.provider,
            options,
        )
    }.onFailure {
        Log.w(TAG, "ウィジェットの直接バインドに失敗しました: ${info.provider}", it)
    }.getOrDefault(false).also { invalidateWidget(appWidgetId) }

    private fun invalidateWidget(appWidgetId: Int) {
        widgetInfoCache.remove(appWidgetId)
        widgetLabelCache.remove(appWidgetId)
    }

    private fun invalidatePackage(packageName: String) {
        widgetInfoCache.forEach { (appWidgetId, info) ->
            if (info.provider.packageName == packageName) invalidateWidget(appWidgetId)
        }
    }

    private fun dropCachesIfConfigurationChanged() {
        val configuration = appContext.resources.configuration
        val densityDpi = configuration.densityDpi
        val locales = configuration.locales
        if (densityDpi == cachedDensityDpi && locales == cachedLocales) return
        widgetInfoCache.clear()
        widgetLabelCache.clear()
        cachedDensityDpi = densityDpi
        cachedLocales = locales
    }

    // onProviderChanged だけでは、購読停止中のアンインストールなどでキャッシュが
    // 古いまま残る場合があるため、パッケージ変更も直接監視する。
    private fun watchPackageChanges() {
        if (watchingPackages) return
        watchingPackages = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(packageReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                appContext.registerReceiver(packageReceiver, filter)
            }
        }.onFailure {
            watchingPackages = false
            Log.w(TAG, "パッケージ変更の監視開始に失敗しました", it)
        }
    }

    fun createView(
        context: Context,
        appWidgetId: Int,
        info: AppWidgetProviderInfo,
    ): AppWidgetHostView {
        reusableViews[appWidgetId]?.get()?.let { cached ->
            if (cached.context === context && cached.parent == null) {
                activeViews.add(cached)
                return cached
            }
        }
        return host.createView(context, appWidgetId, info).apply {
            setAppWidget(appWidgetId, info)
            setPadding(0, 0, 0, 0)
            reusableViews[appWidgetId] = WeakReference(this)
            activeViews.add(this)
        }
    }

    /**
     * 通常のページ往復ではViewを再利用する。メモリ圧迫通知を受けている場合も
     * 表示中のViewには触れず、最後のViewが外れるまで解放を待つ。
     */
    fun releaseView(view: AppWidgetHostView) {
        if (!activeViews.remove(view) || activeViews.isNotEmpty() || !releaseWhenUnused) return
        clearUnusedViews()
    }

    @Suppress("DEPRECATION")
    fun trimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
            level in ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW..ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL
        ) {
            clearUnusedViews()
        }
    }

    /** IDとバインドは保持し、再生成可能な非表示RemoteViewsだけを解放する。 */
    fun clearUnusedViews() {
        releaseWhenUnused = true
        if (activeViews.isNotEmpty()) return
        host.releaseViewReferences()
        reusableViews.clear()
        // 再生成したHostViewにも同じ寸法を通知できるようにする。
        lastWidgetSizes.clear()
        releaseWhenUnused = false
    }

    private class ReleasableWidgetHost(
        context: Context,
        private val onProviderInfoChanged: (appWidgetId: Int) -> Unit,
    ) : AppWidgetHost(context, HOST_ID) {
        fun releaseViewReferences() = clearViews()

        override fun onProviderChanged(appWidgetId: Int, appWidget: AppWidgetProviderInfo) {
            super.onProviderChanged(appWidgetId, appWidget)
            onProviderInfoChanged(appWidgetId)
        }
    }

    @Suppress("DEPRECATION")
    fun startConfiguration(
        activity: android.app.Activity,
        appWidgetId: Int,
        requestCode: Int,
        options: Bundle,
    ) {
        host.startAppWidgetConfigureActivityForResult(
            activity,
            appWidgetId,
            0,
            requestCode,
            options,
        )
    }

    fun bindingOptions(context: Context, heightDp: Int): Bundle {
        val widthDp = (context.resources.displayMetrics.widthPixels /
            context.resources.displayMetrics.density).roundToInt() - HORIZONTAL_MARGIN_DP
        return sizeOptions(widthDp.coerceAtLeast(1), heightDp)
    }

    fun updateSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        val size = WidgetSize(widthDp, heightDp)
        if (lastWidgetSizes[appWidgetId] == size) return
        val options = sizeOptions(widthDp, heightDp)
        runCatching {
            val hostView = reusableViews[appWidgetId]?.get()
            if (hostView == null) {
                manager.updateAppWidgetOptions(appWidgetId, options)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                hostView.updateAppWidgetSize(
                    options,
                    listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                )
            } else {
                @Suppress("DEPRECATION")
                hostView.updateAppWidgetSize(
                    options,
                    widthDp,
                    heightDp,
                    widthDp,
                    heightDp,
                )
            }
        }
            .onSuccess { lastWidgetSizes[appWidgetId] = size }
            .onFailure {
                Log.w(TAG, "ウィジェットサイズ更新に失敗しました: $appWidgetId", it)
            }
    }

    fun placementFor(appWidgetId: Int, info: AppWidgetProviderInfo): WidgetPlacement {
        val density = appContext.resources.displayMetrics.density
        val providerHeightDp = (info.minHeight / density).roundToInt()
        return WidgetPlacement(
            appWidgetId = appWidgetId,
            providerPackage = info.provider.packageName,
            providerClass = info.provider.className,
            widthDp = WidgetPlacement.MATCH_PARENT_WIDTH_DP,
            heightDp = providerHeightDp.coerceIn(MIN_WIDGET_HEIGHT_DP, MAX_WIDGET_HEIGHT_DP),
        )
    }

    /** プロバイダーが宣言したresizeModeとdp制約を、現在の表示可能幅へ変換する。 */
    fun resizeBounds(
        info: AppWidgetProviderInfo,
        availableWidthDp: Int,
    ): WidgetResizeBounds {
        val density = appContext.resources.displayMetrics.density
        fun pxToDp(px: Int): Int = (px / density).roundToInt().coerceAtLeast(1)

        val horizontalDeclared =
            info.resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0
        val verticalDeclared =
            info.resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        val defaultWidthDp = pxToDp(info.minWidth)
        val defaultHeightDp = pxToDp(info.minHeight)
        val declaredMinWidthDp = info.minResizeWidth
            .takeIf { it > 0 }
            ?.let(::pxToDp)
        val declaredMinHeightDp = info.minResizeHeight
            .takeIf { it > 0 }
            ?.let(::pxToDp)

        val minimumWidthDp = if (horizontalDeclared) {
            declaredMinWidthDp ?: defaultWidthDp
        } else {
            defaultWidthDp
        }.coerceAtLeast(WidgetPlacement.MIN_WIDGET_WIDTH_DP)
        val minimumHeightDp = if (verticalDeclared) {
            declaredMinHeightDp ?: defaultHeightDp
        } else {
            defaultHeightDp
        }.coerceAtLeast(WidgetPlacement.MIN_WIDGET_HEIGHT_DP)

        val declaredMaxWidthDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.maxResizeWidth.takeIf { it > 0 }?.let(::pxToDp)
        } else {
            null
        }
        val declaredMaxHeightDp = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            info.maxResizeHeight.takeIf { it > 0 }?.let(::pxToDp)
        } else {
            null
        }
        val availableWidth = availableWidthDp.coerceAtLeast(1)
        val maximumWidthDp = declaredMaxWidthDp
            ?.coerceAtMost(availableWidth)
            ?: availableWidth
        val maximumHeightDp = declaredMaxHeightDp
            ?.coerceAtMost(WidgetPlacement.MAX_WIDGET_HEIGHT_DP)
            ?: WidgetPlacement.MAX_WIDGET_HEIGHT_DP

        val minWidthDp = minimumWidthDp.coerceAtMost(maximumWidthDp)
        val minHeightDp = minimumHeightDp.coerceAtMost(maximumHeightDp)
        return WidgetResizeBounds(
            canResizeHorizontally = horizontalDeclared && minWidthDp < maximumWidthDp,
            canResizeVertically = verticalDeclared && minHeightDp < maximumHeightDp,
            minWidthDp = minWidthDp,
            maxWidthDp = maximumWidthDp,
            minHeightDp = minHeightDp,
            maxHeightDp = maximumHeightDp,
        )
    }

    private fun sizeOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
            )
        }
    }

    fun componentOf(placement: WidgetPlacement): ComponentName = ComponentName(
        placement.providerPackage,
        placement.providerClass,
    )

    private companion object {
        const val HOST_ID = 716
        const val HORIZONTAL_MARGIN_DP = 32
        const val MIN_WIDGET_HEIGHT_DP = 120
        const val MAX_WIDGET_HEIGHT_DP = 360
        const val TAG = "WidgetHost"
    }

    private data class WidgetSize(
        val widthDp: Int,
        val heightDp: Int,
    )
}

data class WidgetResizeBounds(
    val canResizeHorizontally: Boolean,
    val canResizeVertically: Boolean,
    val minWidthDp: Int,
    val maxWidthDp: Int,
    val minHeightDp: Int,
    val maxHeightDp: Int,
)

/** ウィジェット選択シートの1行分。ラベルは読み込み時に確定させておく。 */
data class WidgetProviderEntry(
    val info: AppWidgetProviderInfo,
    val appLabel: String,
    val widgetLabel: String,
)
