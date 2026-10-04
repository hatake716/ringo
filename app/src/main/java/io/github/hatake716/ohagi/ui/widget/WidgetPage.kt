package io.github.hatake716.ohagi.ui.widget

import android.appwidget.AppWidgetHostView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.github.hatake716.ohagi.LocalGraph
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.WidgetPlacement
import io.github.hatake716.ohagi.ui.common.ContinuousRoundedShape
import io.github.hatake716.ohagi.ui.common.GlassContentColor
import io.github.hatake716.ohagi.ui.common.GlassSecondaryContentColor
import io.github.hatake716.ohagi.ui.common.GlassTone
import io.github.hatake716.ohagi.ui.common.IosDestructiveRed
import io.github.hatake716.ohagi.ui.common.IosSystemBlue
import io.github.hatake716.ohagi.ui.common.liquidGlass
import io.github.hatake716.ohagi.ui.common.wallpaperPageBackdrop
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors
import kotlin.math.abs
import kotlin.math.roundToInt

/** iOS Today Viewに相当する、Dockを持たない左端のウィジェット専用ページ。 */
@Composable
fun WidgetPage(
    widgets: List<WidgetPlacement>,
    onAddWidget: () -> Unit,
    onRemoveWidget: (WidgetPlacement) -> Unit,
    onMoveWidget: (WidgetPlacement, Int) -> Unit,
    onResizeWidget: (WidgetPlacement, Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .wallpaperPageBackdrop()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_page_title),
                color = GlassContentColor,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            WidgetHeaderActions(
                showEdit = widgets.isNotEmpty(),
                editing = editing,
                onToggleEditing = { editing = !editing },
                onAddWidget = onAddWidget,
            )
        }

        if (widgets.isEmpty()) {
            EmptyWidgetPage(
                onAddWidget = onAddWidget,
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(
                    items = widgets,
                    key = { _, placement -> placement.appWidgetId },
                ) { index, placement ->
                    HostedWidgetCard(
                        placement = placement,
                        editing = editing,
                        canMoveUp = index > 0,
                        canMoveDown = index < widgets.lastIndex,
                        onRemove = { onRemoveWidget(placement) },
                        onMoveUp = { onMoveWidget(placement, -1) },
                        onMoveDown = { onMoveWidget(placement, 1) },
                        onResize = { widthDp, heightDp ->
                            onResizeWidget(placement, widthDp, heightDp)
                        },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                item { Spacer(Modifier.height(18.dp)) }
            }
        }
    }
}

/**
 * iOS 26 のツールバーと同じく、関連する操作を1枚のガラスのカプセルにまとめる。
 * ウィジェットが無いときは「+」だけの丸いガラスになる。
 */
@Composable
private fun WidgetHeaderActions(
    showEdit: Boolean,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onAddWidget: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(GLASS_BUTTON_SIZE)
            .liquidGlass(CapsuleShape, GlassTone.Regular, highlight = 0.85f),
    ) {
        if (showEdit) {
            GlassPressable(
                onClick = onToggleEditing,
                modifier = Modifier.fillMaxHeight(),
            ) {
                Text(
                    text = stringResource(
                        if (editing) R.string.action_done else R.string.folder_edit,
                    ),
                    color = if (editing) IosSystemBlue else GlassContentColor,
                    fontSize = 16.sp,
                    letterSpacing = 0.sp,
                    fontWeight = if (editing) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
        GlassPressable(
            onClick = onAddWidget,
            contentDescription = stringResource(R.string.widget_add),
            modifier = Modifier.size(GLASS_BUTTON_SIZE),
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = GlassContentColor,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/**
 * ガラス面の上に置く押下領域。押下中は白い光をカプセル形に重ねる。
 * 押下状態は描画フェーズでだけ読み、再compositionもレイヤーも増やさない。
 */
@Composable
private fun GlassPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val pressedHighlight = LocalOhagiColors.current.pressedFill
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .drawBehind {
                if (pressed.value) {
                    val radius = size.minDimension / 2f
                    drawRoundRect(
                        color = pressedHighlight,
                        cornerRadius = CornerRadius(radius, radius),
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                if (contentDescription != null) this.contentDescription = contentDescription
            },
    ) {
        content()
    }
}

@Composable
private fun EmptyWidgetPage(
    onAddWidget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(28.dp)
                .liquidGlass(EmptyCardShape, GlassTone.Regular)
                .padding(horizontal = 28.dp, vertical = 34.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = GlassContentColor,
                modifier = Modifier.size(52.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(R.string.widget_empty_title),
                color = GlassContentColor,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.widget_empty_body),
                color = GlassSecondaryContentColor,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            // ガラスの上にガラスを重ねず、iOS の「強調ボタン」と同じ着色したガラスにする。
            GlassPressable(
                onClick = onAddWidget,
                contentDescription = stringResource(R.string.widget_add),
                modifier = Modifier
                    .size(GLASS_BUTTON_SIZE)
                    .background(PROMINENT_CONTROL_BACKING, CircleShape)
                    .liquidGlass(CircleShape, accent = IosSystemBlue, highlight = 0.9f),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun HostedWidgetCard(
    placement: WidgetPlacement,
    editing: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onResize: (widthDp: Int, heightDp: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controller = LocalGraph.current.widgetHost
    val info = remember(placement.appWidgetId) {
        controller.appWidgetInfo(placement.appWidgetId)
    }
    val providerMatches = remember(info, placement.providerPackage, placement.providerClass) {
        info != null && info.provider == controller.componentOf(placement)
    }
    val density = LocalDensity.current
    var availableWidthPx by remember { mutableIntStateOf(0) }
    val availableWidthDp = with(density) {
        availableWidthPx.toDp().value.roundToInt()
    }
    val persistedWidthDp = when {
        availableWidthDp <= 0 -> placement.widthDp.coerceAtLeast(1)
        placement.widthDp == WidgetPlacement.MATCH_PARENT_WIDTH_DP -> availableWidthDp
        else -> placement.widthDp.coerceIn(1, availableWidthDp)
    }
    val persistedHeightDp = placement.heightDp.coerceIn(
        WidgetPlacement.MIN_WIDGET_HEIGHT_DP,
        WidgetPlacement.MAX_WIDGET_HEIGHT_DP,
    )
    val resizeBounds = remember(info, availableWidthDp, editing) {
        if (!editing || info == null || availableWidthDp <= 0) {
            null
        } else {
            controller.resizeBounds(info, availableWidthDp)
        }
    }
    val canResizeHorizontally = resizeBounds?.canResizeHorizontally == true
    val canResizeVertically = resizeBounds?.canResizeVertically == true
    val minWidthDp = resizeBounds?.minWidthDp ?: persistedWidthDp
    val maxWidthDp = resizeBounds?.maxWidthDp ?: persistedWidthDp
    val minHeightDp = resizeBounds?.minHeightDp ?: persistedHeightDp
    val maxHeightDp = resizeBounds?.maxHeightDp ?: persistedHeightDp

    var draftWidthDp by remember(placement.appWidgetId) {
        mutableIntStateOf(placement.widthDp.coerceAtLeast(1))
    }
    var draftHeightDp by remember(placement.appWidgetId) {
        mutableIntStateOf(persistedHeightDp)
    }
    var resizing by remember(placement.appWidgetId) { mutableStateOf(false) }

    LaunchedEffect(
        placement.widthDp,
        placement.heightDp,
        availableWidthDp,
    ) {
        if (!resizing && availableWidthDp > 0) {
            draftWidthDp = persistedWidthDp
            draftHeightDp = persistedHeightDp
        }
    }

    val displayedWidthDp = if (availableWidthDp > 0) {
        draftWidthDp.coerceIn(1, availableWidthDp)
    } else {
        draftWidthDp.coerceAtLeast(1)
    }
    val displayedHeightDp = draftHeightDp.coerceIn(
        WidgetPlacement.MIN_WIDGET_HEIGHT_DP,
        WidgetPlacement.MAX_WIDGET_HEIGHT_DP,
    )

    LaunchedEffect(
        placement.appWidgetId,
        placement.widthDp,
        placement.heightDp,
        availableWidthDp,
        info,
    ) {
        if (availableWidthDp > 0 && info != null) {
            controller.updateSize(
                appWidgetId = placement.appWidgetId,
                widthDp = persistedWidthDp,
                heightDp = persistedHeightDp,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { availableWidthPx = it.width },
        contentAlignment = Alignment.TopCenter,
    ) {
        val cardWidthModifier = if (availableWidthDp > 0) {
            Modifier.width(displayedWidthDp.dp)
        } else {
            Modifier.fillMaxWidth()
        }
        Box(
            modifier = cardWidthModifier
                .height(displayedHeightDp.dp)
                .liquidGlass(WidgetCardGlassShape, GlassTone.Regular)
                // AppWidgetHostView を含むため、クリップは RenderNode の輪郭で扱える
                // 円弧の角丸のままにする(ガラスの面だけ連続曲率で描く)。
                .clip(WidgetCardClipShape),
        ) {
            if (info == null || !providerMatches) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = stringResource(R.string.widget_unavailable),
                        color = GlassSecondaryContentColor,
                    )
                }
            } else {
                AndroidView<AppWidgetHostView>(
                    factory = { context ->
                        controller.createView(context, placement.appWidgetId, info)
                    },
                    onRelease = controller::releaseView,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (editing) {
                // 名前は編集操作の読み上げにだけ使うため、編集中にだけ読む。
                val label = remember(placement.appWidgetId, info, placement.providerPackage) {
                    info?.let { controller.widgetLabel(placement.appWidgetId, it) }
                        ?: placement.providerPackage
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                ) {
                    if (canMoveUp) {
                        WidgetEditButton(
                            imageVector = Icons.Rounded.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.widget_move_up, label),
                            onClick = onMoveUp,
                        )
                    }
                    if (canMoveDown) {
                        WidgetEditButton(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.widget_move_down, label),
                            onClick = onMoveDown,
                        )
                    }
                    WidgetEditButton(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.widget_remove, label),
                        destructive = true,
                        onClick = onRemove,
                    )
                }

                if (canResizeHorizontally || canResizeVertically) {
                    WidgetEditLabel(
                        text = stringResource(
                            R.string.widget_size_value,
                            displayedWidthDp,
                            displayedHeightDp,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    )
                    WidgetResizeHandle(
                        contentDescription = stringResource(R.string.widget_resize, label),
                        canResizeHorizontally = canResizeHorizontally,
                        canResizeVertically = canResizeVertically,
                        minWidthDp = minWidthDp,
                        maxWidthDp = maxWidthDp,
                        minHeightDp = minHeightDp,
                        maxHeightDp = maxHeightDp,
                        persistedWidthDp = persistedWidthDp,
                        persistedHeightDp = persistedHeightDp,
                        draftWidthDp = draftWidthDp,
                        draftHeightDp = draftHeightDp,
                        availableWidthDp = availableWidthDp,
                        onResizeStateChanged = { resizing = it },
                        onDraftSizeChanged = { widthDp, heightDp ->
                            draftWidthDp = widthDp
                            draftHeightDp = heightDp
                        },
                        onResizeFinished = { widthDp, heightDp ->
                            val storedWidthDp = if (
                                canResizeHorizontally &&
                                abs(widthDp - availableWidthDp) <= FULL_WIDTH_SNAP_TOLERANCE_DP
                            ) {
                                WidgetPlacement.MATCH_PARENT_WIDTH_DP
                            } else if (canResizeHorizontally) {
                                widthDp
                            } else {
                                placement.widthDp
                            }
                            val storedHeightDp = if (canResizeVertically) {
                                heightDp
                            } else {
                                placement.heightDp
                            }
                            if (
                                storedWidthDp != placement.widthDp ||
                                storedHeightDp != placement.heightDp
                            ) {
                                onResize(storedWidthDp, storedHeightDp)
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                    )
                } else {
                    WidgetEditLabel(
                        text = stringResource(R.string.widget_resize_unavailable),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetResizeHandle(
    contentDescription: String,
    canResizeHorizontally: Boolean,
    canResizeVertically: Boolean,
    minWidthDp: Int,
    maxWidthDp: Int,
    minHeightDp: Int,
    maxHeightDp: Int,
    persistedWidthDp: Int,
    persistedHeightDp: Int,
    draftWidthDp: Int,
    draftHeightDp: Int,
    availableWidthDp: Int,
    onResizeStateChanged: (Boolean) -> Unit,
    onDraftSizeChanged: (widthDp: Int, heightDp: Int) -> Unit,
    onResizeFinished: (widthDp: Int, heightDp: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val latestDraftWidthDp by rememberUpdatedState(draftWidthDp)
    val latestDraftHeightDp by rememberUpdatedState(draftHeightDp)
    val latestOnResizeStateChanged by rememberUpdatedState(onResizeStateChanged)
    val latestOnDraftSizeChanged by rememberUpdatedState(onDraftSizeChanged)
    val latestOnResizeFinished by rememberUpdatedState(onResizeFinished)
    val safeMaxWidthDp = maxWidthDp.coerceAtMost(availableWidthDp).coerceAtLeast(1)
    val safeMinWidthDp = minWidthDp.coerceAtMost(safeMaxWidthDp)
    val safeMaxHeightDp = maxHeightDp.coerceAtLeast(1)
    val safeMinHeightDp = minHeightDp.coerceAtMost(safeMaxHeightDp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(44.dp)
            .background(PROMINENT_CONTROL_BACKING, CircleShape)
            .liquidGlass(CircleShape, accent = IosSystemBlue, highlight = 0.9f)
            .semantics { this.contentDescription = contentDescription }
            .pointerInput(
                canResizeHorizontally,
                canResizeVertically,
                safeMinWidthDp,
                safeMaxWidthDp,
                safeMinHeightDp,
                safeMaxHeightDp,
                persistedWidthDp,
                persistedHeightDp,
                availableWidthDp,
                density.density,
            ) {
                var widthAccumulator = draftWidthDp.toFloat()
                var heightAccumulator = draftHeightDp.toFloat()
                var currentWidthDp = draftWidthDp
                var currentHeightDp = draftHeightDp
                detectDragGestures(
                    onDragStart = {
                        widthAccumulator = latestDraftWidthDp.toFloat()
                        heightAccumulator = latestDraftHeightDp.toFloat()
                        currentWidthDp = latestDraftWidthDp
                        currentHeightDp = latestDraftHeightDp
                        latestOnResizeStateChanged(true)
                    },
                    onDragCancel = {
                        latestOnDraftSizeChanged(persistedWidthDp, persistedHeightDp)
                        latestOnResizeStateChanged(false)
                    },
                    onDragEnd = {
                        val finalWidthDp = if (canResizeHorizontally) {
                            snapWidgetSize(
                                currentWidthDp,
                                safeMinWidthDp,
                                safeMaxWidthDp,
                            )
                        } else {
                            persistedWidthDp
                        }
                        val finalHeightDp = if (canResizeVertically) {
                            snapWidgetSize(
                                currentHeightDp,
                                safeMinHeightDp,
                                safeMaxHeightDp,
                            )
                        } else {
                            persistedHeightDp
                        }
                        latestOnDraftSizeChanged(finalWidthDp, finalHeightDp)
                        latestOnResizeFinished(finalWidthDp, finalHeightDp)
                        latestOnResizeStateChanged(false)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (canResizeHorizontally) {
                            // カードは中央寄せのため、ハンドル移動量の2倍で幅を追従させる。
                            widthAccumulator += dragAmount.x / density.density * 2f
                            widthAccumulator = widthAccumulator.coerceIn(
                                safeMinWidthDp.toFloat(),
                                safeMaxWidthDp.toFloat(),
                            )
                            currentWidthDp = snapWidgetSize(
                                widthAccumulator.roundToInt(),
                                safeMinWidthDp,
                                safeMaxWidthDp,
                            )
                        }
                        if (canResizeVertically) {
                            heightAccumulator += dragAmount.y / density.density
                            heightAccumulator = heightAccumulator.coerceIn(
                                safeMinHeightDp.toFloat(),
                                safeMaxHeightDp.toFloat(),
                            )
                            currentHeightDp = snapWidgetSize(
                                heightAccumulator.roundToInt(),
                                safeMinHeightDp,
                                safeMaxHeightDp,
                            )
                        }
                        latestOnDraftSizeChanged(currentWidthDp, currentHeightDp)
                    },
                )
            },
    ) {
        Icon(
            imageVector = Icons.Rounded.AspectRatio,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(23.dp),
        )
    }
}

private fun snapWidgetSize(valueDp: Int, minDp: Int, maxDp: Int): Int {
    val snapped = (valueDp.toFloat() / WIDGET_SIZE_STEP_DP).roundToInt() * WIDGET_SIZE_STEP_DP
    return snapped.coerceIn(minDp, maxDp)
}

/**
 * 編集モードの操作部品。ウィジェットの中身(白い背景のことも多い)の上に載るため、
 * 透明度設定が「クリア」でも記号が読めるよう、ガラスの下に暗い下地を敷く
 * ([WidgetEditLabel] も同じ)。
 */
@Composable
private fun WidgetEditButton(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(34.dp)
            .background(
                color = if (destructive) DESTRUCTIVE_CONTROL_BACKING else EDIT_CONTROL_BACKING,
                shape = CircleShape,
            )
            .liquidGlass(
                shape = CircleShape,
                accent = if (destructive) IosDestructiveRed else null,
                highlight = 0.75f,
            ),
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = if (destructive) Color.White else GlassContentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WidgetEditLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = GlassContentColor,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
        modifier = modifier
            .background(EDIT_CONTROL_BACKING, CapsuleShape)
            .liquidGlass(CapsuleShape, highlight = 0.6f)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

private val GLASS_BUTTON_SIZE = 42.dp
private val CapsuleShape = RoundedCornerShape(50)
private val EmptyCardShape = ContinuousRoundedShape(30.dp)
private val WidgetCardGlassShape = ContinuousRoundedShape(26.dp)
private val WidgetCardClipShape = RoundedCornerShape(26.dp)
private val EDIT_CONTROL_BACKING: Color
    @Composable @ReadOnlyComposable get() = LocalOhagiColors.current.glassBase.copy(alpha = 0.94f)
private val DESTRUCTIVE_CONTROL_BACKING = Color(0xFFB42318)
private val PROMINENT_CONTROL_BACKING = Color(0xFF005AC1)

private const val WIDGET_SIZE_STEP_DP = 8
private const val FULL_WIDTH_SNAP_TOLERANCE_DP = 4
