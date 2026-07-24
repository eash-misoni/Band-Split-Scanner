# Band Split Scanner 進捗メモ

更新日: 2026-07-24

## 1\. このドキュメントの目的

本ドキュメントは、Band Split Scanner の現在の実装状況を記録し、次にどこから開発を再開するか判断できるようにするための進捗メモである。

仕様そのものは `specification.md`、コード構造と将来設計は `architecture.md` を正本とする。

この文書では主に以下を扱う。

```text
- 現在の開発段階
- 実装済み機能
- 現在のコード構成
- 現時点の暫定的な設計
- 未実装機能
- 既知の課題
- 次に行う作業候補
```

\---

## 2\. 現在の開発段階

現在は、**授業提出版MVPを完成として一区切りにした段階**である。

画像選択から帯分割編集、補正結果確認、出力幅・縦横比調整、原画像を使ったJPEG保存までの主要な操作経路は実装・動作確認済みである。

```text
画像選択
↓
編集用縮小Bitmap読込
↓
ページ基準点・境界・分割ライン編集
↓
帯分割補正プレビュー
↓
幅配分・出力縦横比調整
↓
原画像再読込
↓
保存用Bitmap生成
↓
MediaStoreへJPEG保存
```

授業提出版として、以下を完了扱いとする。

```text
- 任意個数の内部境界を扱える
- 境界の追加・移動・削除ができる
- 元画像と補正結果をズーム・パンできる
- 補正結果の幅配分と縦横比を再調整できる
- 編集状態を反映した画像を保存できる
- 保存処理中もUI操作を継続できる
- 画像読込をImageLoaderへ分離できている
- JPEG・MediaStore保存をImageSaverへ分離できている
- 主要操作の回帰確認とエラー操作確認を実施済み
```

現時点で残っている項目は、授業提出版の必須機能ではなく、保守性・ライフサイクル対応・将来機能を目的とした改善である。

今後開発を再開する場合は、編集状態のView外分離、ViewModel導入、画面回転対応、Fragment分割などへ進む。

\---

## 3\. 開発環境

```text
アプリ名: Band Split Scanner
開発環境: Android Studio
言語: Java
UI: XMLレイアウト + Android View + Custom View
対象: Androidアプリ
```

学校課題の条件により、実装言語は Java を使用する。

\---

## 4\. 現在できる操作

現時点では、概ね以下の流れを実行できる。

```text
画像を選択
↓
selectedImageUriと原画像サイズを保持
↓
長辺最大2048pxの編集用sourceBitmapを読み込む
↓
ページ基準点4点を調整
↓
初期状態では内部境界0本・補正帯1つ
↓
幅配分バーの空き領域を長押し
↓
「ここに境界を追加」
↓
境界マーカーと対応する分割ラインを追加
↓
必要に応じてさらに境界を追加
↓
2本指ピンチで元画像を拡大・縮小
↓
空き領域を1本指ドラッグして表示位置をパン
↓
画像が縦横それぞれ最低48dp残る範囲で表示位置を制限
↓
分割ラインの端点または線本体をドラッグ
↓
左右端境界の端点または線本体をドラッグ
↓
幅配分バーの境界マーカーをドラッグ
↓
必要に応じて境界マーカーを長押しして削除
↓
補正を実行
↓
幅1200pxの補正結果プレビューを生成
↓
補正結果と出力境界線・出力外枠を表示
↓
必要に応じて出力境界線の表示・非表示を切り替え
↓
2本指ピンチで補正結果を拡大・縮小
↓
外枠の辺以外を1本指ドラッグして表示位置をパン
↓
補正後画像が縦横それぞれ最低48dp残る範囲で表示位置を制限
↓
補正結果画面でも幅配分を調整
↓
出力外枠の左辺または右辺をドラッグして縦横比を調整
↓
指を離した時点で補正結果プレビューを再生成
↓
保存ボタンを押す
↓
保存開始時の編集状態をSaveRequestへまとめる
↓
保存処理をバックグラウンドスレッドで開始
↓
selectedImageUriから原画像を再読込
↓
PageCornersとBoundaryPairの入力端点を原画像座標へ拡大変換
↓
保存用OutputSettingsを生成
↓
保存用Bitmapを再生成
↓
JPEGとしてMediaStoreへ保存
↓
再読込Bitmapと保存用Bitmapを解放
↓
Android 10以降ではPictures/BandSplitScannerから保存画像を確認
↓
必要に応じて元画像編集画面へ戻って再調整
```

\---

## 5\. 実装済み機能

### 5.1 画像選択と編集用縮小読込

端末または利用可能な画像プロバイダから画像を選択し、補正対象画像として読み込める。

現在は `ActivityResultContracts.GetContent` を使用している。

画像選択時には、次の情報を保持する。

```text
- selectedImageUri
- selectedImageWidth
- selectedImageHeight
- sourceBitmap
```

読込処理は以下。

```text
selectedImageUri
↓
inJustDecodeBoundsで原画像サイズだけを取得
↓
長辺が2048px以内になるinSampleSizeを計算
↓
編集用sourceBitmapを縮小読込
```

`inSampleSize` は2の累乗で増やす。

画像寸法取得、`inSampleSize` の計算、Bitmap読込は `ImageLoader` が担当する。

`sourceBitmap` は、元画像編集画面と補正結果プレビュー生成に使用する。

保存時には `sourceBitmap` を保存入力として再利用せず、`ImageLoader.loadOriginal()` で `selectedImageUri` から原画像を改めて読み込む。

\---

### 5.2 ページ基準点4点の編集

`CornerEditView` 上で以下の4点を表示し、個別にドラッグできる。

```text
- 左上
- 右上
- 右下
- 左下
```

4点は `PageCorners` として画像座標系で保持する。

表示時には画像座標からView座標へ変換し、タッチ時にはView座標から画像座標へ逆変換する。

ページ基準点を移動しても、すでに編集した分割ラインの `inputTop` / `inputBottom` は初期位置へリセットしない。

\---

### 5.3 BoundaryPairによる境界状態管理

内部の分割境界は `BoundaryPair` で管理する。

概念上の構造は以下。

```text
BoundaryPair
├ id
├ outputX
├ inputTop
└ inputBottom
```

各値の意味は以下。

```text
id
= 境界の識別ID

outputX
= 補正後画像上の出力境界位置
= 0.0〜1.0の正規化座標

inputTop
= 元画像上の入力境界上端点
= 画像座標

inputBottom
= 元画像上の入力境界下端点
= 画像座標
```

初期状態では内部境界を生成しない。

```text
内部境界数 = 0
補正帯数 = 1
```

ユーザーが幅配分バーから境界を追加すると、新しい `BoundaryPair` を生成する。

内部構造と補正処理は任意個数の `BoundaryPair` を扱える。

\---

### 5.4 BoundaryMarkerによる幅配分バーの状態分離

`WidthDistributionBarView` は `BoundaryPair` 全体を内部状態として保持しない。

幅配分バーに必要な情報だけを `BoundaryMarker` として保持する。

```text
BoundaryMarker
├ boundaryId
└ outputX
```

この分離により、幅配分バーの古い `inputTop` / `inputBottom` が元画像編集側へ逆流し、分割ライン位置を巻き戻す問題を防いでいる。

幅配分バーの責務は、出力境界の表示と `outputX` の編集に限定する。

\---

### 5.5 元画像編集画面の入力境界表示

元画像編集画面では、以下を表示できる。

```text
- 左端境界
- 右端境界
- 分割ライン
- 分割ライン上端点
- 分割ライン下端点
- 上側の端点接続線
- 下側の端点接続線
- 端点接続線と入力境界で構成される補正帯枠
```

ページ基準点4点を単純な四角形として結ぶのではなく、入力境界の上端点列と下端点列を順番に接続することで、実際の補正帯形状が見える表示にしている。

\---

### 5.6 分割ライン操作

各分割ラインについて、以下の操作を実装済み。

```text
上端点をドラッグ
→ inputTop を更新

下端点をドラッグ
→ inputBottom を更新

線本体をドラッグ
→ inputTop と inputBottom を同じ移動量で移動
```

分割ライン本体の移動は、線の傾きと長さを保つ平行移動として扱う。

\---

### 5.7 左右端境界の操作

左右端境界は `PageCorners` の点を使って表現する。

```text
左端境界
= topLeft と bottomLeft

右端境界
= topRight と bottomRight
```

以下の操作を行える。

```text
コーナー点をドラッグ
→ 対応する1点だけ移動

左端境界の線本体をドラッグ
→ topLeft と bottomLeft を同じ移動量で移動

右端境界の線本体をドラッグ
→ topRight と bottomRight を同じ移動量で移動
```

線本体のドラッグでは、2点へ共通の移動量を適用し、境界線の傾きと長さを保つ。

\---

### 5.8 幅配分バーと境界数編集

`WidthDistributionBarView` を実装済み。

幅配分バーでは以下の操作を行える。

```text
境界マーカーをドラッグ
→ outputXを変更

空き領域を長押し
→ 長押し位置付近にメニュー表示
→ 「ここに境界を追加」
→ 追加要求をMainActivityへ通知

既存マーカーを長押し
→ 長押し位置付近にメニュー表示
→ 「削除」
→ boundaryIdをMainActivityへ通知
```

境界マーカーのドラッグでは、対応する `BoundaryPair.outputX` のみを変更する。

```text
BoundaryMarker.outputXを更新
↓
boundaryIdと新しいoutputXを通知
↓
対応するBoundaryPair.outputXのみ更新
↓
inputTop / inputBottomは変更しない
```

境界マーカーには以下の制約がある。

```text
- 他のマーカーを追い越せない
- 左右端へ密着できない
- 隣接マーカーとの最小間隔を維持する
- 最小間隔を満たさない位置には新しい境界を追加できない
```

境界追加時には、新しい `outputX` の左右に隣接する境界を求め、その2境界間で入力側の上端点・下端点を線形補間して、新しい分割ラインの初期位置を決定する。

左右に内部境界が存在しない場合は、ページの左端境界を `outputX = 0.0`、右端境界を `outputX = 1.0` として補間に使用する。

削除時には `boundaryId` に対応する `BoundaryPair` を削除し、残存する境界の位置は変更しない。

\---


### 5.9 元画像編集画面のズーム・パン

`CornerEditView` で、元画像のピンチズームとパン移動を実装済み。

```text
2本指ピンチ
→ ピンチ中心を基準に拡大・縮小

空き領域を1本指ドラッグ
→ 元画像の表示位置をパン
```

ズーム倍率は、画像全体を画面内へ収める初期表示を `1.0` として管理する。

```text
最小倍率 = 1.0
最大倍率 = 5.0
```

パン量はView座標系で保持している。

パン操作とピンチ操作には、共通の最低可視制約を適用する。

```text
最低可視量 = 48dp
```

`48dp` は端末の画面密度に応じてpxへ変換し、表示画像が横方向・縦方向のそれぞれで最低その量だけ画面内へ残るよう、`panX` と `panY` を制限する。

表示画像またはView自体が `48dp` より小さい方向では、実際に表示可能な長さを最低可視量の上限として扱う。

制約は、以下のタイミングで共通して適用する。

```text
- 1本指パンによる表示位置更新後
- ピンチ倍率更新後
- ピンチ中心維持のためのパン量補正後
- ピンチ終了時
```

ピンチ操作では、通常はピンチ中心が指していた画像上の点を維持する。ただし、その位置では最低可視量を満たせない場合に限り、画像を最低48dp残せる位置まで表示位置を補正する。

描画とタッチ判定には、ズーム・パン状態を含む共通の座標変換行列を使用する。

```text
画像座標
↓ imageToViewMatrix
ズーム・パン後のView座標

View座標
↓ viewToImageMatrix
画像座標
```

このため、`PageCorners` と `BoundaryPair.inputTop` / `inputBottom` は画像座標のまま保持される。

操作の優先順位は以下。

```text
1. ページ基準点・ライン端点
2. 左右端境界・分割ライン本体
3. 空き領域の1本指パン
4. 2本指ピンチズーム
```

2本目の指が置かれてピンチ操作を開始した場合は、進行中の1本指編集操作を解除し、境界が意図せず移動し続けないようにしている。

以下を実装済み。

```text
- ピンチズーム
- 空き領域のドラッグによるパン
- ピンチ中心を維持した拡大・縮小
- パン後の最低48dp可視制約
- ピンチ倍率更新後の最低48dp可視制約
- 画像がViewより小さい方向でのパン
- 拡大後のページ基準点編集
- 拡大後の分割ライン端点編集
- 拡大後の境界線本体の平行移動
- ピンチ開始時の編集操作解除
```

\---

### 5.10 補正処理の方式分離

補正処理は、共通処理と描画方式の差分を分離している。

```text
correction/
├ BandCorrectionEngine.java
├ BandRenderer.java
├ BandCorrectionMath.java
├ PerspectiveBandRenderer.java
└ ScanlineBandRenderer.java
```

責務は以下。

```text
BandCorrectionEngine
= 出力サイズ決定
= PageCornersとBoundaryPairから境界列を生成
= BandRendererを呼び出す

BandRenderer
= 補正描画方式のインターフェース

PerspectiveBandRenderer
= 帯ごとの射影変換方式

ScanlineBandRenderer
= スキャンライン補間方式

BandCorrectionMath
= lerp、距離、面積、clamp等の共通計算
```

現在の実行経路では、帯境界での位置の連続性を重視して `ScanlineBandRenderer` を使用している。

\---

### 5.11 スキャンライン補間方式

`ScanlineBandRenderer` では、出力画素から元画像座標を逆算して色を取得する。

概念的な流れは以下。

```text
出力画素 (x, y)
↓
所属する補正帯を決定
↓
帯内横方向比率 u を計算
↓
出力縦方向比率 v を計算
↓
左入力境界上の点 L(v) を計算
↓
右入力境界上の点 R(v) を計算
↓
L(v) と R(v) を u で補間
↓
元画像座標を取得
↓
バイリニア補間で画素値を取得
```

隣接帯が同じ `BoundaryLine` を共有するため、帯ごとの独立射影変換より境界位置の連続性を保ちやすい。

\---

### 5.12 補正結果表示

補正結果は `ResultPreviewView` で表示する。

現在の責務は以下。

```text
- 補正後Bitmapの表示
- 出力境界線のオーバーレイ描画
- 出力外枠のオーバーレイ描画
- 出力境界線の表示・非表示切り替え
- outputX変更の表示反映
- 補正結果プレビューのズーム・パン
- 補正後Bitmap、出力境界線、出力外枠への共通表示変換
```

補正結果画面には、出力境界線の表示状態を切り替えるUIを実装済み。

```text
表示オン
→ 補正帯ごとの出力境界線を表示

表示オフ
→ 補正後画像のみを表示
```

出力境界線は補正後Bitmapそのものへ描き込まず、`ResultPreviewView` 上の表示用オーバーレイとして描画する。

そのため、表示状態を切り替える際には補正処理やBitmap生成を再実行せず、オーバーレイだけを再描画する。

表示状態は `MainActivity` が保持し、元画像編集画面へ戻って再び補正結果画面を開いた場合も、直前の表示状態を引き継ぐ。

\---

### 5.13 補正結果画面での幅配分調整

補正結果画面でも幅配分バーを操作できる。

```text
ドラッグ中
→ outputXを更新
→ 出力境界線の表示位置を更新

ドラッグ終了
→ 現在のBoundaryPair一覧で補正結果を再生成
```

重い補正処理はドラッグ終了時に実行する。

\---

### 5.14 出力外枠と出力縦横比調整

補正結果画面へ出力外枠を表示し、左右辺ドラッグによる出力縦横比変更を実装済み。

```text
右辺をドラッグ
→ 左辺を固定して右辺を更新

左辺をドラッグ
→ 右辺を固定して左辺を更新
```

左右のヒット領域が重なる場合は、タッチ位置に近い辺を操作対象とする。

```text
辺をドラッグ中
→ 一時的な出力外枠だけを更新
→ outputAspectRatioの候補値を通知

指を離す
→ outputAspectRatioを確定
→ 固定プレビュー横幅と縦横比から出力高さを計算
→ OutputSettingsを生成
→ 補正結果Bitmapを再生成
```

ドラッグ中には補正処理を繰り返さず、外枠のみを再描画する。

左右どちらの操作でも、MainActivityへ通知する値は `outputAspectRatio` と操作完了状態で共通としている。

出力外枠の左右辺は、`ResultPreviewView` の端では制限しない。

```text
最小縦横比 = 0.4
最大縦横比 = 3.0
```

操作対象の辺と固定側の辺から、上記の最小・最大縦横比だけを使って可動範囲を決める。そのため、必要に応じてView端より外側まで外枠を拡張できる。

---

### 5.15 補正結果プレビューのズーム・パン

`ResultPreviewView` で、補正結果のピンチズームとパン移動を実装済み。

```text
2本指ピンチ
→ ピンチ中心を基準に拡大・縮小

出力外枠の辺以外を1本指ドラッグ
→ 補正結果の表示位置をパン
```

ズーム倍率は、補正結果全体を画面内へ収める初期表示を `1.0` として管理する。

```text
最小倍率 = 0.25
初期倍率 = 1.0
最大倍率 = 5.0
```

`1.0` 未満への縮小を許可し、横長の出力外枠を操作するための画面上の作業領域を確保できる。

表示状態は `ResultPreviewView` 内で保持する。

```text
previewZoomScale
previewPanX
previewPanY
```

補正後Bitmap、出力境界線、出力外枠には、共通の `bitmapToViewMatrix` を適用する。逆変換用に `viewToBitmapMatrix` も保持し、ピンチ中心が指していた補正結果上の点を倍率変更後も同じView位置へ近づける。

パン操作とピンチ操作には、補正前画面と同じ最低可視制約を適用する。

```text
最低可視量 = 48dp
```

`48dp` は端末の画面密度に応じてpxへ変換し、補正後画像が横方向・縦方向のそれぞれで最低その量だけ画面内へ残るよう、`previewPanX` と `previewPanY` を制限する。

表示画像またはView自体が `48dp` より小さい方向では、実際に表示可能な長さを最低可視量の上限として扱う。

操作の優先順位は以下。

```text
1. 出力外枠の左辺・右辺
2. 外枠の辺以外の1本指パン
3. 2本指ピンチズーム
```

2本目の指が置かれてピンチ操作を開始した場合は、進行中の外枠ドラッグまたはパン操作を解除する。

---

### 5.16 元画像編集画面への戻り

補正結果画面から元画像編集画面へ戻り、分割ラインや境界を再調整できる。

現在は同一Activity内でCustom Viewを差し替える方式で実装している。

\---

### 5.17 補正結果の保存用再読込・座標変換

補正結果画面に保存ボタンを実装済み。

保存時は、幅 `1200px` のプレビュー用 `correctedBitmap` を直接保存しない。

`selectedImageUri` から原画像を再読込し、現在の編集状態を保存用原画像座標へ変換して、保存用Bitmapを別途生成する。

```text
保存入力
├ selectedImageUri
├ selectedImageWidth / selectedImageHeight
├ sourceBitmapの幅・高さ
├ PageCorners
├ BoundaryPair一覧
└ outputAspectRatio
```

座標変換倍率は以下。

```text
scaleX
= 保存用原画像幅 / 編集用sourceBitmap幅

scaleY
= 保存用原画像高さ / 編集用sourceBitmap高さ
```

保存時には次を変換する。

```text
PageCornersの4点
BoundaryPair.inputTop
BoundaryPair.inputBottom
```

次は画像サイズに依存しないため変更しない。

```text
BoundaryPair.id
BoundaryPair.outputX
outputAspectRatio
```

保存用の出力幅は、原画像座標へ変換したページ上辺と下辺の長さの平均から決める。

```text
基準出力幅
= (distance(topLeft, topRight)
   + distance(bottomLeft, bottomRight))
  / 2

基準出力高さ
= 基準出力幅 / outputAspectRatio
```

保存用出力サイズには次の上限を設けている。

```text
最大画素数
= 4,000,000画素

最大長辺
= 4096px
```

どちらかの上限を超える場合は、縦横比を維持して縮小する。

保存画像はJPEG品質95でMediaStoreへ保存する。

```text
ファイル名
= BandSplitScanner_yyyyMMdd_HHmmss.jpg

Android 10以降の保存先
= Pictures/BandSplitScanner
```

Android 9以前では、保存前に `WRITE_EXTERNAL_STORAGE` 権限を確認する。

保存処理ではBitmap本体だけをJPEGへ圧縮するため、以下は保存画像へ含まれない。

```text
- 出力境界線
- 出力外枠
- 幅配分バー
- 境界マーカー
- プレビューのズーム・パン
```

保存途中で失敗した場合は、途中まで登録したMediaStore項目を削除する。

`OutOfMemoryError` は一般の保存失敗と分けて通知する。

\---

### 5.18 保存処理のバックグラウンド実行

保存ボタンを押した時点で、保存に必要な値を `SaveRequest` へまとめる。

```text
SaveRequest
├ imageUri
├ originalImageWidth
├ originalImageHeight
├ previewImageWidth
├ previewImageHeight
├ previewCorners
├ previewBoundaryPairs
└ outputAspectRatio
```

保存処理は単一の `ExecutorService` で実行する。

```text
UIスレッド
↓ SaveRequestを生成
↓ saveInProgress = true
↓ 保存ボタンを無効化
↓
保存用Executor
├ 原画像再読込
├ 座標変換
├ 補正Bitmap生成
├ JPEG圧縮
└ MediaStore保存
↓
UIスレッド
├ 完了・失敗Toast
├ saveInProgress = false
└ 保存ボタンを再有効化
```

保存処理スレッドには `THREAD_PRIORITY_BACKGROUND` を設定する。

これにより、保存中も補正結果画面のパン・ズームなどのUI操作を継続できる。

同時に複数の保存処理を開始しないよう、保存中は保存ボタンを無効化する。

Activity破棄時にはExecutorへ `shutdownNow()` を呼ぶ。

\---

### 5.19 ScanlineBandRendererの画素ループ最適化

保存処理のバックグラウンド化後もUI応答性が低下した原因として、従来の画素ループでは出力画素ごとに補間用 `PointF` を生成していた。

現在は、画素ループ内の座標計算を `float` の直接補間へ変更している。

```text
変更前
出力画素ごとにPointFを生成して補間

変更後
leftX / leftY / rowDeltaX / rowDeltaYをfloatで計算
↓
sourceX / sourceYを直接計算
```

各出力行では `outputRowOffset` を先に計算し、配列インデックス計算も軽減している。

補正方式とバイリニア補間の結果は維持しつつ、大量の一時オブジェクト生成とGC負荷を抑えている。

\---

### 5.20 ImageLoaderへの画像読込責務分離

画像読込に関する処理を `MainActivity` から `image/ImageLoader.java` へ分離済み。

`ImageLoader` の公開メソッドは以下。

```text
loadPreview(uri, maxEdge)
= 原画像寸法を取得
= inSampleSizeを計算
= 編集用縮小Bitmapを返す

loadOriginal(uri)
= URIから原画像Bitmapを等倍読込

readBounds(uri)
= Bitmapを生成せず画像寸法だけを取得
```

`loadPreview()` は次をまとめた `LoadedImage` を返す。

```text
LoadedImage
├ bitmap
├ originalWidth
└ originalHeight
```

`MainActivity` は読込手順を直接実装せず、取得結果を状態として保持する。

\---

### 5.21 ImageSaverへのMediaStore保存責務分離

JPEG圧縮とMediaStore操作を `MainActivity` から `image/ImageSaver.java` へ分離済み。

`ImageSaver.saveJpeg()` は以下を担当する。

```text
- MediaStoreへ保存先を登録
- JPEG形式でBitmapを圧縮
- Android 10以降でRELATIVE_PATHを設定
- Android 10以降でIS_PENDINGを解除して公開
- 保存失敗時に途中作成したMediaStore項目を削除
```

`MainActivity` は以下だけを担当する。

```text
- Android 9以前の権限要求
- 保存用Bitmap生成
- 保存ファイル名生成
- バックグラウンド処理の開始
- 保存完了・失敗のUI通知
```

画像の出力内容、JPEG品質95、保存先 `Pictures/BandSplitScanner` は変更していない。

\---

## 6\. 現在の主要クラス構成

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

\---

## 7\. 現在の責務分担

### MainActivity

```text
- Activity Result APIによる画像選択
- selectedImageUriと原画像寸法の保持
- sourceBitmap / correctedBitmapの保持
- ImageLoaderの呼び出し
- CornerEditView生成
- 元画像編集表示と補正結果表示の切り替え
- BandCorrectionEngineの呼び出し
- WidthDistributionBarViewの変更通知受け取り
- 境界追加・削除要求の処理
- 各Viewの状態同期
- outputAspectRatioの保持
- 補正結果プレビューの再生成
- 保存ボタンの表示・有効状態管理
- Android 9以前のWRITE_EXTERNAL_STORAGE権限要求
- 保存開始時のSaveRequest生成
- 単一Executorによる保存処理実行
- 保存スレッドのバックグラウンド優先度設定
- 編集用画像座標から原画像座標への変換
- 保存用OutputSettingsの計算
- 保存用Bitmapの再生成
- ImageSaverの呼び出し
- 保存完了・失敗・メモリ不足のUI通知
- 一時Bitmapの解放
- Activity破棄時のExecutor終了
```

画像のデコード手順とMediaStore操作は専用クラスへ移動した。

一方、編集状態の取得、座標変換、保存ジョブの管理は引き続き `MainActivity` が担当する。

\---

### ImageLoader

```text
- ContentResolverからInputStreamを開く
- inJustDecodeBoundsによる画像寸法取得
- 編集用inSampleSizeの計算
- 編集用縮小Bitmapの読込
- 保存用原画像Bitmapの読込
- 読込失敗時のIOException通知
```

ActivityやViewの状態は保持しない。

\---

### ImageSaver

```text
- MediaStoreへの保存先登録
- JPEG圧縮
- Android 10以降のRELATIVE_PATH設定
- Android 10以降のIS_PENDING解除
- 保存失敗時のMediaStore項目削除
```

権限要求やToast表示は担当しない。

\---

### CornerEditView

```text
- 編集用縮小Bitmapの表示
- PageCorners保持
- BoundaryPair一覧保持
- 左右端境界・分割ライン・端点・補正帯枠の描画
- ページ基準点・境界線・端点のドラッグ操作
- ピンチズーム・パン
- 最低48dp可視制約
- 画像座標とView座標の相互変換
- outputX変更の反映
```

`PageCorners` と `BoundaryPair` の正本が実質的にView内にある点は、次の改善対象である。

\---

### WidthDistributionBarView

```text
- 幅配分バーと境界マーカーの描画
- マーカードラッグ
- outputX変更
- 順序・最小間隔制約
- 境界追加・削除要求の通知
```

`inputTop` / `inputBottom` は保持しない。

\---

### ResultPreviewView

```text
- 補正後Bitmap表示
- 出力境界線・出力外枠の描画
- 出力境界線の表示切り替え
- 補正結果のズーム・パン
- 最低48dp可視制約
- 出力外枠の左右辺ドラッグ
- 出力縦横比変更イベント通知
```

\---

### BandCorrectionEngine

```text
- PageCornersとBoundaryPairから境界列を生成
- outputX順に境界を並べる
- OutputSettingsを受け取る
- BandRendererを呼び出す
```

\---

### ScanlineBandRenderer

```text
- 出力画素から入力画像座標を逆算
- バイリニア補間による画素取得
- 無効な補正帯のエラー色表示
- floatによる直接座標補間
- 行単位の配列オフセット計算
```

画素ループ内では補間用 `PointF` を生成しない。

\---

## 8\. 現在の状態管理


現在はまだViewModelを導入していない。

`MainActivity` は画像入出力に関する以下の状態を保持する。

```text
- selectedImageUri
- selectedImageWidth
- selectedImageHeight
- sourceBitmap
- correctedBitmap
- saveInProgress
```

各Bitmapの役割は以下。

```text
sourceBitmap
= 長辺最大2048pxの編集・プレビュー入力

correctedBitmap
= 幅1200pxの補正結果プレビュー

saveSourceBitmap
= 保存処理中だけURIから等倍読込する原画像

saveBitmap
= 保存処理中だけ生成する最終出力
```

編集状態の中心は実質的に `CornerEditView` が保持する。

```text
- PageCorners
- BoundaryPair一覧
```

これらの入力座標は `sourceBitmap` の画像座標である。

保存開始時には、必要な値を `SaveRequest` へまとめて保存用Executorへ渡す。

```text
SaveRequest
├ selectedImageUri
├ 原画像サイズ
├ 編集用画像サイズ
├ PageCorners
├ BoundaryPair一覧
└ outputAspectRatio
```

保存処理では、原画像と編集用画像のサイズ比から入力座標を変換する。

元画像編集画面の表示状態は `CornerEditView` が保持する。

```text
- zoomScale
- panX
- panY
```

補正結果画面の表示状態は `ResultPreviewView` が保持する。

```text
- previewZoomScale
- previewPanX
- previewPanY
```

`MainActivity` は補正結果画面に関する次の状態も保持する。

```text
- showOutputBoundaryLines
- outputAspectRatio
```

表示専用状態は補正入力や保存結果へ反映しない。

現在の概略データフローは以下。

```text
selectedImageUri
├ 編集開始時
│  └ 縮小sourceBitmap
│     └ CornerEditView
│        ├ PageCorners
│        └ BoundaryPair
│
└ 保存時
   └ 等倍saveSourceBitmap
      + SaveRequest内の編集状態
      ↓ 座標変換
      ↓ BandCorrectionEngine
      └ saveBitmap
         ↓ MediaStore
```

幅配分変更、境界追加・削除、出力境界線表示、出力外枠操作、プレビューのズーム・パンについては、従来どおり各ViewからMainActivityを介して同期する。

\---

## 9\. 現時点の暫定実装


以下は、現段階では意図的に維持している暫定構成である。

### 9.1 MainActivity中心の画面制御

まだFragmentを導入していない。

```text
FrameLayout
↓
CornerEditView または ResultPreviewView を動的に配置
```

本格MVPで画面数と状態管理が複雑になった段階で再構成する。

\---

### 9.2 URIとBitmapのActivity内保持

現在は `MainActivity` が画像URI、画像寸法、プレビュー用Bitmapを状態として保持する。

```text
常時保持
├ selectedImageUri
├ selectedImageWidth
├ selectedImageHeight
├ sourceBitmap
└ correctedBitmap

保存処理中だけ保持
├ saveSourceBitmap
└ saveBitmap
```

画像の読込処理自体は `ImageLoader`、JPEG圧縮とMediaStore操作は `ImageSaver` へ分離済み。

`MainActivity` に残る画像関連責務は以下。

```text
- 読込結果の保持
- 保存用座標変換
- 保存用Bitmap生成
- 保存ジョブ管理
- UI通知
```

Bitmapは用途別に分離できているが、画面回転やActivity再生成後の復元には未対応である。

\---

### 9.3 ページ基準点指定と元画像編集の統合

仕様上はページ基準点指定画面と元画像編集画面を分ける予定だが、現在は `CornerEditView` が両方の操作を担当している。

現段階では編集UIと補正方式の検証を優先する。

\---

### 9.4 初期状態と境界数

初期状態では内部境界を生成しない。

```text
内部境界数 = 0
補正帯数 = 1
```

幅配分バーから境界マーカーを追加・削除することで、任意個数の内部境界を扱える。

```text
内部境界0本 → 1帯
内部境界1本 → 2帯
内部境界2本 → 3帯
...
```

補正処理、元画像編集表示、幅配分バーはいずれも複数の `BoundaryPair` をリストとして扱う。

\---

## 10\. 授業提出版では対象外とする機能

以下は未実装だが、授業提出版MVPの完成条件には含めない。

### 10.1 状態復元・ライフサイクル対応

```text
- 画面回転後の編集状態復元
- View再生成後のズーム・パン状態復元
- Activity再生成後の保存中状態復元
- 保存処理の協調的なキャンセル
```

\---

### 10.2 画像取得・出力の拡張

```text
- アプリ内カメラ撮影
- 保存画像の共有
- PDF出力
- 複数ページ管理
```

\---

### 10.3 画面・状態管理の正式構成

```text
- ScanEditStateの導入
- PageCornersとBoundaryPairのView外への移動
- ViewModel導入
- Fragment分割
- Custom ViewのXML配置対応
```

\---

### 10.4 画像処理・自動化

```text
- グレースケール化
- 白黒化
- コントラスト調整
- 明るさ調整
- シャープ化
- OCR
- 自動ページ検出
- 本文行・罫線による半自動補正
- 曲線分割ライン
```

これらは将来拡張として扱い、授業提出版の既知の未実装項目として記録する。

\---

## 11\. 既知の課題・注意点

## 11\. 既知の課題・注意点

### 11.1 MainActivityに残るオーケストレーション責務

`ImageLoader` と `ImageSaver` の導入により、画像デコードとMediaStore処理は `MainActivity` から分離された。

現在も以下は `MainActivity` が担当する。

```text
- 画像・編集・結果画面の状態保持
- Custom View間の同期
- 補正プレビュー生成
- 保存開始時の編集状態取得
- 編集用座標から原画像座標への変換
- 保存用OutputSettings計算
- 保存用Bitmap生成
- Executor管理
- 保存完了・失敗のUI通知
```

次の改善では、責務をさらに細分化するより先に、編集状態の正本をView外へ移す。

\---

### 11.2 CornerEditViewへの編集状態集中

現在は `PageCorners` と `BoundaryPair` の正本が実質的に `CornerEditView` 内にある。

保存開始時や幅配分変更時に、`MainActivity` がViewから状態を取得している。

画面回転、View再生成、Fragment分割へ進む前に、編集状態を専用モデルまたはViewModelへ移す必要がある。

\---

### 11.3 高解像度画像のメモリ使用量

編集用 `sourceBitmap` は長辺最大2048pxへ縮小される。

保存時には `ImageLoader.loadOriginal()` で原画像を等倍読込する。

さらに `ScanlineBandRenderer` は入力・出力の画素配列を確保するため、原画像が非常に大きい場合の入力側メモリ負荷は残る。

保存出力は最大400万画素・長辺4096pxへ制限し、`OutOfMemoryError` はユーザーへ通知する。

\---

### 11.4 非同期保存とActivityライフサイクル

保存処理は単一Executorで実行し、スレッド優先度をバックグラウンドへ下げている。

ただし、Executorは `MainActivity` が所有し、Activity破棄時に `shutdownNow()` を呼ぶ構成である。

画面回転後の保存継続、キャンセル、完了通知先の復元は未対応。

\---

### 11.5 仕様と補正方式の表現

仕様書には射影変換を中心とした説明が残る箇所がある一方、現在の主実装はスキャンライン補間方式である。

今後、補正方式を確定する段階で、仕様上の表現と実装方式の関係を整理する。

\---

### 11.6 ズーム・パン状態のView内保持

ズーム倍率とパン量は各Custom View内で保持する。

同じViewインスタンスを再表示する場合は維持できるが、画面回転やView再生成では失われる。

編集状態のView外分離後に、表示状態をどこまで復元対象とするか決める。

\---

## 12\. 授業提出版MVPの完了判定

以下の理由から、現在の状態を授業提出版MVPの完成と判断する。

```text
- アプリの中心となる帯分割補正を実行できる
- 補正対象領域と内部境界を手動編集できる
- 境界数を固定せず追加・削除できる
- 補正前後の表示をズーム・パンできる
- 補正結果を再調整できる
- 編集用表示を含めずに補正画像を保存できる
- 高解像度の原画像を保存入力として再読込できる
- 保存処理をバックグラウンドで実行できる
- 画像読込と画像保存の責務を専用クラスへ分離できている
- 主要操作の回帰確認とエラー操作確認を実施済み
```

ViewModelやFragmentを導入していないことは、現在の授業提出版における機能不足ではなく、将来の保守性改善に関する設計課題として扱う。

\---

## 13\. 開発を再開する場合の推奨順序

将来開発を再開する場合は、次の順序を推奨する。

```text
1. ScanEditStateを追加する
2. PageCornersとBoundaryPairの正本をView外へ移す
3. ScanEditViewModelを導入する
4. 画面回転とActivity再生成へ対応する
5. 保存ジョブのライフサイクルを整理する
6. Custom ViewをXML配置対応にする
7. 必要に応じてFragmentへ分割する
8. カメラ撮影・共有・PDFなどを追加する
```

授業提出版の保守だけを行う場合は、大規模な状態管理変更を加えず、不具合修正と文書更新に限定する。

\---

## 14\. 現時点のまとめ

Band Split Scannerは、授業提出版MVPとして以下の状態に到達した。

```text
- Java + XML + Custom ViewによるAndroidアプリ
- 画像選択と編集用縮小読込
- ページ基準点4点の編集
- 任意個数の内部境界編集
- 境界追加・移動・削除
- 幅配分バーによる出力幅調整
- 元画像と補正結果のズーム・パン
- スキャンライン補間による帯分割補正
- 出力境界線の表示切り替え
- 出力外枠による縦横比調整
- 原画像再読込と保存用座標変換
- 保存用Bitmapの再生成
- 非同期JPEG保存
- 保存中のUI応答性維持
- ImageLoader / ImageSaverによる画像入出力責務分離
- 主要操作の回帰確認とエラー操作確認
```

画像を選択し、湾曲したページを複数の帯へ分けて補正し、結果を端末へ保存する一連の操作が成立している。

この状態を授業提出版の完成地点とする。

ViewModel、Fragment、画面回転対応、カメラ撮影、OCR、PDFなどは将来改善として残す。
