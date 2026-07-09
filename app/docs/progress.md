# BandSplit Scanner 進捗メモ

## 1. このドキュメントの目的

本ドキュメントは、BandSplit Scanner の現在の実装状況、完了済み機能、現時点の構成、未実装項目、次の作業候補を整理するための進捗メモである。

本アプリは、撮影または選択した本・ノート・書類のページ画像を横方向の複数の補正帯に分割し、各帯を個別に補正することで、通常の四隅補正では残りやすい湾曲を軽減する Android アプリである。

現在は、本格MVPの前段階として、帯分割補正の動作確認と編集UIの基礎実装を進めている。

---

## 2. 開発環境

```text
アプリ名: BandSplit Scanner
開発環境: Android Studio
言語: Java
UI: XMLレイアウト + Android View / Custom View
対象: Androidアプリ
```

学校課題の条件により、実装言語は Java を使用する。

---

## 3. 現在の到達点

現時点では、以下の一連の操作ができるところまで実装済みである。

```text
画像選択
↓
画像表示
↓
ページ基準点4点のドラッグ調整
↓
固定2本の分割ライン表示
↓
分割ライン全体・上下端点のドラッグ編集
↓
幅配分バー上の境界マーカー操作
↓
現在の入力境界・出力境界を使った帯分割補正
↓
補正結果表示
↓
元画像編集画面へ戻って再調整
```

補正結果画面では、補正帯の出力境界を示す出力境界線も表示できる状態になっている。

---

## 4. 実装済み機能

### 4.1 画像選択

端末内の画像を選択し、補正対象画像として読み込める。

現在は撮影機能よりも画像選択を優先して実装している。

---

### 4.2 ページ基準点指定

画像上に以下の4点を表示し、ドラッグして移動できる。

```text
- 左上
- 右上
- 右下
- 左下
```

4点は `PageCorners` として画像座標系で保持する。

表示時には画像座標からView座標へ変換し、タッチ時にはView座標から画像座標へ逆変換する。

---

### 4.3 固定3分割の帯補正

初期状態では、ページ領域を以下の比率で3つの補正帯に分ける。

```text
0.0
1/3
2/3
1.0
```

内部境界は2本であり、補正帯は3つになる。

---

### 4.4 補正方式の分離

補正処理は、共通処理と描画方式の差分を分離する構成へ整理した。

現在の主な構成は以下。

```text
correction/
├ BandCorrectionEngine.java
├ BandRenderer.java
├ BandCorrectionMath.java
├ PerspectiveBandRenderer.java
└ ScanlineBandRenderer.java
```

役割は以下。

```text
BandCorrectionEngine
= 出力サイズ決定、境界列生成、Renderer呼び出し

BandRenderer
= 補正描画方式の共通インターフェース

PerspectiveBandRenderer
= 帯ごとの射影変換方式

ScanlineBandRenderer
= スキャンライン補間方式

BandCorrectionMath
= lerp、距離、面積判定、clamp等の共通計算
```

現在は、境界での画像の連続性を優先するため、主に `ScanlineBandRenderer` を使用している。

---

### 4.5 BoundaryPairによる境界情報管理

入力境界と出力境界の対応関係を `BoundaryPair` として管理するようにした。

概念的な構造は以下。

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
= 境界識別用ID

outputX
= 補正後画像上の出力境界位置
  0.0〜1.0の正規化座標

inputTop
= 元画像上の分割ライン上端点

inputBottom
= 元画像上の分割ライン下端点
```

これにより、元画像側の分割ライン表示と補正処理側が同じ境界情報を使う構成になった。

---

### 4.6 分割ライン表示

`CornerEditView` 上に、現在の `BoundaryPair` 一覧をもとに分割ラインを表示する。

各分割ラインには以下を表示する。

```text
- 上端点
- 下端点
- 上下端点を結ぶ線
```

現在は固定2本から開始しているが、内部構造は複数の `BoundaryPair` を扱える形になっている。

---

### 4.7 分割ライン操作

分割ラインに対して以下の操作を実装済み。

```text
上端点をドラッグ
→ inputTop を更新

下端点をドラッグ
→ inputBottom を更新

ライン本体をドラッグ
→ inputTop と inputBottom を同じ移動量だけ更新
```

分割ライン編集後に補正を実行すると、変更後の入力境界が補正結果へ反映される。

現時点では、ページ基準点4点を再度動かすと、内部の分割ラインは初期比率位置へ再配置される。

---

### 4.8 補正結果表示

補正後Bitmapを表示できる。

現在は `ResultPreviewView` を使い、補正画像の上に編集補助用のオーバーレイを描画できる構成にしている。

---

### 4.9 出力境界線表示

補正結果画面上に、各 `BoundaryPair.outputX` に対応する縦線を表示できる。

```text
分割ライン
= 元画像上の入力境界

出力境界線
= 補正後画像上の出力境界
```

出力境界線は補正画像そのものには描き込まず、`ResultPreviewView` のオーバーレイとして描画している。

---

### 4.10 幅配分バー

`WidthDistributionBarView` を追加し、境界マーカーを左右にドラッグして `BoundaryPair.outputX` を変更できるようにした。

現在の操作仕様は以下。

```text
境界マーカーを左右にドラッグ
↓
BoundaryPair.outputX を更新
↓
元画像上の inputTop / inputBottom は変更しない
↓
補正後画像における各帯の幅配分だけが変化する
```

境界マーカーには順序制約と最小間隔を設けている。

現時点では、補正結果画面で境界マーカーを動かした場合、ドラッグ終了後に補正プレビューを再生成する構成としている。

---

## 5. 現在の主要クラス構成

現在の概略構成は以下。

```text
com.example.bandsplitscanner
├ MainActivity.java
│
├ model/
│  ├ PageCorners.java
│  ├ BoundaryPair.java
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

## 6. 現在の責務分担

### MainActivity

```text
- 画像選択
- Bitmap読み込み
- CornerEditViewの生成と表示
- 補正処理の呼び出し
- 補正結果画面への切り替え
- 元画像編集画面への戻り
- 幅配分バーとの状態同期
```

検証版としてはMainActivityに処理が集まっているが、将来的にはFragmentとViewModelへ分割する予定。

---

### CornerEditView

```text
- 元画像表示
- ページ基準点4点の描画
- 分割ライン描画
- ページ基準点ドラッグ
- 分割ライン上端点ドラッグ
- 分割ライン下端点ドラッグ
- 分割ライン全体ドラッグ
- 画像座標とView座標の変換
- PageCornersとBoundaryPair一覧の保持
```

---

### WidthDistributionBarView

```text
- 幅配分バー描画
- 境界マーカー描画
- 境界マーカーのヒット判定
- outputXの更新
- 順序制約
- 最小間隔制約
- 変更結果のコールバック通知
```

---

### ResultPreviewView

```text
- 補正結果Bitmap表示
- 出力境界線のオーバーレイ描画
- outputXから表示位置を計算
```

---

### BandCorrectionEngine

```text
- PageCornersから左右端境界を生成
- BoundaryPairから内部境界を生成
- outputX順に境界を並べる
- 出力サイズを決定
- BandRendererを呼び出す
```

---

### ScanlineBandRenderer

```text
- 各出力ピクセルが属する補正帯を処理
- 帯内横方向比率uを計算
- 出力縦方向比率vを計算
- 左右入力境界上の点を補間
- 元画像座標を計算
- バイリニア補間で画素値を取得
- 出力Bitmapを生成
```

帯境界では左右の帯が同じ `BoundaryLine` を共有するため、射影変換方式より境界位置の連続性を保ちやすい。

---

## 7. 現時点での既知の設計上の扱い

### 7.1 ページ基準点変更時の分割ライン

現在は、ページ基準点4点を動かすと分割ラインを初期位置へ再配置する。

```text
ページ基準点変更
↓
1/3、2/3位置でBoundaryPairを再生成
```

将来的には、ページ基準点変更後も既存の分割ライン編集状態をどのように維持するか検討が必要。

---

### 7.2 補正方式

以下の2方式を比較できる構成になっている。

```text
PerspectiveBandRenderer
= 帯ごとの射影変換

ScanlineBandRenderer
= スキャンライン補間
```

現在は、帯境界での非連続を減らすため、スキャンライン補間方式を主に使用している。

---

### 7.3 Bitmap保持

現在の検証版では `MainActivity` が以下を直接保持している。

```text
- sourceBitmap
- correctedBitmap
- CornerEditView
```

検証版ではこの構成を維持する。

本格MVPへ進む段階で、画像URIと編集状態を中心にした構成へ移行する予定。

---

## 8. 未実装機能

現時点で未実装の主な項目は以下。

### 元画像編集側

```text
- 分割ラインの追加
- 分割ラインの削除
- 境界マーカーの長押しメニュー
- 幅配分バー空き領域の長押し追加
- 元画像のズーム
- 元画像のパン
- 補正帯枠の表示改善
```

### 補正結果側

```text
- 出力境界線の表示オン・オフ
- 出力外枠表示
- 出力外枠のドラッグによる縦横比変更
- ドラッグ中の低解像度プレビュー
```

### 画像取得・保存

```text
- カメラ撮影
- 補正画像保存
- 高解像度保存処理
- 共有
```

### 将来機能

```text
- 白黒化
- コントラスト調整
- 明るさ調整
- シャープ化
- OCR
- PDF複数ページ管理
- 自動ページ検出
- 曲線分割ライン
```

---

## 9. 次の作業候補

次の作業としては、以下の順序が自然。

### 候補1：幅配分バーの追加・削除操作

現在は固定2個の境界マーカーを動かせる状態なので、次に以下を追加する。

```text
空き領域長押し
↓
「ここに境界を追加」
↓
BoundaryPairを追加
↓
同じoutputX比率でinputTop / inputBottomを初期生成
```

削除は以下。

```text
境界マーカー長押し
↓
「削除」
↓
BoundaryPairを削除
↓
対応する分割ラインも消える
```

この実装により、固定3分割から任意の帯数へ進める。

---

### 候補2：出力境界線の表示オン・オフ

`ResultPreviewView` にはすでに `showOutputBoundaryLines` の考え方があるため、UIにスイッチまたはボタンを追加して切り替え可能にする。

---

### 候補3：ズーム・パン

分割ラインの細かい調整には必要になる。

タッチ優先順位は仕様上、以下を想定する。

```text
1. ライン端点
2. 分割ライン本体
3. 空き領域ドラッグによるパン
4. 2本指ピンチによるズーム
```

---

### 候補4：出力外枠による縦横比調整

補正結果画面に矩形外枠を表示し、右辺または下辺のドラッグで出力縦横比を変更する。

`BoundaryPair.outputX` は正規化座標なので、出力画像サイズが変わっても相対位置を維持できる。

---

## 10. 推奨する次の一手

現時点では、次に以下を進めるのが最も自然。

```text
境界マーカーの追加・削除
```

理由は以下。

```text
- BoundaryPairによる境界管理がすでにできている
- WidthDistributionBarViewができている
- CornerEditViewがBoundaryPair一覧を描画できる
- BandCorrectionEngineが任意個数のBoundaryPairを処理できる
```

したがって、現在の構造を大きく変えずに、固定3分割から本来の「任意帯数の編集」に進める段階に来ている。

---

## 11. 将来の設計移行方針

本格MVPへ進む段階で、以下の構成へ段階的に移行する。

```text
XMLレイアウト
= 固定UI配置

Activity / Fragment
= 画面遷移と操作制御

Custom View
= 画像表示、オーバーレイ描画、タッチ操作

ViewModel
= selectedImageUri、PageCorners、BoundaryPair一覧、OutputSettings等の保持

Correction Engine
= UIに依存しない補正処理
```

ただし、現時点では検証版の実装を優先し、MainActivity中心の構成を維持する。

---

## 12. 現時点のまとめ

BandSplit Scanner は、現在以下の状態まで到達している。

```text
- Java + XML + Custom View構成
- 画像選択
- ページ基準点4点編集
- BoundaryPairによる境界管理
- 分割ライン表示
- 分割ライン端点編集
- 分割ライン全体移動
- 帯分割補正
- 射影変換方式とスキャンライン補間方式の分離
- 補正結果表示
- 出力境界線表示
- 幅配分バー
- 境界マーカー移動
- outputX変更による帯幅調整
- 補正結果の再生成
```

現在は、固定3分割の検証版から、ユーザーが境界数と幅配分を自由に編集できる本格MVPへ移行し始めた段階である。
