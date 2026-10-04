package io.github.hatake716.ohagi.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors
import kotlinx.coroutines.launch

/** iOS のホームアイコンマスクに近づけるための丸角比率。 */
const val IOS_ICON_CORNER_RATIO = 0.2237f

const val IOS_PRESSED_SCALE = 0.94f

/** 長押しで持ち上げた後、元位置に残す半透明のプレースホルダー。 */
const val IOS_DRAG_SOURCE_ALPHA = 0.14f

@Stable
class IosDragVisualState internal constructor(
    private val interactionScale: State<Float>,
    private val landingScale: State<Float>,
    private val sourceAlpha: State<Float>,
    /** ドロップ成立時に、着地点を一度だけ弾ませて等倍へ収束させる。 */
    val settle: (emphasized: Boolean) -> Unit,
) {
    // アニメーション値は呼び出し側のgraphicsLayer内で読む。
    // Floatへ先に展開すると、毎フレームHome/Dock/Folderのセル全体が再composeされる。
    val scale: Float get() = interactionScale.value * landingScale.value
    val alpha: Float get() = sourceAlpha.value
}

/** iOS のアイコンマスク(連続曲率の角丸)。 */
fun iosIconShape(size: Dp): Shape = ContinuousRoundedShape(size * IOS_ICON_CORNER_RATIO)

/** アイコン・フォルダアイコンの影の輪郭。理由は [continuousShadowShape]。 */
fun iosIconShadowShape(size: Dp): Shape = continuousShadowShape(size * IOS_ICON_CORNER_RATIO)

val IosSheetShape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)

/**
 * 指が触れた瞬間は短く沈み、離したときだけ軽いスプリングで戻す。
 * 大きく跳ねさせず、頻繁な操作でも待ち時間を感じない設定にする。
 */
@Composable
fun animateIosPressScale(
    pressed: Boolean,
    label: String,
    pressedScale: Float = IOS_PRESSED_SCALE,
): Float = rememberIosPressScale(pressed, label, pressedScale).value

/** 描画だけに使う押下スケールは、Stateを渡してdraw/layerまで読み取りを遅らせる。 */
@Composable
fun rememberIosPressScale(
    pressed: Boolean,
    label: String,
    pressedScale: Float = IOS_PRESSED_SCALE,
): State<Float> = animateFloatAsState(
    targetValue = if (pressed) pressedScale else 1f,
    animationSpec = if (pressed) {
        IosMotion.pressDownSpec
    } else {
        IosMotion.pressReleaseSpec
    },
    label = label,
)

/**
 * iOSホームのD&Dに近い、沈み込み → lift → target拡大 → 着地の共通モーション。
 *
 * ドラッグ中の元アイコンは小さく薄く残し、指下のOS drag decorationを主役にする。
 * 通常targetは控えめに、フォルダ化targetは一段大きくし、成立時は低減衰の
 * springで等倍へ戻す。各セルが独自の無限アニメーションを持たないため、
 * ページ数が増えても待機中のCPU/RAM負荷は増えにくい。
 */
@Composable
fun rememberIosDragVisualState(
    pressed: Boolean,
    isDragging: Boolean,
    dropHovered: Boolean = false,
    folderReady: Boolean = false,
    label: String,
): IosDragVisualState {
    val interactionScale = animateFloatAsState(
        targetValue = when {
            isDragging -> 0.90f
            folderReady -> 1.065f
            dropHovered -> 1.04f
            pressed -> IOS_PRESSED_SCALE
            else -> 1f
        },
        animationSpec = when {
            pressed && !isDragging && !dropHovered ->
                IosMotion.pressDownSpec
            isDragging -> spring(dampingRatio = 0.88f, stiffness = 720f)
            folderReady -> spring(dampingRatio = 0.72f, stiffness = 420f)
            dropHovered -> spring(dampingRatio = 0.82f, stiffness = 520f)
            else -> spring(dampingRatio = 0.72f, stiffness = 610f)
        },
        label = "${label}InteractionScale",
    )
    val sourceAlpha = animateFloatAsState(
        targetValue = if (isDragging) IOS_DRAG_SOURCE_ALPHA else 1f,
        animationSpec = tween(
            durationMillis = if (isDragging) {
                IosMotion.QUICK_FADE_MS
            } else {
                IosMotion.STANDARD_FADE_MS
            },
            easing = if (isDragging) IosMotion.easeIn else IosMotion.easeOut,
        ),
        label = "${label}SourceAlpha",
    )
    val landingScale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val settle = remember(landingScale, scope) {
        { emphasized: Boolean ->
            scope.launch {
                landingScale.stop()
                // フォルダtargetはinteraction側ですでに約1.12倍なので、着地pulseを重ねすぎない。
                landingScale.snapTo(if (emphasized) 1.02f else 1.065f)
                landingScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(dampingRatio = 0.68f, stiffness = 470f),
                )
            }
            Unit
        }
    }

    return remember(interactionScale, landingScale, sourceAlpha, settle) {
        IosDragVisualState(
            interactionScale = interactionScale,
            landingScale = landingScale.asState(),
            sourceAlpha = sourceAlpha,
            settle = settle,
        )
    }
}

/** ナビゲーションバー上で使う、Liquid Glass の丸いシンボルボタン。 */
@Composable
fun IosGlassIconButton(
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        pressedScale = 0.9f,
        label = "iosGlassButtonScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .liquidGlass(CircleShape, highlight = 0.85f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
                role = Role.Button
            },
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = GlassContentColor,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * iOS 26 の検索カプセル。Liquid Glass の丸い面に虫眼鏡とプレースホルダーを置き、
 * 入力中だけ右端に消去ボタンを出す。
 */
@Composable
fun IosSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    clearContentDescription: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val contentColor = GlassContentColor
    val textStyle = remember(contentColor) {
        TextStyle(
            color = contentColor,
            fontSize = 17.sp,
            letterSpacing = (-0.2).sp,
        )
    }
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle,
        cursorBrush = SolidColor(IosSystemBlue),
        keyboardOptions = KeyboardOptions(
            imeAction = ImeAction.Search,
            autoCorrectEnabled = false,
        ),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
        modifier = modifier
            .height(IOS_SEARCH_FIELD_HEIGHT)
            .liquidGlass(IosSearchFieldShape, highlight = 0.8f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { innerTextField ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 14.dp, end = 6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = GlassSecondaryContentColor,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.weight(1f),
                ) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle,
                            color = GlassSecondaryContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (value.isNotEmpty()) {
                    // iOS の xmark.circle.fill。当たり判定は32dp、見た目の円は18dp。
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onValueChange("") }
                            .semantics {
                                contentDescription = clearContentDescription
                                role = Role.Button
                            },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(18.dp)
                                .background(GlassSecondaryContentColor, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                tint = LocalOhagiColors.current.backdrop,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }
            }
        },
    )
}

private val IOS_SEARCH_FIELD_HEIGHT = 44.dp
private val IosSearchFieldShape = RoundedCornerShape(50)
