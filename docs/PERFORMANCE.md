# 動作・メモリ最適化の検証記録

2026-09-04。比較元は `42537d6cccd9f9fca7bab74d63866bebf3163200`（v0.2.0）。機能と保存形式を維持し、描画の更新範囲、画像の生成・保持量、メモリ圧迫時の再生成可能なデータを対象に変更した。

## 実装

- **描画更新**：Pagerの小数位置、押下・ドラッグの縮尺と透明度、フォルダ背景と編集時の揺れ、端末回転のアニメーション値を描画フェーズで読み取る。HomeScreenやセル全体の毎フレームの再compositionを避ける。読み上げ用ページ番号は整数ページの変化を監視する。D&Dの安定したキー、最新payload、ドロップ対象の登録順は維持する。
- **アプリアイコン**：既存の96／144／192pxに32／48／72pxを追加し、表示に必要な解像度以上を選択する。密度3倍の小さなフォルダプレビューでは96→48pxとなり、その画像のピクセル容量は75%減る。これは画像単位の計算であり、プロセス全体のメモリ削減率ではない。Hardware Bitmap、既存の最大192px、6MiB／Low-RAM端末3MiBのキャッシュ上限を維持する。
- **ファイルサムネイル**：48件の件数制限を実容量4MiB／Low-RAM端末2MiBのLRUに変更。URIと要求サイズが同じ読み込みを共有し、providerへの同時アクセスを2件／Low-RAM端末1件に制限する。最後の利用者が離れた要求はキャンセルし、失敗を保存しない。providerが返した過大な画像は縦横比を保ち、要求サイズ内に縮小する。表示中のBitmapをキャッシュ解放時にrecycleしない。
- **メモリ通知**：画像キャッシュに世代番号を持たせ、解放前に開始した読み込みによるキャッシュの再充填を防ぐ。進行中の画面表示には取得結果を渡せる。
- **ウィジェット**：通常のページ往復では既存のHostViewを再利用する。実際のメモリ圧迫通知を受けた場合は、表示中のViewがなくなってからAndroidのAppWidgetHost内部の強参照も解放する。ID、バインド、設定、保存サイズは維持し、再表示時にRemoteViewsを取得する。

Composeの状態読み取りを遅らせる方針は[公式の性能ガイド](https://developer.android.com/develop/ui/compose/performance/bestpractices)に沿う。ウィジェットはアプリ側の弱参照だけでは保持量を管理できないため、[AppWidgetHostの実装](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/appwidget/AppWidgetHost.java)のView保持も対象にした。通常のページ離脱で毎回再生成するとprovider内のスクロール位置などを失う可能性があるため、解放はメモリ圧迫時に限定した。

ホーム／Dock／フォルダ／Appライブラリ、SAFの永続アクセス許可、アプリ起動と分割画面、横画面の配置、ウィジェットの保存形式に仕様変更はない。

## ビルドと自動検証

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
./gradlew --no-daemon :app:assembleRelease :app:bundleRelease
```

- 単体テスト66件成功（既存53件＋追加13件）、失敗・エラー・skipなし。
- Lintはエラー0件、警告21件。警告数は比較元と同じ。
- Debug APK、R8による縮小を有効にしたRelease APK、Release AABのビルド成功。
- 追加テストは、表示解像度の境界、描画時の状態購読、サムネイルの容量・LRU・重複要求共有・利用者ごとのキャンセル・待機中キャンセル・失敗再試行・並列数・メモリ通知との競合を検証する。

Release成果物の検証用APKはローカルDebug証明書で署名する。公開用のRelease署名を行ったAABではない。

## 比較測定の条件

専用のAndroid 15 / API 35 Google APIs x86_64エミュレータを使用した。Pixel 7プロファイル、1080×2400、420dpi、RAM 3072MB、4 vCPU、SwiftShader。両版ともDebug APKを使い、測定中にGradle／R8ビルドを並行実行しない。

専用fixtureは96個の異なるアイコンを持つ合成アプリと標準アプリ19件、ホーム3ページ、直接配置51件、ホーム内24アプリのフォルダ、Dock内12アプリのフォルダ、Clockウィジェット、DocumentsUIで権限を取得したファイル／ディレクトリピンからなる。アプリのデータ消去やアンインストールは行わない。

APK交換ごとに、同じインストール方式とARTのコンパイル条件を明示する。

```sh
adb -s emulator-5554 install --no-incremental -r <debug-apk>
adb -s emulator-5554 shell cmd package compile -f -m verify io.github.hatake716.ohagi
```

両版で実効filterが`verify`、理由が`cmdline`、プロセスが`base.vdex`をmapしていることを確認する。[Android公式のメモリ測定手順](https://developer.android.com/topic/performance/memory/guide/app-code#art-compilation-modes-and-memory)でもコンパイル条件を指定して測定する。

各試行は同一fixtureを復元し、Ohagiをforce-stopして`am start -W`で起動、5秒後にcoldメモリを記録する。ウィジェット→ホーム3ページ→ライブラリの上下スクロール→ホーム／Dockフォルダのページ送りを1回暖機し、gfxinfoをリセットして同じ操作を2回実施する。8秒静置した後のメモリをwarmとして記録する。各版3回の中央値を使う。fixture復元スクリプトは専用エミュレータだけを対象にする。

最初に取得した`baseline/`と`optimized/`の比較は採用しない。比較元は圧縮DEX約60,575KiBを匿名メモリに展開していた一方、更新後はfile-backedなVDEXを使っていた。`Unknown`項目の約60,592KiB差はコード最適化の成果とはみなせない。採用するデータは、条件を統一して再取得した`baseline-controlled/`と`optimized-controlled/`のみ。

これらはDebug・ソフトウェアGPU・合成fixtureの比較であり、実機のRelease性能を示すものではない。PSSは共有コードページの帰属やGC時期にも影響される。総PSS、Java／Native Heap、描画時間を併記し、サムネイルの容量制限をアプリ全体の上限と混同しない。

## 比較結果

全6試行のJSONとraw meminfo／gfxinfo／起動ログを照合し、一致を確認した。表は各3回の中央値［最小–最大］。メモリ単位はKiB、Java／Native HeapはmeminfoのApp Summary値であり、raw表のHeap Allocとは異なる。

| 指標 | 比較元 | 最適化後 | 中央値の変化 |
| --- | ---: | ---: | ---: |
| 起動時間（ms） | 1,298［1,284–1,319］ | 1,347［1,296–1,352］ | +3.8% |
| 描画p50（ms） | 30［29–31］ | 28［27–28］ | −6.7% |
| 描画p90（ms） | 40［38–40］ | 36［34–36］ | −10.0% |
| 描画p95（ms） | 44［42–44］ | 40［38–40］ | −9.1% |
| Janky frames率 | 9.55%［9.19–9.57］ | 8.96%［8.84–8.99］ | −0.59ポイント |
| cold 総PSS | 177,743［168,478–178,224］ | 175,367［168,743–175,457］ | −1.3% |
| warm 総PSS | 173,859［145,401–191,416］ | 187,763［184,299–195,108］ | +8.0% |
| cold Java Heap | 17,736［17,732–17,752］ | 17,716［17,712–17,756］ | −0.1% |
| warm Java Heap | 18,464［18,380–20,212］ | 22,868［18,564–23,268］ | +23.9% |
| cold Native Heap | 45,636［45,568–45,636］ | 44,864［44,852–44,868］ | −1.7% |
| warm Native Heap | 55,772［50,488–55,780］ | 56,748［55,900–56,868］ | +1.7% |

今回確認できたのはページ・フォルダ操作の描画時間の改善傾向であり、アプリ全体のメモリ削減や起動高速化は確認できていない。操作後の総PSSは中央値で8.0%増えている。raw Dalvik Heap Alloc中央値は9,185→9,201KiBとほぼ同じだが、Native Heap Alloc中央値は64,395→65,067KiBで約1.0%増えた。GC時期や共有ページなどの影響はあり得るものの、差の原因は今回の記録だけでは確定できない。

通常操作後の総PSSとは別に、ホーム3ページ目まで離れてメモリ圧迫を通知した場合のView数は比較元14個、最適化後8個だった。ウィジェットのIDと保存レイアウトを維持したまま非使用Viewを解放し、再表示後に時計が更新され、タップでClockアプリが開くことを確認した。このView数は専用fixtureの1回の確認であり、全providerに共通するメモリ削減量ではない。

## 操作と保存データの確認

| 対象 | 確認内容 |
| --- | --- |
| ホーム／DockのD&D | 実ジェスチャーでホーム→空Dock→元のホームへ移動。Debugの保存レイアウトで対象AppRefの移動と復元をassert |
| ページ／フォルダ | ホーム3ページ、Appライブラリ、ホーム／Dockの複数ページフォルダを反復操作 |
| ファイルサムネイル | DocumentsUIでPNGを登録し、ページ往復・force-stop後の再起動・メモリ圧迫後もRGB格子の表示を確認。ReleaseではPhotosで実画像を開けることも確認 |
| PDF | このproviderはサムネイルを提供せず、PDFの種類アイコンを表示 |
| ウィジェット | OSのバインド／設定画面を通してClockを追加。メモリ圧迫後もID・レイアウトを維持し、再表示・時刻更新・タップ起動を確認 |
| アプリ起動／履歴 | ライブラリ検索から合成アプリを起動。usage.jsonと「よく使うアプリ」表示への反映を確認 |
| 分割画面 | ReleaseでSettingsを起動し、Ohagiの通知から2つ目のアプリ選択画面を開いてClockを選択。両アプリの表示とOSのmulti-window状態を確認 |
| Release APK | Debugから同じデータへ上書きし、ホーム／Dock／フォルダ／ピン／メニュー／Clock／縦横表示を確認。non-debuggableを確認、確認時のcrash bufferは空 |

確認範囲には次の限界がある。

- 回転時にホーム1ページ目へ戻る挙動を観測した。縦横でHomeScreenのcomposition位置を切り替える既存のPortraitStage分岐と整合する。この分岐、初期ページ設定、MainActivity、Manifestは今回未変更。変更前APKでの同操作の再現試験は追加していない。
- ライブラリから外部アプリを起動した後のHOME復帰ではホーム1が表示され、この試験では元のライブラリページ保持を確認できなかった。起動イベント経路は今回未変更。
- ディレクトリピンはFilesを起動できたが、このGoogle DocumentsUIではDownloads親階層を表示した。dango未導入のため、対象ディレクトリ直下への遷移は未確認。
- 全Androidバージョン、全ファイルprovider、全ウィジェットproviderを網羅した検証ではない。

## 実機への導入

エミュレータで確認したものと同一SHAのRelease APKを、接続中のPixel 10aへ`install -r`で導入した。インストールは成功し、versionCode 2／versionName 0.2.0、non-debuggable、Ohagiの既定HOMEロールを確認した。既存アプリと同一の署名証明書を使い、データ消去・アンインストールは行っていない。実機への入力操作は行っておらず、実機の操作感・Release性能を計測した結果ではない。

検証用APKは`app/build/outputs/apk/release/ohagi-optimized-debugsigned.apk`。SHA-256:

```text
252e37761e27ec009a7c682efe26570276e96f8fca231070f77c723d83ded70a
```

詳細な測定スクリプト、raw dumps、ビルドログ、操作検証結果はローカルの`app/build/reports/optimization/`へ保存する。同ディレクトリはビルド成果物でありGitの対象外。

## 2026-09-23 追記：Composeの安定性設定、Baseline Profile、再compositionの計測

この節の計測値と「最終版」は2026-09-23時点のもの。後述する2026-10-04のテーマ・透明度変更を含む版での性能再計測値ではない。

比較元は `0ff328b`（上記の最適化を含むHEAD）。上の節で実施済みの描画フェーズでの状態読み取り、アイコン／サムネイルのキャッシュ、ウィジェットのView解放は対象外とした。分析とビルドは専用のgit worktreeで行い、Releaseビルド（R8有効）を使った。

### 反映した変更

| ファイル | 内容 |
| --- | --- |
| `app/compose-stability.conf`（新規） | `kotlin.collections.*`、`androidx.compose.ui.graphics.ImageBitmap`、`HomeItem`／`DockItem`／`FolderLocation`／`DragPayload`（sealed interface）をstableとして宣言する |
| `app/build.gradle.kts` | `composeCompiler { stabilityConfigurationFiles.add(...) }` で上記ファイルを読み込む |
| `app/src/main/baseline-prof.txt`（新規） | アプリ自身のコードのHSPLルール。対象は起動（Application／Graph／リポジトリ）、ホーム初回描画、ページ送り、フォルダ、Appライブラリ、起動経路。ウィジェット選択、分割画面ピッカー、ファイルピンのピッカー、名前変更ダイアログなど頻度の低い画面は含めない |

#### 安定性設定の前提（コードで確認した内容）

- Composeへ渡る `List`／`Set`／`Map` は、`copy()`、`map`、`toList()`、`buildList`、`LayoutRepository.normalized()` で毎回作り直される。公開後に同じインスタンスを書き換える箇所はない。`LayoutEditor` の `toMutableList()` はDataStoreの変換関数の中だけで使われる。`rememberRequestedAppIconBitmaps` は `toList()` で複製してから公開する。`AppPickerSheet` の `mutableStateListOf` は引数に渡さず、`toList()` した値だけを使う。
- 要素はvalだけを持つdata class（`AppRef`、`AppInfo`、`HomeItem`、`DockItem`、`WidgetPlacement`、`AppIconRequest`、`MenuEntry`）か、`equals()` が参照比較の `ImageBitmap`／`AppWidgetProviderInfo` である。
- アイコンはHARDWARE Bitmap（変換できない端末では `prepareToDraw()` 後に描き込まないsoftware Bitmap）で、サムネイルも表示中にrecycleや書き換えをしない。
- Kotlin 2.2.20ではstrong skippingが既定で有効である。HEADでもrestartableな46個のcomposableはすべてskippableで、値を返す14個（`remember*`、`ohagiDragSource` など）はrestartableではない。設定の効果は「unstable引数を参照で比較する」から「`equals()` で比較する」への変更である。
- `kotlin.collections.*` は型引数の安定性を引き継ぐ。`List<AppWidgetProviderInfo>` は引き続きunstableになる。

今後、同じListを書き換えて再描画させるコードや、可変オブジェクトのListを渡すコードを追加する場合は、この設定から外すか、新しいListを渡すこと。

コンパイラレポート（`composeCompiler.reportsDestination` をworktreeだけで一時的に有効化）の比較：

| 指標 | HEAD | 設定後 |
| --- | ---: | ---: |
| knownStableArguments | 2,305 | 2,401 |
| knownUnstableArguments | 121 | 45 |
| unknownStableArguments | 26 | 6 |
| inferredStableClasses | 40 | 50 |
| inferredUnstableClasses | 34 | 28 |
| inferredUncertainClasses | 5 | 1 |
| composables.txtのunstable引数 | 43 | 4 |

設定後に残るunstable引数は `HomeScreen` の `Flow` 2個（Activityの同一インスタンス）、`WidgetPickerSheet.providers`、`WidgetProviderRow.entry` だけである。`LayoutState`、`HomeFolder`、`DockFolder`、`FolderContent`、`UsageState`、`AppBrowserOverviewContent` がstableになった。`Overlay.FolderView`／`RenameFolder`、`PickTarget.FolderAdd`／`FolderCreate` は実行時判定からstableになった。`HomeGrid.home`、`DockBar.dock`、`HomeCell.item`、`DockSlot.item`、各所の `activeDrag`、`AppDrawer.apps` もstableとして比較される。

**ビルド上の注意**：Kotlin 2.2.20＋AGP 8.13では、`compose-stability.conf` の追加や編集が `compileReleaseKotlin` のup-to-date判定に入らない。設定の追加直後も内容を編集した後も、タスクは `UP-TO-DATE` のままでレポートが変わらないことを確認した。設定を変えた後は `./gradlew :app:compileReleaseKotlin --rerun`（Debugも同様）かcleanビルドで全体を再コンパイルする。

#### Baseline Profileの確認

- HEADのRelease APKにも `assets/dexopt/baseline.prof`（4,538 byte）が入っている。これはCompose／AndroidXライブラリのルールだけで、アプリのクラスは0件だった。`androidx.profileinstaller`（`ProfileInstallerInitializer` と `ProfileInstallReceiver`）は推移的依存としてmerged manifestに入っている。
- 追加後のAGP中間ファイル：`merged_art_profile` は3,024→3,104行（アプリのワイルドカード行を含む）。`expandReleaseArtProfileWildcards` はアプリのルール2,456行、485クラスに展開された。R8で書き換えた `minifyReleaseWithR8` は9,717→11,018行で、`mapping.txt` で戻すと969メソッド、288クラスがアプリのコードだった。`baseline.prof` は4,791 byte。
- エミュレータでprofileinstallerの `INSTALL_PROFILE` を送り、`pm compile -f -m speed-profile` の後に `pm dump-profiles` で確認した。インストール済みプロファイルは11,018ルールで、アプリの969メソッドと288クラスを含む。
- 現在のビルドに存在しないクラス名のルール（例：他の作業で追加中の `LiquidGlassKt`）は、ワイルドカード展開の段階で無視されることを確認した。

### 計測（エミュレータの参考値）

専用エミュレータ `ldfa35`（Android 15／API 35 Google APIs x86_64、1080×2400、420dpi、SwiftShader）で測った。HEADと各変種をRelease APKとしてビルドし、ローカルのDebug証明書で署名した。上書きインストール後に `pm compile --reset` を実行した。speed-profileの変種では、profileinstallerの `INSTALL_PROFILE` を送ってから `pm compile -f -m speed-profile` を実行した。既存fixture（ホーム2ページ、フォルダ1、ファイルピン1、Dock 5枠）を使い、エミュレータの既定HOME（`com.google.android.apps.nexuslauncher`）は変更していない。

- 起動：既定HOMEを前面にしてOhagiをforce-stopし、`am start -W` の `TotalTime` を記録した。全試行が `LaunchState: COLD` である。
- ページ送り：起動5秒後に `dumpsys gfxinfo reset` を実行し、「ホーム1→ホーム2→Appライブラリ→ホーム2→ホーム1」の `input swipe` を5周（20回の遷移）行ってから `dumpsys gfxinfo` を取得した。

ホストでは他の作業のGradleビルドが並行して動いていた。同じHEAD／speed-profileでも、時間帯によって起動の中央値が990msから926msまで変わった。このため比較には、変種を交互に並べた回の値だけを使う。

**起動（交互2巡、各n=10、中央値［最小–最大］、ms）**

| APK／コンパイル | TotalTime |
| --- | ---: |
| HEAD／verify | 1,021［980–1,064］ |
| HEAD／speed-profile（ライブラリのプロファイルのみ） | 954［918–987］ |
| 本変更後（アプリのルール＋安定性設定）／speed-profile | 932［899–962］ |

verifyと比べると、本変更後のspeed-profileは中央値で89ms（8.7%）短い。HEADのspeed-profile（ライブラリのプロファイルのみ）との差は22ms（2.3%）で、範囲が重なる。

別の交互測定（各n=10）でも、HEAD／speed-profileは926［907–1027］、プロファイルを追加した版は928［905–953］、安定性設定も加えた版は920［896–960］で、差は範囲内に収まった。アプリのルールがこのエミュレータの起動時間に与える効果は、ノイズと区別できない。

**ページ送り（交互2巡、各4回の中央値。フレーム数は1回あたり）**

| 変種 | 描画フレーム | 16ms以内 | 17–32ms | 50ms超 | p50 | p90 | Slow UI thread | deadline missed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| HEAD／speed-profile | 601 | 224（37%） | 366 | 8 | 21ms | 28ms | 11.5 | 37 |
| ＋アプリのプロファイル | 603 | 229（38%） | 358 | 10 | 20ms | 28ms | 11.5 | 36 |
| ＋安定性設定（本変更） | 594 | 256（43%） | 327 | 10 | 20ms | 28ms | 8 | 36 |
| ＋下記の試作修正1・2（未反映） | 575 | 434（75%） | 129 | 10 | 16ms | 26ms | 7 | 46 |
| ＋試作修正＋`beyondViewportPageCount = 1`（未反映） | 577 | 386（67%） | 180 | 9 | 16ms | 28ms | 8.5 | 47 |

- 本変更（プロファイル＋安定性設定）で、16ms以内のフレームは37%から43%へ増え、Slow UI threadは11.5から8へ減った。p90と50ms超のフレーム数は変わらない。p99は各回6フレーム程度の末尾で決まり、回ごとに69〜150msで揺れるため比較に使わない。
- 試作修正1・2は、16ms以内のフレームを75%まで増やし、p50を16msへ下げた。ただしFrameTimelineのdeadline missedは36から46へ増えた。内訳は全変種で「Slow issue draw commands」（SwiftShaderのRenderThread）が大半を占める。実機での効果はPixelで確認する必要がある。
- 50ms超のフレームは、どの変種でも1周あたり約2回残る。ページやAppライブラリへ入る時の初回compositionと考えられるが、今回の計測では特定していない。
- `beyondViewportPageCount = 1` は、ウィジェットページとホーム2ページ目を起動時にcomposeする。このため起動が1,180［1,158–1,187］msとなり、約250ms遅くなった。ページ送りの改善も試作修正1・2だけの場合を上回らなかったため、採用しない。

**再compositionの回数（計測用のログを仕込んだRelease APK、1周＝4回の遷移）**

| 対象 | HEAD | ＋安定性設定 | ＋安定性設定＋試作修正1・2 |
| --- | ---: | ---: | ---: |
| `HomeScreen` | 16 | 16 | 2 |
| Pagerのページcontent lambda | 40 | 42 | 8 |
| `HomeGrid` | 27 | 27 | 3 |
| `DockBar` | 15 | 15 | 1 |
| `HomeCell`（画面に入るページの初回compositionを含む） | 72 | 72 | 72 |
| セルの `onGloballyPositioned`（`boundsInRoot()` と状態の書き込み） | 約4,100 | 約3,900 | 約3,700 |

安定性設定だけでは `HomeGrid` と `DockBar` の再compositionは減らない。理由は2つある。foundation-layout 1.9.3の `statusBarsPadding()`／`navigationBarsPadding()`／`windowInsetsPadding()` は `composed {}` で実装されており、呼び出すたびに等しくない `Modifier` を返す。また、`HomeScreen` がcomposition中に `pagerState.currentPage`／`isScrollInProgress` を読むため、1回の遷移で約4回再composeされ、そのたびにPagerのページcontent lambdaが更新される。起動時のcompositionは3変種とも同じで、`HomeScreen` 1回、`HomeCell` 33回（24セル＋アイコン読み込み後の9回）だった。

### 反映したコード側の改善（iOS 27風UIの導入と同時に実施）

上の分析で挙げた候補は、すべて作業ツリーへ反映した。あわせて、iOS 27風のLiquid Glass表現を入れる際に増えうる描画コストも抑えた。

1. **挿入余白のModifierを記憶**：`HomeGrid`／`DockBar`／検索カプセルへ渡すModifierの連鎖（`statusBarsPadding()`、`navigationBarsPadding()`、`windowInsetsPadding()`、背面ぼかし）を `remember` で一度だけ作る。
2. **Pagerの状態読み取りを限定**：BackHandlerを子composable（`HomePagerBackHandlers`）へ移し、`derivedStateOf { currentPage == PRIMARY }` だけを読む。`composeHomeChrome` は `derivedStateOf`、検索カプセル⇔ページドットの切り替えは子composable（`HomeSearchChrome`）の中で読む。計測時の `HomeScreen` 本体はcomposition中に `currentPage`／`isScrollInProgress`／小数位置を読まない構成とした。現在はシステムバーの配色切り替えのために `currentPage` を読むが、小数位置は引き続き描画時だけ読む。
3. **矩形をフレームごとに書き込まない**：`ui/common/LayoutBounds.kt` の `LayoutBoundsHolder` と `trackLayoutBounds`（`LayoutAwareModifierNode` の専用要素。同じholderなら等しい要素になる）で `LayoutCoordinates` だけを保持し、タップ・ドロップ・ドラッグ移動の瞬間に `boundsInRoot()` を計算する。ページ跨ぎのフォールバックは、グローバルindexをキーにした通常の `HashMap<Int, LayoutCoordinates>` をドロップ時にだけ引く。`HomeGrid` 単位の `DisposableEffect` で登録を外す。
4. **ウィジェット選択をIOスレッドへ**：`WidgetHostController.loadProviderEntries()` がprovider列挙・ラベル取得（1件1回）・ソートをIOスレッドで行い、`WidgetPickerSheet` は自分で読み込む。検索はメモリ上の絞り込みだけ。IDごとの `AppWidgetProviderInfo`／ラベルもキャッシュする。
5. **起動のクリティカルパスからBinder/I/Oを外す**：`Application.onCreate` の通知チャネル作成を削除（`post()` 側で作成）。外観設定（ガラスの透明度）の読み込みと壁紙色リスナーの登録もIOスレッドで行う。
6. **回転アニメーションを1つに**：`PortraitStage` が回転角の `State` を1つだけ作り、`LocalAnimatedUprightRotation` で配る。`uprightWithDevice()` は `graphicsLayer` で読むだけになり、セルごとのAnimatableとcoroutineがなくなった。
7. **小さな重複**：ファイルピンのサムネイル二重購読と、ウィジェットページのcompose毎のBinder呼び出しを解消した。`LayoutState.homePage()` の結果も `remember(layout.home)` で記憶する。

iOS 27風のUIで追加・変更した描画は、次の方針で負荷を抑えた。

- **ガラス面は描画だけ**：`Modifier.liquidGlass` は `drawWithCache` の中で輪郭・グラデーションを作り、レイヤーを作らない。透明度スライダーの値と壁紙色は描画フェーズで読み、スライダー操作中も再compositionしない。
- **アイコンの角丸・光の縁はビットマップに焼き込み**：連続曲率のマスク（アンチエイリアス）とスペキュラの縁は `AppRepository` がアイコン生成時に描く。表示側の `clip`／`border`／下地の3レイヤーをやめ、アイコン1個あたり影のレイヤー1枚と描画1回にした。フォルダのミニアイコン、パネル、ドット、バッジのクリップも外した。
- **影は角丸矩形の輪郭**：連続曲率の汎用パスを影の輪郭にすると、HWUIの角丸矩形用の解析的な影を使えず、拡大縮小のたびにCPUで影をテッセレーションし直す。そのため影だけは同等の円弧角丸（`continuousShadowShape`）を使う。
- **背面ぼかしの二重掛けを避ける**：メニュー・シート・ダイアログは別ウィンドウなので、Android 12以降のウィンドウ背面ぼかし（`FLAG_BLUR_BEHIND`、SurfaceFlingerが合成）で壁紙ごとぼかす。アプリ内の `RenderEffect` によるぼかしは、同じウィンドウに出るフォルダと、ウィンドウぼかしが無効な場合（省電力モードなど）だけに限る。ぼかしが0の間は `RenderEffect` を外し、オフスクリーン描画を残さない。Low-RAM端末とAPI 30以前ではぼかさない。現在の不透明度下限は「着色」側にだけ適用し、「クリア」側の透過は維持する。

### 最終版の比較（エミュレータの参考値）

変更前（`0ff328b`）と最終版をそれぞれRelease APK（R8有効、ローカルのDebug証明書で署名）としてビルドし、上と同じエミュレータで交互に計測した。各回、上書きインストール後にprofileinstallerの `INSTALL_PROFILE` を送って `pm compile -f -m speed-profile` を実行した。fixtureはホーム2ページ、フォルダ2、ファイル／フォルダのピン3、Dock 5枠。

計測時のホストでは、別作業のエミュレータ5台とGradleが動いていた（負荷平均9〜16）。同じ変更前APKでもジャンク率が6.7%〜57%まで振れたため、数値は中央値でのみ比較する。

**起動（`am start -W` の TotalTime、各3巡×5回、中央値［最小–最大］）**：変更前 1,108［879–1,422］ms、最終版 1,045［889–1,284］ms。

**ページ送り（`input swipe` を4往復、`dumpsys gfxinfo`。順序を入れ替えた交互計測で各6回の中央値）**

| シナリオ | 版 | 描画フレーム | Janky | p50 | p90 | p95 | Slow UI thread | Slow issue draw commands |
| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| ホーム1⇔ホーム2 | 変更前 | 211.5 | 29.5% | 33ms | 61ms | 77ms | 40.5 | 60 |
| ホーム1⇔ホーム2 | 最終版 | 320 | 14.9% | 22.5ms | 39ms | 54.5ms | 17.5 | 47 |
| ホーム2⇔Appライブラリ | 変更前 | 245 | 12.7% | 26.5ms | 41ms | 49.5ms | 14 | 30.5 |
| ホーム2⇔Appライブラリ | 最終版 | 290 | 12.7% | 24.5ms | 36ms | 45ms | 8.5 | 37.5 |

- ホーム間のページ送りでは、UIスレッドが遅れたフレームが半分以下になり、同じ操作で描けたフレーム数は約1.5倍になった。再compositionの削減（上の表で `HomeScreen` 16→2回、`HomeGrid` 27→3回、`DockBar` 15→1回／周は試作での値）と整合する。
- Appライブラリへの遷移では、UIスレッドの遅れは減った。一方、ガラスのカードの描画でRenderThreadの「Slow issue draw commands」はわずかに増えた。SwiftShaderはGPUを持たないため、実機GPUでの描画コストはこれより小さいと見込むが、確認はしていない。

### 限界

- 起動とフレームの数値はSwiftShaderのエミュレータでの参考値である。Pixelなど実機のGPU／CPU性能やRelease配信（Playの `.dm` によるプロファイル適用）での効果を示すものではない。
- 再compositionの回数は、計測用のログを入れた別ビルドの1周だけの値である。
- Pixel 10aへは最終版を導入したが、実機の操作はしておらず、実機での計測は行っていない。

## 2026-10-04 追記：テーマ・透明度の変更と検証範囲

- 外観設定に「システムに合わせる」「ライト」「ダーク」を追加し、選択を永続化。起動時は保存された設定をIOスレッドで読み込んでからUIを構成し、誤ったテーマの一瞬の表示を防ぐ。
- 最大「クリア」での標準ガラスの塗りは、不透明度8%（ライト）／10%（ダーク）。Appライブラリ・ウィジェットページの背景とシート・フォルダの暗幕も設定に連動する。ぼかし非対応時も固定の濃い下地でクリア設定を遮らない。
- ガラス面とページ背景は `drawWithCache` で設定を読み、スライダー操作によるページ全体の再compositionを避ける。`ModalBottomSheet` の暗幕は色の引数が必要なため、設定変更時はシート側のcompositionで更新する。
- 単体テスト73件成功、Releaseビルド成功、Lintはエラー0件（警告22件・ヒント1件）。テーマ解決、クリア時の不透明度下限、背景の多重合成、スライダーの単調な変化、着色側の文字コントラストをテストした。
- Pixel 10aで両モード、クリア／着色の変化、Dock・フォルダ・Appライブラリ・設定シートの表示、再起動後の設定保持を確認し、データを消去せず上書きインストールした。
- 今回は実機の見た目・設定保持の検証であり、フレーム時間・RAM・起動時間の性能再計測は行っていない。最大クリア時は背面の柄や明るさによって文字のコントラストが変わるため、必要に応じて「着色」側へ調整する。
