# Band Split Scanner
補正前</br>
<img width="500" height="300" alt="補正前" src="https://github.com/user-attachments/assets/cfd93a17-8658-4428-800a-329065f38db0" /></br>
補正後</br>
<img width="500" height="300" alt="補正後" src="https://github.com/user-attachments/assets/4d3c8f5d-2291-446f-9e4a-d26059fa0c78" /></br>
画像編集画面1</br>
<img width="500" height="300" alt="編集中1" src="https://github.com/user-attachments/assets/0a77fb02-4174-48c7-9266-698307579d89" /></br>
画像編集画面2</br>
<img width="500" height="300" alt="編集中2" src="https://github.com/user-attachments/assets/d1ba9176-41c1-46cb-aa30-485ce619b521" /></br>

本のページなど、横方向に湾曲した紙面を複数の帯へ分割し、帯ごとに補正して1枚の画像へ連結するAndroidアプリです。

Androidアプリ開発の授業課題として、Javaで開発しました。

## 開発状況

**授業提出版完成**

画像選択から補正範囲の編集、帯分割補正、補正結果の再調整、JPEG保存までの主要な操作を実装しています。

## 主な機能

- 端末から補正対象画像を選択
- ページ基準点4点のドラッグ編集
- 左右端境界の端点編集と平行移動
- 内部境界となる分割ラインの追加・移動・削除
- 分割ライン上端点・下端点の個別編集
- 幅配分バーによる各補正帯の出力幅調整
- 元画像編集画面のピンチズーム・パン
- スキャンライン補間による帯分割補正
- 補正結果画面のピンチズーム・パン
- 出力境界線の表示・非表示切り替え
- 出力外枠の左右辺ドラッグによる縦横比調整
- 原画像を再読込した保存用画像生成
- MediaStoreへのJPEG保存
- バックグラウンド保存
- 保存中のUI操作継続

## 基本的な使い方

1. 「画像選択」から補正対象の画像を選びます。
2. ページの四隅をドラッグして補正対象領域を指定します。
3. 必要に応じて幅配分バーを長押しし、内部境界を追加します。
4. 分割ラインの端点または線本体をドラッグし、紙面の湾曲に沿わせます。
5. 幅配分バーの境界マーカーを動かし、各帯の出力幅を調整します。
6. 「補正する」を押して結果を確認します。
7. 補正結果画面で幅配分や出力縦横比を再調整します。
8. 「保存」を押して補正画像を保存します。

Android 10以降では、画像を次の場所へ保存します。

```text
Pictures/BandSplitScanner
```

## 補正方式

補正対象領域を左右方向の複数の帯へ分割します。

各出力画素について、対応する入力画像上の座標を帯の左右境界から計算し、バイリニア補間で画素値を取得します。

隣接する帯が同じ境界を共有するため、帯の境目で位置を連続させやすい構成です。

## 画像処理の流れ

```text
画像URI
├ 編集時
│  └ 長辺最大2048pxの縮小Bitmap
│     └ 編集・補正結果プレビュー
│
└ 保存時
   └ 原画像を再読込
      └ 編集座標を原画像座標へ変換
         └ 保存用Bitmapを生成
            └ JPEGとして保存
```

補正結果プレビューは幅1200pxで生成します。

保存画像は端末メモリを考慮し、次の範囲へ制限します。

```text
最大画素数: 4,000,000画素
最大長辺: 4096px
```

## 技術構成

```text
言語: Java 11
UI: XMLレイアウト + Android View + Custom View
minSdk: 27
targetSdk: 36
```

主要クラスの役割は次のとおりです。

```text
MainActivity
= 画面制御と各処理のオーケストレーション

CornerEditView
= 元画像、ページ基準点、境界、分割ラインの表示・編集

WidthDistributionBarView
= 出力境界位置と帯幅配分の編集

ResultPreviewView
= 補正結果、出力境界線、出力外枠の表示・操作

BandCorrectionEngine
= 境界列の生成とRendererの呼び出し

ScanlineBandRenderer
= スキャンライン補間による補正画像生成

ImageLoader
= 画像寸法取得、編集用縮小読込、保存用原画像読込

ImageSaver
= JPEG圧縮とMediaStore保存
```

## パッケージ構成

```text
app/src/main/java/com/example/bandsplitscanner/
├ MainActivity.java
├ correction/
├ image/
├ model/
└ view/
```

## ビルド・実行

1. このリポジトリを取得します。
2. Android Studioでプロジェクトを開きます。
3. Gradle Syncを実行します。
4. Android 8.1（API 27）以上の実機またはエミュレータで実行します。

## 授業提出版での既知の制約

- アプリ内カメラ撮影には未対応
- 画面回転後の編集状態復元には未対応
- ViewModelとFragmentは未導入
- 非常に大きい原画像では保存時のメモリ負荷が高くなる可能性がある
- OCR、PDF出力、自動ページ検出、画像の白黒化などには未対応

これらは授業提出版の完成条件には含めず、将来改善として扱います。

## ドキュメント

- [`docs/specification.md`](docs/specification.md): 機能・操作仕様
- [`docs/architecture.md`](docs/architecture.md): 現在の構造と将来設計
- [`docs/progress.md`](docs/progress.md): 実装状況と既知の課題

## ステータス

授業提出版として開発を一区切りにしています。

今後再開する場合は、編集状態のView外分離、ViewModel導入、画面回転対応、Fragment分割の順で進める予定です。
