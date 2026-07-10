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

現在は、固定3分割の検証版MVPから、本格MVPの編集機能へ移行している段階である。

帯分割補正そのものについては、以下を確認できている。

```text
- ページ基準点から補正対象領域を定義できる
- 複数の入力境界から補正帯を生成できる
- スキャンライン補間方式で帯ごとの補正画像を生成できる
- 入力境界を編集して補正結果へ反映できる
- 出力境界を編集して各補正帯の出力幅を変更できる
- 元画像編集画面と補正結果画面を往復して再調整できる
```

現在の主な作業対象は、任意の帯数を扱うための境界追加・削除、ズーム・パン、補正結果画面の操作機能、保存処理などである。

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
左右端境界・分割ライン・補正帯枠を確認
↓
分割ラインの端点または線本体をドラッグ
↓
左右端境界の端点または線本体をドラッグ
↓
幅配分バーの境界マーカーをドラッグ
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

初期状態では2個の `BoundaryPair` を生成し、3つの補正帯に分割する。

```text
0.0
1/3
2/3
1.0
```

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

### 5.8 幅配分バー

`WidthDistributionBarView` を実装済み。

幅配分バー上の境界マーカーを左右にドラッグすると、対応する境界の `outputX` を変更できる。

```text
境界マーカーをドラッグ
↓
BoundaryMarker.outputX を更新
↓
boundaryId と新しい outputX を通知
↓
対応する BoundaryPair.outputX のみ更新
↓
inputTop / inputBottom は変更しない
```

境界マーカーには以下の制約がある。

```text
- 他のマーカーを追い越せない
- 左右端へ密着できない
- 隣接マーカーとの最小間隔を維持する
```

\---

### 5.9 補正処理の方式分離

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

### 5.10 スキャンライン補間方式

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

### 5.11 補正結果表示

補正結果は `ResultPreviewView` で表示する。

現在の責務は以下。

```text
- 補正後Bitmapの表示
- 出力境界線のオーバーレイ描画
- outputX変更の表示反映
```

出力境界線は補正後Bitmapそのものへ描き込まず、表示用オーバーレイとして描画する。

\---

### 5.12 補正結果画面での幅配分調整

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

### 5.13 元画像編集画面への戻り

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
- 画像座標とView座標の相互変換
- outputX変更の反映
```

\---

### WidthDistributionBarView

```text
- 幅配分バー描画
- BoundaryMarker一覧保持
- 境界マーカー描画
- マーカーヒット判定
- outputX変更
- 順序制約
- 最小間隔制約
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

幅配分バーは `BoundaryMarker` のみを保持し、変更を外部へ通知する。

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

### 9.4 初期境界数は固定

内部構造は複数の `BoundaryPair` を扱えるが、現在の初期生成は2本の内部境界である。

```text
1/3
2/3
```

境界追加・削除UIは未実装。

\---

## 10\. 未実装機能

### 10.1 境界数の編集

```text
- 幅配分バー空き領域の長押し
- 「ここに境界を追加」メニュー
- BoundaryPair追加
- 追加した境界に対応する分割ライン生成
- マーカー長押し
- 「削除」メニュー
- BoundaryPair削除
```

\---

### 10.2 元画像編集操作

```text
- 元画像のパン
- ピンチズーム
- ズーム・パン状態を考慮したタッチ操作
```

\---

### 10.3 補正結果画面

```text
- 出力境界線の表示オン・オフUI
- 出力外枠表示
- 出力外枠の右辺または下辺ドラッグ
- 出力縦横比の変更
- 必要に応じた低解像度プレビュー生成
```

\---

### 10.4 画像取得・保存

```text
- カメラ撮影
- 補正画像保存
- MediaStore等を使った保存
- 保存時の高解像度再読込
- 共有
```

\---

### 10.5 状態管理・画面構成

```text
- selectedImageUri中心の状態管理
- ViewModel導入
- Fragment分割
- 画面回転時の編集状態維持
- 低解像度プレビューと高解像度保存の分離
```

\---

### 10.6 将来機能

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

## 12\. 次の作業候補

### 候補1: 境界マーカーの追加・削除

最も自然な次の作業候補である。

現在は以下がすでに成立している。

```text
- BoundaryPairで内部境界を管理できる
- WidthDistributionBarViewが複数マーカーを描画できる
- CornerEditViewが複数のBoundaryPairを描画できる
- BandCorrectionEngineが任意個数のBoundaryPairを処理できる
```

次は以下を追加する。

```text
空き領域を長押し
↓
「ここに境界を追加」
↓
新しいBoundaryPairを生成
↓
BoundaryMarkerを追加
↓
CornerEditViewへ新しい分割ラインを表示
```

削除も同様に、境界IDを基準として全体状態から削除する。

\---

### 候補2: ズーム・パン

細かい境界調整を行うために必要。

仕様上のタッチ優先順位を基準に実装する。

```text
1. 操作点
2. 入力境界線本体
3. 空き領域ドラッグによるパン
4. 2本指ピンチによるズーム
```

\---

### 候補3: 出力境界線表示切り替え

`ResultPreviewView` には表示状態を切り替える仕組みがあるため、UI側にボタンまたはスイッチを追加する。

\---

### 候補4: 出力外枠

補正結果画面へ出力外枠を追加し、補正後画像全体の縦横比を変更できるようにする。

\---

## 13\. 推奨する次の一手

現時点では、次に以下を進めるのが自然である。

```text
境界マーカーと分割ラインの追加・削除
```

これにより、現在の固定3分割から、本アプリの中心機能である任意の帯数による手動補正へ進める。

\---

## 14\. 現時点のまとめ

Band Split Scanner は現在、以下まで到達している。

```text
- Java + XML + Custom View構成
- 画像選択
- ページ基準点4点編集
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
- outputXのみを変更する状態同期
- BandRendererによる補正方式分離
- スキャンライン補間による補正
- 補正結果表示
- 出力境界線表示
- 補正結果画面での幅配分調整
- ドラッグ終了時の補正結果再生成
```

現在は、帯分割補正の基本検証を終え、固定3分割から任意の境界数を扱う本格MVPへ移行する段階にある。

