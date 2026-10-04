package io.github.hatake716.ohagi.ui.common

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.DialogWindowProvider
import java.util.function.Consumer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.hatake716.ohagi.data.AppearanceRepository
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

/*
 * iOS 26/27 の Liquid Glass を Compose で近似する共通素材。
 *
 * 壁紙は別ウィンドウ(WallpaperService)が描くため、通常権限のランチャーから
 * 背面の壁紙そのものを屈折・ぼかしすることはできない。そこで次の4層を
 * 描画フェーズだけで重ね、ガラスらしい奥行きを出す。
 *
 * 1. 塗り: 透明度スライダー(クリア〜着色)と壁紙の主色で決まる半透明色
 * 2. 拡散: 上端から柔らかく消える白い光(ガラス内部の拡散)
 * 3. 暗い縁: iOS 27 で加わった、背景と分離するための外周の陰り
 * 4. スペキュラ: 左上と右下で明るくなる内側の光の縁(iOS 27 で強化)
 *
 * どの層もレイアウトや再compositionを発生させず、形状と色は drawWithCache で
 * サイズ・設定値が変わった時だけ作り直す。
 */

/** ガラスの色調。 */
enum class GlassTone {
    /** Dock、メニュー、Appライブラリのカードなど、壁紙上の標準的なガラス。 */
    Regular,

    /** フォルダアイコンなど、白く曇った明るいガラス。 */
    Light,
}

/**
 * 外観設定の読み取り口。値は描画フェーズで読むこと(スライダー操作中に
 * 画面全体を再composeしないため)。
 */
@Stable
class GlassAppearance(
    private val tintState: State<Float>,
    private val wallpaperState: State<Color>,
) {
    /** 0 = クリア、1 = 着色。 */
    val tint: Float get() = tintState.value

    /** 壁紙の主色。取得できない場合は中立色。 */
    val wallpaperTint: Color get() = wallpaperState.value
}

val NeutralWallpaperTint = Color(0xFF3A3A44)

val LocalGlassAppearance = staticCompositionLocalOf {
    GlassAppearance(
        tintState = mutableStateOf(AppearanceRepository.DEFAULT_GLASS_TINT),
        wallpaperState = mutableStateOf(NeutralWallpaperTint),
    )
}

/** Activityのルートで1回だけ作り、[LocalGlassAppearance] で配る。 */
@Composable
fun rememberGlassAppearance(repository: AppearanceRepository): GlassAppearance {
    val tint = repository.glassTint.collectAsState()
    val wallpaperArgb = repository.wallpaperPrimaryArgb.collectAsState()
    val wallpaper = remember(wallpaperArgb) {
        derivedStateOf { wallpaperArgb.value?.let { Color(it) } ?: NeutralWallpaperTint }
    }
    return remember(tint, wallpaper) { GlassAppearance(tint, wallpaper) }
}

/**
 * Liquid Glass の面を描く。
 *
 * - 形状の外へはみ出す描画はしない(暗い縁は外周線の上に半分だけ乗る)。
 * - 中身をクリップしたい場合は呼び出し側で `clip(shape)` を併用する。
 * - [accent] を渡すと塗りをその色へ寄せる(削除領域の赤など)。
 * - [highlight] はスペキュラの強さの倍率。小さな部品では弱めるとよい。
 * - [minOpacity] は「着色」側の不透明度の下限。スライダーに比例して適用し、
 *   シートやメニューでも「クリア」側の透過を妨げない。
 */
@Composable
fun Modifier.liquidGlass(
    shape: Shape,
    tone: GlassTone = GlassTone.Regular,
    accent: Color? = null,
    highlight: Float = 1f,
    minOpacity: Float = 0f,
): Modifier {
    val appearance = LocalGlassAppearance.current
    val palette = LocalOhagiColors.current
    return this.drawWithCache {
        val tint = appearance.tint
        val wallpaper = appearance.wallpaperTint
        val colors = glassColors(tone, tint, wallpaper, accent, highlight, minOpacity, palette.isDark)

        val outline = shape.createOutline(size, layoutDirection, this)
        val edgePx = GLASS_EDGE_WIDTH.toPx()
        val rimPx = GLASS_RIM_WIDTH.toPx()
        val rimInset = rimPx
        val innerSize = Size(
            width = (size.width - rimInset * 2f).coerceAtLeast(0f),
            height = (size.height - rimInset * 2f).coerceAtLeast(0f),
        )
        val innerOutline = shape.createOutline(innerSize, layoutDirection, this)

        val diffusion = Brush.verticalGradient(
            0f to Color.White.copy(alpha = colors.diffusionAlpha),
            0.55f to Color.White.copy(alpha = 0f),
            startY = 0f,
            endY = size.height,
        )
        val specular = Brush.linearGradient(
            0f to Color.White.copy(alpha = colors.specularStrong),
            0.28f to Color.White.copy(alpha = colors.specularSoft),
            0.72f to Color.White.copy(alpha = colors.specularSoft * 0.6f),
            1f to Color.White.copy(alpha = colors.specularReflect),
            start = Offset.Zero,
            end = Offset(size.width, size.height),
        )
        val edgeStroke = Stroke(width = edgePx)
        val rimStroke = Stroke(width = rimPx)

        onDrawBehind {
            drawOutline(outline, colors.fill)
            drawOutline(outline, diffusion)
            drawOutline(outline, colors.edge, style = edgeStroke)
            translate(rimInset, rimInset) {
                drawOutline(innerOutline, specular, style = rimStroke)
            }
        }
    }
}

@Immutable
internal data class GlassColors(
    val fill: Color,
    val edge: Color,
    val diffusionAlpha: Float,
    val specularStrong: Float,
    val specularSoft: Float,
    val specularReflect: Float,
)

internal fun glassColors(
    tone: GlassTone,
    tint: Float,
    wallpaper: Color,
    accent: Color?,
    highlight: Float,
    minOpacity: Float,
    dark: Boolean,
): GlassColors {
    val t = tint.coerceIn(0f, 1f)
    val h = highlight.coerceIn(0f, 1.5f)
    val floor = minOpacity.coerceIn(0f, 1f) * t
    return when (tone) {
        GlassTone.Regular -> {
            val base = lerp(if (dark) GLASS_REGULAR_BASE else Color(0xFFF9FAFC), wallpaper, 0.06f)
            val colored = if (accent != null) lerp(base, accent, 0.62f) else base
            GlassColors(
                fill = colored.copy(alpha = maxOf(floor, lerpFloat(if (dark) 0.10f else 0.08f, 0.96f, t))),
                edge = Color.Black.copy(alpha = if (dark) 0.24f else 0.09f),
                diffusionAlpha = lerpFloat(0.02f, if (dark) 0.03f else 0.04f, t),
                specularStrong = (if (dark) 0.40f else 0.70f) * h,
                specularSoft = 0.10f * h,
                specularReflect = 0.30f * h,
            )
        }

        GlassTone.Light -> {
            val base = lerp(if (dark) Color(0xFF44444B) else Color.White, wallpaper, 0.12f)
            val colored = if (accent != null) lerp(base, accent, 0.55f) else base
            GlassColors(
                fill = colored.copy(alpha = maxOf(floor, lerpFloat(0.08f, 0.72f, t))),
                edge = Color.Black.copy(alpha = lerpFloat(0.22f, 0.14f, t)),
                diffusionAlpha = lerpFloat(0.025f, 0.12f, t),
                specularStrong = 0.72f * h,
                specularSoft = 0.14f * h,
                specularReflect = 0.38f * h,
            )
        }
    }
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/** 壁紙上のページ背景もガラスと同じ設定に追従させる。値は描画時のみ読む。 */
@Composable
fun Modifier.wallpaperPageBackdrop(): Modifier {
    val appearance = LocalGlassAppearance.current
    val color = LocalOhagiColors.current.backdrop
    return drawWithCache {
        val tint = appearance.tint
        val brush = Brush.verticalGradient(
            0f to color.copy(alpha = wallpaperBackdropOpacity(tint, 0f)),
            0.35f to color.copy(alpha = wallpaperBackdropOpacity(tint, 0.5f)),
            1f to color.copy(alpha = wallpaperBackdropOpacity(tint, 1f)),
            endY = size.height,
        )
        onDrawBehind { drawRect(brush) }
    }
}

internal fun wallpaperBackdropOpacity(tint: Float, depth: Float): Float {
    val d = depth.coerceIn(0f, 1f)
    return lerpFloat(lerpFloat(0.06f, 0.16f, d), lerpFloat(0.88f, 0.96f, d), tint.coerceIn(0f, 1f))
}

/** クリア時に、面の後ろの暗幕だけが濃く残ることも防ぐ。 */
internal fun modalScrimColor(tint: Float, blurred: Boolean): Color = Color.Black.copy(
    alpha = lerpFloat(if (blurred) 0.06f else 0.10f, if (blurred) 0.18f else 0.38f, tint.coerceIn(0f, 1f)),
)

private val GLASS_REGULAR_BASE = Color(0xFF16151B)
private val GLASS_EDGE_WIDTH = 1.25.dp
private val GLASS_RIM_WIDTH = 1.dp

/** Liquid Glass の上に置く文字・記号の標準色。 */
val GlassContentColor: Color
    @Composable @ReadOnlyComposable get() = LocalOhagiColors.current.content
val GlassSecondaryContentColor: Color
    @Composable @ReadOnlyComposable get() = LocalOhagiColors.current.secondaryContent

/** iOS のシステムレッド(ダーク)。削除系の操作に使う。 */
val IosDestructiveRed: Color
    @Composable @ReadOnlyComposable get() = LocalOhagiColors.current.destructive

/** iOS のシステムブルー(ダーク)。「完了」などの文字ボタンに使う。 */
val IosSystemBlue: Color
    @Composable @ReadOnlyComposable get() = LocalOhagiColors.current.accent

// ---------------------------------------------------------------------------
// 背面ぼかし

/**
 * フォルダやメニューを開いたとき、背面のホーム画面をぼかすか。
 * RenderEffect は API 31 以降のみ。Low-RAM端末ではオフスクリーン描画を避ける。
 */
fun isBackdropBlurSupported(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val activityManager = context.getSystemService(ActivityManager::class.java)
    return activityManager?.isLowRamDevice != true
}

@Composable
fun rememberBackdropBlurSupported(): Boolean {
    val context = LocalContext.current
    return remember(context) { isBackdropBlurSupported(context) }
}

/**
 * 前面にフォルダ・メニューが出ている間、この要素(ホームのページ・Dockなど)をぼかす。
 * [progress] は 0〜1 で、graphicsLayer 内(描画フェーズ)でのみ読む。
 * 0 のときは RenderEffect を外し、オフスクリーン描画を発生させない。
 */
fun Modifier.overlayBackdropBlur(
    enabled: Boolean,
    progress: () -> Float,
    maxRadius: Dp = BACKDROP_BLUR_RADIUS,
): Modifier {
    if (!enabled) return this
    return graphicsLayer {
        val p = progress().coerceIn(0f, 1f)
        renderEffect = if (p > 0.01f) {
            val radius = maxRadius.toPx() * p
            BlurEffect(radius, radius, TileMode.Clamp)
        } else {
            null
        }
    }
}

val BACKDROP_BLUR_RADIUS = 24.dp

/**
 * ボトムシート・ダイアログのウィンドウに、Android 12 以降のウィンドウ背面ぼかし
 * (FLAG_BLUR_BEHIND + blurBehindRadius)を掛ける。背面の壁紙ごとぼかせる唯一の
 * 通常権限の手段で、合成は SurfaceFlinger が行う。
 *
 * ダイアログ(Dialog / ModalBottomSheet)の中身から呼ぶこと。返り値は、いま実際に
 * ぼかしが効いているか。省電力モードや端末設定で無効化されると false になるので、
 * 呼び出し側はその間スクリムやガラスの不透明度を上げて読みやすさを保つ。
 */
@Composable
fun rememberDialogWindowBackdropBlur(radius: Dp = DIALOG_BACKDROP_BLUR_RADIUS): Boolean {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(view) { view.findDialogWindow() }
    val colors = LocalOhagiColors.current
    SideEffect {
        window?.let {
            WindowCompat.getInsetsController(it, it.decorView).apply {
                isAppearanceLightStatusBars = !colors.isDark
                isAppearanceLightNavigationBars = !colors.isDark
            }
        }
    }
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val supported = remember(context) { isBackdropBlurSupported(context) }
    if (window == null || !supported) return false
    val radiusPx = with(LocalDensity.current) { radius.roundToPx() }
    val windowManager = window.windowManager
    var enabled by remember(window) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(window, radiusPx) {
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = radiusPx }
        val listener = Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

/**
 * 端末でウィンドウ背面ぼかし(クロスウィンドウブラー)がいま有効か。
 * 省電力モードや開発者設定で切り替わるので、変化を監視する。
 */
@Composable
fun rememberCrossWindowBlurEnabled(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
    val context = LocalContext.current
    val supported = remember(context) { isBackdropBlurSupported(context) }
    val windowManager = remember(context) { context.getSystemService(WindowManager::class.java) }
    if (!supported || windowManager == null) return false
    var enabled by remember(windowManager) { mutableStateOf(windowManager.isCrossWindowBlurEnabled) }
    DisposableEffect(windowManager) {
        val listener = Consumer<Boolean> { enabled = it }
        windowManager.addCrossWindowBlurEnabledListener(listener)
        onDispose { windowManager.removeCrossWindowBlurEnabledListener(listener) }
    }
    return enabled
}

val DIALOG_BACKDROP_BLUR_RADIUS = 30.dp

/** 着色側のシート・メニューの不透明度下限。クリア側では適用しない。 */
const val MODAL_GLASS_MIN_OPACITY_BLURRED = 0.50f

/** ぼかしが使えないときの着色側の下限。 */
const val MODAL_GLASS_MIN_OPACITY_OPAQUE = 0.86f

private fun View.findDialogWindow(): Window? {
    if (this is DialogWindowProvider) return window
    var current: ViewParent? = parent
    while (current != null) {
        if (current is DialogWindowProvider) return current.window
        current = current.parent
    }
    return null
}

// ---------------------------------------------------------------------------
// 連続曲率の角丸(iOS の squircle)

/**
 * iOS の `cornerCurve = .continuous` と同じ曲率連続の角丸矩形。
 * 円弧の角丸と違い、直線から曲線へ曲率がなめらかに増える。アイコン、フォルダ、
 * カードなど iOS で squircle が使われる部品に使う。
 * 丸いカプセル形状には通常の `RoundedCornerShape(50)` を使うこと。
 */
@Immutable
class ContinuousRoundedShape(private val radius: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radiusPx = with(density) { radius.toPx() }
        val path = Path()
        path.addContinuousRoundedRect(size.width, size.height, radiusPx)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?): Boolean =
        other is ContinuousRoundedShape && other.radius == radius

    override fun hashCode(): Int = radius.hashCode()

    override fun toString(): String = "ContinuousRoundedShape($radius)"
}

/**
 * [ContinuousRoundedShape] と同じ見た目の大きさの、影専用の円弧角丸。
 *
 * 影の輪郭が汎用パス(Outline.Generic)だと、HWUI は角丸矩形用の解析的な影を使えず、
 * 拡大縮小のたびに影の頂点を CPU でテッセレーションし直す。ページ送りやフォルダの
 * 開閉ではアイコン全部の影が毎フレーム作り直されるため、影には角丸矩形を使う。
 * ぼけた影では曲率の違いは見分けられない。係数は連続曲率の角が円弧より長く
 * 伸びる分(外周の中点がほぼ一致する値)。
 */
fun continuousShadowShape(radius: Dp): Shape =
    RoundedCornerShape(radius * CONTINUOUS_SHADOW_RADIUS_SCALE)

private const val CONTINUOUS_SHADOW_RADIUS_SCALE = 1.2f

/**
 * (0,0)-(width,height) に連続曲率の角丸矩形を追加する。
 * 係数は iOS 7 以降の UIBezierPath(roundedRect:cornerRadius:) の実測値として広く
 * 知られているもの。角の曲線は各辺から radius×1.5287 まで伸びるため、短辺の半分を
 * 超える場合は radius を縮めて辺同士が交差しないようにする。
 */
fun Path.addContinuousRoundedRect(width: Float, height: Float, radius: Float) {
    if (width <= 0f || height <= 0f) return
    val limit = minOf(width, height) / 2f / CONTINUOUS_EXTENT
    val r = radius.coerceIn(0f, limit)
    if (r <= 0f) {
        moveTo(0f, 0f)
        lineTo(width, 0f)
        lineTo(width, height)
        lineTo(0f, height)
        close()
        return
    }
    val w = width
    val h = height
    moveTo(CONTINUOUS_EXTENT * r, 0f)
    lineTo(w - CONTINUOUS_EXTENT * r, 0f)
    cubicTo(w - C_B * r, 0f, w - C_C * r, 0f, w - C_D * r, C_E * r)
    cubicTo(w - C_F * r, C_G * r, w - C_G * r, C_F * r, w - C_E * r, C_D * r)
    cubicTo(w, C_C * r, w, C_B * r, w, CONTINUOUS_EXTENT * r)
    lineTo(w, h - CONTINUOUS_EXTENT * r)
    cubicTo(w, h - C_B * r, w, h - C_C * r, w - C_E * r, h - C_D * r)
    cubicTo(w - C_G * r, h - C_F * r, w - C_F * r, h - C_G * r, w - C_D * r, h - C_E * r)
    cubicTo(w - C_C * r, h, w - C_B * r, h, w - CONTINUOUS_EXTENT * r, h)
    lineTo(CONTINUOUS_EXTENT * r, h)
    cubicTo(C_B * r, h, C_C * r, h, C_D * r, h - C_E * r)
    cubicTo(C_F * r, h - C_G * r, C_G * r, h - C_F * r, C_E * r, h - C_D * r)
    cubicTo(0f, h - C_C * r, 0f, h - C_B * r, 0f, h - CONTINUOUS_EXTENT * r)
    lineTo(0f, CONTINUOUS_EXTENT * r)
    cubicTo(0f, C_B * r, 0f, C_C * r, C_E * r, C_D * r)
    cubicTo(C_G * r, C_F * r, C_F * r, C_G * r, C_D * r, C_E * r)
    cubicTo(C_C * r, 0f, C_B * r, 0f, CONTINUOUS_EXTENT * r, 0f)
    close()
}

const val CONTINUOUS_EXTENT = 1.52866483f
private const val C_B = 1.08849323f
private const val C_C = 0.86840689f
private const val C_D = 0.63149399f
private const val C_E = 0.07491100f
private const val C_F = 0.36994867f
private const val C_G = 0.17964654f
