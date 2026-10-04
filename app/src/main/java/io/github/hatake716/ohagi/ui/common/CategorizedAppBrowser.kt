package io.github.hatake716.ohagi.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppCategory
import io.github.hatake716.ohagi.data.AppIconRequest
import io.github.hatake716.ohagi.data.AppInfo
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

private sealed interface AppBrowserMode {
    data object Overview : AppBrowserMode
    data object Search : AppBrowserMode
    data class Category(val category: AppCategory) : AppBrowserMode
}

/**
 * 通常ドロワーと全アプリピッカーで共有する、iOS App Library 風ブラウザー。
 *
 * - 通常時: よく使うアプリの独立カード + 自動カテゴリーカード(縦画面は2列、幅に応じて増やす)
 * - カテゴリー選択時: そのカテゴリーだけのグリッド(縦画面は4列)
 * - 検索時: 全カテゴリー横断のグリッド(縦画面は4列)
 */
@Composable
fun CategorizedAppBrowser(
    apps: List<AppInfo>,
    query: String,
    selectedCategory: AppCategory?,
    onCategorySelected: (AppCategory?) -> Unit,
    onPreviewAppClick: (AppInfo, Rect?) -> Unit,
    modifier: Modifier = Modifier,
    frequentApps: List<AppRef> = emptyList(),
    preferredApps: List<AppRef> = emptyList(),
    selectedApps: Set<AppRef> = emptySet(),
    appCell: @Composable (AppInfo) -> Unit,
) {
    val matchingApps = remember(apps, query) {
        val normalized = query.trim()
        if (normalized.isEmpty()) {
            apps
        } else {
            apps.filter { app ->
                app.label.contains(normalized, ignoreCase = true) ||
                    app.ref.packageName.contains(normalized, ignoreCase = true)
            }
        }
    }

    val browserMode = remember(query.isNotBlank(), selectedCategory) {
        when {
            query.isNotBlank() -> AppBrowserMode.Search
            selectedCategory != null -> AppBrowserMode.Category(selectedCategory)
            else -> AppBrowserMode.Overview
        }
    }

    AnimatedContent(
        targetState = browserMode,
        transitionSpec = {
            when {
                initialState is AppBrowserMode.Overview &&
                    targetState is AppBrowserMode.Category -> {
                    (fadeIn(IosMotion.itemFadeInSpec) + scaleIn(
                        animationSpec = tween(
                            durationMillis = 220,
                            easing = IosMotion.easeOut,
                        ),
                        initialScale = 0.94f,
                    )).togetherWith(
                        fadeOut(IosMotion.itemFadeOutSpec) + scaleOut(
                            animationSpec = tween(
                                durationMillis = 150,
                                easing = IosMotion.easeIn,
                            ),
                            targetScale = 1.025f,
                        ),
                    )
                }

                initialState is AppBrowserMode.Category &&
                    targetState is AppBrowserMode.Overview -> {
                    (fadeIn(IosMotion.itemFadeInSpec) + scaleIn(
                        animationSpec = tween(
                            durationMillis = 210,
                            easing = IosMotion.easeOut,
                        ),
                        initialScale = 1.02f,
                    )).togetherWith(
                        fadeOut(IosMotion.itemFadeOutSpec) + scaleOut(
                            animationSpec = tween(
                                durationMillis = 160,
                                easing = IosMotion.easeIn,
                            ),
                            targetScale = 0.94f,
                        ),
                    )
                }

                else -> {
                    (fadeIn(IosMotion.itemFadeInSpec) + scaleIn(
                        animationSpec = tween(
                            durationMillis = IosMotion.STANDARD_FADE_MS,
                            easing = IosMotion.easeOut,
                        ),
                        initialScale = 0.985f,
                    )).togetherWith(fadeOut(IosMotion.itemFadeOutSpec))
                }
            }
        },
        label = "appBrowserMode",
        modifier = modifier.fillMaxSize(),
    ) { mode ->
        when (mode) {
            AppBrowserMode.Search -> AppGrid(
                apps = matchingApps,
                modifier = Modifier.fillMaxSize(),
                appCell = appCell,
            )

            is AppBrowserMode.Category -> {
                val categoryApps = remember(apps, mode.category) {
                    apps.filter { it.category == mode.category }
                }
                AppGrid(
                    apps = categoryApps,
                    modifier = Modifier.fillMaxSize(),
                    appCell = appCell,
                )
            }

            AppBrowserMode.Overview -> {
                val overview = remember(apps, frequentApps, preferredApps) {
                    buildAppBrowserOverviewContent(
                        apps = apps,
                        frequentAppRefs = frequentApps,
                        preferredApps = preferredApps,
                    )
                }
                // カードごとのBoxWithConstraintsは、Pagerへ入る最初のフレームで
                // 可視カード数だけSubcomposeを発生させる。グリッド幅から1回だけ
                // 実寸を計算し、全カードへ共有する。
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = appLibraryCategoryColumns(maxWidth)
                    val cardWidth = (
                        maxWidth -
                            CATEGORY_GRID_HORIZONTAL_PADDING * 2 -
                            CATEGORY_GRID_GAP * (columns - 1)
                        ) / columns
                    // 先読み(HomeScreen)は APP_LIBRARY_PREVIEW_ICON_SIZE で画像を用意するため、
                    // 一般的な幅の端末ではこの上限に張り付く寸法にしておく。3列以上は
                    // カード幅が CATEGORY_CARD_FULL_ICON_WIDTH 以上になる列数だけを選ぶ。
                    val previewIconSize = minOf(
                        APP_LIBRARY_PREVIEW_ICON_SIZE,
                        (
                            cardWidth -
                                CATEGORY_CARD_MIN_PADDING * 2 -
                                APP_LIBRARY_PREVIEW_ICON_GAP
                            ) / 2,
                    ).coerceAtLeast(1.dp)

                    // 横に広い時は「よく使うアプリ」を1行にして、カテゴリーカードを
                    // 最初の画面に入れる。
                    val frequentColumns = if (columns >= WIDE_CATEGORY_COLUMNS) {
                        FREQUENT_APP_LIMIT
                    } else {
                        FREQUENT_APP_COLUMNS
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(columns),
                        contentPadding = PaddingValues(
                            start = CATEGORY_GRID_HORIZONTAL_PADDING,
                            end = CATEGORY_GRID_HORIZONTAL_PADDING,
                            top = 10.dp,
                            bottom = 28.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(CATEGORY_GRID_GAP),
                        verticalArrangement = Arrangement.spacedBy(CATEGORY_GRID_ROW_GAP),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (overview.frequentApps.isNotEmpty()) {
                            item(
                                key = "frequent_apps",
                                span = { GridItemSpan(maxLineSpan) },
                                contentType = "frequent_apps",
                            ) {
                                FrequentAppsCard(
                                    apps = overview.frequentApps,
                                    columns = frequentColumns,
                                    selectedApps = selectedApps,
                                    onAppClick = onPreviewAppClick,
                                )
                            }
                        }
                        items(
                            items = overview.categoryGroups,
                            key = { it.first.name },
                            contentType = { "category" },
                        ) { (category, groupedApps) ->
                            val title = appCategoryTitle(category)
                            CategoryCard(
                                title = title,
                                apps = groupedApps,
                                selectedApps = selectedApps,
                                previewIconSize = previewIconSize,
                                onOpenCategory = { onCategorySelected(category) },
                                onAppClick = onPreviewAppClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 履歴上位を通常カテゴリーから独立させ、必ず見出し付きで表示する。 */
@Composable
private fun FrequentAppsCard(
    apps: List<AppInfo>,
    columns: Int,
    selectedApps: Set<AppRef>,
    onAppClick: (AppInfo, Rect?) -> Unit,
) {
    val visibleApps = remember(apps) { apps.take(FREQUENT_APP_LIMIT) }
    val appRows = remember(visibleApps, columns) { visibleApps.chunked(columns) }
    val density = LocalDensity.current
    val iconRequests = remember(visibleApps, density.density) {
        val iconSizePx = with(density) { FREQUENT_APP_ICON_SIZE.roundToPx() }
        visibleApps.map { app -> AppIconRequest(app.ref, iconSizePx) }
    }
    val icons by rememberRequestedAppIconBitmaps(iconRequests)

    // 中身は余白の内側に収まるのでclipしない(カードごとのレイヤーを作らない)。
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .liquidGlass(AppLibraryCardShape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = stringResource(R.string.frequent_apps_title),
            color = GlassContentColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.height(8.dp))
        appRows.forEachIndexed { rowIndex, rowApps ->
            if (rowIndex > 0) Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(columns) { column ->
                    val index = rowIndex * columns + column
                    val app = rowApps.getOrNull(column)
                    if (app == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        FrequentAppCell(
                            app = app,
                            icon = icons.getOrNull(index),
                            selected = app.ref in selectedApps,
                            onClick = { bounds -> onAppClick(app, bounds) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FrequentAppCell(
    app: AppInfo,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconBounds = rememberLayoutBoundsHolder()
    val interactionSource = remember(app.ref) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        label = "frequentAppScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .semantics {
                contentDescription = app.label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(iconBounds.boundsInRoot()) },
            )
            .padding(horizontal = 2.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(FREQUENT_APP_ICON_SIZE)
                .trackLayoutBounds(iconBounds)
                .drawWithContent {
                    scale(scaleX = scale, scaleY = scale) {
                        this@drawWithContent.drawContent()
                    }
                },
        ) {
            AppIconImage(
                icon = icon,
                size = FREQUENT_APP_ICON_SIZE,
            )
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = IOS_SELECTION_BLUE,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(LocalOhagiColors.current.backdrop, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = app.label,
            color = GlassContentColor,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

fun appCategoryTitleRes(category: AppCategory): Int =
    when (category) {
        AppCategory.SOCIAL -> R.string.category_social
        AppCategory.PRODUCTIVITY_FINANCE -> R.string.category_productivity_finance
        AppCategory.PHOTO_VIDEO -> R.string.category_photo_video
        AppCategory.ENTERTAINMENT -> R.string.category_entertainment
        AppCategory.GAMES -> R.string.category_games
        AppCategory.NEWS_READING -> R.string.category_news_reading
        AppCategory.TRAVEL_WEATHER -> R.string.category_travel_weather
        AppCategory.SHOPPING_FOOD -> R.string.category_shopping_food
        AppCategory.HEALTH_FITNESS -> R.string.category_health_fitness
        AppCategory.UTILITIES -> R.string.category_utilities
        AppCategory.OTHER -> R.string.category_other
    }

@Composable
fun appCategoryTitle(category: AppCategory): String =
    stringResource(appCategoryTitleRes(category))

@Composable
private fun AppGrid(
    apps: List<AppInfo>,
    modifier: Modifier,
    appCell: @Composable (AppInfo) -> Unit,
) {
    if (apps.isEmpty()) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier.fillMaxSize(),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.SearchOff,
                    contentDescription = null,
                    tint = GlassSecondaryContentColor,
                    modifier = Modifier.size(44.dp),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.category_no_apps),
                    color = GlassSecondaryContentColor,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    LazyVerticalGrid(
        columns = AppGridColumns,
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 8.dp,
            bottom = 28.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(APP_GRID_GAP),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        items(
            items = apps,
            key = { "${it.ref.packageName}/${it.ref.className}" },
        ) { app ->
            appCell(app)
        }
    }
}

@Composable
private fun CategoryCard(
    title: String,
    apps: List<AppInfo>,
    selectedApps: Set<AppRef>,
    previewIconSize: Dp,
    onOpenCategory: () -> Unit,
    onAppClick: (AppInfo, Rect?) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        pressedScale = 0.975f,
        label = "appCategoryCardScale",
    )
    val miniIconSize = minOf(
        APP_LIBRARY_MINI_ICON_SIZE,
        (previewIconSize - APP_LIBRARY_MINI_ICON_GAP) / 2,
    )
    val density = LocalDensity.current
    val iconRequests = remember(
        apps,
        previewIconSize,
        miniIconSize,
        density.density,
    ) {
        val previewSizePx = with(density) { previewIconSize.roundToPx() }
        val miniSizePx = with(density) { miniIconSize.roundToPx() }
        buildList {
            apps.take(3).forEach { app ->
                add(AppIconRequest(app.ref, previewSizePx))
            }
            apps.drop(3).take(4).forEach { app ->
                add(AppIconRequest(app.ref, miniSizePx))
            }
        }
    }
    val previewIcons by rememberRequestedAppIconBitmaps(iconRequests)

    // iOS の Appライブラリと同じく、正方形のガラスカードの下にカテゴリー名を置く。
    // 名前もカードと同じクリック領域に含め、カードのsemanticsへ従来どおり統合する。
    // カードはclipしない(中身は余白の内側に収まるため、カードごとのレイヤーを作らない)。
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .drawWithContent {
                scale(scaleX = scale, scaleY = scale) { this@drawWithContent.drawContent() }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenCategory,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .liquidGlass(AppLibraryCardShape),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(APP_LIBRARY_PREVIEW_ICON_GAP),
            ) {
                PreviewRow(
                    apps = apps.take(2),
                    icons = previewIcons.take(2),
                    selectedApps = selectedApps,
                    onAppClick = onAppClick,
                    iconSize = previewIconSize,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        space = APP_LIBRARY_PREVIEW_ICON_GAP,
                        alignment = Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    apps.getOrNull(2)?.let { app ->
                        CategoryPreviewIcon(
                            app = app,
                            icon = previewIcons.getOrNull(2),
                            selected = app.ref in selectedApps,
                            onClick = { bounds -> onAppClick(app, bounds) },
                            size = previewIconSize,
                        )
                    } ?: PreviewPlaceholder(previewIconSize)
                    MiniPreviewCluster(
                        title = title,
                        apps = apps.drop(3).take(4),
                        icons = previewIcons.drop(3),
                        onClick = onOpenCategory,
                        size = previewIconSize,
                        miniIconSize = miniIconSize,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = GlassSecondaryContentColor,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun PreviewRow(
    apps: List<AppInfo>,
    icons: List<ImageBitmap?>,
    selectedApps: Set<AppRef>,
    onAppClick: (AppInfo, Rect?) -> Unit,
    iconSize: Dp,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(
            space = APP_LIBRARY_PREVIEW_ICON_GAP,
            alignment = Alignment.CenterHorizontally,
        ),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        repeat(2) { index ->
            apps.getOrNull(index)?.let { app ->
                CategoryPreviewIcon(
                    app = app,
                    icon = icons.getOrNull(index),
                    selected = app.ref in selectedApps,
                    onClick = { bounds -> onAppClick(app, bounds) },
                    size = iconSize,
                )
            } ?: PreviewPlaceholder(iconSize)
        }
    }
}

@Composable
private fun CategoryPreviewIcon(
    app: AppInfo,
    icon: ImageBitmap?,
    selected: Boolean,
    onClick: (Rect?) -> Unit,
    size: Dp,
) {
    val iconBounds = rememberLayoutBoundsHolder()
    val interactionSource = remember(app.ref) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        label = "categoryPreviewIconScale",
    )
    Box(
        modifier = Modifier
            .size(size)
            .trackLayoutBounds(iconBounds)
            .drawWithContent {
                scale(scaleX = scale, scaleY = scale) { this@drawWithContent.drawContent() }
            }
            .semantics {
                contentDescription = app.label
                role = Role.Button
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(iconBounds.boundsInRoot()) },
            ),
    ) {
        AppIconImage(icon = icon, size = size, decorated = false)
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = IOS_SELECTION_BLUE,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(20.dp)
                    .background(LocalOhagiColors.current.backdrop, CircleShape),
            )
        }
    }
}

@Composable
private fun PreviewPlaceholder(size: Dp) {
    Spacer(Modifier.size(size))
}

@Composable
private fun MiniPreviewCluster(
    title: String,
    apps: List<AppInfo>,
    icons: List<ImageBitmap?>,
    onClick: () -> Unit,
    size: Dp,
    miniIconSize: Dp,
) {
    val description = stringResource(R.string.category_open, title)
    // iOS と同じく下地を敷かず、小さいアイコン4つだけを大アイコン1個分の枠に並べる。
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (apps.isEmpty()) {
            Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = GlassSecondaryContentColor,
                modifier = Modifier.size(28.dp),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(APP_LIBRARY_MINI_ICON_GAP)) {
                repeat(2) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(APP_LIBRARY_MINI_ICON_GAP)) {
                        repeat(2) { column ->
                            val app = apps.getOrNull(row * 2 + column)
                            if (app == null) {
                                Spacer(Modifier.size(miniIconSize))
                            } else {
                                AppIconImage(
                                    icon = icons.getOrNull(row * 2 + column),
                                    size = miniIconSize,
                                    decorated = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

internal val APP_LIBRARY_PREVIEW_ICON_SIZE = 66.dp
internal val FREQUENT_APP_ICON_SIZE = 56.dp
private val APP_LIBRARY_PREVIEW_ICON_GAP = 12.dp
internal val APP_LIBRARY_MINI_ICON_SIZE = 28.dp
private val APP_LIBRARY_MINI_ICON_GAP = 5.dp
private val CATEGORY_GRID_HORIZONTAL_PADDING = 20.dp
private val CATEGORY_GRID_GAP = 16.dp
private val CATEGORY_GRID_ROW_GAP = 14.dp

/** 正方形カード内で、アイコン群の外側に最低限残す余白。 */
private val CATEGORY_CARD_MIN_PADDING = 12.dp

/** 大アイコンが上限の APP_LIBRARY_PREVIEW_ICON_SIZE のまま収まる最小のカード幅(168dp)。 */
private val CATEGORY_CARD_FULL_ICON_WIDTH =
    APP_LIBRARY_PREVIEW_ICON_SIZE * 2 + APP_LIBRARY_PREVIEW_ICON_GAP + CATEGORY_CARD_MIN_PADDING * 2
private const val CATEGORY_GRID_MIN_COLUMNS = 2
private const val CATEGORY_GRID_MAX_COLUMNS = 8
private const val WIDE_CATEGORY_COLUMNS = 4
private val AppLibraryCardShape = ContinuousRoundedShape(26.dp)
private const val FREQUENT_APP_COLUMNS = 4

internal val IOS_SELECTION_BLUE = Color(0xFF0A84FF)

/**
 * カテゴリーカードの列数。縦画面の端末幅(約360〜550dp)では従来どおり2列で、
 * それより広い時はカード幅が [CATEGORY_CARD_FULL_ICON_WIDTH] を下回らない最大の列数にする。
 * これにより3列以上では大アイコンが常に先読みと同じ寸法になる。
 */
internal fun appLibraryCategoryColumns(availableWidth: Dp): Int {
    val contentWidth = availableWidth - CATEGORY_GRID_HORIZONTAL_PADDING * 2
    val fitting = (
        (contentWidth + CATEGORY_GRID_GAP) / (CATEGORY_CARD_FULL_ICON_WIDTH + CATEGORY_GRID_GAP)
        ).toInt()
    return fitting.coerceIn(CATEGORY_GRID_MIN_COLUMNS, CATEGORY_GRID_MAX_COLUMNS)
}

private val APP_GRID_GAP = 4.dp

/** 縦画面では4列のまま、横画面など幅に余裕がある時だけ列を増やす。 */
private class MinCountAdaptiveCells(
    private val minCount: Int,
    private val minCellWidth: Dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(availableSize: Int, spacing: Int): List<Int> {
        val adaptiveCount = (availableSize + spacing) / (minCellWidth.roundToPx() + spacing)
        val count = maxOf(minCount, adaptiveCount)
        val usable = (availableSize - spacing * (count - 1)).coerceAtLeast(0)
        val cellSize = usable / count
        val remainder = usable % count
        return List(count) { index -> cellSize + if (index < remainder) 1 else 0 }
    }

    override fun equals(other: Any?): Boolean =
        other is MinCountAdaptiveCells &&
            other.minCount == minCount &&
            other.minCellWidth == minCellWidth

    override fun hashCode(): Int = 31 * minCount + minCellWidth.hashCode()
}

/** 約411dp幅の縦画面では4列(1セル約94dp)。横画面の約860dpでは8列になる。 */
private val AppGridColumns: GridCells = MinCountAdaptiveCells(minCount = 4, minCellWidth = 96.dp)

/**
 * 検索欄のフォーカスを、ソフトキーボードが閉じられた時(検索文字が空の場合)と
 * カテゴリーを開いた時に外す。
 *
 * フォーカス直後はまだキーボードが出ていないので、「このフォーカス中に一度キーボードが
 * 出た」後の非表示化だけを閉じる操作とみなす。ソフトキーボードを使わない
 * ハードウェアキーボード入力ではフォーカスを外さない。
 * 外すのは検索欄がフォーカスを持っている時だけで、他の入力欄のフォーカスには触れない。
 */
@Stable
internal class SearchFieldFocusReleaser(private val focusManager: FocusManager) {
    private var focused = false
    private var imeVisible = false
    private var imeShownWhileFocused = false

    /** 検索欄の modifier に連結する。 */
    val modifier: Modifier = Modifier.onFocusChanged { state ->
        val nowFocused = state.isFocused
        if (nowFocused != focused) {
            focused = nowFocused
            imeShownWhileFocused = nowFocused && imeVisible
        }
    }

    /** カテゴリーを開いた時など、検索欄から離れる操作で呼ぶ。 */
    fun release() {
        if (focused) focusManager.clearFocus()
    }

    internal fun onImeOrQueryChanged(imeVisible: Boolean, queryBlank: Boolean) {
        this.imeVisible = imeVisible
        if (!focused) return
        if (imeVisible) {
            imeShownWhileFocused = true
        } else if (queryBlank && imeShownWhileFocused) {
            focusManager.clearFocus()
        }
    }
}

@Composable
internal fun rememberSearchFieldFocusReleaser(query: String): SearchFieldFocusReleaser {
    val focusManager = LocalFocusManager.current
    val releaser = remember(focusManager) { SearchFieldFocusReleaser(focusManager) }
    SearchFieldImeWatcher(releaser = releaser, queryBlank = query.isBlank())
    return releaser
}

/** IME の表示状態はこの小さなスコープだけで読み、切り替わりで呼び出し元を再composeしない。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchFieldImeWatcher(releaser: SearchFieldFocusReleaser, queryBlank: Boolean) {
    val imeVisible = WindowInsets.isImeVisible
    LaunchedEffect(releaser, imeVisible, queryBlank) {
        releaser.onImeOrQueryChanged(imeVisible = imeVisible, queryBlank = queryBlank)
    }
}
