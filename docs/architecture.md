# 帯分割補正アプリ 将来設計メモ

## 1. このドキュメントの目的

本ドキュメントでは、帯分割補正アプリの将来的なAndroid実装設計を整理する。

現在の検証版では、`MainActivity` が以下を直接持っている。

```text
- sourceBitmap
- correctedBitmap
- CornerEditView
```

また、`CornerEditView` はコンストラクタで `Bitmap` を受け取り、`MainActivity` が `FrameLayout` に動的に追加している。

検証版としてはこの構成で問題ないが、本格的なMVPに進むと、以下の機能が増える。

```text
- 写真撮影
- 画像選択
- ページ基準点指定
- 元画像編集
- 分割ライン編集
- 幅配分バー
- 補正結果表示
- 出力境界線表示
- 出力外枠による縦横比調整
- 保存
```

そのため、将来的にはActivityに状態と処理を集めすぎず、以下のように責務を分ける。

```text
XMLレイアウト
= 固定UIの配置

Activity / Fragment
= 画面遷移、画像選択、補正処理の呼び出し

Custom View
= 画像表示、編集用オーバーレイ描画、タッチ操作

ViewModel
= 編集状態、選択画像URI、境界情報、出力設定の保持

Correction Engine
= 補正画像の生成
```

---

## 2. 現在の検証版の構成

現在の検証版は、概ね以下の構成である。

```text
MainActivity
├ 画像選択
├ sourceBitmap を保持
├ CornerEditView を生成
├ CornerEditView から PageCorners を取得
├ BandCorrectionEngine を呼び出す
├ correctedBitmap を保持
└ ImageView に補正結果を表示
```

現在の構成のメリットは、実装が単純で分かりやすいことである。

一方で、本格的な編集機能を追加すると、以下の問題が出やすい。

```text
- Activityが肥大化する
- BitmapをActivityフィールドで長く保持する
- 画面回転などで状態が消えやすい
- 編集状態がView内部に閉じてしまう
- 元画像編集画面と補正結果画面で状態共有しにくい
- 保存時に必要な高解像度画像とプレビュー画像の扱いが混ざる
```

したがって、検証版からMVPに進む段階で、状態管理と画面構成を整理する。

---

## 3. 基本方針

将来的な基本方針は以下とする。

```text
- 固定UIはXMLレイアウトに置く
- Custom ViewはXMLに置き、画像や編集状態は後からsetする
- Activityはアプリ全体の入口と画面遷移を担当する
- 各画面の処理はFragmentに分ける
- 編集状態はViewModelに集約する
- Bitmapそのものを長期間の主状態にしない
- 主状態としては画像URI、編集座標、出力設定を保持する
- Bitmapは表示や補正に必要なタイミングで読み込む
```

特に重要なのは、`Bitmap` を状態の中心にしないことである。

本アプリでは、最終的に必要な状態は画像そのものだけではなく、以下の編集情報である。

```text
- どの画像を編集しているか
- ページ基準点はどこか
- 分割ラインはどこか
- 各境界の出力位置はどこか
- 補正後画像の縦横比はいくつか
- 出力境界線を表示するか
```

したがって、中心に置くべき状態は `Bitmap` ではなく、編集モデルである。

---

## 4. XMLレイアウトの役割

XMLレイアウトは、画面に固定で存在するUIを配置するために使う。

たとえば以下はXMLに置く。

```text
- ボタン
- 画像表示領域
- ツールバー
- 幅配分バーの配置領域
- 保存ボタン
- 戻るボタン
- 境界表示のオン・オフスイッチ
```

一方で、XMLから直接 `Bitmap` を渡すことはできない。

そのため、Custom ViewはXMLに配置しつつ、画像や編集状態はActivityまたはFragmentから後で渡す。

例：

```xml
<com.example.bandsplitscanner.view.SourceEditView
    android:id="@+id/sourceEditView"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

Java側では以下のようにする。

```java
sourceEditView = findViewById(R.id.sourceEditView);
sourceEditView.setBitmap(previewBitmap);
sourceEditView.setEditState(editState);
```

つまり、XMLはViewの置き場所を決めるだけであり、実際の画像や編集データはコード側から渡す。

---

## 5. Activityの役割

将来的に `MainActivity` は、できるだけ薄くする。

`MainActivity` の主な役割は以下とする。

```text
- アプリ全体のエントリーポイント
- Navigationのホスト
- Activity Result APIによる画像選択や撮影結果の受け取り
- 必要に応じた権限リクエスト
- Fragment間で共有するViewModelの所有
```

`MainActivity` は、細かい編集処理や補正処理の詳細を持たない。

現在のように、

```java
private Bitmap sourceBitmap;
private Bitmap correctedBitmap;
private CornerEditView cornerEditView;
```

をActivityに持つ構成は、検証版では問題ない。

ただし将来的には、Activityに直接 `Bitmap` や編集Viewを長く保持しない。

---

## 6. Fragmentの役割

本格的なMVPでは、画面ごとにFragmentを分ける方針が自然である。

想定するFragmentは以下。

```text
ImageSelectFragment
= 写真撮影または画像選択

PageCornerFragment
= ページ基準点指定

SourceEditFragment
= 元画像編集、分割ライン編集、幅配分バー操作

ResultFragment
= 補正結果表示、出力境界線表示、出力外枠調整、保存
```

画面遷移は以下のようになる。

```text
ImageSelectFragment
↓
PageCornerFragment
↓
SourceEditFragment
↓↑
ResultFragment
```

Fragmentはそれぞれの画面表示とユーザー操作を担当する。

ただし、編集状態そのものはFragment内に閉じ込めず、共有ViewModelに置く。

---

## 7. Custom Viewの役割

Custom Viewは、画像の表示と編集用オーバーレイの描画、タッチ操作を担当する。

たとえば以下のCustom Viewを想定する。

```text
PageCornerEditView
= ページ四隅の指定

SourceEditView
= 元画像上の分割ライン、端点、補正帯枠の編集

WidthDistributionBarView
= 境界マーカーによる出力幅配分の編集

ResultPreviewView
= 補正後画像、出力境界線、出力外枠の表示・編集
```

Custom Viewが担当することは以下。

```text
- Bitmapを表示する
- Matrixで画像座標とView座標を変換する
- 編集点や線を描画する
- タッチ位置を判定する
- ドラッグ操作を解釈する
- 操作結果をリスナーやコールバックで外部へ通知する
```

Custom Viewが持ってよい状態は以下。

```text
- 表示用Bitmap
- 表示用Matrix
- タッチ中の対象
- ドラッグ中の一時座標
- ズーム・パン状態
```

逆に、Custom Viewだけに閉じ込めない方がよい状態は以下。

```text
- 最終的なPageCorners
- BoundaryPair一覧
- OutputSettings
- ResultViewSettings
- 選択画像URI
```

これらはViewModel側に置く。

---

## 8. ViewModelの役割

ViewModelは、編集状態の中心になる。

将来的な `ScanEditViewModel` は、以下のような状態を持つ。

```text
- selectedImageUri
- previewBitmap または previewImageState
- pageCorners
- boundaryPairs
- outputSettings
- resultViewSettings
- correctedPreviewBitmap
```

ただし、`Bitmap` は大きなメモリを使うため、ViewModelに持たせる場合も扱いには注意する。

基本的には、長期保存すべき主状態は以下である。

```text
- selectedImageUri
- PageCorners
- BoundaryPair一覧
- OutputSettings
- ResultViewSettings
```

`Bitmap` は以下のように扱う。

```text
プレビュー用Bitmap
= 必要に応じて低解像度で読み込む

保存用Bitmap
= 保存時に元画像URIから高解像度で読み込む

補正結果Bitmap
= プレビュー表示用に生成し、必要なら破棄・再生成する
```

ViewModelを使うことで、以下がやりやすくなる。

```text
- 画面回転時に編集状態を維持する
- 元画像編集画面と補正結果画面で状態を共有する
- Fragmentを分けても同じ編集状態を参照できる
- 補正結果画面から戻っても分割ライン状態を保持できる
```

---

## 9. データの流れ

将来的なデータの流れは以下とする。

```text
画像選択
↓
selectedImageUri を ViewModel に保存
↓
プレビュー用Bitmapを生成
↓
PageCornerEditView に表示
↓
ユーザーがページ基準点を編集
↓
PageCorners を ViewModel に保存
↓
SourceEditView で分割ラインを編集
↓
BoundaryPair一覧を ViewModel に保存
↓
ResultFragment で補正処理を実行
↓
correctedPreviewBitmap を生成
↓
ResultPreviewView に表示
↓
保存時に元画像URIから高解像度Bitmapを読み込み直す
↓
現在の編集状態で最終補正画像を生成
↓
保存
```

重要なのは、View同士が直接データを渡さないことである。

たとえば、

```text
SourceEditView → ResultPreviewView
```

に直接データを渡すのではない。

実際には以下のようにする。

```text
SourceEditView
↓ 操作結果を通知
ViewModel
↓ 状態を参照
ResultFragment
↓ 補正処理を実行
ResultPreviewView
```

これにより、画面間の依存が弱くなる。

---

## 10. 補正処理の呼び出し

補正処理は、ActivityやCustom Viewの中に書かない。

補正処理は以下のような専用クラスに任せる。

```text
BandCorrectionEngine
ScanlineBandRenderer
OutputSettings
BoundaryLine
```

呼び出し側は、ViewModelにある編集状態から補正に必要なデータを作る。

```text
PageCorners
BoundaryPair一覧
OutputSettings
↓
BoundaryLine一覧
↓
BandCorrectionEngine
↓
補正後Bitmap
```

補正処理の入力は、できるだけUIに依存しない形にする。

つまり、補正エンジンは以下を知らない。

```text
- Button
- Fragment
- Custom View
- タッチイベント
- 画面上のView座標
```

補正エンジンが扱うのは、画像座標と出力設定だけである。

---

## 11. 座標系の方針

本アプリでは、座標系の扱いを明確に分ける。

```text
画像座標
= 元画像Bitmap上のピクセル座標

View座標
= Android画面上の表示座標

出力座標
= 補正後画像上の座標

正規化出力座標
= 0.0〜1.0で表す出力境界位置
```

編集状態として保存するのは、原則として画像座標または正規化座標である。

```text
PageCorners
= 画像座標

BoundaryPair.inputTop
= 画像座標

BoundaryPair.inputBottom
= 画像座標

BoundaryPair.outputX
= 0.0〜1.0の正規化座標

OutputSettings
= 出力画像の幅・高さ・縦横比
```

View座標は保存しない。

View座標は画面サイズ、ズーム、パン、端末向きによって変わるため、永続的な編集状態には向かない。

Custom Viewは、描画時とタッチ処理時に以下の変換を行う。

```text
描画時：
画像座標 → View座標

タッチ時：
View座標 → 画像座標
```

---

## 12. Bitmap保持方針

将来的には、Activityフィールドで元画像Bitmapを長く保持し続ける設計は避ける。

理由は以下。

```text
- スマホ写真は解像度が大きく、メモリを圧迫しやすい
- 画面回転やActivity再生成で状態が消えやすい
- 保存時には高解像度、編集中には低解像度が適している
- Bitmapより画像URIを保持した方が状態復元しやすい
```

基本方針は以下。

```text
ViewModelに保持する主状態：
- selectedImageUri
- 編集座標
- 出力設定

必要に応じて生成するもの：
- previewBitmap
- correctedPreviewBitmap
- finalCorrectedBitmap
```

編集中は低解像度のプレビュー画像を使う。

保存時には、元画像URIから高解像度画像を読み込み直し、同じ編集状態を使って最終補正画像を生成する。

---

## 13. 現在の検証版からの移行方針

いきなり全体をFragment + ViewModel構成にする必要はない。

段階的に以下のように移行する。

### 第1段階：現在の検証版を維持

```text
MainActivity
CornerEditView
BandCorrectionEngine
ScanlineBandRenderer
```

固定3分割補正の検証を優先する。

### 第2段階：CornerEditViewをXML配置対応にする

`CornerEditView` のコンストラクタで `Bitmap` を必須にするのではなく、後からセットできるようにする。

```java
public void setBitmap(Bitmap bitmap) {
    this.bitmap = bitmap;
    initDefaultCorners();
    invalidate();
}
```

これにより、Custom ViewをXMLに配置できる。

### 第3段階：編集状態をViewModelへ移す

`PageCorners` を `CornerEditView` 内だけで持つのではなく、ViewModel側にも反映する。

```text
CornerEditViewでドラッグ
↓
onCornersChanged(PageCorners corners)
↓
ViewModelを更新
```

### 第4段階：SourceEditViewを追加する

固定3分割ではなく、`BoundaryPair` 一覧を使って分割ラインを表示・編集する。

```text
PageCorners
BoundaryPair一覧
↓
SourceEditViewで描画・編集
```

### 第5段階：ResultPreviewViewを追加する

補正結果画面を単なる `ImageView` ではなく、出力境界線や出力外枠を重ねて描画できるCustom Viewにする。

### 第6段階：Fragment分割する

画面ごとにFragmentを分ける。

```text
PageCornerFragment
SourceEditFragment
ResultFragment
```

共有ViewModelを使って編集状態を共有する。

---

## 14. 推奨パッケージ構成案

将来的なパッケージ構成は以下を目安にする。

```text
com.example.bandsplitscanner
├ MainActivity
│
├ model
│  ├ PageCorners
│  ├ BoundaryPair
│  ├ BoundaryLine
│  ├ OutputSettings
│  └ ResultViewSettings
│
├ view
│  ├ PageCornerEditView
│  ├ SourceEditView
│  ├ WidthDistributionBarView
│  └ ResultPreviewView
│
├ ui
│  ├ ImageSelectFragment
│  ├ PageCornerFragment
│  ├ SourceEditFragment
│  └ ResultFragment
│
├ viewmodel
│  └ ScanEditViewModel
│
├ correction
│  ├ BandCorrectionEngine
│  ├ BandRenderer
│  ├ ScanlineBandRenderer
│  └ CorrectionBand
│
└ image
   ├ ImageLoader
   └ ImageSaver
```

`view` はCustom View、`ui` はFragment、`model` は編集データ、`correction` は補正処理、`image` は画像読み書きに分ける。

---

## 15. 最終的な責務分担

最終的な責務分担は以下とする。

```text
XMLレイアウト
- 固定UIを配置する
- Custom Viewの置き場所を決める
- ボタンやバーの基本構造を定義する

Activity
- アプリの入口
- Navigationの管理
- 画像選択・撮影結果の受け取り
- 共有ViewModelのホスト

Fragment
- 各画面の表示制御
- ボタン操作の処理
- ViewModelとCustom Viewの接続
- 補正処理の呼び出しタイミング制御

Custom View
- 画像表示
- 編集用オーバーレイ描画
- タッチ操作
- View座標と画像座標の変換
- 操作結果の通知

ViewModel
- 選択画像URI
- PageCorners
- BoundaryPair一覧
- OutputSettings
- ResultViewSettings
- 編集状態の一元管理

Correction Engine
- UIに依存しない補正処理
- 境界列から補正帯を生成
- 各補正帯の変換
- 補正後Bitmapの生成
- 変換不能領域のエラー描画

ImageLoader / ImageSaver
- URIからBitmapを読み込む
- プレビュー用に縮小読み込みする
- 保存時に高解像度で読み込む
- 補正後画像を保存する
```

---

## 16. 現時点での結論

現在の検証版では、`MainActivity` が `Bitmap` と `CornerEditView` を直接持ち、`FrameLayout` に動的にViewを差し替える構成で問題ない。

ただし、本格的なMVPでは以下の設計に移行する。

```text
- Viewは基本的にXMLに置く
- BitmapはコンストラクタではなくsetBitmapで渡す
- 編集状態はView内部ではなくViewModelに集約する
- View間で直接データを渡さない
- 画像URI、編集座標、出力設定を主状態とする
- Bitmapは必要なタイミングで読み込む
- 補正処理はUIから独立したCorrection Engineに任せる
```

これにより、以下が実現しやすくなる。

```text
- 画面回転への対応
- 元画像編集画面と補正結果画面の状態共有
- 低解像度プレビューと高解像度保存の分離
- 分割ライン編集や幅配分バーの追加
- 補正結果画面での再調整
- 保存機能の追加
- コードの見通しの維持
```

したがって、将来的な設計の中心は `MainActivity` ではなく、`ScanEditViewModel` と編集モデル群に置く。

`Activity / Fragment` は画面を進める役、`Custom View` は操作と描画の役、`Correction Engine` は補正計算の役として分離する。
