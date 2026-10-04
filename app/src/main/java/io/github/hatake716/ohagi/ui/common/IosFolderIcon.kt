package io.github.hatake716.ohagi.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import io.github.hatake716.ohagi.data.AppRef

/**
 * iPhoneのホームフォルダを意識した3×3プレビュー。
 * 先頭ページの最大9アプリを、壁紙の色を拾う曇りガラスの中へ並べる。
 *
 * ミニアイコンの格子(3×0.205 + 2×0.055 = 0.725倍)は内側余白を除いた0.80倍の
 * 領域に収まり、角の連続曲率(対角で約0.066倍)にも掛からないため、
 * コンテナのクリップ(レイヤー)は使わない。
 */
@Composable
fun IosFolderIcon(
    apps: List<AppRef>,
    size: Dp,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    preloadedIcons: List<ImageBitmap?>? = null,
) {
    val iconShape = iosIconShape(size)
    val shadowShape = iosIconShadowShape(size)
    val scale by animateFloatAsState(
        // セル全体のtarget拡大と組み合わせ、合成後がおよそ1.12倍に収まる値。
        targetValue = if (highlighted) 1.05f else 1f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 520f),
        label = "iosFolderTargetScale",
    )
    // 値は描画フェーズで読み、ハイライト中もホーム/Dockのセルを再composeしない。
    val targetGlow = animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(durationMillis = 130),
        label = "iosFolderTargetGlow",
    )
    val shadowElevation = size * 0.045f
    val miniSize = size * 0.205f
    val spacing = size * 0.055f

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                // 影もこの1枚のレイヤーで落とし、フォルダごとのレイヤーを増やさない。
                this.shadowElevation = shadowElevation.toPx()
                shape = shadowShape
                clip = false
                ambientShadowColor = FOLDER_AMBIENT_SHADOW
                spotShadowColor = FOLDER_SPOT_SHADOW
            }
            .liquidGlass(iconShape, GlassTone.Light)
            .drawWithCache {
                val outline = iconShape.createOutline(this.size, layoutDirection, this)
                onDrawBehind {
                    val glow = targetGlow.value
                    if (glow > 0f) {
                        drawOutline(
                            outline,
                            Color.White.copy(alpha = FOLDER_TARGET_GLOW_ALPHA * glow),
                        )
                    }
                }
            }
            .padding(size * 0.10f),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
            repeat(3) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing)) {
                    repeat(3) { column ->
                        val app = apps.getOrNull(row * 3 + column)
                        if (app == null) {
                            Spacer(Modifier.size(miniSize))
                        } else if (preloadedIcons != null) {
                            AppIconImage(
                                icon = preloadedIcons.getOrNull(row * 3 + column),
                                size = miniSize,
                                decorated = false,
                            )
                        } else {
                            FolderMiniIcon(app = app, size = miniSize)
                        }
                    }
                }
            }
        }
    }
}

/**
 * ミニアイコンはRepository側で角丸済みのBitmapをそのまま描く。
 * 装飾(影・クリップ)を付けると1フォルダあたり最大18枚のレイヤーになるため避ける。
 */
@Composable
private fun FolderMiniIcon(app: AppRef, size: Dp) {
    val icon by rememberAppIconBitmap(app, size)
    AppIconImage(icon = icon, size = size, decorated = false)
}

private val FOLDER_AMBIENT_SHADOW = Color.Black.copy(alpha = 0.20f)
private val FOLDER_SPOT_SHADOW = Color.Black.copy(alpha = 0.26f)

/** フォルダ化targetになったときに曇りガラスへ重ねる白の強さ。 */
private const val FOLDER_TARGET_GLOW_ALPHA = 0.24f
