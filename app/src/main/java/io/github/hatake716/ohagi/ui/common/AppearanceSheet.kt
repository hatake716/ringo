package io.github.hatake716.ohagi.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.LocalGraph
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.ThemeMode
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

/**
 * ⚙ から開く設定シート。画面下端から浮いた角丸のガラスカードとして表示する。
 *
 * ガラスの透明度はドラッグ中は [io.github.hatake716.ohagi.data.AppearanceRepository.previewGlassTint]
 * で即時反映し、指を離した時だけ永続化する。各ガラス面とページ背景は描画フェーズで
 * 値を読む。ModalBottomSheetの暗幕は色の引数が必要なのでシート内だけ再composeする。
 *
 * シート自身のガラスと暗幕も設定に追従する。クリア側で固定の濃い下地は敷かない。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSheet(
    onOpenDefaultHome: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repository = LocalGraph.current.appearanceRepository
    // ガラス面は描画側で読む。ここではスライダーとシート暗幕の色だけが値を使う。
    val glassTint = repository.glassTint.collectAsState()
    val themeMode by repository.themeMode.collectAsStateWithLifecycle()
    val backdrop = rememberSheetBackdropState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        // 面はカード側の liquidGlass で描く。シート本体は透明・角丸なしにして、
        // カードの角がシートの形でクリップされないようにする。
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = GlassContentColor,
        tonalElevation = 0.dp,
        scrimColor = backdrop.scrimColor,
        dragHandle = null,
    ) {
        val glassMinOpacity = rememberSheetGlassMinOpacity(backdrop)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                .liquidGlass(AppearanceSheetShape, minOpacity = glassMinOpacity)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                color = GlassContentColor,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.settings_appearance),
                color = GlassSecondaryContentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    val label = stringResource(when (mode) {
                        ThemeMode.System -> R.string.settings_theme_system
                        ThemeMode.Light -> R.string.settings_theme_light
                        ThemeMode.Dark -> R.string.settings_theme_dark
                    })
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = themeMode == mode,
                                role = Role.RadioButton,
                                onClick = { repository.setThemeMode(mode) },
                            )
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.System -> Icons.Rounded.BrightnessAuto
                                ThemeMode.Light -> Icons.Rounded.LightMode
                                ThemeMode.Dark -> Icons.Rounded.DarkMode
                            },
                            contentDescription = null,
                            tint = GlassContentColor,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(label, color = GlassContentColor, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        RadioButton(selected = themeMode == mode, onClick = null)
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_theme_caption),
                color = GlassSecondaryContentColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = LocalOhagiColors.current.separator,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            Text(
                text = stringResource(R.string.settings_glass_transparency),
                color = GlassContentColor,
                fontSize = 17.sp,
            )
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.settings_glass_clear),
                    color = GlassSecondaryContentColor,
                    fontSize = 13.sp,
                )
                IosSlider(
                    value = glassTint,
                    onValueChange = repository::previewGlassTint,
                    onValueChangeFinished = repository::persistGlassTint,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.settings_glass_tinted),
                    color = GlassSecondaryContentColor,
                    fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_glass_caption),
                color = GlassSecondaryContentColor,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = LocalOhagiColors.current.separator,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            SettingsRow(
                label = stringResource(R.string.menu_set_default_home),
                onClick = {
                    onDismiss()
                    onOpenDefaultHome()
                },
            )
        }
    }
}

/** iOS 風スライダー: 細いトラックと、影付きの白い丸つまみ。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosSlider(
    value: State<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value.value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = IosSystemBlue,
            inactiveTrackColor = LocalOhagiColors.current.separator,
        ),
        thumb = {
            Spacer(
                Modifier
                    .size(IOS_SLIDER_THUMB_SIZE)
                    .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
                    .background(Color.White, CircleShape),
            )
        },
        track = { state -> IosSliderTrack(state) },
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IosSliderTrack(state: SliderState) {
    val activeColor = IosSystemBlue
    val inactiveColor = LocalOhagiColors.current.separator
    // 値は Canvas の描画ラムダ内で読み、ドラッグ中はトラックの再描画だけで済ませる。
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(IOS_SLIDER_TRACK_HEIGHT),
    ) {
        val range = state.valueRange
        val span = (range.endInclusive - range.start).takeIf { it > 0f } ?: 1f
        val fraction = ((state.value - range.start) / span).coerceIn(0f, 1f)
        val radius = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(
            color = inactiveColor,
            size = size,
            cornerRadius = radius,
        )
        val activeWidth = size.width * fraction
        if (activeWidth > 0f) {
            drawRoundRect(
                color = activeColor,
                topLeft = Offset.Zero,
                size = Size(activeWidth, size.height),
                cornerRadius = radius,
            )
        }
    }
}

@Composable
private fun SettingsRow(
    label: String,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = label,
            color = GlassContentColor,
            fontSize = 17.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = GlassSecondaryContentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * ModalBottomSheet の暗幕とガラスの濃さを、シートのウィンドウ背面ぼかしが効いているかで切り替える。
 *
 * ぼかしが効いているかはシートの中身(ダイアログのウィンドウ)でしか判定できない一方、
 * 暗幕の色は ModalBottomSheet の引数なので、中身で得た結果をここへ書き戻して次のフレームで
 * 暗幕へ反映する。表示開始時の暗幕は透明から現れるので、この1フレームの遅れは見えない。
 */
@Stable
internal class SheetBackdropState(private val appearance: GlassAppearance) {
    private var blurred by mutableStateOf(false)

    /** ModalBottomSheet の scrimColor に渡す。 */
    val scrimColor: Color
        get() = modalScrimColor(appearance.tint, blurred)

    internal fun update(blurred: Boolean) {
        this.blurred = blurred
    }
}

@Composable
internal fun rememberSheetBackdropState(): SheetBackdropState {
    val appearance = LocalGlassAppearance.current
    return remember(appearance) { SheetBackdropState(appearance) }
}

/**
 * シートの中身から呼ぶ。そのウィンドウに背面ぼかしを掛け、結果を [backdrop] へ伝え、
 * シートのガラスに渡す不透明度の下限を返す。
 */
@Composable
internal fun rememberSheetGlassMinOpacity(backdrop: SheetBackdropState): Float {
    val blurred = rememberDialogWindowBackdropBlur()
    SideEffect { backdrop.update(blurred) }
    return if (blurred) MODAL_GLASS_MIN_OPACITY_BLURRED else MODAL_GLASS_MIN_OPACITY_OPAQUE
}

private val AppearanceSheetShape = ContinuousRoundedShape(30.dp)
private val IOS_SLIDER_THUMB_SIZE = 26.dp
private val IOS_SLIDER_TRACK_HEIGHT = 4.dp
