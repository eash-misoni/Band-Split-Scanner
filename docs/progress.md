# Band Split Scanner 進捗メモ

更新日: 2026-07-10

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

現在は、固定帯数による補正方式の検証を終え、本格MVPの編集機能を実装している段階である。

帯分割補正そのものについては、以下を確認できている。

```text
- ページ基準点から補正対象領域を定義できる
- 任意個数の入力境界から補正帯を生成できる
- スキャンライン補間方式で帯ごとの補正画像を生成できる
- 入力境界を編集して補正結果へ反映できる
- 出力境界を編集して各補正帯の出力幅を変更できる
- 境界マーカーと分割ラインを追加・削除できる
- 初期状態を0内部境界・1帯として開始できる
- 元画像編集画面をピンチズーム・パンできる
- ズーム・パン後も画像座標に基づいて境界を編集できる
- 元画像編集画面と補正結果画面を往復して再調整できる
```

現在は、任意の帯数を扱うための基本編集機能と、細部を調整するためのズーム・パン操作まで実装済みである。

次の主な作業対象は、補正結果画面の操作機能、出力外枠、保存処理などである。

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
元画像を表示
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
補正結果と出力境界線を表示
↓
補正結果画面でも幅配分を調整
↓
元画像編集画面へ戻って再調整
```

\---

## 5\. 実装済み機能

### 5.1 画像選択

端末または利用可能な画像プロバイダから画像を選択し、補正対象画像として読み込める。

現在は `ActivityResultContracts.GetContent` を使用している。

選択した画像は `Bitmap` として読み込み、現在の検証版では `MainActivity` が保持する。

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

パン量はView座標系で保持し、拡大後の画像が画面外へ完全に移動しないよう制限する。

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

実機で以下を確認済み。

```text
- ピンチズーム
- 空き領域のドラッグによるパン
- ピンチ中心を維持した拡大・縮小
- パン可能範囲の制限
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
- outputX変更の表示反映
```

出力境界線は補正後Bitmapそのものへ描き込まず、表示用オーバーレイとして描画する。

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

### 5.14 元画像編集画面への戻り

補正結果画面から元画像編集画面へ戻り、分割ラインや境界を再調整できる。

現在は同一Activity内でCustom Viewを差し替える方式で実装している。

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
└ correction/
   ├ BandCorrectionEngine.java
   ├ BandRenderer.java
   ├ BandCorrectionMath.java
   ├ PerspectiveBandRenderer.java
   └ ScanlineBandRenderer.java
```

\---

## 7\. 現在の責務分担

### MainActivity

```text
- 画像選択
- Bitmap読み込み
- sourceBitmap / correctedBitmap保持
- CornerEditView生成
- 元画像編集表示と補正結果表示の切り替え
- 補正処理の呼び出し
- WidthDistributionBarViewの変更通知受け取り
- outputX変更の各Viewへの反映
- 境界追加要求の処理
- 隣接境界間の補間によるBoundaryPair生成
- 境界削除要求の処理
- boundaryIdによるBoundaryPair削除
- WidthDistributionBarViewとの再同期
- ドラッグ終了時の補正結果再生成
```

現状は検証版としてMainActivity中心の構成を維持している。

\---

### CornerEditView

```text
- 元画像表示
- PageCorners保持
- BoundaryPair一覧保持
- 左右端境界描画
- 分割ライン描画
- ライン端点描画
- 端点接続線描画
- 補正帯枠の視覚化
- ページ基準点ドラッグ
- 左右端境界線本体の平行移動
- 分割ライン端点の個別移動
- 分割ライン本体の平行移動
- ピンチズーム
- 空き領域ドラッグによるパン
- ズーム・パン範囲の制限
- ピンチ開始時の進行中ドラッグ解除
- ズーム・パン状態を含む画像座標とView座標の相互変換
- outputX変更の反映
```

\---

### WidthDistributionBarView

```text
- 幅配分バー描画
- BoundaryMarker一覧保持
- 境界マーカー描画
- マーカーヒット判定
- マーカードラッグ
- outputX変更
- 順序制約
- 最小間隔制約
- 空き領域と既存マーカーの長押し判定
- 長押し位置付近へのコンテキストメニュー表示
- 境界追加要求の通知
- boundaryIdを使った境界削除要求の通知
- boundaryIdとoutputX変更の通知
```

`inputTop` / `inputBottom` は保持しない。

\---

### ResultPreviewView

```text
- 補正後Bitmap表示
- BoundaryPairの表示用コピー保持
- 出力境界線描画
- outputX変更の表示反映
```

\---

### BandCorrectionEngine

```text
- PageCornersから左右端境界を作る
- BoundaryPairから内部境界を作る
- outputX順に境界を並べる
- 出力サイズを決定する
- BandRendererを呼び出す
```

\---

## 8\. 現在の状態管理

現在はまだViewModelを導入していない。

編集状態の中心は実質的に `CornerEditView` が保持する以下の状態である。

```text
- PageCorners
- BoundaryPair一覧
```

また、元画像編集画面の表示状態として、`CornerEditView` が以下を保持する。

```text
- zoomScale
- panX
- panY
```

ズーム・パン状態は表示専用であり、補正入力となる画像座標の `PageCorners` や `BoundaryPair` には反映しない。

幅配分バーは `BoundaryMarker` のみを保持し、変更・追加・削除の要求を外部へ通知する。

補正結果Viewは表示用の状態を保持する。

現在の概略データフローは以下。

```text
CornerEditView
    │
    │ PageCorners / BoundaryPair
    ▼
MainActivity
    │
    ├─ BandCorrectionEngineへ補正入力
    │
    ├─ WidthDistributionBarViewへBoundaryMarker表示情報
    │
    └─ ResultPreviewViewへ結果表示情報
```

幅配分変更時は以下。

```text
WidthDistributionBarView
↓ boundaryId + outputX
MainActivity
├ CornerEditViewの対応BoundaryPair.outputXを更新
└ ResultPreviewViewの表示位置を更新
```

境界追加時は以下。

```text
WidthDistributionBarView
↓ outputX
MainActivity
↓ 左右の隣接境界を取得
↓ inputTop / inputBottomを線形補間
↓ BoundaryPairを追加
├ CornerEditViewへ反映
└ WidthDistributionBarViewを再同期
```

境界削除時は以下。

```text
WidthDistributionBarView
↓ boundaryId
MainActivity
↓ 対応するBoundaryPairを削除
├ CornerEditViewへ反映
└ WidthDistributionBarViewを再同期
```

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

### 9.2 Bitmapの直接保持

現在は `MainActivity` が以下を保持する。

```text
- sourceBitmap
- correctedBitmap
```

検証版ではこの構成を維持する。

保存処理と高解像度画像対応を進める際に、URI中心の状態管理へ移行する。

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

## 10\. 未実装機能

### 10.1 補正結果画面

```text
- 出力境界線の表示オン・オフUI
- 出力外枠表示
- 出力外枠の右辺または下辺ドラッグ
- 出力縦横比の変更
- 必要に応じた低解像度プレビュー生成
```

\---

### 10.2 画像取得・保存

```text
- カメラ撮影
- 補正画像保存
- MediaStore等を使った保存
- 保存時の高解像度再読込
- 共有
```

\---

### 10.3 状態管理・画面構成

```text
- selectedImageUri中心の状態管理
- ViewModel導入
- Fragment分割
- 画面回転時の編集状態維持
- 低解像度プレビューと高解像度保存の分離
```

\---

### 10.4 将来機能

```text
- グレースケール化
- 白黒化
- コントラスト調整
- 明るさ調整
- シャープ化
- OCR
- PDF複数ページ管理
- 自動ページ検出
- 本文行・罫線による半自動補正
- 曲線分割ライン
```

\---

## 11\. 既知の課題・注意点

### 11.1 MainActivityへの責務集中

機能追加に伴い、MainActivityが以下を同時に担当している。

```text
- 画像取得
- 状態同期
- 画面切り替え
- 補正処理起動
- プレビュー再生成
```

現時点では許容するが、保存・撮影・画面分割を実装する段階では整理が必要。

\---

### 11.2 CornerEditViewへの状態集中

現在は `PageCorners` と `BoundaryPair` の正本が実質的に `CornerEditView` 内にある。

本格MVPでFragment分割を行う前に、編集状態をUIから分離する必要がある。

\---

### 11.3 高解像度画像のメモリ使用量

現在は選択画像を `BitmapFactory.decodeStream` で直接読み込んでいる。

スマートフォン写真の解像度によってはメモリ負荷が大きくなるため、将来的にはプレビュー用縮小読込と保存用高解像度読込を分離する。

\---

### 11.4 仕様と補正方式の表現

仕様書には射影変換を中心とした説明が残る箇所がある一方、現在の主実装はスキャンライン補間方式である。

今後、補正方式を確定する段階で、仕様上の表現と実装方式の関係を整理する。

\---

### 11.5 ズーム・パン状態のView内保持

現在の `zoomScale`、`panX`、`panY` は `CornerEditView` 内で保持している。

同じ `CornerEditView` インスタンスを再表示する場合は状態を維持できるが、画面回転やView再生成では失われる。

ViewModel導入と画面分割を行う段階で、表示状態をどこまで復元対象とするか整理する。

\---

## 12\. 次の作業候補

### 候補1: 出力境界線表示切り替え

`ResultPreviewView` には出力境界線を描画する機能があるため、補正結果画面へ表示オン・オフUIを追加する。

```text
表示オン
→ 補正帯ごとの出力境界線を表示

表示オフ
→ 補正後画像のみを表示
```

出力境界線は表示用オーバーレイであり、補正後Bitmapや保存画像には描き込まない。

\---

### 候補2: 出力外枠

補正結果画面へ出力外枠を追加し、補正後画像全体の縦横比を変更できるようにする。

MVPでは、右辺または下辺のドラッグによる調整から実装する。

\---

### 候補3: 保存処理

現在の編集状態から補正結果を生成し、端末へ保存する経路を実装する。

高解像度画像対応との関係を考慮し、プレビュー用Bitmapと保存用画像処理の分離も検討する。

\---

### 候補4: 画像取得経路の拡張

現在の画像選択に加えて、カメラ撮影から補正対象画像を取得する経路を実装する。

\---

## 13\. 推奨する次の一手

現時点では、次に以下を進めるのが自然である。

```text
補正結果画面の出力境界線表示オン・オフ
```

出力境界線の描画自体はすでに実装済みであるため、次は表示状態を切り替えるUIとイベント経路を追加する。

補正結果画面の機能拡張を小さい単位で進められ、その後の出力外枠実装にも接続しやすい。

\---

## 14\. 現時点のまとめ

Band Split Scanner は現在、以下まで到達している。

```text
- Java + XML + Custom View構成
- 画像選択
- ページ基準点4点編集
- 初期状態0内部境界・1帯
- 左右端境界の編集
- 左右端境界線本体の平行移動
- BoundaryPairによる入力・出力境界の対応管理
- BoundaryMarkerによる幅配分バー用状態の分離
- 分割ライン表示
- 分割ライン端点編集
- 分割ライン本体の平行移動
- 端点接続線表示
- 補正帯枠の視覚化
- ページ基準点移動時の分割ライン編集状態維持
- 幅配分バー
- 境界マーカー移動
- 境界マーカー追加
- 分割ライン追加
- 隣接境界間の局所補間による追加ライン初期位置決定
- 境界マーカー削除
- boundaryId対応による分割ライン削除
- 長押し位置付近へのコンテキストメニュー表示
- outputXのみを変更する状態同期
- BandRendererによる補正方式分離
- スキャンライン補間による補正
- 補正結果表示
- 出力境界線表示
- 補正結果画面での幅配分調整
- ドラッグ終了時の補正結果再生成
- 元画像編集画面のピンチズーム
- 空き領域ドラッグによるパン
- ピンチ中心を維持した倍率変更
- 1.0〜5.0のズーム倍率制限
- 画像が画面外へ消えないパン範囲制限
- ズーム・パン後の画像座標ベースの境界編集
- ピンチ開始時の進行中ドラッグ解除
```

現在は、帯分割補正の基本検証に加えて、任意の境界数を編集するための追加・移動・削除操作と、ズーム・パンによる詳細編集操作まで実装済みである。

次の段階では、補正結果画面の表示切り替え、出力外枠、保存処理などの機能拡張へ進む。