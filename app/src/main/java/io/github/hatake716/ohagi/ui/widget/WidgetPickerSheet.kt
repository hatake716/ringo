package io.github.hatake716.ohagi.ui.widget

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.LocalGraph
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.ui.common.ContinuousRoundedShape
import io.github.hatake716.ohagi.ui.common.GlassContentColor
import io.github.hatake716.ohagi.ui.common.GlassSecondaryContentColor
import io.github.hatake716.ohagi.ui.common.GlassTone
import io.github.hatake716.ohagi.ui.common.IosSearchField
import io.github.hatake716.ohagi.ui.common.iosIconShape
import io.github.hatake716.ohagi.ui.common.liquidGlass
import io.github.hatake716.ohagi.ui.common.rememberModalSheetBackdrop
import io.github.hatake716.ohagi.widget.WidgetProviderEntry
import kotlinx.coroutines.flow.collectLatest

/**
 * インストール済みAppWidgetProviderを選ぶ、iOS風の下部シート。
 * 一覧はシートを開いてからIOスレッドで読み込み、アプリの追加・削除があれば読み直す。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetPickerSheet(
    onSelect: (AppWidgetProviderInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val graph = LocalGraph.current
    val allEntries by produceState<List<WidgetProviderEntry>?>(initialValue = null, graph) {
        graph.appRepository.apps.collectLatest {
            value = graph.widgetHost.loadProviderEntries()
        }
    }
    val backdrop = rememberModalSheetBackdrop()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RectangleShape,
        containerColor = Color.Transparent,
        contentColor = GlassContentColor,
        tonalElevation = 0.dp,
        scrimColor = backdrop.scrimColor,
        dragHandle = null,
        // 余白はカード側で取る。IME を含む safeDrawing で検索中もリストが隠れない。
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        val glassMinOpacity = backdrop.blurDialogWindow()
        var query by remember { mutableStateOf("") }
        val loaded = allEntries
        val entries = remember(loaded, query) {
            val normalized = query.trim()
            if (loaded == null || normalized.isEmpty()) {
                loaded.orEmpty()
            } else {
                loaded.filter { entry ->
                    entry.appLabel.contains(normalized, ignoreCase = true) ||
                        entry.widgetLabel.contains(normalized, ignoreCase = true)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .liquidGlass(PickerSheetShape, GlassTone.Regular, minOpacity = glassMinOpacity)
                .clip(PickerSheetShape),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp)
                    .size(width = 36.dp, height = 5.dp)
                .background(LocalOhagiColors.current.separator, RoundedCornerShape(50)),
            )
            Text(
                text = stringResource(R.string.widget_picker_title),
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
            IosSearchField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.widget_search_hint),
                clearContentDescription = stringResource(R.string.action_clear_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
            if (loaded == null) {
                Spacer(Modifier.fillMaxWidth().weight(1f))
            } else if (entries.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    Text(
                        text = stringResource(R.string.widget_no_providers),
                        color = GlassSecondaryContentColor,
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(
                        items = entries,
                        key = { it.info.provider },
                    ) { entry ->
                        WidgetProviderRow(
                            entry = entry,
                            onClick = { onSelect(entry.info) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                    item { Spacer(Modifier.size(16.dp)) }
                }
            }
        }
    }
}

/**
 * ガラスのシートの上に置く1行。ガラスを重ねず、iOS のグループ化リストと同じ
 * 薄い塗りのセルにする。押下状態は描画フェーズでだけ読む。
 */
@Composable
private fun WidgetProviderRow(
    entry: WidgetProviderEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember(entry.info.provider) { MutableInteractionSource() }
    val pressed = interactionSource.collectIsPressedAsState()
    val colors = LocalOhagiColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .drawWithCache {
                val outline = ProviderRowShape.createOutline(size, layoutDirection, this)
                onDrawBehind {
                    drawOutline(
                        outline = outline,
                        color = if (pressed.value) colors.pressedFill else colors.controlFill,
                    )
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(PROVIDER_ICON_SIZE)
                .background(colors.controlFill, ProviderIconShape),
        ) {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = GlassContentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.widgetLabel,
                fontSize = 17.sp,
                letterSpacing = 0.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.appLabel,
                color = GlassSecondaryContentColor,
                fontSize = 15.sp,
                letterSpacing = 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PickerSheetShape = ContinuousRoundedShape(34.dp)
private val ProviderRowShape = ContinuousRoundedShape(20.dp)
private val PROVIDER_ICON_SIZE = 50.dp
private val ProviderIconShape = iosIconShape(PROVIDER_ICON_SIZE)
