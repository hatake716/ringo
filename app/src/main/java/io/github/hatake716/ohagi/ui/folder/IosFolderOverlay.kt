package io.github.hatake716.ohagi.ui.folder

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.data.FolderLocation
import io.github.hatake716.ohagi.ui.common.AppIconImage
import io.github.hatake716.ohagi.ui.common.ContinuousRoundedShape
import io.github.hatake716.ohagi.ui.common.continuousShadowShape
import io.github.hatake716.ohagi.ui.common.GlassContentColor
import io.github.hatake716.ohagi.ui.common.GlassSecondaryContentColor
import io.github.hatake716.ohagi.ui.common.GlassTone
import io.github.hatake716.ohagi.ui.common.IosGlassIconButton
import io.github.hatake716.ohagi.ui.common.IosMotion
import io.github.hatake716.ohagi.ui.common.IosSystemBlue
import io.github.hatake716.ohagi.ui.common.LocalGlassAppearance
import io.github.hatake716.ohagi.ui.common.modalScrimColor
import io.github.hatake716.ohagi.ui.common.folderMotionKeys
import io.github.hatake716.ohagi.ui.common.iosPageDistance
import io.github.hatake716.ohagi.ui.common.liquidGlass
import io.github.hatake716.ohagi.ui.common.rememberAppIconBitmap
import io.github.hatake716.ohagi.ui.common.rememberBackdropBlurSupported
import io.github.hatake716.ohagi.ui.common.rememberIosDragVisualState
import io.github.hatake716.ohagi.ui.common.rememberIosPressScale
import io.github.hatake716.ohagi.ui.common.rememberLayoutBoundsHolder
import io.github.hatake716.ohagi.ui.common.trackLayoutBounds
import io.github.hatake716.ohagi.ui.dragdrop.DragPayload
import io.github.hatake716.ohagi.ui.dragdrop.folderLocationOrNull
import io.github.hatake716.ohagi.ui.common.animatedUprightRotationState
import io.github.hatake716.ohagi.ui.common.uprightWithDevice
import io.github.hatake716.ohagi.ui.dragdrop.ohagiDragSource
import io.github.hatake716.ohagi.ui.dragdrop.ohagiDropTarget
import io.github.hatake716.ohagi.ui.dragdrop.rememberOhagiDropTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

private const val APPS_PER_FOLDER_PAGE = 9

/**
 * ホームとDockで共用するiOS風フォルダ。
 *
 * - 中央の Liquid Glass パネル
 * - 1ページ3×3、複数ページとページドット
 * - 長押しD&Dによる並べ替えとフォルダ外への移動
 * - 編集モードの揺れと削除ボタン
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IosFolderOverlay(
    location: FolderLocation,
    sourceBounds: Rect?,
    folderName: String,
    apps: List<AppRef>,
    labelOf: (AppRef) -> String,
    activeDrag: DragPayload?,
    onLaunch: (AppRef, Rect?) -> Unit,
    onAddApps: () -> Unit,
    onRemoveApp: (AppRef) -> Unit,
    onRename: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDragMoved: (Offset) -> Unit,
    onDragStarted: (DragPayload) -> Unit,
    onDragEnded: () -> Unit,
    onDragOutside: () -> Unit,
    onDismiss: () -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    // パネルの画面上の矩形は、ドラッグの外出し判定と開閉アニメーションの瞬間にだけ求める。
    val panelBounds = rememberLayoutBoundsHolder()
    // ヘッダ移動の計算にはレイヤー変換(横画面時のPortraitStage回転)の影響を受けない
    // レイアウト寸法を使う。boundsInRoot は変換後のAABBなので横画面では使えない。
    var panelLayoutSize by remember { mutableStateOf(IntSize.Zero) }
    var dragOutRequested by remember { mutableStateOf(false) }
    var sessionPayload by remember { mutableStateOf<DragPayload?>(null) }
    var closing by remember(location) { mutableStateOf(false) }
    val reveal = remember(location) { Animatable(0f) }
    val scope = rememberCoroutineScope()

    val pageCount = max(1, (apps.size + APPS_PER_FOLDER_PAGE - 1) / APPS_PER_FOLDER_PAGE)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val pageSnapSpec = remember {
        spring<Float>(
            dampingRatio = IosMotion.PAGE_SNAP_DAMPING,
            stiffness = IosMotion.PAGE_SNAP_STIFFNESS,
        )
    }
    val pagerFlingBehavior = PagerDefaults.flingBehavior(
        state = pagerState,
        snapAnimationSpec = pageSnapSpec,
        snapPositionalThreshold = IosMotion.PAGE_POSITIONAL_THRESHOLD,
    )
    LaunchedEffect(location) {
        // 開き始めの縮小率と位置はパネルの矩形から求めるので、パネルが測られるまで待つ。
        snapshotFlow { panelLayoutSize != IntSize.Zero }.first { it }
        if (!closing && reveal.value == 0f) {
            reveal.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = IosMotion.FOLDER_OPEN_DAMPING,
                    stiffness = IosMotion.FOLDER_OPEN_STIFFNESS,
                ),
            )
        }
    }
    LaunchedEffect(pageCount) {
        if (pagerState.currentPage >= pageCount) {
            pagerState.scrollToPage(pageCount - 1)
        }
    }
    LaunchedEffect(activeDrag) {
        if (activeDrag == null) {
            dragOutRequested = false
            sessionPayload = null
        }
    }

    fun isFromThisFolder(payload: DragPayload?): Boolean =
        payload?.folderLocationOrNull() == location

    fun handleDragMoved(position: Offset) {
        onDragMoved(position)
        if (dragOutRequested || !isFromThisFolder(sessionPayload ?: activeDrag)) return
        val bounds = panelBounds.boundsInRoot() ?: return
        if (!bounds.contains(position)) {
            dragOutRequested = true
            onDragOutside()
        }
    }

    fun handleDragStarted(payload: DragPayload) {
        // HomeScreen側の再構成を待たず、このOSセッションの開始payloadを即時保持する。
        sessionPayload = payload
        onDragStarted(payload)
    }

    fun handleDragEnded() {
        sessionPayload = null
        onDragEnded()
    }

    fun requestDismiss() {
        if (closing) return
        closing = true
        scope.launch {
            reveal.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = IosMotion.FOLDER_CLOSE_DAMPING,
                    stiffness = IosMotion.FOLDER_CLOSE_STIFFNESS,
                ),
            )
            onDismiss()
        }
    }

    BackHandler(enabled = !closing) { requestDismiss() }

    val backgroundDropTarget = rememberOhagiDropTarget(
        onStarted = ::handleDragStarted,
        onMoved = ::handleDragMoved,
        onEnded = ::handleDragEnded,
        onDrop = { _, _ -> false },
    )
    val contentColor = GlassContentColor
    // 背面ぼかしが効く端末ではHomeScreen側がホームをぼかすので、暗転は控えめにする。
    val backdropBlurred = rememberBackdropBlurSupported()
    val appearance = LocalGlassAppearance.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val scrim = modalScrimColor(appearance.tint, backdropBlurred)
                drawRect(scrim.copy(alpha = scrim.alpha * reveal.value))
            }
            .ohagiDropTarget(backgroundDropTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = ::requestDismiss,
            )
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 30.dp),
    ) {
        val panelShape = FOLDER_PANEL_SHAPE
        Box(
            modifier = Modifier
                .widthIn(max = 390.dp)
                .fillMaxWidth()
                .heightIn(min = 430.dp, max = 520.dp)
                .trackLayoutBounds(panelBounds)
                .onSizeChanged { panelLayoutSize = it },
        ) {
            // 回転角はレイアウト/描画フェーズでだけ読み、回転アニメーション中に
            // パネル全体を毎フレーム再compositionしない。
            val uprightRotation = animatedUprightRotationState()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    // 横画面時はパネル背景だけを物理の上下(=このレイアウト座標の左右)へ広げ、
                    // 物理最下段のアイコン名称が背景の縁で切れないようにする。
                    // コンテンツ(グリッド等)の幅は同量の水平paddingで据え置く。
                    .panelUprightWidth(uprightRotation)
                    .graphicsLayer {
                        val progress = reveal.value.coerceIn(0f, 1f)
                        // この層は開閉アニメーション中だけ再実行されるので、その都度求める。
                        val finalBounds = panelBounds.boundsInRoot()
                        val originBounds = sourceBounds
                        val startScale = if (
                            finalBounds != null && originBounds != null && finalBounds.width > 0f
                        ) {
                            (originBounds.width / finalBounds.width).coerceIn(0.16f, 0.82f)
                        } else {
                            0.82f
                        }
                        val startTranslationX = if (finalBounds != null && originBounds != null) {
                            originBounds.center.x - finalBounds.center.x
                        } else {
                            0f
                        }
                        val startTranslationY = if (finalBounds != null && originBounds != null) {
                            originBounds.center.y - finalBounds.center.y
                        } else {
                            0f
                        }
                        alpha = (reveal.value * 1.35f).coerceIn(0f, 1f)
                        val scale = startScale + (1f - startScale) * progress
                        scaleX = scale
                        scaleY = scale
                        translationX = startTranslationX * (1f - progress)
                        translationY = startTranslationY * (1f - progress)
                        // 影も同じレイヤーで落とし、パネルのレイヤーを1枚にする。
                        // 中身(横画面時のヘッダ移動・グリッドのシフトを含む)は角の曲線より
                        // 内側に収まるためクリップしない。クリップするとガラスの暗い縁の
                        // 外側半分が欠ける。
                        shadowElevation = FOLDER_PANEL_ELEVATION.toPx()
                        shape = FOLDER_PANEL_SHADOW_SHAPE
                        clip = false
                        ambientShadowColor = FOLDER_PANEL_AMBIENT_SHADOW
                        spotShadowColor = FOLDER_PANEL_SPOT_SHADOW
                    }
                // panelUprightWidthで広げた幅(横画面時)の全体にガラスを描く。
                .liquidGlass(panelShape, GlassTone.Regular)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {}
                .padding(top = 10.dp, bottom = 14.dp)
                .panelUprightPadding(uprightRotation),
            ) {
                // 端末を横へ倒したときは、タイトル/編集/追加のヘッダ行をパネルの
                // 「ユーザーから見て上」= 左右どちらかの辺へ回転しながら移す。
                // レイアウト位置は変えず(パネル寸法もグリッドも不変)、描画変換だけで
                // 辺の内側中央へ移動する。ヒットテストは変換に追従するため操作も可能。
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            val rotation = uprightRotation.value
                            rotationZ = rotation
                            val panel = panelLayoutSize
                            if (panel != IntSize.Zero && rotation != 0f) {
                                val margin = 4.dp.toPx()
                                val headerTop = 10.dp.toPx()
                                val edgeShift =
                                    (panel.width / 2f - size.height / 2f - margin)
                                        .coerceAtLeast(0f)
                                val downShift =
                                    (panel.height / 2f - headerTop - size.height / 2f)
                                        .coerceAtLeast(0f)
                                // +90(ユーザーの上=画面右)なら右辺へ、-90なら左辺へ。
                                val fraction = rotation / 90f
                                translationX = fraction * edgeShift
                                translationY = kotlin.math.abs(fraction) * downShift
                            }
                        }
                        // 最小タップ領域と同じ48dpを保ち、グリッドの縦位置を変えない。
                        .heightIn(min = 48.dp)
                        .padding(horizontal = 12.dp),
                ) {
                FolderGlassCapsuleButton(
                    text = stringResource(
                        if (editMode) R.string.action_done else R.string.folder_edit,
                    ),
                    prominent = editMode,
                    onClick = { editMode = !editMode },
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRename,
                        )
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = folderName,
                        color = GlassContentColor,
                        fontSize = 22.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        // 長い名前でも鉛筆アイコンを押し出さないよう、先に残り幅を確保させる。
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(5.dp))
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = stringResource(R.string.action_rename),
                        tint = GlassSecondaryContentColor,
                        modifier = Modifier.size(16.dp),
                    )
                }

                IosGlassIconButton(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.folder_add_apps),
                    onClick = onAddApps,
                    size = FOLDER_HEADER_BUTTON_HEIGHT,
                )
            }

            HorizontalPager(
                state = pagerState,
                flingBehavior = pagerFlingBehavior,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(342.dp)
                    .graphicsLayer {
                        // 横画面時はヘッダ(物理では上=パネルの側辺)と3列目のアイコンが
                        // 重ならないよう、グリッド全体を物理の下方向へ少しずらす。
                        translationX =
                            -(uprightRotation.value / 90f) * FOLDER_GRID_UPRIGHT_SHIFT.toPx()
                    },
            ) { page ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val distance = iosPageDistance(
                                currentPage = pagerState.currentPage,
                                currentPageOffsetFraction = pagerState.currentPageOffsetFraction,
                                page = page,
                            )
                            val centered = 1f - distance
                            val scale = 0.975f + 0.025f * centered
                            scaleX = scale
                            scaleY = scale
                            alpha = 0.88f + 0.12f * centered
                        },
                ) {
                    FolderPage(
                        location = location,
                        page = page,
                        apps = apps,
                        labelOf = labelOf,
                        activeDrag = activeDrag,
                        editMode = editMode,
                        onLaunch = onLaunch,
                        onRemoveApp = onRemoveApp,
                        onReorder = onReorder,
                        onDragMoved = ::handleDragMoved,
                        onDragStarted = ::handleDragStarted,
                        onDragEnded = ::handleDragEnded,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(20.dp),
            ) {
                repeat(pageCount) { page ->
                    // ドットごとのレイヤーを作らず、ページ位置は描画フェーズで読む。
                    Box(
                        Modifier
                            .size(7.dp)
                            .drawBehind {
                                val pagePosition = pagerState.currentPage +
                                    pagerState.currentPageOffsetFraction
                                val proximity = 1f -
                                    kotlin.math.abs(page - pagePosition).coerceIn(0f, 1f)
                                drawCircle(
                                    color = contentColor,
                                    radius = size.minDimension / 2f * (0.86f + 0.14f * proximity),
                                    alpha = 0.30f + 0.65f * proximity,
                                )
                            },
                    )
                }
            }
        }
    }
}
}

@Composable
private fun FolderPage(
    location: FolderLocation,
    page: Int,
    apps: List<AppRef>,
    labelOf: (AppRef) -> String,
    activeDrag: DragPayload?,
    editMode: Boolean,
    onLaunch: (AppRef, Rect?) -> Unit,
    onRemoveApp: (AppRef) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDragMoved: (Offset) -> Unit,
    onDragStarted: (DragPayload) -> Unit,
    onDragEnded: () -> Unit,
) {
    val indexOffset = page * APPS_PER_FOLDER_PAGE
    val pageApps = remember(apps, page) {
        List<AppRef?>(APPS_PER_FOLDER_PAGE) { localIndex ->
            apps.getOrNull(indexOffset + localIndex)
        }
    }
    val motionKeys = remember(pageApps, indexOffset) {
        folderMotionKeys(pageApps, indexOffset)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxSize(),
    ) {
        itemsIndexed(
            items = pageApps,
            key = { localIndex, _ -> motionKeys[localIndex] },
        ) { localIndex, app ->
            val globalIndex = indexOffset + localIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .animateItem(
                        fadeInSpec = IosMotion.itemFadeInSpec,
                        placementSpec = IosMotion.placementSpec,
                        fadeOutSpec = IosMotion.itemFadeOutSpec,
                    ),
            ) {
                if (app != null) {
                    FolderAppCell(
                        location = location,
                        app = app,
                        appIndex = globalIndex,
                        labelOf = labelOf,
                        activeDrag = activeDrag,
                        editMode = editMode,
                        onLaunch = { bounds -> onLaunch(app, bounds) },
                        onRemove = { onRemoveApp(app) },
                        onReorder = onReorder,
                        onDragMoved = onDragMoved,
                        onDragStarted = onDragStarted,
                        onDragEnded = onDragEnded,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderAppCell(
    location: FolderLocation,
    app: AppRef,
    appIndex: Int,
    labelOf: (AppRef) -> String,
    activeDrag: DragPayload?,
    editMode: Boolean,
    onLaunch: (Rect?) -> Unit,
    onRemove: () -> Unit,
    onReorder: (Int, Int) -> Unit,
    onDragMoved: (Offset) -> Unit,
    onDragStarted: (DragPayload) -> Unit,
    onDragEnded: () -> Unit,
) {
    val label = labelOf(app)
    var pressed by remember { mutableStateOf(false) }
    var dropHovered by remember { mutableStateOf(false) }
    // 起動アニメーション元の矩形はタップの瞬間にだけ求める。
    val iconBounds = rememberLayoutBoundsHolder()
    val haptic = LocalHapticFeedback.current
    val icon by rememberAppIconBitmap(app)
    val payload = remember(location, appIndex, app) {
        when (location) {
            is FolderLocation.Home ->
                DragPayload.FromHomeFolder(location.index, appIndex, app)
            is FolderLocation.Dock ->
                DragPayload.FromDockFolder(location.slot, appIndex, app)
        }
    }
    val isDragging = activeDrag == payload

    val dragVisual = rememberIosDragVisualState(
        pressed = pressed,
        isDragging = isDragging,
        dropHovered = dropHovered,
        label = "folderApp",
    )
    // 通常表示中は無限アニメーションをcompositionから外し、編集時だけ揺らす。
    val wiggle = if (editMode) {
        val infiniteTransition = rememberInfiniteTransition(label = "folderWiggle")
        infiniteTransition.animateFloat(
            initialValue = -1.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 135 + (appIndex % 3) * 12),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "folderWiggleAngle",
        )
    } else {
        null
    }

    val dropTarget = rememberOhagiDropTarget(
        onStarted = onDragStarted,
        onEntered = { dropHovered = true },
        onMoved = onDragMoved,
        onExited = { dropHovered = false },
        onEnded = {
            dropHovered = false
            onDragEnded()
        },
        onDrop = { dropped, _ ->
            dropHovered = false
            val fromIndex = when (dropped) {
                is DragPayload.FromHomeFolder ->
                    if (location == FolderLocation.Home(dropped.index)) dropped.appIndex else null
                is DragPayload.FromDockFolder ->
                    if (location == FolderLocation.Dock(dropped.slot)) dropped.appIndex else null
                else -> null
            } ?: return@rememberOhagiDropTarget false
            onReorder(fromIndex, appIndex)
            dragVisual.settle(false)
            true
        },
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(92.dp)
            .graphicsLayer {
                scaleX = dragVisual.scale
                scaleY = dragVisual.scale
                alpha = dragVisual.alpha
                rotationZ = wiggle?.value ?: 0f
                // 角丸クリップも同じレイヤーで行い、セルごとのレイヤーを1枚に保つ。
                shape = FOLDER_CELL_SHAPE
                clip = true
            }
            .ohagiDropTarget(dropTarget)
            .ohagiDragSource(
                payload = payload,
                icon = icon,
                onTap = { if (!editMode) onLaunch(iconBounds.boundsInRoot()) },
                onPressChanged = { pressed = it },
                onDragStarted = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDragStarted(payload)
                },
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
            // 端末を横へ倒したときは、フォルダ内のセル位置を保ったまま
            // アイコンと名称だけ立て直す(ヒット領域はレイアウト位置のまま)。
            .uprightWithDevice(),
    ) {
        Box(
            modifier = Modifier.trackLayoutBounds(iconBounds),
        ) {
            AppIconImage(icon = icon, size = 58.dp)
            if (editMode) {
                // iOS 26 の明るいガラスの小円。アイコンの色に負けないよう下地を敷き、
                // クリップ(レイヤー)は使わず描画だけで作る。
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(22.dp)
                        .background(FOLDER_REMOVE_BADGE_BASE, CircleShape)
                        .liquidGlass(CircleShape, GlassTone.Light, highlight = 0.7f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onRemove,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.folder_remove_app, label),
                        tint = FOLDER_REMOVE_BADGE_SYMBOL,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = GlassContentColor,
            style = MaterialTheme.typography.labelMedium,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * ヘッダの「編集」/「完了」。iOS 26 のガラスのカプセルで、編集中は
 * 塗りつぶしの青いガラス(プロミネント)にして終了操作を目立たせる。
 */
@Composable
private fun FolderGlassCapsuleButton(
    text: String,
    prominent: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale = rememberIosPressScale(
        pressed = pressed,
        pressedScale = 0.92f,
        label = "folderEditButtonScale",
    )
    val shape = FOLDER_CAPSULE_SHAPE
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(FOLDER_HEADER_BUTTON_HEIGHT)
            .widthIn(min = 64.dp)
            .graphicsLayer {
                scaleX = pressScale.value
                scaleY = pressScale.value
            }
            .then(
                if (prominent) Modifier.background(IosSystemBlue, shape) else Modifier,
            )
            .liquidGlass(
                shape = shape,
                tone = GlassTone.Regular,
                accent = if (prominent) IosSystemBlue else null,
                highlight = 0.85f,
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp),
    ) {
        Text(
            text = text,
            color = GlassContentColor,
            fontSize = 15.sp,
            fontWeight = if (prominent) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

private val FOLDER_PANEL_SHAPE = ContinuousRoundedShape(40.dp)
private val FOLDER_PANEL_SHADOW_SHAPE = continuousShadowShape(40.dp)
private val FOLDER_CAPSULE_SHAPE = RoundedCornerShape(50)
private val FOLDER_CELL_SHAPE = RoundedCornerShape(18.dp)
private val FOLDER_HEADER_BUTTON_HEIGHT = 36.dp

private val FOLDER_PANEL_ELEVATION = 26.dp
private val FOLDER_PANEL_AMBIENT_SHADOW = Color.Black.copy(alpha = 0.30f)
private val FOLDER_PANEL_SPOT_SHADOW = Color.Black.copy(alpha = 0.40f)

/** 削除バッジの下地。ガラスの透け具合に関係なく「−」を読めるようにする。 */
private val FOLDER_REMOVE_BADGE_BASE = Color(0xC7E5E5EA)
private val FOLDER_REMOVE_BADGE_SYMBOL = Color(0xFF1C1C1E)

/** 横画面時にフォルダグリッドをヘッダから離す物理下方向へのシフト量。 */
private val FOLDER_GRID_UPRIGHT_SHIFT = 28.dp

/** 横画面時にパネル背景を物理の上下(それぞれ)へ広げる量。名称の見切れ防止。 */
private val FOLDER_PANEL_UPRIGHT_EXTRA = 34.dp

private fun panelUprightExtra(rotation: Float): Dp =
    FOLDER_PANEL_UPRIGHT_EXTRA * (kotlin.math.abs(rotation) / 90f)

/**
 * requiredWidth(親の最大幅 + 両側の拡張分) と同じ丸め・同じ配置で測る。
 * 拡張分は回転角で変わるので、角度をこの測定ブロックの中で読む。
 */
private fun Modifier.panelUprightWidth(rotation: State<Float>): Modifier =
    layout { measurable, constraints ->
        val extra = panelUprightExtra(rotation.value)
        val width = (constraints.maxWidth.toDp() + extra * 2).roundToPx().coerceAtLeast(0)
        val placeable = measurable.measure(
            Constraints(
                minWidth = width,
                maxWidth = width,
                minHeight = constraints.minHeight,
                maxHeight = constraints.maxHeight,
            ),
        )
        layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
    }

/** padding(horizontal = 拡張分) と同じ測り方。[panelUprightWidth] で広げた分を中身から戻す。 */
private fun Modifier.panelUprightPadding(rotation: State<Float>): Modifier =
    layout { measurable, constraints ->
        val inset = panelUprightExtra(rotation.value).roundToPx()
        val placeable = measurable.measure(constraints.offset(horizontal = -inset * 2))
        layout(
            constraints.constrainWidth(placeable.width + inset * 2),
            constraints.constrainHeight(placeable.height),
        ) { placeable.placeRelative(inset, 0) }
    }
