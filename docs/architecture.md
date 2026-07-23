# Band Split Scanner アーキテクチャ設計書

更新日: 2026-07-23

## 1. このドキュメントの目的

本ドキュメントは、Band Split Scanner のコード構造、責務分担、状態管理、座標系、補正処理、将来的な移行方針を定義する。

役割は以下の通り。

```text
specification.md
= 何を作るか

architecture.md
= どのような構造で実現するか

progress.md
= 現在どこまで実装されているか

現行コード
= 実際に今どう動いているか
```

本ドキュメントでは、現在の検証版アーキテクチャと、本格MVPで目指す構成を区別して記述する。

---

## 2. 設計の基本方針

Band Split Scannerでは、以下を基本原則とする。

```text
- UI描画と補正計算を分離する
- 入力境界と出力境界を明確に区別する
- 座標系を混在させない
- Viewは必要な状態だけを保持する
- 変更できない状態までViewへコピーしない
- View間で状態全体を相互上書きしない
- 境界IDを使って対応する状態だけを更新する
- Bitmapを長期状態の中心にしない
- 検証版から本格MVPへ段階的に移行する
```

特に、本アプリでは1つの内部境界が以下の2種類の位置を持つ。

```text
入力側
= 元画像上の分割ライン位置
= inputTop / inputBottom

出力側
= 補正後画像上の境界位置
= outputX
```

この2種類を混同しないことが、状態管理上の最重要事項である。

---

## 3. 現在のアーキテクチャ

現在は、機能検証を優先した単一Activity中心の構成である。

```text
MainActivity
├ 画像選択
├ sourceBitmap保持
├ correctedBitmap保持
├ CornerEditView生成・表示
├ WidthDistributionBarViewとの同期
├ BandCorrectionEngine呼び出し
├ ResultPreviewView生成・表示
├ 現在のcorrectedBitmapをMediaStoreへ保存
└ 元画像編集画面と結果画面の切り替え
```

画面の表示領域には `FrameLayout` を使用し、以下を動的に差し替える。

```text
元画像編集時
FrameLayout
└ CornerEditView

補正結果表示時
FrameLayout
└ ResultPreviewView
```

`WidthDistributionBarView` はXMLに配置された固定UIとして、両方の状態で使用する。

この構成は最終形ではないが、現在のMVP開発段階では以下の利点がある。

```text
- 実装が単純
- 補正方式の検証がしやすい
- Custom Viewの操作をすぐ確認できる
- FragmentやViewModelの導入前にUI仕様を固められる
```

---

## 4. 現在のパッケージ構成

```text
com.example.bandsplitscanner
├ MainActivity.java
│
├ model/
│  ├ PageCorners.java
│  ├ BoundaryPair.java
│  ├ BoundaryMarker.java
│  ├ BoundaryLine.java
│  └ OutputSettings.java
│
├ view/
│  ├ CornerEditView.java
│  ├ WidthDistributionBarView.java
│  └ ResultPreviewView.java
│
└ correction/
   ├ BandCorrectionEngine.java
   ├ BandRenderer.java
   ├ BandCorrectionMath.java
   ├ PerspectiveBandRenderer.java
   └ ScanlineBandRenderer.java
```

---

## 5. 現在の責務分担

### 5.1 MainActivity

現在の `MainActivity` は、画面全体のオーケストレーションを担当する。

```text
- Activity Result APIによる画像選択
- URIからBitmapを読み込む
- sourceBitmapを保持する
- correctedBitmapを保持する
- CornerEditViewを生成する
- 表示Viewを切り替える
- WidthDistributionBarViewの変更イベントを受け取る
- 対応するoutputXを編集状態へ反映する
- 境界追加要求を受け取る
- 新しいBoundaryPairを生成する
- 境界削除要求を受け取る
- boundaryIdに対応するBoundaryPairを削除する
- CornerEditViewとWidthDistributionBarViewを再同期する
- BandCorrectionEngineを呼び出す
- outputAspectRatioを保持する
- outputAspectRatioからOutputSettingsを生成する
- 補正結果をResultPreviewViewへ設定する
- 出力外枠の縦横比変更イベントを受け取る
- ドラッグ終了時に補正プレビューを再生成する
- 保存ボタンの表示状態と有効状態を管理する
- Android 9以前のWRITE_EXTERNAL_STORAGE権限要求を行う
- correctedBitmapをJPEG品質95で圧縮する
- MediaStoreへ保存し、Android 10以降ではPictures/BandSplitScannerへ公開する
- 保存成功・失敗をToastで通知する
```

境界追加時には、新しい `outputX` の左右に隣接する境界を求め、入力側の上端点と下端点をそれぞれ線形補間して、新しい `BoundaryPair` を生成する。

左右端境界は、追加位置探索と補間計算において次の仮想的な出力位置を持つ境界として扱う。

```text
左端境界 = outputX 0.0
右端境界 = outputX 1.0
```

現段階では `MainActivity` が状態同期と境界追加・削除のオーケストレーションを担当する。

ただし、本格MVPではMainActivityを薄くし、状態保持と画面ごとの処理をViewModelとFragmentへ移す。

---

### 5.2 CornerEditView

`CornerEditView` は、現在の元画像編集UIを担当する。

```text
- 元画像Bitmap表示
- PageCorners保持
- BoundaryPair一覧保持
- 画像座標とView座標の変換
- 左右端境界描画
- 分割ライン描画
- ライン端点描画
- 上下の端点接続線描画
- 補正帯枠の視覚化
- ページ基準点4点のドラッグ
- 左右端境界線本体の平行移動
- 分割ライン端点の個別移動
- 分割ライン本体の平行移動
- outputX変更の反映
```

現時点では `PageCorners` と `BoundaryPair` の編集状態の正本が実質的にこのView内にある。

これは検証版としての暫定設計であり、本格MVPでは編集状態をViewModelへ移す。

---

### 5.3 WidthDistributionBarView

`WidthDistributionBarView` は、出力境界位置の編集と、境界追加・削除操作の入力受付を担当するViewである。

責務は以下。

```text
- バー描画
- 境界マーカー描画
- ヒット判定
- マーカードラッグ
- outputX更新
- 境界順序制約
- 最小間隔制約
- 長押しジェスチャの判定
- 空き領域と既存マーカーの判別
- 長押し位置付近へのコンテキストメニュー表示
- 境界追加要求イベント通知
- 境界削除要求イベント通知
- outputX変更イベント通知
```

内部状態として `BoundaryPair` 全体は保持しない。

必要な情報だけを `BoundaryMarker` として保持する。

```text
BoundaryMarker
├ boundaryId
└ outputX
```

これにより、幅配分バーは入力座標を知らない。

```text
WidthDistributionBarViewが直接変更してよい状態
= BoundaryMarker.outputX

WidthDistributionBarViewが直接変更してはいけない状態
= BoundaryPair.inputTop
= BoundaryPair.inputBottom
= BoundaryPair一覧そのもの
```

境界追加時、Viewは新しい `BoundaryPair` を生成しない。

```text
WidthDistributionBarView
↓
onBoundaryAddRequested(outputX)
↓
MainActivity
↓
BoundaryPair生成
```

境界削除時も、View内部のマーカーだけを先に削除しない。

```text
WidthDistributionBarView
↓
onBoundaryDeleteRequested(boundaryId)
↓
MainActivity
↓
BoundaryPair削除
↓
最新BoundaryPair一覧からBoundaryMarkerを再同期
```

この制約により、境界状態の部分的な不整合を防ぐ。

---

### 5.4 ResultPreviewView

`ResultPreviewView` は補正結果の表示と、出力外枠の直接操作を担当する。

```text
- 補正後Bitmap表示
- 出力境界線オーバーレイ描画
- 出力外枠オーバーレイ描画
- outputX変更の表示反映
- 補正結果プレビューのピンチズーム
- 外枠の辺以外の1本指パン
- ピンチ中心を維持する表示位置補正
- パン・ピンチ後の最低48dp可視制約
- 補正後Bitmap、出力境界線、出力外枠への共通表示変換
- 出力外枠の左右辺のヒット判定
- 操作対象となる左右辺の識別
- 左右辺ドラッグ中の一時外枠更新
- 最小・最大縦横比による外枠可動範囲の制限
- 出力縦横比変更イベント通知
```

左右辺のドラッグを実装済みである。

```text
右辺ドラッグ
→ 左辺固定

左辺ドラッグ
→ 右辺固定
```

左右のヒット領域が重なる場合は、タッチ位置に近い辺を操作対象とする。

`ResultPreviewView` はドラッグ中の一時的な表示矩形を保持するが、確定した出力縦横比の正本にはしない。

```text
ResultPreviewView
→ aspectRatio + isFinishedを通知

MainActivity
→ outputAspectRatioを更新
→ ドラッグ終了時に補正結果を再生成
```

出力境界線と出力外枠は補正Bitmapへ描き込まない。

保存対象と表示補助を分離する。

補正結果プレビューの表示状態として、以下を出力設定から分離して保持する。

```text
previewZoomScale
previewPanX
previewPanY
```

これらは画面上の見え方だけを変更し、`OutputSettings` や `outputAspectRatio` には反映しない。

---

### 5.5 Correction Engine

補正処理はUIから分離する。

```text
BandCorrectionEngine
↓
BandRenderer
├ PerspectiveBandRenderer
└ ScanlineBandRenderer
```

UI側は、補正アルゴリズムの詳細を知らない。

`BandCorrectionEngine` は、ページ基準点から縦横比を推定する従来の入口に加え、`OutputSettings` を直接受け取る入口を持つ。

```text
createResult(..., outputWidth)
→ ページ基準点から出力高さを推定

createResult(..., OutputSettings)
→ 指定された出力幅・高さで生成
```

補正エンジン側も以下を知らない。

```text
- Activity
- Fragment
- View
- Button
- MotionEvent
- View座標
- タッチ中の操作対象
```

---

## 6. データモデル

### 6.1 PageCorners

元画像上のページ基準点を表す。

```text
PageCorners
├ topLeft
├ topRight
├ bottomRight
└ bottomLeft
```

各点は画像座標で保持する。

左右端境界は以下から構成される。

```text
左端境界
= topLeft → bottomLeft

右端境界
= topRight → bottomRight
```

---

### 6.2 BoundaryPair

1つの内部境界について、入力側と出力側の対応を保持する。

```text
BoundaryPair
├ id
├ outputX
├ inputTop
└ inputBottom
```

意味は以下。

```text
id
= 境界を一意に識別するID

outputX
= 補正後画像上の正規化横位置
= 0.0〜1.0

inputTop
= 元画像上の入力境界上端点
= 画像座標

inputBottom
= 元画像上の入力境界下端点
= 画像座標
```

`BoundaryPair` は、補正処理へ渡す完全な境界状態である。

---

### 6.3 BoundaryMarker

幅配分バーで必要な最小状態を表す。

```text
BoundaryMarker
├ boundaryId
└ outputX
```

`BoundaryMarker` は `BoundaryPair` の表示・操作用射影であり、入力境界座標を持たない。

このモデルの目的は、Viewの責務を制限することである。

---

### 6.4 BoundaryLine

補正処理時に使用する境界表現。

```text
BoundaryLine
├ outputX
├ inputTop
└ inputBottom
```

補正処理時には、

```text
左端境界
+ BoundaryPair一覧
+ 右端境界
```

を `BoundaryLine` の列へ変換する。

---

### 6.5 OutputSettings

補正後画像の出力サイズを保持する。

現在は主に以下を扱う。

```text
- outputWidth
- outputHeight
```

将来的には縦横比管理を明示的に追加してもよい。

---

## 7. 現在の状態の流れ

### 7.1 画像選択

```text
Activity Result API
↓
画像URI
↓
ContentResolver.openInputStream
↓
BitmapFactory.decodeStream
↓
sourceBitmap
↓
CornerEditView
```

現在はURIを長期状態として保持せず、Bitmapを直接保持する。

---

### 7.2 入力境界編集

```text
ユーザー操作
↓
CornerEditView
↓
PageCorners または BoundaryPair.inputTop/inputBottom を更新
↓
invalidate
↓
元画像編集オーバーレイを再描画
```

入力境界編集では `outputX` を変更しない。

---

### 7.3 幅配分変更

```text
ユーザーが境界マーカーをドラッグ
↓
WidthDistributionBarView
↓
BoundaryMarker.outputXを更新
↓
boundaryId + outputX を通知
↓
MainActivity
├ CornerEditViewの対応BoundaryPair.outputXを更新
└ ResultPreviewViewの表示状態へ反映
```

この経路では `inputTop` / `inputBottom` を変更しない。

---

### 7.4 境界追加

```text
ユーザーが幅配分バーの空き領域を長押し
↓
WidthDistributionBarView
↓
長押し位置付近にコンテキストメニュー表示
↓
「ここに境界を追加」
↓
onBoundaryAddRequested(outputX)
↓
MainActivity
↓
既存BoundaryPairをoutputX順に確認
↓
追加位置の左右に隣接する境界を取得
↓
隣接境界間の補間比率tを計算
↓
inputTop / inputBottomを線形補間
↓
新しいBoundaryPairを生成
↓
BoundaryPair一覧へ追加してoutputX順に整列
↓
CornerEditViewへ反映
↓
WidthDistributionBarViewへBoundaryMarkerを再同期
↓
必要に応じて補正結果を再生成
```

補間比率は次の通り。

```text
t =
    (newOutputX - leftOutputX)
    /
    (rightOutputX - leftOutputX)
```

入力境界位置は次のように生成する。

```text
newInputTop =
    lerp(leftInputTop, rightInputTop, t)

newInputBottom =
    lerp(leftInputBottom, rightInputBottom, t)
```

内部境界が存在しない側では、ページの左端境界を `outputX = 0.0`、右端境界を `outputX = 1.0` として使用する。

---

### 7.5 境界削除

```text
ユーザーが境界マーカーを長押し
↓
WidthDistributionBarView
↓
長押し位置付近にコンテキストメニュー表示
↓
「削除」
↓
onBoundaryDeleteRequested(boundaryId)
↓
MainActivity
↓
boundaryIdに対応するBoundaryPairを削除
↓
CornerEditViewへ反映
↓
WidthDistributionBarViewへBoundaryMarkerを再同期
↓
必要に応じて補正結果を再生成
```

削除時には、残存する他の境界の `outputX`、`inputTop`、`inputBottom` を変更しない。

削除された境界の左右に存在していた境界が新しく隣接し、その間が1つの補正帯として扱われる。

---

### 7.6 補正結果生成

```text
MainActivity
↓
CornerEditViewからPageCorners取得
↓
CornerEditViewからBoundaryPair一覧取得
↓
BandCorrectionEngine
↓
BoundaryLine列生成
↓
BandRenderer
↓
補正後Bitmap
↓
ResultPreviewView
```

---

### 7.7 補正結果画面での幅調整

```text
境界マーカーをドラッグ
↓
outputXを更新
↓
出力境界線を再描画
↓
指を離す
↓
現在状態で補正結果を再生成
```

ドラッグ中の軽い表示更新と、ドラッグ終了時の重い補正処理を分ける。

---

### 7.8 出力外枠による縦横比変更

```text
ユーザーが出力外枠の左辺または右辺をドラッグ
↓
ResultPreviewView
↓
一時的な出力外枠だけを更新
↓
aspectRatio + isFinishedを通知
↓
MainActivity
↓
outputAspectRatioを更新
↓
isFinished = true の場合
↓
OutputSettingsを生成
↓
BandCorrectionEngine
↓
補正結果Bitmapを再生成
```

左右辺のドラッグを実装済み。

```text
右辺操作
→ leftを固定してrightを更新

左辺操作
→ rightを固定してleftを更新
```

どちらの操作でも、ViewからMainActivityへ通知する値は最終的な `aspectRatio` と `isFinished` で共通とする。

ドラッグ可能範囲は、View端ではなく `MIN_OUTPUT_ASPECT_RATIO` と `MAX_OUTPUT_ASPECT_RATIO` だけで制限する。左辺操作では右辺、右辺操作では左辺を固定し、固定側の辺と出力高さから可動範囲を計算する。

---

### 7.9 補正結果プレビューのズーム・パン

補正結果画面には、表示専用のズーム・パン状態を実装済みである。

```text
ResultPreviewView
├ outputAspectRatio
│  └ 補正結果の内容・形状に関係する
└ previewViewport
   ├ zoomScale
   ├ panX
   └ panY
      └ 画面上の見え方だけに関係する
```

出力外枠編集とプレビュー表示変換を分離することで、画面端に依存せず出力外枠を拡張できる。

補正後Bitmap、出力境界線、出力外枠へ同じ `bitmapToViewMatrix` を適用し、逆変換には `viewToBitmapMatrix` を使用する。

操作の優先順位は以下。

```text
1. 出力外枠の左辺・右辺
2. 外枠の辺以外の1本指パン
3. 2本指ピンチズーム
```

2本目の指が置かれた場合は、進行中の外枠ドラッグまたはパンを解除してピンチ操作へ移行する。

パンとピンチのどちらでも、変換後の画像表示矩形へ同じ表示位置制約を適用する。

```text
minimumVisiblePx
= 48dpを端末densityでpxへ変換した値

minimumVisibleWidth
= min(minimumVisiblePx, displayedWidth)

minimumVisibleHeight
= min(minimumVisiblePx, displayedHeight)
```

許可する表示矩形は以下を満たす。

```text
imageRect.right  >= minimumVisibleWidth
imageRect.left   <= viewWidth - minimumVisibleWidth

imageRect.bottom >= minimumVisibleHeight
imageRect.top    <= viewHeight - minimumVisibleHeight
```

この制約は、パン移動後、ピンチ倍率更新後、ピンチ終了時に共通して適用する。

ピンチ中は、倍率変更前にフォーカス位置の画像座標を取得し、倍率変更後も同じView位置へ来るようパン量を補正する。その後、最低表示量を満たす範囲へ最終的にクランプする。

---

### 7.10 補正結果の保存

現在の最小保存経路は以下。

```text
ユーザーが保存ボタンを押す
↓
MainActivity
↓
現在のcorrectedBitmapを取得
↓
JPEG品質95で圧縮
↓
MediaStoreへ登録
↓
Android 10以降
├ RELATIVE_PATH = Pictures/BandSplitScanner
└ IS_PENDINGを解除して公開
↓
Android 9以前
└ WRITE_EXTERNAL_STORAGE権限を確認して保存
```

保存対象は `correctedBitmap` だけであり、`ResultPreviewView` のCanvasへ描画している次の表示要素は保存対象に含めない。

```text
- 出力境界線
- 出力外枠
- ズーム・パンによる表示変換
```

幅配分バーと境界マーカーも別Viewであるため、保存画像には含まれない。

保存途中で失敗した場合は、挿入済みのMediaStore項目を削除する。

この経路は保存方法を検証するための暫定実装であり、保存画像は幅 `1200px` のプレビュー用 `correctedBitmap` である。保存時の高解像度再生成は別段階で実装する。

---

## 8. 座標系

本アプリでは、以下の座標系を明確に区別する。

### 8.1 画像座標

元画像Bitmap上のピクセル座標。

```text
PageCorners
BoundaryPair.inputTop
BoundaryPair.inputBottom
```

は画像座標で保持する。

---

### 8.2 View座標

Android画面上の表示座標。

Viewサイズ、画像拡縮、パン状態によって変わるため、編集状態の保存には使用しない。

---

### 8.3 出力座標

補正後Bitmap上のピクセル座標。

補正レンダラーが処理時に使用する。

### 8.4 補正結果プレビュー座標

`ResultPreviewView` 上の表示座標。

```text
出力座標
↓ resultToViewMatrix
ズーム・パン後の補正結果プレビュー座標
```

出力外枠の縦横比とプレビュー座標は分離する。

プレビューのズーム・パンによって `OutputSettings` や `outputAspectRatio` を変更しない。

---

### 8.5 共通の表示位置制約

`CornerEditView` と `ResultPreviewView` は、パン・ピンチ後の画像位置に同じ考え方の制約を使う。

```text
最低可視量 = 48dp
```

実装では `DisplayMetrics.density` を使ってpxへ変換する。

```text
minimumVisiblePx
= 48f * density
```

画像の表示サイズが `minimumVisiblePx` より小さい方向では、画像全体を可視範囲として扱う。

表示位置制約を行う処理は、`constrainPan()` よりも意味の広い、次のような責務として扱う。

```text
constrainImagePosition()
または
constrainViewport()
```

この処理はパン専用ではなく、以下から共通して呼び出す。

```text
- 1本指パンの更新後
- ピンチ倍率の更新後
- ピンチ中心維持のためのpan補正後
- ピンチ終了時
- Viewサイズ変更後
```

---

### 8.6 正規化出力座標

`BoundaryPair.outputX` が使用する。

```text
0.0 = 補正後画像左端
1.0 = 補正後画像右端
```

実座標は以下で計算する。

```text
actualOutputX = outputX * outputWidth
```

出力サイズを変更しても相対位置を維持できる。

---

## 9. Custom Viewの設計ルール

Custom Viewは以下を担当してよい。

```text
- 描画
- 座標変換
- ヒット判定
- ジェスチャ解釈
- ドラッグ中の一時状態
- 表示に必要な局所状態
- 操作結果の通知
```

一方、以下を避ける。

```text
- 他のViewが所有する状態を勝手に変更する
- 自分の責務に不要なモデル全体をコピー保持する
- 補正処理そのものをViewへ書く
- View座標を永続的な編集状態として扱う
- View同士でモデル全体を直接上書きし合う
```

`WidthDistributionBarView` が `BoundaryMarker` のみを保持する設計は、この原則の具体例である。

---

## 10. タッチ操作の設計

元画像編集Viewでは、操作対象の優先順位を明確にする。

現在および将来の基本方針は以下。

```text
1. 操作点
   - ページ基準点
   - 分割ライン端点

2. 入力境界線本体
   - 左端境界
   - 右端境界
   - 分割ライン

3. 空き領域
   - 将来: パン

4. 2本指操作
   - 将来: ピンチズーム
```

線本体の移動は、2端点へ同じ移動量を適用する。

画像端で制限する場合は、各点を個別にclampして線形状を壊すのではなく、2点に共通して適用できる移動量を求める。

---

## 11. 補正アーキテクチャ

### 11.1 BandCorrectionEngine

補正処理の入口。

現在の責務は以下。

```text
- PageCornersから左右端BoundaryLineを生成
- BoundaryPairをoutputX順に並べる
- 内部BoundaryLineを生成
- 出力サイズを決める
- BandRendererを呼び出す
```

---

### 11.2 BandRenderer

補正方式を差し替えるためのインターフェース。

```text
BandRenderer
├ PerspectiveBandRenderer
└ ScanlineBandRenderer
```

新しい補正方式を追加するときは、可能な限りエンジン本体ではなくRendererとして追加する。

---

### 11.3 PerspectiveBandRenderer

各補正帯について、入力四角形を出力長方形へ射影変換する方式。

比較用・検証用として残す。

---

### 11.4 ScanlineBandRenderer

現在の主方式。

出力画素ごとに元画像座標を計算する。

帯内座標を `u`, `v` とすると、概念的には以下。

```text
L(v)
= 左入力境界の上端から下端への補間点

R(v)
= 右入力境界の上端から下端への補間点

source(u, v)
= L(v) と R(v) の補間点
```

隣接帯は共有境界上で同じ `v` に対して同じ入力座標を参照するため、位置の連続性を保ちやすい。

---

## 12. Bitmapと画像URIの方針

### 12.1 現在

現在は以下。

```text
MainActivity
├ sourceBitmap
└ correctedBitmap
```

画像選択時に原画像をBitmapへ読み込んでいる。

補正結果の確認時には、幅 `1200px` の `correctedBitmap` を生成する。

現在の保存処理は、この `correctedBitmap` をMainActivityがJPEGへ圧縮してMediaStoreへ保存する。

これは保存経路を確認する検証版として許容するが、プレビュー表示用Bitmapと最終保存用Bitmapはまだ分離されていない。

---

### 12.2 将来

本格MVPでは、主状態をBitmapからURIと編集データへ移す。

```text
主状態
├ selectedImageUri
├ PageCorners
├ BoundaryPair一覧
├ OutputSettings
└ ResultViewSettings
```

Bitmapは必要に応じて生成する。

```text
編集中
→ 低解像度previewBitmap

結果確認
→ correctedPreviewBitmap

保存時
→ URIから高解像度Bitmapを再読込
→ 現在の編集状態で最終補正
→ 保存
```

---

## 13. 本格MVPで目指す構成

機能が増えた段階では、以下を目標とする。

```text
MainActivity
= アプリの入口
= Navigationホスト
= Activity Result APIの受け口

Fragment
= 各画面の表示制御
= ViewとViewModelの接続
= ボタン操作
= 補正処理の起動タイミング制御

ScanEditViewModel
= 編集状態の正本

Custom View
= 描画とタッチ操作

Correction Engine
= UI非依存の画像補正

ImageLoader / ImageSaver
= 画像の読込・縮小・保存
```

想定画面は以下。

```text
ImageSelectFragment
↓
PageCornerFragment
↓
SourceEditFragment
↓↑
ResultFragment
```

ただし、現在の検証版を動かしながら段階的に移行する。

---

## 14. 将来の状態管理

将来は `ScanEditViewModel` を編集状態の正本とする。

概念的な状態は以下。

```text
ScanEditState
├ selectedImageUri
├ pageCorners
├ boundaryPairs
├ outputSettings
│  └ outputAspectRatio
└ resultViewSettings
   ├ showOutputBoundaryLines
   ├ previewZoomScale
   ├ previewPanX
   └ previewPanY
```

Custom Viewからの操作はイベントとして通知する。

例:

```text
SourceEditView
↓ onBoundaryInputChanged(id, top, bottom)
ViewModel
↓ state更新
SourceEditFragment
↓ 必要なViewへ反映
```

幅配分バーの場合:

```text
WidthDistributionBarView
↓ onBoundaryOutputChanged(id, outputX)
ViewModel
↓ BoundaryPair.outputXのみ更新
SourceEditView / ResultPreviewView
↓ 最新状態を反映
```

View同士を直接同期元にしない。

---

## 15. 段階的移行方針

### 第1段階: 現在の編集機能を完成させる

現在のMainActivity中心構成を維持したまま、MVP操作を完成させる。

境界追加・削除、補正前・補正後画面のズーム・パン、両画面の最低48dp可視制約、出力境界線表示切り替え、出力外枠表示、View端に依存しない左右辺ドラッグによる縦横比変更、現在のプレビュー用補正BitmapのMediaStore保存は実装済み。

残る主な作業は以下。

```text
- 保存時の保存用Bitmap再生成
- 必要に応じた低解像度プレビュー生成
- 保存用高解像度画像の再読込
- 画像入出力責務の分離
- 状態管理の段階的な分離
```

---

### 第2段階: 画像入出力を分離する

```text
ImageLoader
ImageSaver
```

を追加する。

目的は以下。

```text
- MainActivityから画像IOを外す
- プレビュー用縮小読込
- 高解像度保存用再読込
- URI中心の管理への移行準備
```

---

### 第3段階: 編集状態をView外へ出す

`PageCorners` と `BoundaryPair` の正本をViewから分離する。

この時点で `ScanEditViewModel` または同等の状態保持クラスを導入する。

---

### 第4段階: Custom ViewをXML配置対応にする

画像をコンストラクタ必須にせず、後から設定できる形にする。

例:

```text
sourceEditView.setBitmap(previewBitmap);
sourceEditView.setPageCorners(pageCorners);
sourceEditView.setBoundaryPairs(boundaryPairs);
```

---

### 第5段階: 画面をFragmentへ分割する

```text
ImageSelectFragment
PageCornerFragment
SourceEditFragment
ResultFragment
```

へ段階的に分割する。

共有編集状態はViewModelを通して参照する。

---

## 16. 将来の推奨パッケージ構成

```text
com.example.bandsplitscanner
├ MainActivity.java
│
├ model/
│  ├ ScanEditState.java
│  ├ PageCorners.java
│  ├ BoundaryPair.java
│  ├ BoundaryMarker.java
│  ├ BoundaryLine.java
│  ├ OutputSettings.java
│  └ ResultViewSettings.java
│
├ view/
│  ├ PageCornerEditView.java
│  ├ SourceEditView.java
│  ├ WidthDistributionBarView.java
│  └ ResultPreviewView.java
│
├ ui/
│  ├ ImageSelectFragment.java
│  ├ PageCornerFragment.java
│  ├ SourceEditFragment.java
│  └ ResultFragment.java
│
├ viewmodel/
│  └ ScanEditViewModel.java
│
├ correction/
│  ├ BandCorrectionEngine.java
│  ├ BandRenderer.java
│  ├ BandCorrectionMath.java
│  ├ PerspectiveBandRenderer.java
│  └ ScanlineBandRenderer.java
│
└ image/
   ├ ImageLoader.java
   └ ImageSaver.java
```

この構成は最終目標の目安であり、機能規模に応じて必要な段階まで導入する。

---

## 17. 設計上の重要ルール

### 17.1 入力境界と出力境界を別操作として扱う

```text
元画像編集
→ inputTop / inputBottom

幅配分バー
→ outputX
```

一方の操作で、他方の値を意図せず上書きしない。

---

### 17.2 IDで部分更新する

状態同期はモデル全体の上書きではなく、可能な限り境界IDによる部分更新を使う。

```text
悪い例
古いBoundaryPair全体で現状態を上書き

良い例
boundaryIdに対応するoutputXだけ更新
```

---

### 17.3 コピーの目的を明確にする

Viewへコピーを渡す場合、そのコピーは表示用・操作用の局所状態とする。

コピー元へ戻すときは、Viewが変更する責務を持つ値だけを返す。

---

### 17.4 補正処理をUIから独立させる

Correction Engineの入力は画像座標と出力設定のみとする。

---

### 17.5 View座標を編集状態にしない

View座標は端末サイズ、向き、ズーム、パンで変化する。

編集状態は画像座標または正規化座標で保持する。

---

### 17.6 重い処理と軽い表示更新を分離する

例:

```text
マーカードラッグ中
→ 線の位置だけ再描画

指を離す
→ 補正画像を再生成
```

出力外枠操作でも同じ考え方を使う。

```text
出力外枠ドラッグ中
→ 一時外枠だけ再描画

指を離す
→ 新しいOutputSettingsで補正画像を再生成
```

---

## 18. 現在の設計上の評価

現在の構成は最終形ではないが、検証版として以下が成立している。

```text
- 補正処理がUIから分離されている
- 補正方式をRendererで差し替えられる
- 入力境界と出力境界をBoundaryPairで対応付けられる
- 幅配分バーの状態をBoundaryMarkerへ限定できている
- 境界IDによる部分更新が導入されている
- 境界追加・削除をIDベースのイベント経路で同期できている
- 境界追加時に隣接入力境界間の局所補間を行える
- 元画像座標とView座標を分離している
- 補正画像と表示オーバーレイを分離している
- OutputSettingsを明示指定して補正結果を生成できる
- 出力外枠ドラッグ中の軽量更新と補正再生成を分離できている
- 出力縦横比と補正結果プレビューの表示状態を分離できている
- 補正前・補正後のパンとピンチに最低48dp可視制約を適用できている
- 補正後Bitmap、出力境界線、出力外枠へ共通の表示変換を適用できている
- 出力外枠の操作範囲をView端から分離し、縦横比制約だけで決定できている
- 補正Bitmapと編集用オーバーレイを分離したままMediaStoreへ保存できている
- Androidのバージョン差を考慮した最小保存経路を確認できている
```

今後の大きな設計課題は、MainActivityとCornerEditViewに集中している状態管理と、MainActivityへ追加された画像保存責務を、高解像度画像対応・撮影・画面分割のタイミングで段階的に外へ出すことである。

現段階では、最終構成への一括リファクタリングより、保存用Bitmapの再生成、プレビュー用画像・保存用画像の分離、ImageLoader / ImageSaverへの画像入出力分離を検証しながら、必要な単位で責務分離を進める。
