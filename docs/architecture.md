# Band Split Scanner アーキテクチャ設計書

更新日: 2026-07-24

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

現在は、単一Activity中心のUI構成を維持しつつ、補正処理と画像入出力処理を専用クラスへ分離した構成である。

```text
MainActivity
├ 画面状態と編集フローを管理
├ ImageLoaderを呼び出す
├ CornerEditViewとWidthDistributionBarViewを同期
├ BandCorrectionEngineを呼び出す
├ ResultPreviewViewを表示
├ SaveRequestを生成
├ 単一Executorで保存処理を実行
├ ImageSaverを呼び出す
└ 元画像編集画面と結果画面を切り替える

ImageLoader
├ 画像寸法取得
├ 編集用縮小Bitmap読込
└ 保存用原画像Bitmap読込

ImageSaver
├ JPEG圧縮
├ MediaStore登録
├ 保存画像公開
└ 失敗時削除

ScanlineBandRenderer
└ float直接補間による画素生成
```

画面表示は `FrameLayout` 内のCustom Viewを差し替える方式を維持する。

```text
元画像編集時
FrameLayout
└ CornerEditView

補正結果表示時
FrameLayout
└ ResultPreviewView
```

保存時には次の流れを使用する。

```text
selectedImageUri
↓ ImageLoader
原画像Bitmap
↓ 編集座標を原画像座標へ変換
↓ BandCorrectionEngine
保存用Bitmap
↓ ImageSaver
MediaStore
```

画像入出力の責務分離は完了したが、編集状態の正本はまだ `CornerEditView` 内にある。

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

---

## 5. 現在の責務分担


### 5.1 MainActivity

`MainActivity` は画面全体のオーケストレーションを担当する。

```text
- Activity Result APIによる画像選択
- selectedImageUriと原画像寸法の保持
- sourceBitmap / correctedBitmapの保持
- ImageLoaderの呼び出し
- CornerEditViewの生成
- 表示Viewの切り替え
- WidthDistributionBarViewとの同期
- BoundaryPairの追加・削除
- BandCorrectionEngineの呼び出し
- outputAspectRatioの保持
- 補正結果プレビュー再生成
- 保存ボタン状態の管理
- Android 9以前の保存権限要求
- SaveRequest生成
- 単一Executorによる保存処理
- 保存スレッドの優先度設定
- 編集用座標から原画像座標への変換
- 保存用OutputSettingsの生成
- 保存用Bitmapの生成
- ImageSaverの呼び出し
- 完了・失敗・メモリ不足のUI通知
- 一時Bitmap解放
- Activity破棄時のExecutor終了
```

画像のデコード手順とMediaStore操作は担当しない。

保存開始時には、UIから必要な状態を取得して `SaveRequest` へまとめ、バックグラウンド処理へ渡す。

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

### 5.6 ImageLoader

画像読込処理を担当する。

```text
- ContentResolverからInputStreamを取得
- inJustDecodeBoundsによる画像寸法取得
- inSampleSize計算
- 編集用縮小Bitmap読込
- 保存用原画像Bitmap読込
```

UI状態や編集状態は保持しない。

---

### 5.7 ImageSaver

画像保存処理を担当する。

```text
- MediaStoreへの保存先登録
- JPEG圧縮
- Android 10以降のRELATIVE_PATH設定
- Android 10以降のIS_PENDING解除
- 保存失敗時のMediaStore項目削除
```

権限要求、ファイル名生成、完了Toastは `MainActivity` が担当する。

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

### 7.1 画像選択と編集用縮小読込

```text
Activity Result API
↓
selectedImageUri
↓
MainActivity
↓ ImageLoader.loadPreview(uri, 2048)
ImageLoader
├ 画像寸法取得
├ inSampleSize計算
└ 編集用縮小Bitmap読込
↓
LoadedImage
├ bitmap
├ originalWidth
└ originalHeight
↓
MainActivityが状態として保持
↓
CornerEditView
```

保存時の原画像読込には `ImageLoader.loadOriginal()` を使用する。

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

現在の保存経路は以下。

```text
保存ボタン
↓
MainActivity
↓ SaveRequestを生成
↓ saveInProgress = true
↓ 保存ボタンを無効化
↓
単一Executor
↓ THREAD_PRIORITY_BACKGROUND
↓
ImageLoader.loadOriginal(imageUri)
↓
編集用画像から原画像へのscaleX / scaleYを計算
↓
PageCornersとBoundaryPairの入力端点を原画像座標へ変換
↓
保存用OutputSettingsを生成
↓
BandCorrectionEngine
↓
保存用Bitmap
↓
ImageSaver.saveJpeg()
├ MediaStore登録
├ JPEG品質95で圧縮
├ Android 10以降のIS_PENDING解除
└ 失敗時の登録削除
↓
一時Bitmapを解放
↓
UIスレッドへ完了または失敗を通知
```

プレビュー補正は幅1200pxの `correctedBitmap` を使用する。

編集用入力は長辺最大2048pxの `sourceBitmap` を使用する。

保存サイズは最大400万画素・長辺4096px以内へ縮小する。

Android 9以前の権限要求、保存ファイル名生成、完了Toastは `MainActivity` が担当する。

---

## 8. 座標系


本アプリでは、以下の座標系を明確に区別する。

### 8.1 編集用画像座標

長辺最大2048pxへ縮小した `sourceBitmap` 上のピクセル座標。

現在、次の編集状態はこの座標系で保持する。

```text
PageCorners
BoundaryPair.inputTop
BoundaryPair.inputBottom
```

---

### 8.2 原画像座標

`selectedImageUri` から保存時に等倍読込したBitmap上のピクセル座標。

保存時には編集用画像座標を次の倍率で原画像座標へ変換する。

```text
scaleX
= originalWidth / previewWidth

scaleY
= originalHeight / previewHeight

originalX
= previewX * scaleX

originalY
= previewY * scaleY
```

保存用の `PageCorners` と `BoundaryPair` はコピーを生成して変換し、CornerEditView内の編集状態は変更しない。

---

### 8.3 View座標

Android画面上の表示座標。

Viewサイズ、画像拡縮、パン状態によって変わるため、編集状態や保存状態には使用しない。

---

### 8.4 出力座標

補正後Bitmap上のピクセル座標。

補正レンダラーが処理時に使用する。

---

### 8.5 補正結果プレビュー座標

`ResultPreviewView` 上の表示座標。

```text
出力座標
↓ bitmapToViewMatrix
ズーム・パン後の補正結果プレビュー座標
```

出力外枠の縦横比とプレビュー座標は分離する。

プレビューのズーム・パンによって `OutputSettings` や `outputAspectRatio` を変更しない。

---

### 8.6 共通の表示位置制約

`CornerEditView` と `ResultPreviewView` は、パン・ピンチ後の画像位置に同じ考え方の制約を使う。

```text
最低可視量 = 48dp
```

実装では `DisplayMetrics.density` を使ってpxへ変換する。

表示画像が最低可視量より小さい方向では、実際の表示サイズを上限として扱う。

この制約は、パン更新後、ピンチ倍率更新後、ピンチ中心維持の補正後、ピンチ終了時、Viewサイズ変更後に適用する。

---

### 8.7 正規化出力座標

`BoundaryPair.outputX` が使用する。

```text
0.0 = 補正後画像左端
1.0 = 補正後画像右端
```

実座標は以下で計算する。

```text
actualOutputX = outputX * outputWidth
```

`outputX` は入力画像の解像度に依存しないため、保存時の編集用画像座標から原画像座標への変換でも変更しない。

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

出力画素ごとに元画像座標を計算し、バイリニア補間で画素値を取得する。

帯内座標を `u`, `v` とすると、概念的には以下。

```text
L(v)
= 左入力境界上の点

R(v)
= 右入力境界上の点

source(u, v)
= L(v) + (R(v) - L(v)) * u
```

現在の実装では、画素ループ内で補間用 `PointF` を生成しない。

```text
各行
├ leftX / leftY
├ rowDeltaX / rowDeltaY
└ outputRowOffset

各画素
├ sourceX = leftX + rowDeltaX * u
└ sourceY = leftY + rowDeltaY * u
```

この構成により、数百万画素を処理するときの一時オブジェクト生成とGC負荷を抑える。

隣接帯は共有境界上で同じ入力座標を参照するため、位置の連続性を保ちやすい。

---

## 12. Bitmapと画像URIの方針

### 12.1 現在

現在の主な画像状態は以下。

```text
MainActivity
├ selectedImageUri
├ selectedImageWidth
├ selectedImageHeight
├ sourceBitmap
└ correctedBitmap

保存処理中
├ saveSourceBitmap
└ saveBitmap
```

各役割は以下。

```text
selectedImageUri
= 原画像を再取得するための参照

sourceBitmap
= 長辺最大2048pxの編集・プレビュー入力

correctedBitmap
= 幅1200pxの補正結果プレビュー

saveSourceBitmap
= ImageLoaderが保存中だけ読み込む原画像

saveBitmap
= 保存中だけ生成する最終出力
```

読込は `ImageLoader`、保存は `ImageSaver` が担当する。

BitmapとURIの保持はまだ `MainActivity` が担当する。

---

### 12.2 次の移行

次は編集状態の正本をCustom Viewから分離する。

```text
ScanEditState
├ PageCorners
├ BoundaryPair一覧
└ outputAspectRatio
```

その後、必要に応じて以下も状態保持層へ移す。

```text
- selectedImageUri
- originalImageSize
- showOutputBoundaryLines
- 保存中状態
```

Bitmapは必要に応じて生成・解放する派生データとして扱う。

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

### 第1段階: 主要な編集・保存経路を完成させる

実装・動作確認済み。

```text
- 境界追加・削除
- 補正前・補正後画面のズーム・パン
- 出力境界線表示切り替え
- 出力外枠による縦横比変更
- 編集用縮小Bitmap
- 保存時の原画像再読込
- 座標変換
- 非同期保存
- レンダラー最適化
```

---

### 第2段階: 画像入出力を分離する

実装・動作確認済み。

```text
ImageLoader
= 画像寸法取得
= 編集用縮小読込
= 保存用原画像読込

ImageSaver
= JPEG圧縮
= MediaStore登録
= 保存画像公開
= 失敗時削除
```

---

### 第3段階: 編集状態をView外へ出す

次に進める段階。

まず `ScanEditState` を追加し、以下をまとめる。

```text
- PageCorners
- BoundaryPair一覧
- outputAspectRatio
```

その後、`CornerEditView` を状態の正本から描画・操作入力中心のViewへ移行する。

---

### 第4段階: 保存ジョブのライフサイクルを整理する

保存処理の所有者、キャンセル、画面回転後の完了通知を整理する。

---

### 第5段階: Custom ViewをXML配置対応にする

画像や編集状態をコンストラクタ必須にせず、後から設定できる形にする。

---

### 第6段階: 画面をFragmentへ分割する

```text
ImageSelectFragment
PageCornerFragment
SourceEditFragment
ResultFragment
```

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

編集操作中は軽い表示更新だけを行い、確定時に補正を再生成する。

```text
マーカードラッグ中
→ 線の位置だけ再描画

指を離す
→ 補正画像を再生成
```

出力外枠操作も同様。

```text
出力外枠ドラッグ中
→ 一時外枠だけ再描画

指を離す
→ 新しいOutputSettingsで補正画像を再生成
```

保存処理では、プレビュー表示用Bitmapと保存用Bitmapを分ける。

```text
結果確認
→ 幅1200pxのcorrectedBitmap

保存
→ URIから原画像を再読込
→ 現在の編集状態を原画像座標へ変換
→ saveBitmapを生成
→ 保存後に一時Bitmapを解放
```

保存処理は単一Executorでバックグラウンド実行し、スレッド優先度を `THREAD_PRIORITY_BACKGROUND` に設定する。

レンダラーの画素ループでは、一時オブジェクトを生成せず `float` で直接補間する。

---

## 18. 現在の設計上の評価

現在の構成では、以下が成立している。

```text
- 補正処理がUIから分離されている
- 補正方式をRendererで差し替えられる
- 入力境界と出力境界をBoundaryPairで対応付けられる
- 幅配分バーの状態をBoundaryMarkerへ限定できている
- 画像座標とView座標を分離できている
- 編集用縮小画像座標と保存用原画像座標を変換できている
- 補正画像と表示オーバーレイを分離できている
- プレビュー用Bitmapと保存用Bitmapを分離できている
- 保存処理をバックグラウンド実行できている
- ImageLoaderへ画像読込責務を分離できている
- ImageSaverへJPEG圧縮とMediaStore保存責務を分離できている
- ScanlineBandRendererの画素ループからPointF生成を除去できている
```

主要なMVP操作と画像入出力責務の分離は完了した。

現在の最大の設計課題は、`PageCorners` と `BoundaryPair` の正本が実質的に `CornerEditView` 内にあることである。

次は `ScanEditState` を導入し、画面の挙動を変えずに編集状態をView外へ移す準備を進める。

その後、ViewModel、保存ジョブのライフサイクル対応、Fragment分割へ進む。
