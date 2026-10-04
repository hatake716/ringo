package io.github.hatake716.ohagi.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo

/**
 * 要素の座標オブジェクトだけを覚え、画面上の矩形はタップ・ドロップ・ドラッグ移動の
 * 時にだけ計算する。
 *
 * onGloballyPositioned で毎回 boundsInRoot() を計算して State へ書き込むと、Pager の
 * スクロール中は表示中の全セルで毎フレーム Rect の生成と snapshot への書き込みが起きる。
 * LayoutCoordinates はノードが生きている間は同じオブジェクトで、問い合わせた時点の
 * 位置(graphicsLayer の変形を含む)を返すので、保持するだけで足りる。
 * 保持は snapshot 状態ではないため、書き換えても再compositionは起きない。
 */
@Stable
class LayoutBoundsHolder {
    var coordinates: LayoutCoordinates? = null
        private set

    /** 配置済みでまだ画面に付いていれば、その時点のルート座標系での矩形。 */
    fun boundsInRoot(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInRoot()

    internal fun update(coordinates: LayoutCoordinates) {
        this.coordinates = coordinates
    }

    fun clear() {
        coordinates = null
    }
}

@Composable
fun rememberLayoutBoundsHolder(): LayoutBoundsHolder = remember { LayoutBoundsHolder() }

/**
 * [holder] へこの位置の座標を登録する。配置時だけ呼ばれ、祖先の移動では呼ばれない。
 * 同じ holder なら等しい要素になるので、呼び出し側で remember しなくても
 * 再compositionのたびに再計測を起こさない。
 */
fun Modifier.trackLayoutBounds(holder: LayoutBoundsHolder): Modifier =
    this then TrackLayoutBoundsElement(holder)

private data class TrackLayoutBoundsElement(
    val holder: LayoutBoundsHolder,
) : ModifierNodeElement<TrackLayoutBoundsNode>() {
    override fun create(): TrackLayoutBoundsNode = TrackLayoutBoundsNode(holder)

    override fun update(node: TrackLayoutBoundsNode) {
        node.holder = holder
        node.lastCoordinates?.let(holder::update)
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "trackLayoutBounds"
    }
}

private class TrackLayoutBoundsNode(
    var holder: LayoutBoundsHolder,
) : Modifier.Node(), LayoutAwareModifierNode {
    var lastCoordinates: LayoutCoordinates? = null
        private set

    override fun onPlaced(coordinates: LayoutCoordinates) {
        lastCoordinates = coordinates
        holder.update(coordinates)
    }

    override fun onDetach() {
        lastCoordinates = null
    }
}

/** 座標を外部の登録先(例: セル番号ごとの Map)へ渡したい場合の版。 */
fun Modifier.onLayoutCoordinates(onCoordinates: (LayoutCoordinates) -> Unit): Modifier =
    onPlaced(onCoordinates)
