package io.github.hatake716.ohagi.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hatake716.ohagi.LocalGraph
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppCategory
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.data.preferredAppRefs
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

/** 通知のringoボタンから開く、2つ目のアプリ専用カテゴリー式ドロワー。 */
@Composable
fun SplitAppPickerScreen(
    firstApp: AppRef,
    onSelectApp: (AppRef) -> Unit,
    onDismiss: () -> Unit,
) {
    val graph = LocalGraph.current
    val apps by graph.appRepository.apps.collectAsStateWithLifecycle()
    val layout by graph.layoutRepository.state.collectAsStateWithLifecycle()
    val rankedLaunches by graph.usageRepository.rankedApps.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    val availableApps = remember(apps, firstApp.packageName) {
        apps.filter { it.ref.packageName != firstApp.packageName }
    }
    val preferredApps = remember(
        layout.home,
        layout.dock,
        rankedLaunches,
        firstApp.packageName,
    ) {
        layout.preferredAppRefs(rankedLaunches)
            .filterNot { it.packageName == firstApp.packageName }
    }
    val firstLabel = remember(apps, firstApp) {
        apps.firstOrNull { it.ref == firstApp }?.label
            ?: graph.appRepository.labelOf(firstApp)
    }
    val headerTitle = selectedCategory?.let { appCategoryTitle(it) }
        ?: stringResource(R.string.picker_split_second_title)
    val appearance = LocalGlassAppearance.current
    val colors = LocalOhagiColors.current
    val searchFocus = rememberSearchFieldFocusReleaser(query)

    BackHandler {
        if (selectedCategory != null) selectedCategory = null else onDismiss()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            // この画面は壁紙を透かさない不透明ウィンドウなので、壁紙の主色を上端に
            // 薄く敷いてガラスのカードに映り込む色を作る。色は描画フェーズで読む。
            .drawWithCache {
                val top = lerp(colors.backdrop, appearance.wallpaperTint, if (colors.isDark) SPLIT_BACKDROP_TINT else 0.06f)
                val brush = Brush.verticalGradient(
                    0f to top,
                    0.6f to colors.backdrop,
                    startY = 0f,
                    endY = size.height,
                )
                onDrawBehind { drawRect(brush) }
            }
            .safeDrawingPadding(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        ) {
            if (selectedCategory != null) {
                IosGlassIconButton(
                    imageVector = Icons.Rounded.ArrowBackIosNew,
                    contentDescription = stringResource(R.string.category_back),
                    onClick = { selectedCategory = null },
                    size = 40.dp,
                )
                Spacer(Modifier.width(12.dp))
            } else {
                Spacer(Modifier.width(4.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headerTitle,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = GlassContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.picker_split_first_app, firstLabel),
                    fontSize = 13.sp,
                    color = GlassSecondaryContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IosGlassIconButton(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.action_close),
                onClick = onDismiss,
                size = 40.dp,
            )
        }

        IosSearchField(
            value = query,
            onValueChange = {
                query = it
                if (it.isNotBlank()) selectedCategory = null
            },
            placeholder = stringResource(R.string.search_apps_hint),
            clearContentDescription = stringResource(R.string.action_clear_search),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .then(searchFocus.modifier),
        )

        if (apps.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                CircularProgressIndicator(
                    color = GlassContentColor,
                    modifier = Modifier.size(36.dp),
                )
            }
        } else {
            CategorizedAppBrowser(
                apps = availableApps,
                query = query,
                selectedCategory = selectedCategory,
                onCategorySelected = {
                    selectedCategory = it
                    if (it != null) searchFocus.release()
                },
                onPreviewAppClick = { app, _ -> onSelectApp(app.ref) },
                frequentApps = rankedLaunches,
                preferredApps = preferredApps,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { app ->
                PickerAppCell(
                    app = app,
                    selected = false,
                    onClick = { onSelectApp(app.ref) },
                )
            }
        }
    }
}

/** 上端に混ぜる壁紙の主色の割合。 */
private const val SPLIT_BACKDROP_TINT = 0.32f
