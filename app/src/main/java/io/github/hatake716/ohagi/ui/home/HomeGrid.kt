package io.github.hatake716.ohagi.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.data.HomeItem
import io.github.hatake716.ohagi.data.LayoutState
import io.github.hatake716.ohagi.ui.common.AppIconImage
import io.github.hatake716.ohagi.ui.common.DirectoryPinIcon
import io.github.hatake716.ohagi.ui.common.FilePinIcon
import io.github.hatake716.ohagi.ui.common.IosFolderIcon
import io.github.hatake716.ohagi.ui.common.IosMotion
import io.github.hatake716.ohagi.ui.common.homeMotionKeys
import io.github.hatake716.ohagi.ui.common.onLayoutCoordinates
import io.github.hatake716.ohagi.ui.common.rememberIosDragVisualState
import io.github.hatake716.ohagi.ui.common.rememberLayoutBoundsHolder
import io.github.hatake716.ohagi.ui.common.trackLayoutBounds
import io.github.hatake716.ohagi.ui.common.uprightWithDevice
import io.github.hatake716.ohagi.ui.common.rememberAppIconBitmap
import io.github.hatake716.ohagi.ui.common.rememberFilePinThumbnail
import io.github.hatake716.ohagi.ui.common.rememberAppIconBitmaps
import io.github.hatake716.ohagi.ui.dragdrop.DragPayload
import io.github.hatake716.ohagi.ui.dragdrop.ohagiDragSource
import io.github.hatake716.ohagi.ui.dragdrop.ohagiDropTarget
import io.github.hatake716.ohagi.ui.dragdrop.rememberOhagiDropTarget

/**
 * ホーム主画面の固定グリッド。
 * 各セルが公式 Compose D&D の source/target を直接持つため、画面座標による判定は不要。
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun HomeGrid(
    home: List<HomeItem?>,
    indexOffset: Int,
    activeDrag: DragPayload?,
    labelOf: (AppRef) -> String,
    onCellTap: (Int, Rect?) -> Unit,
    onCellMenu: (Int) -> Unit,
    /**
     * グローバルindex → セルの座標の登録先。ページ跨ぎドロップの位置解決用で、矩形は
     * 使う側がドロップ時にだけ計算する。snapshot 状態の Map を渡さないこと
     * (配置のたびに書き込むため、再compositionを誘発する)。
     */
    cellCoordinates: MutableMap<Int, LayoutCoordinates>? = null,
    onDrop: (Int, DragPayload, Offset, Boolean) -> Boolean,
    canStack: (Int, DragPayload) -> Boolean,
    onDragMoved: (Offset) -> Unit,
    onDragSessionStarted: (DragPayload) -> Unit,
    onDragSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motionKeys = remember(home, indexOffset) { homeMotionKeys(home, indexOffset) }
    if (cellCoordinates != null) {
        val cellCount = home.size
        // 破棄されたページの座標を持ち続けると、切り離されたセルの LayoutNode の木
        // (アイコンの Bitmap を含む)を次に同じページが配置されるまで保持してしまう。
        DisposableEffect(cellCoordinates, indexOffset, cellCount) {
            onDispose {
                for (index in indexOffset until indexOffset + cellCount) {
                    cellCoordinates.remove(index)
                }
            }
        }
    }
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // 6行を領域いっぱいに均等配置し、最下段をドック直上にそろえる。
        val rowHeight = (maxHeight - 8.dp) / LayoutState.HOME_ROWS
        LazyVerticalGrid(
            columns = GridCells.Fixed(LayoutState.HOME_COLUMNS),
            userScrollEnabled = false,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(home, key = { index, _ -> motionKeys[index] }) { index, item ->
                val globalIndex = indexOffset + index
                HomeCell(
                    index = globalIndex,
                    item = item,
                    rowHeight = rowHeight,
                    activeDrag = activeDrag,
                    labelOf = labelOf,
                    isDragging = activeDrag == DragPayload.FromHome(globalIndex),
                    onTap = { bounds -> onCellTap(globalIndex, bounds) },
                    onMenu = { onCellMenu(globalIndex) },
                    cellCoordinates = cellCoordinates,
                    onDrop = { payload, position, stack ->
                        onDrop(globalIndex, payload, position, stack)
                    },
                    canStack = { payload -> canStack(globalIndex, payload) },
                    onDragMoved = onDragMoved,
                    onDragSessionStarted = onDragSessionStarted,
                    onDragSessionEnded = onDragSessionEnded,
                    modifier = Modifier.animateItem(
                        fadeInSpec = IosMotion.itemFadeInSpec,
                        placementSpec = IosMotion.placementSpec,
                        fadeOutSpec = IosMotion.itemFadeOutSpec,
                    ),
                )
            }
        }
    }
}

@Composable
private fun HomeCell(
    index: Int,
    item: HomeItem?,
    rowHeight: Dp,
    activeDrag: DragPayload?,
    labelOf: (AppRef) -> String,
    isDragging: Boolean,
    onTap: (Rect?) -> Unit,
    onMenu: () -> Unit,
    cellCoordinates: MutableMap<Int, LayoutCoordinates>?,
    onDrop: (DragPayload, Offset, Boolean) -> Boolean,
    canStack: (DragPayload) -> Boolean,
    onDragMoved: (Offset) -> Unit,
    onDragSessionStarted: (DragPayload) -> Unit,
    onDragSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    var dropHovered by remember { mutableStateOf(false) }
    var folderReady by remember { mutableStateOf(false) }
    val dragVisual = rememberIosDragVisualState(
        pressed = pressed,
        isDragging = isDragging,
        dropHovered = dropHovered,
        folderReady = folderReady,
        label = "homeCell",
    )
    val haptic = LocalHapticFeedback.current

    val payload = item?.let { DragPayload.FromHome(index) }
    val dragIconApp = (item as? HomeItem.HomeApp)?.app
    val appDragIcon by rememberAppIconBitmap(dragIconApp, HOME_ICON_SIZE)
    // ファイルピンのサムネイルはここで1回だけ取得し、セルの表示とドラッグ影の両方に使う。
    val pinDragThumb by rememberFilePinThumbnail(item as? HomeItem.HomeFile, HOME_ICON_SIZE)
    val dragIcon = appDragIcon ?: pinDragThumb
    val folderDragIcons by rememberAppIconBitmaps(
        (item as? HomeItem.HomeFolder)?.apps.orEmpty(),
        size = FOLDER_PREVIEW_ICON_REQUEST_SIZE,
    )

    // アイコンの矩形は起動アニメーションの起点とフォルダ化判定に使い、その時だけ計算する。
    // trackLayoutBounds は呼ぶたびに等しくない Modifier を返すため remember する
    // (等しくないと配置モディファイアの更新扱いになり、毎回再計測が走る)。
    val iconBounds = rememberLayoutBoundsHolder()
    val iconTracking = remember(iconBounds) { Modifier.trackLayoutBounds(iconBounds) }
    // 移動でセル番号が変わるとラムダが変わり、再配置時に新しい番号で登録し直される。
    val cellTracking = if (cellCoordinates == null) {
        Modifier
    } else {
        Modifier.onLayoutCoordinates { cellCoordinates[index] = it }
    }
    val stackCandidate = activeDrag?.let(canStack) == true
    val hoverColor by animateColorAsState(
        targetValue = when {
            folderReady -> Color.White.copy(alpha = 0.18f)
            dropHovered -> Color.White.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "homeDropHover",
    )
    val dropTarget = rememberOhagiDropTarget(
        onStarted = onDragSessionStarted,
        onEntered = { dropHovered = true },
        onMoved = { position ->
            folderReady = stackCandidate &&
                iconBounds.boundsInRoot()?.contains(position) == true
            onDragMoved(position)
        },
        onExited = {
            dropHovered = false
            folderReady = false
        },
        onEnded = {
            dropHovered = false
            folderReady = false
            onDragSessionEnded()
        },
        onDrop = { dropped, position ->
            dropHovered = false
            val stack = canStack(dropped) &&
                iconBounds.boundsInRoot()?.contains(position) == true
            folderReady = false
            val accepted = onDrop(dropped, position, stack)
            if (accepted) dragVisual.settle(stack)
            accepted
        },
    )

    val sourceModifier = if (payload == null) {
        // 空きセル: タップは無反応のまま、長押しでピン追加メニューを開く
        // (ファイル/フォルダの配置導線。ohagiDragSource はドラッグ対象が無いので付けない)。
        Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {},
            onLongClick = onMenu,
        )
    } else {
        Modifier.ohagiDragSource(
            payload = payload,
            icon = dragIcon,
            folderIcons = folderDragIcons,
            onTap = { onTap(iconBounds.boundsInRoot()) },
            onPressChanged = { pressed = it },
            onLift = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            onDragStarted = { onDragSessionStarted(payload) },
            onLongPressMenu = onMenu,
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(rowHeight)
            // 押下・ドラッグ時の縮小(graphicsLayer)を含まないセル全体の枠を登録する。
            .then(cellTracking)
            .graphicsLayer {
                scaleX = dragVisual.scale
                scaleY = dragVisual.scale
                alpha = dragVisual.alpha
            }
            .clip(RoundedCornerShape(18.dp))
            .background(hoverColor)
            .ohagiDropTarget(dropTarget)
            .then(sourceModifier),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp, vertical = homeCellVerticalPadding(rowHeight)),
        ) {
            HomeCellContent(
                item = item,
                icon = dragIcon,
                folderIcons = folderDragIcons,
                labelOf = labelOf,
                folderHighlighted = folderReady,
                iconModifier = iconTracking,
            )
        }

    }
}

/**
 * セルの中身（アイコン + ラベル / 空きセル）。
 * [icon] はアプリならアプリアイコン、ファイルピンならサムネイル(呼び出し側で取得済みのもの)。
 * [iconModifier] はアイコン枠の末尾に付く(位置の登録用)。
 */
@Composable
internal fun HomeCellContent(
    item: HomeItem?,
    icon: ImageBitmap?,
    folderIcons: List<ImageBitmap?>,
    labelOf: (AppRef) -> String,
    folderHighlighted: Boolean = false,
    iconModifier: Modifier = Modifier,
) {
    // 端末を横へ倒したときは、セル位置を保ったままアイコンと名称のブロックだけ立て直す。
    when (item) {
        is HomeItem.HomeApp -> UprightCellBlock {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HOME_ICON_TARGET_SIZE)
                    .then(iconModifier),
            ) {
                AppIconImage(icon = icon, size = HOME_ICON_SIZE)
            }
            Spacer(Modifier.height(4.dp))
            HomeLabel(text = labelOf(item.app))
        }
        is HomeItem.HomeFolder -> UprightCellBlock {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HOME_ICON_TARGET_SIZE)
                    .then(iconModifier),
            ) {
                IosFolderIcon(
                    apps = item.apps,
                    preloadedIcons = folderIcons,
                    size = HOME_ICON_SIZE,
                    highlighted = folderHighlighted,
                )
            }
            Spacer(Modifier.height(4.dp))
            HomeLabel(text = item.name)
        }
        is HomeItem.HomeFile -> UprightCellBlock {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HOME_ICON_TARGET_SIZE)
                    .then(iconModifier),
            ) {
                FilePinIcon(
                    mimeType = item.mimeType,
                    size = HOME_ICON_SIZE,
                    thumbnail = icon,
                )
            }
            Spacer(Modifier.height(4.dp))
            HomeLabel(text = item.displayName)
        }
        is HomeItem.HomeDirectory -> UprightCellBlock {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(HOME_ICON_TARGET_SIZE)
                    .then(iconModifier),
            ) {
                DirectoryPinIcon(size = HOME_ICON_SIZE)
            }
            Spacer(Modifier.height(4.dp))
            HomeLabel(text = item.displayName)
        }
        null -> {
            Box(
                Modifier
                    .size(HOME_ICON_TARGET_SIZE)
                    .then(iconModifier),
            )
            Spacer(Modifier.height(4.dp))
            HomeLabel(text = "")
        }
    }
}

/** iPhoneの4列ホームに近い、アイコン本体と隣接余白の視覚比率。 */
private val HOME_ICON_SIZE = 60.dp
private val HOME_ICON_TARGET_SIZE = 68.dp
private val FOLDER_PREVIEW_ICON_REQUEST_SIZE = 24.dp

/** アイコン枠 + 間隔 + ラベル1行(lineHeight 14sp)の高さ。 */
private val HOME_CELL_CONTENT_HEIGHT = HOME_ICON_TARGET_SIZE + 4.dp + 14.dp
private val HOME_CELL_MAX_VERTICAL_PADDING = 8.dp

/**
 * 背の低い画面(3ボタンナビ等)では行の高さが中身に足りなくなるので、上下の余白から
 * 削ってラベルの1行を残す。
 */
private fun homeCellVerticalPadding(rowHeight: Dp): Dp =
    ((rowHeight - HOME_CELL_CONTENT_HEIGHT) / 2).coerceIn(0.dp, HOME_CELL_MAX_VERTICAL_PADDING)

/** アイコン+名称を端末の向きへ立て直す共通ブロック(セル位置は不変)。 */
@Composable
private fun UprightCellBlock(content: @Composable ColumnScope.() -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.uprightWithDevice(),
        content = content,
    )
}

/** iOS ホームと同じ白い名称 + ごく弱い影のラベル。空文字なら高さだけ確保。 */
@Composable
private fun HomeLabel(text: String) {
    androidx.compose.material3.Text(
        text = text,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        color = Color.White,
        style = HomeLabelStyle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 16.dp),
    )
}

// 明るい壁紙でも名称が消えない最低限の影。セルごとに TextStyle を作り直さない。
private val HomeLabelStyle = androidx.compose.ui.text.TextStyle(
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 1f),
        blurRadius = 4f,
    ),
)
