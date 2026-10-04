package io.github.hatake716.ohagi.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** ボトムシートメニューの 1 項目 */
data class MenuEntry(
    val label: String,
    val icon: ImageVector? = null,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * 共通の長押しメニュー。iOS 26/27 のコンテキストメニューに倣い、画面端から浮いた
 * Liquid Glass のカードとして下端に表示する。
 * header にはタイトルやアプリ情報などの任意コンテンツを表示できる。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuSheet(
    entries: List<MenuEntry>,
    onDismiss: () -> Unit,
    header: (@Composable () -> Unit)? = null,
) {
    val backdrop = rememberModalSheetBackdrop()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetMaxWidth = MENU_MAX_WIDTH,
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = GlassContentColor,
        tonalElevation = 0.dp,
        scrimColor = backdrop.scrimColor,
        dragHandle = null,
        // 余白はカード側で取る。シート既定のインセットでは透明な面が伸びるだけになる。
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        val glassMinOpacity = backdrop.blurDialogWindow()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp)
                .liquidGlass(MenuCardShape, GlassTone.Regular, minOpacity = glassMinOpacity)
                .clip(MenuCardShape),
        ) {
            if (header != null) {
                CompositionLocalProvider(
                    LocalContentColor provides GlassSecondaryContentColor,
                    LocalTextStyle provides LocalTextStyle.current.copy(
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        letterSpacing = 0.sp,
                    ),
                ) {
                    Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp)) {
                        header()
                    }
                }
                MenuSeparator()
            }
            entries.forEachIndexed { index, entry ->
                MenuRow(
                    entry = entry,
                    onClick = {
                        onDismiss()
                        entry.onClick()
                    },
                )
                if (index != entries.lastIndex) {
                    MenuSeparator()
                }
            }
        }
    }
}

/** iOS のコンテキストメニューと同じく、ラベルを左・シンボルを右端に置く1行。 */
@Composable
private fun MenuRow(
    entry: MenuEntry,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val color = if (entry.destructive) IosDestructiveRed else GlassContentColor
    val pressedColor = LocalOhagiColors.current.pressedFill
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MENU_ROW_MIN_HEIGHT)
            // 押下状態は描画フェーズでだけ読み、行を再composeしない。
            .drawBehind { if (pressed.value) drawRect(pressedColor) }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = entry.label,
            color = color,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = 0.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (entry.icon != null) {
            Spacer(Modifier.width(14.dp))
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun MenuSeparator() {
    HorizontalDivider(thickness = 0.5.dp, color = LocalOhagiColors.current.separator)
}

/**
 * ModalBottomSheet の背面の扱い。シートのウィンドウに背面ぼかしを掛け、効いているかで
 * スクリムの濃さとガラスの着色側の不透明度下限を切り替える。
 * クリア側では、ぼかしの有無にかかわらず設定した透過を保つ。
 *
 * ぼかしが効くかはシートのウィンドウの中でしか分からない一方、スクリムの色は
 * ModalBottomSheet の引数なので、中身で判定した結果をこの状態へ戻して使う。
 * 推測が外れた場合は1フレーム遅れて切り替わるが、スクリムはフェードイン中なので目立たない。
 */
@Stable
class ModalSheetBackdrop internal constructor(initialBlurred: Boolean, private val appearance: GlassAppearance) {
    private var windowBlurred by mutableStateOf(initialBlurred)

    val scrimColor: Color
        get() = modalScrimColor(appearance.tint, windowBlurred)

    /**
     * ModalBottomSheet の中身から呼び、シートのウィンドウに背面ぼかしを掛ける。
     * 返り値はガラス([liquidGlass])に渡す minOpacity。
     */
    @Composable
    fun blurDialogWindow(): Float {
        val blurred = rememberDialogWindowBackdropBlur()
        SideEffect { windowBlurred = blurred }
        return if (blurred) MODAL_GLASS_MIN_OPACITY_BLURRED else MODAL_GLASS_MIN_OPACITY_OPAQUE
    }
}

/** シートを表示する composable で作り、[ModalSheetBackdrop.scrimColor] を ModalBottomSheet に渡す。 */
@Composable
fun rememberModalSheetBackdrop(): ModalSheetBackdrop {
    val blurSupported = rememberBackdropBlurSupported()
    val appearance = LocalGlassAppearance.current
    return remember(appearance) { ModalSheetBackdrop(initialBlurred = blurSupported, appearance = appearance) }
}

private val MenuCardShape = ContinuousRoundedShape(30.dp)
private val MENU_MAX_WIDTH = 480.dp
private val MENU_ROW_MIN_HEIGHT = 50.dp
