package io.github.hatake716.ohagi.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppCategory
import io.github.hatake716.ohagi.data.AppInfo
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.ui.common.AppIcon
import io.github.hatake716.ohagi.ui.common.AppIconImage
import io.github.hatake716.ohagi.ui.common.AppearanceSheet
import io.github.hatake716.ohagi.ui.common.CategorizedAppBrowser
import io.github.hatake716.ohagi.ui.common.GlassContentColor
import io.github.hatake716.ohagi.ui.common.IosGlassIconButton
import io.github.hatake716.ohagi.ui.common.IosMotion
import io.github.hatake716.ohagi.ui.common.IosSearchField
import io.github.hatake716.ohagi.ui.common.MenuEntry
import io.github.hatake716.ohagi.ui.common.MenuSheet
import io.github.hatake716.ohagi.ui.common.appCategoryTitle
import io.github.hatake716.ohagi.ui.common.rememberAppIconBitmap
import io.github.hatake716.ohagi.ui.common.rememberIosDragVisualState
import io.github.hatake716.ohagi.ui.common.rememberLayoutBoundsHolder
import io.github.hatake716.ohagi.ui.common.rememberSearchFieldFocusReleaser
import io.github.hatake716.ohagi.ui.common.trackLayoutBounds
import io.github.hatake716.ohagi.ui.common.wallpaperPageBackdrop
import io.github.hatake716.ohagi.ui.dragdrop.DragPayload
import io.github.hatake716.ohagi.ui.dragdrop.ohagiDragSource
import io.github.hatake716.ohagi.ui.theme.Ink
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

/**
 * アプリドロワー(iOS の Appライブラリ)。ホームのPagerの最後のページとして表示される。
 * 自動カテゴリーからアプリを選び、カテゴリー内ではタップ起動・長押しD&D・メニュー操作を行う。
 */
@Composable
fun AppDrawer(
    apps: List<AppInfo>,
    frequentApps: List<AppRef>,
    preferredApps: List<AppRef>,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAddToDock: (AppInfo) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
    onOpenDefaultHome: () -> Unit,
    /** 公式D&D開始後にドロワーを閉じ、背後のdrop targetを露出する。 */
    onDragStarted: (DragPayload) -> Unit,
    /** iOS と同じく閉じるボタンは置かない。ホスト側の戻る操作との互換のため残している。 */
    @Suppress("UNUSED_PARAMETER") onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** ホームの「検索」ボタンが押されるたびに増える。0 より大きい変化で検索欄へフォーカスする。 */
    focusSearchSignal: Int = 0,
) {
    var query by remember { mutableStateOf("") }
    var menuTarget by remember { mutableStateOf<AppInfo?>(null) }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    // 戻る時の縮むアニメーション中も見出しを保つため、最後に開いたカテゴリーを覚える。
    var titleCategory by remember { mutableStateOf<AppCategory?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val searchFocus = rememberSearchFieldFocusReleaser(query)
    val keyboardController = LocalSoftwareKeyboardController.current
    // ホーム側はこのページがcompose済みになってから値を増やす。初回compose時の値は
    // 過去の合図なので反応せず、このページが表示中に起きた変化だけを拾う。
    var lastFocusSignal by remember { mutableIntStateOf(focusSearchSignal) }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    LaunchedEffect(focusSearchSignal) {
        if (focusSearchSignal == lastFocusSignal) return@LaunchedEffect
        lastFocusSignal = focusSearchSignal
        if (focusSearchSignal <= 0) return@LaunchedEffect
        selectedCategory = null
        searchFocusRequester.requestFocus()
        keyboardController?.show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // 壁紙は別ウィンドウなのでぼかせない。iOS の Appライブラリの暗い曇りに寄せ、
            // カードの下のカテゴリー名が壁紙の柄に負けない濃さにする。
            .wallpaperPageBackdrop()
            // ステータスバー/ナビバー/IME/ディスプレイカットアウトすべてを避ける
            .safeDrawingPadding()
    ) {
        // 上部: iOS の Appライブラリと同じ検索カプセル + 設定ボタン。
        // 入力でカテゴリー選択が外れても検索欄が作り直されて(フォーカスを失わ)ないよう、
        // 表示状態によらず常に同じ位置に置く。
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 10.dp, bottom = 6.dp)
        ) {
            IosSearchField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isNotBlank()) selectedCategory = null
                },
                placeholder = stringResource(R.string.drawer_title),
                clearContentDescription = stringResource(R.string.action_clear_search),
                focusRequester = searchFocusRequester,
                modifier = Modifier
                    .weight(1f)
                    .then(searchFocus.modifier),
            )
            Spacer(Modifier.width(10.dp))
            IosGlassIconButton(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.settings_title),
                onClick = { showSettings = true },
                size = 44.dp,
            )
        }

        // カテゴリー詳細: 戻るボタン + カテゴリー名
        AnimatedVisibility(
            visible = selectedCategory != null,
            enter = fadeIn(IosMotion.itemFadeInSpec) + expandVertically(
                animationSpec = tween(IosMotion.STANDARD_FADE_MS, easing = IosMotion.easeOut),
                expandFrom = Alignment.Top,
            ),
            exit = fadeOut(IosMotion.itemFadeOutSpec) + shrinkVertically(
                animationSpec = tween(IosMotion.QUICK_FADE_MS, easing = IosMotion.easeIn),
                shrinkTowards = Alignment.Top,
            ),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 20.dp, top = 4.dp, bottom = 2.dp),
            ) {
                IosGlassIconButton(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.category_back),
                    onClick = { selectedCategory = null },
                    size = 40.dp,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = titleCategory?.let { appCategoryTitle(it) }.orEmpty(),
                    color = GlassContentColor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        CategorizedAppBrowser(
            apps = apps,
            query = query,
            selectedCategory = selectedCategory,
            onCategorySelected = { category ->
                selectedCategory = category
                if (category != null) {
                    titleCategory = category
                    searchFocus.release()
                }
            },
            onPreviewAppClick = onLaunch,
            frequentApps = frequentApps,
            preferredApps = preferredApps,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { app ->
            DrawerCell(
                app = app,
                onTap = { bounds -> onLaunch(app, bounds) },
                onMenu = { menuTarget = app },
                onDragStarted = onDragStarted,
            )
        }
    }

    // セルの長押し(動かさず離す)で開くメニュー
    menuTarget?.let { app ->
        MenuSheet(
            entries = listOf(
                MenuEntry(stringResource(R.string.drawer_add_to_dock), Icons.Rounded.Dock) {
                    onAddToDock(app)
                },
                MenuEntry(stringResource(R.string.action_app_info), Icons.Rounded.Info) {
                    onAppInfo(app)
                },
                MenuEntry(
                    stringResource(R.string.action_uninstall),
                    Icons.Rounded.Delete,
                    destructive = true,
                ) {
                    onUninstall(app)
                },
            ),
            onDismiss = { menuTarget = null },
            header = { DrawerMenuHeader(app) },
        )
    }

    if (showSettings) {
        AppearanceSheet(
            onOpenDefaultHome = onOpenDefaultHome,
            onDismiss = { showSettings = false },
        )
    }
}

/** ドロワーの1セル。長押しでリフトし、動かすとD&D・動かさず離すとメニュー(iOS風)。 */
@Composable
private fun DrawerCell(
    app: AppInfo,
    onTap: (Rect?) -> Unit,
    onMenu: () -> Unit,
    onDragStarted: (DragPayload) -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    val iconBounds = rememberLayoutBoundsHolder()
    val dragVisual = rememberIosDragVisualState(
        pressed = pressed,
        isDragging = isDragging,
        label = "drawerCell",
    )
    val haptic = LocalHapticFeedback.current

    val icon by rememberAppIconBitmap(app.ref, DRAWER_ICON_SIZE)
    val payload = remember(app.ref) { DragPayload.FromDrawer(app.ref) }

    // 押下の波紋は出さないのでclipは不要。セルごとのレイヤーはgraphicsLayerの1枚だけにする。
    Box(
        modifier = Modifier
            .graphicsLayer {
                scaleX = dragVisual.scale
                scaleY = dragVisual.scale
                alpha = dragVisual.alpha
            }
            .ohagiDragSource(
                payload = payload,
                icon = icon,
                onTap = { onTap(iconBounds.boundsInRoot()) },
                onPressChanged = {
                    pressed = it
                    if (!it) isDragging = false
                },
                onLift = {
                    isDragging = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                },
                onDragStarted = { onDragStarted(payload) },
                onLongPressMenu = onMenu,
            )
            .padding(horizontal = 4.dp, vertical = 10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(DRAWER_ICON_SIZE)
                    .trackLayoutBounds(iconBounds),
            ) {
                AppIconImage(icon = icon, size = DRAWER_ICON_SIZE)
            }
            Spacer(Modifier.height(6.dp))
            // iOS ホーム風: アイコン下に最大 2 行のラベル。
            Text(
                text = app.label,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                letterSpacing = 0.1.sp,
                color = GlassContentColor,
                style = if (LocalOhagiColors.current.isDark) DrawerLabelStyle else TextStyle.Default,
                maxLines = 2,
                minLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 30.dp),
            )
        }
    }
}

private val DRAWER_ICON_SIZE = 56.dp

private val DrawerLabelStyle = TextStyle(
    shadow = Shadow(color = Ink, offset = Offset(0f, 1f), blurRadius = 4f),
)

/** セル操作メニューのヘッダー（アイコン + ラベル）。 */
@Composable
private fun DrawerMenuHeader(app: AppInfo) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        AppIcon(app = app.ref, size = 36.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = app.label,
            color = GlassContentColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
