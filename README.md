# VISIONOID MAG PLOTTER

**ドローンショー現場磁場環境調査アプリ**

![Platform](https://img.shields.io/badge/Platform-Android%2015-green)
![Language](https://img.shields.io/badge/Language-Java-orange)
![License](https://img.shields.io/badge/License-Proprietary-blue)

## 概要

VISIONOID MAG PLOTTERは、ドローンショー実施前の現場磁場環境を調査するためのAndroidアプリケーションです。地面からの磁場ノイズを計測・可視化し、ドローンのコンパスに影響を与える危険エリアをヒートマップで特定します。

## 主な機能

### 🎯 ミッション管理
- 調査ミッションの作成・編集・削除
- ミッション毎の閾値設定
- 計測データの保存・管理

### 📍 位置情報取得
- GPS/GNSSによるリアルタイム位置取得
- 位置精度の表示
- オフライン動作対応

### 🧭 磁場計測
- 端末内蔵磁気センサーによる3軸計測
- 総磁場強度（μT）のリアルタイム表示
- ノイズ値（基準値との偏差）の算出

### 🗺️ ヒートマップ表示
- OpenStreetMap ベースの地図
- 計測ポイントを色分け表示
  - 🟢 **緑**: 安全（ノイズ < 安全閾値）
  - 🟡 **黄**: 注意（安全閾値 ≤ ノイズ < 危険閾値）
  - 🔴 **赤**: 危険（ノイズ ≥ 危険閾値）

### 📸 エクスポート
- 地図画面のスクリーンショット保存

### 📥 オフライン地図
- 事前に地図タイルをダウンロード
- ネット接続のない現場でも利用可能

## 技術仕様

| 項目 | 内容 |
|------|------|
| 対応OS | Android 15 (API Level 35) |
| 最小SDK | Android 8.0 (API Level 26) |
| 開発言語 | Java |
| 地図API | OpenStreetMap (osmdroid) |
| データベース | SQLite (Room) |
| アーキテクチャ | MVVM |

## デフォルト設定値

| 設定項目 | デフォルト値 |
|---------|------------|
| 基準磁場値 | 46.0 μT（日本平均） |
| 安全閾値 | 10.0 μT |
| 危険閾値 | 50.0 μT |
| 計測間隔 | 1.0 秒 |

## 必要なパーミッション

- `ACCESS_FINE_LOCATION` - GPS位置情報
- `ACCESS_COARSE_LOCATION` - おおよその位置情報
- `INTERNET` - 地図タイル取得
- `WRITE_EXTERNAL_STORAGE` - スクリーンショット保存（Android 9以下）
- `READ_MEDIA_IMAGES` - メディアアクセス（Android 13以上）

## ビルド方法

### 前提条件
- Android Studio Hedgehog | 2023.1.1 以上
- JDK 17
- Android SDK 35

### ビルド手順

```bash
# リポジトリをクローン
git clone https://github.com/your-repo/android_mag_plotter.git
cd android_mag_plotter

# Android Studio でプロジェクトを開く
# または Gradle でビルド
./gradlew assembleDebug
```

## プロジェクト構造

```
app/src/main/
├── java/com/visionoid/magplotter/
│   ├── MagPlotterApplication.java      # Applicationクラス
│   ├── data/
│   │   ├── dao/                        # Data Access Objects
│   │   ├── db/                         # Database
│   │   ├── model/                      # エンティティ
│   │   └── repository/                 # リポジトリ
│   ├── ui/
│   │   ├── splash/                     # スプラッシュ画面
│   │   ├── mission/                    # ミッション管理
│   │   ├── measurement/                # 計測画面
│   │   ├── settings/                   # 設定画面
│   │   └── offlinemap/                 # オフライン地図
│   └── service/                        # バックグラウンドサービス
└── res/
    ├── layout/                         # レイアウトXML
    ├── values/                         # リソース値
    ├── drawable/                       # ドローアブル
    └── menu/                           # メニュー
```

## UI デザイン

**スパイテック（ミッションインポッシブル風）**をコンセプトにしたダークテーマUIを採用。

### カラースキーム
- **背景**: #0A0A0F（ほぼ黒）
- **パネル**: #1A1A2E（ダークネイビー）
- **アクセント**: #00F5FF（シアン）
- **警告**: #FF6B35（オレンジ）
- **危険**: #FF0055（ネオンレッド）

### デザイン要素
- HUD風データパネル
- グリッド/スキャンライン背景
- モノスペースフォント
- グロー効果のあるボタン

## 使用方法

1. **ミッション作成**: 新規ミッションを作成し、場所名・担当者・閾値を設定
2. **現場へ移動**: オフライン地図を事前にダウンロード（必要に応じて）
3. **計測実施**: 
   - 自動モード: 設定間隔で自動的に計測
   - 手動モード: ボタンタップで任意のポイントを計測
4. **結果確認**: ヒートマップで危険エリアを視覚的に確認
5. **エクスポート**: スクリーンショットを保存して共有

## ライセンス

Proprietary - VISIONOID Inc.

## 謝辞

- [osmdroid](https://github.com/osmdroid/osmdroid) - OpenStreetMap for Android
- [OpenStreetMap](https://www.openstreetmap.org/) - 地図データ

