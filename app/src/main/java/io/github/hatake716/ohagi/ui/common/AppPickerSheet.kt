package io.github.hatake716.ohagi.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.hatake716.ohagi.R
import io.github.hatake716.ohagi.data.AppCategory
import io.github.hatake716.ohagi.data.AppInfo
import io.github.hatake716.ohagi.data.AppRef
import io.github.hatake716.ohagi.ui.theme.LocalOhagiColors

/**
 * Dock割り当てとフォルダ追加で共用する自動カテゴリー式ピッカー。
 * 単一選択はタップで即確定し、複数選択はカテゴリーを跨いで選択状態を保持する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    apps: List<AppInfo>,
    frequentApps: List<AppRef> = emptyList(),
    preferredApps: List<AppRef> = emptyList(),
    multiSelect: Boolean,
    onConfirm: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
    title: String = stringResource(R.string.picker_title),
    excluded: Set<AppRef> = emptySet(),
) {
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<AppCategory?>(null) }
    val selected = remember { mutableStateListOf<AppInfo>() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val availableApps = remember(apps, excluded) {
        apps.filter { it.ref !in excluded }
    }
    val selectedRefs = selected.mapTo(mutableSetOf()) { it.ref }
    val categoryLabel = selectedCategory?.let { appCategoryTitle(it) }
    val backdrop = rememberSheetBackdropState()

    fun select(app: AppInfo) {
        if (!multiSelect) {
            onConfirm(listOf(app))
            return
        }
        val existingIndex = selected.indexOfFirst { it.ref == app.ref }
        if (existingIndex >= 0) selected.removeAt(existingIndex) else selected.add(app)
    }

    BackHandler(enabled = selectedCategory != null) {
        selectedCategory = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = IosSheetShape,
        // 面は中身の liquidGlass で描く。濃さはウィンドウ背面ぼかしの有無に応じた
        // 不透明度の下限で決める(ぼかせない時は不透明寄りの厚いガラスになる)。
        containerColor = Color.Transparent,
        contentColor = GlassContentColor,
        scrimColor = backdrop.scrimColor,
        dragHandle = null,
        // ガラス面をシートの下端まで広げるため、インセットは中身の内側で避ける。
        contentWindowInsets = { NoWindowInsets },
    ) {
        val glassMinOpacity = rememberSheetGlassMinOpacity(backdrop)
        val searchFocus = rememberSearchFieldFocusReleaser(query)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .liquidGlass(IosSheetShape, minOpacity = glassMinOpacity)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
        ) {
            IosSheetGrabber()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
            ) {
                if (selectedCategory != null) {
                    IosGlassIconButton(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = stringResource(R.string.category_back),
                        onClick = { selectedCategory = null },
                        size = 38.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = GlassContentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    categoryLabel?.let {
                        Text(
                            text = it,
                            color = GlassSecondaryContentColor,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (multiSelect) {
                    GlassCapsuleButton(
                        text = stringResource(R.string.action_cancel),
                        onClick = onDismiss,
                    )
                    Spacer(Modifier.width(8.dp))
                    GlassCapsuleButton(
                        text = if (selected.isEmpty()) {
                            stringResource(R.string.action_add)
                        } else {
                            stringResource(R.string.action_add_count, selected.size)
                        },
                        onClick = { onConfirm(selected.toList()) },
                        enabled = selected.isNotEmpty(),
                        accent = IosSystemBlue,
                    )
                }
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

            CategorizedAppBrowser(
                apps = availableApps,
                query = query,
                selectedCategory = selectedCategory,
                onCategorySelected = {
                    selectedCategory = it
                    if (it != null) searchFocus.release()
                },
                onPreviewAppClick = { app, _ -> select(app) },
                frequentApps = frequentApps,
                preferredApps = preferredApps,
                selectedApps = selectedRefs,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) { app ->
                PickerAppCell(
                    app = app,
                    selected = app.ref in selectedRefs,
                    onClick = { select(app) },
                )
            }
        }
    }
}

/** iOS のシート上端のつまみ。 */
@Composable
private fun IosSheetGrabber() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
    ) {
        Spacer(
            Modifier
                .size(width = 36.dp, height = 5.dp)
                .background(LocalOhagiColors.current.separator, RoundedCornerShape(50)),
        )
    }
}

/** ガラスの丸いカプセル型の文字ボタン。[accent] を渡すとその色のガラスになる。 */
@Composable
private fun GlassCapsuleButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accent: Color? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        pressedScale = 0.94f,
        label = "glassCapsuleButtonScale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(36.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.45f
            }
            .liquidGlass(RoundedCornerShape(50), accent = accent, highlight = 0.8f)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { role = Role.Button }
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = text,
            color = GlassContentColor,
            fontSize = 15.sp,
            fontWeight = if (accent != null) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
internal fun PickerAppCell(
    app: AppInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember(app.ref) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by rememberIosPressScale(
        pressed = pressed,
        label = "pickerCategoryCellScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Box {
            AppIcon(app = app.ref, size = 56.dp)
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = stringResource(R.string.app_selected, app.label),
                    tint = IOS_SELECTION_BLUE,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .background(LocalOhagiColors.current.backdrop, CircleShape),
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodySmall,
            color = GlassContentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val NoWindowInsets = WindowInsets(0, 0, 0, 0)
