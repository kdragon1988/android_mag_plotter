# VISIONOID MAG PLOTTER

**ドローンショー現場磁場環境調査アプリ**

![Platform](https://img.shields.io/badge/Platform-Android%2015-green)
![Language](https://img.shields.io/badge/Language-Java-orange)
![License](https://img.shields.io/badge/License-Proprietary-blue)
![Version](https://img.shields.io/badge/Version-2.2.0-blue)

## 📱 プラットフォーム

| バージョン | リポジトリ | 状態 |
|-----------|-----------|------|
| **Android（安定版）** | このリポジトリ | ✅ v2.2.0 リリース済み |
| **Flutter（iOS/Android）** | [mag_plotter_flutter](https://github.com/kdragon1988/mag_plotter_flutter) | 🚧 開発中 |

## 概要

VISIONOID MAG PLOTTERは、ドローンショー実施前の現場磁場環境を調査するためのAndroidアプリケーションです。地面からの磁場ノイズを計測・可視化し、ドローンのコンパスに影響を与える危険エリアをヒートマップで特定します。

## 🆕 v2.2.0 新機能

### 🛰️ USB RTK GPS対応（H-RTK F9P）

高精度RTK GPS（H-RTK F9P）をUSB経由で接続し、センチメートル級の位置精度で計測できるようになりました。

| 機能 | 説明 |
|------|------|
| **RTK Fix表示** | Fix状態（RTK Fix/Float/3D Fix/DGPS）をリアルタイム表示 |
| **高精度位置** | RTK補正時はcm級の位置精度 |
| **衛星情報** | 使用衛星数・受信衛星数の表示 |

### 🧲 外部磁気センサー対応（IST8310）

H-RTK F9P内蔵のIST8310磁気センサーに対応。スマートフォン内蔵センサーより安定した磁場計測が可能です。

| 特徴 | 内蔵センサー | IST8310（USB） |
|------|-------------|----------------|
| 安定性 | 端末による差あり | 高安定 |
| スマホの影響 | 受ける | 受けない |
| 更新レート | 端末依存 | 20Hz固定 |
| 接続 | 不要 | Raspberry Pi Pico経由 |

### 🍓 Raspberry Pi Pico対応

Raspberry Pi PicoをUSBブリッジとして使用し、H-RTK F9PのGPS・磁気データをAndroidに転送します。

```
H-RTK F9P (GPS + Compass)
        │
        ├── UART (TX/RX) ──► Raspberry Pi Pico ──► USB CDC ──► Android
        └── I2C (SDA/SCL) ──► IST8310 磁気センサー
```

**対応モデル:**
- Raspberry Pi Pico (RP2040)
- Raspberry Pi Pico 2 (RP2350)

詳細なセットアップ手順は [`pico/README.md`](pico/README.md) を参照してください。

---

## 🆕 v2.1.0 新機能

### ✏️ 地図作図機能
計測画面上で図形を描画できるようになりました：

| 図形 | 機能 |
|------|------|
| **ポリゴン** | 任意の多角形を描画、面積計算 |
| **ライン** | 線分を描画、距離計算 |
| **サークル** | 円を描画、半径・面積表示 |

---

## 🆕 v2.0.0 新機能

### 🗺️ GeoJSONマップレイヤー
計測画面上に以下のレイヤーをオーバーレイ表示できるようになりました：

| レイヤー | 説明 | 色 |
|---------|------|-----|
| **DID（人口集中地区）** | 国土地理院の人口集中地区データ（日本全国） | 🟠 オレンジ |
| **空港等周辺制限区域** | 空港周辺の飛行制限エリア | 🔴 赤 |
| **飛行禁止区域** | 重要施設周辺など飛行禁止エリア | 🔵 青 |

これらのレイヤーは**改正航空法**に基づくドローン飛行規制区域を視覚化し、安全な飛行計画の立案をサポートします。

---

## 主な機能

### 🎯 ミッション管理
- 調査ミッションの作成・編集・削除
- ミッション毎の閾値設定
- 計測データの保存・管理

### 📍 位置情報取得
- GPS/GNSSによるリアルタイム位置取得
- **USB RTK GPS対応**（H-RTK F9P）
- RTK Fix状態・精度の表示
- オフライン動作対応

### 🧭 磁場計測
- 端末内蔵磁気センサーによる3軸計測
- **USB外部磁気センサー対応**（IST8310）
- 総磁場強度（μT）のリアルタイム表示（20Hz）
- ノイズ値（基準値との偏差）の算出

### 🗺️ マップレイヤー
- DID（人口集中地区）レイヤー表示
- 空港等周辺制限区域レイヤー表示
- 飛行禁止区域レイヤー表示
- GeoJSON形式のカスタムレイヤー対応

### ✏️ 作図機能
- ポリゴン・ライン・サークル描画
- 距離・面積の自動計算
- 図形の保存・編集

## 🧲 磁場ノイズとドローンの関係（やさしい解説）

### なぜ磁場を調べる必要があるの？

ドローンは**コンパス（方位センサー）**を使って「自分がどっちを向いているか」を知ります。このコンパスは**地球の磁場（地磁気）**を利用して方角を判断しています。スマホの地図アプリで方角がわかるのと同じ仕組みです。

しかし、地面の下に**鉄筋・鉄骨・配管・地下ケーブル**などがあると、その場所の磁場が乱れます。これが「**磁場ノイズ**」です。

```
🌍 正常な場所        🏗️ ノイズのある場所
    ↓                    ↓
 地球の磁場          地球の磁場 + 鉄からの磁場
    ↓                    ↓
 コンパス正常         コンパス狂う！
    ↓                    ↓
 安定飛行 ✅          墜落リスク ⚠️
```

### ドローンへの影響

| 状況 | 起こること |
|------|-----------|
| 離陸前にノイズを検知 | ドローンが離陸を拒否する（安全機能） |
| ノイズに気づかず離陸 | 飛行中に機体が回転・暴走する可能性 |
| 着陸地点にノイズ | 着陸時に位置がズレる・制御不能になる |

**ドローンショーでは数百機が同時に飛ぶため、1機でも制御不能になると大事故につながります。**

だからこそ、**事前に現場を歩いて磁場ノイズをチェックする**ことが重要なのです。

---

## 📐 MAG FIELD / NOISE の算出方法

### MAG FIELD（磁場強度）とは？

**「その場所の磁場の強さ」**を表す数値です。

磁気センサー（内蔵またはIST8310）から取得した前後（X）・左右（Y）・上下（Z）の3方向の磁場を合成して、**トータルの磁場の強さ**を計算しています。

```
MAG FIELD = √(Bx² + By² + Bz²)
```

> 💡 難しく考えなくてOK！要は「磁石の力がどれくらい強いか」を表す数字です。

**単位**: μT（マイクロテスラ）

**目安**:
| 場所 | 磁場強度 |
|------|---------|
| 地球の自然磁場（日本） | 約46μT |
| 鉄筋建造物の近く | 50〜100μT以上 |
| 冷蔵庫のマグネット | 約5,000μT |

---

### NOISE（ノイズ）とは？

**「本来あるべき磁場からどれだけズレているか」**を表す数値です。

```
NOISE = |MAG FIELD − 基準値|
```

**計算例**:
- 日本の基準値: 46μT
- 計測した値: 48μT
- **ノイズ = |48 − 46| = 2μT** → 安全 ✅

- 計測した値: 60μT
- **ノイズ = |60 − 46| = 14μT** → 危険！🔴

---

### 色で見る危険度

| 色 | 判定 | ノイズ値 | 意味 |
|-----|------|---------|------|
| 🟢 緑 | **SAFE** | 0〜5μT | 問題なし。ドローン離陸OK |
| 🟡 黄 | **WARNING** | 5.1〜10μT | 要注意。なるべく避ける |
| 🔴 赤 | **DANGER** | 10μT超 | 危険！この場所での離着陸NG |

---

### よくあるノイズの原因

| 原因 | 例 |
|------|-----|
| **地中の金属** | 鉄筋コンクリート基礎、マンホール、配管 |
| **電気設備** | 送電線、変圧器、電気自動車の充電器 |
| **車両** | 駐車中の車、重機 |
| **建造物** | 鉄骨ビル、橋、フェンス |

---

## 📊 技術詳細（エンジニア向け）

### センサー仕様

#### 内蔵磁気センサー
端末の磁気センサー（`TYPE_MAGNETIC_FIELD`）から取得したX, Y, Z軸の磁場値を使用。

#### USB磁気センサー（IST8310）
H-RTK F9P内蔵のIST8310からRaspberry Pi Pico経由でデータを取得。独自の`$PIMAG`プロトコルを使用。

```
$PIMAG,magX,magY,magZ,totalField*XX
```

| フィールド | 説明 | 単位 |
|-----------|------|------|
| magX | X軸磁場強度 | μT |
| magY | Y軸磁場強度 | μT |
| magZ | Z軸磁場強度 | μT |
| totalField | 総磁場強度 | μT |
| XX | チェックサム | - |

```java
// 3軸の磁場値から総磁場強度を計算
double magField = Math.sqrt(Bx*Bx + By*By + Bz*Bz);

// ノイズ値を計算（基準値との差分）
double noise = Math.abs(magField - referenceMag);
```

### GPS/GNSS仕様

#### 内蔵GPS
Android標準の`LocationManager`を使用。

#### USB RTK GPS（H-RTK F9P）
NMEA 0183およびu-blox UBXプロトコルに対応。

| プロトコル | メッセージ | 用途 |
|-----------|-----------|------|
| NMEA | GGA, RMC, GSV | 位置・時刻・衛星情報 |
| UBX | NAV-PVT | 高精度位置・速度 |
| UBX | NAV-SAT | 衛星詳細情報 |

**RTK Fix状態**:
| 状態 | 精度 | 色 |
|------|------|-----|
| RTK Fix | cm級 | 🟢 緑 |
| RTK Float | dm級 | 🟡 黄 |
| 3D Fix / DGPS | m級 | 🔵 青 |
| No Fix | - | 🔴 赤 |

---

### 🗺️ ヒートマップ表示
- OpenStreetMap ベースの地図
- 計測ポイントを色分け表示
  - 🟢 **緑**: 安全（ノイズ ≤ 5μT）
  - 🟡 **黄**: 注意（5μT < ノイズ ≤ 10μT）
  - 🔴 **赤**: 危険（ノイズ > 10μT）

### 🗺️ GeoJSONレイヤー

マップ上にGeoJSON形式のポリゴンレイヤーを表示できます。

```java
// GeoJSONパーサーでPolygonを生成
List<Polygon> polygons = GeoJsonParser.parse(
    geoJsonString,
    fillColor,
    strokeColor,
    displayStyle
);

// MapLayerManagerでレイヤーを追加
mapLayerManager.addLayer(LayerType.DID, geoJsonData);
```

**対応データ形式**:
- FeatureCollection
- Feature
- Polygon / MultiPolygon

**レイヤータイプ**:
| タイプ | アセットパス | データソース |
|--------|-------------|--------------|
| DID | `layers/did_japan.geojson` | 国土地理院 |
| 空港制限 | `layers/airport_restriction.geojson` | カスタム |
| 飛行禁止 | `layers/no_fly_zone.geojson` | カスタム |

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
| USB Serial | hoho.android.usbserial |

## デフォルト設定値

| 設定項目 | デフォルト値 |
|---------|------------|
| 基準磁場値 | 46.0 μT（日本平均） |
| 安全閾値 | 5.0 μT |
| 危険閾値 | 10.0 μT |
| 計測間隔 | 1.0 秒 |
| 磁気センサー更新 | 20Hz（50ms） |

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
git clone https://github.com/kdragon1988/android_mag_plotter.git
cd android_mag_plotter

# Android Studio でプロジェクトを開く
# または Gradle でビルド
./gradlew assembleDebug
```

## プロジェクト構造

```
app/src/main/
├── assets/
│   └── layers/                        # GeoJSONレイヤーデータ
│       ├── did_japan.geojson          # DID（人口集中地区）
│       ├── airport_restriction.geojson # 空港制限区域
│       └── no_fly_zone.geojson        # 飛行禁止区域
├── java/com/visionoid/magplotter/
│   ├── MagPlotterApplication.java     # Applicationクラス
│   ├── data/
│   │   ├── dao/                       # Data Access Objects
│   │   ├── db/                        # Database
│   │   ├── layer/                     # レイヤーデータ管理
│   │   ├── model/                     # エンティティ
│   │   └── repository/                # リポジトリ
│   ├── gps/                           # GPS/GNSS関連（v2.2.0〜）
│   │   ├── GpsFixStatus.java          # RTK Fix状態
│   │   ├── GpsLocation.java           # GPS位置情報
│   │   ├── GpsSourceType.java         # GPSソース種別
│   │   ├── NmeaParser.java            # NMEAパーサー
│   │   ├── UbxMessage.java            # UBXメッセージ
│   │   ├── UbxParser.java             # UBXパーサー
│   │   └── UsbGpsManager.java         # USB GPS管理
│   ├── ui/
│   │   ├── splash/                    # スプラッシュ画面
│   │   ├── mission/                   # ミッション管理
│   │   ├── measurement/               # 計測画面
│   │   ├── settings/                  # 設定画面
│   │   ├── offlinemap/                # オフライン地図
│   │   ├── view/                      # カスタムビュー
│   │   │   └── NoiseLevelGauge.java   # ノイズレベルゲージ
│   │   └── map/                       # マップ関連
│   │       ├── drawing/               # 作図機能
│   │       └── layer/                 # レイヤー管理
│   └── util/                          # ユーティリティ
├── res/
│   ├── layout/                        # レイアウトXML
│   ├── values/                        # リソース値
│   ├── drawable/                      # ドローアブル
│   └── menu/                          # メニュー
└── pico/                              # Raspberry Pi Pico ファームウェア
    ├── main.py                        # MicroPythonファームウェア
    └── README.md                      # Picoセットアップガイド
```

## UI デザイン

**スパイテック（ミッションインポッシブル風）**をコンセプトにしたダークテーマUIを採用。

### カラースキーム
- **背景**: #0A0A0F（ほぼ黒）
- **パネル**: #1A1A2E（ダークネイビー）
- **アクセント**: #00F5FF（シアン）
- **警告**: #FF6B35（オレンジ）
- **危険**: #FF0055（ネオンレッド）

### レイヤーカラー
- **DID**: #FF9800（オレンジ）透過度30%
- **空港制限**: #F44336（赤）透過度30%
- **飛行禁止**: #2196F3（青）透過度30%

### デザイン要素
- HUD風データパネル
- グリッド/スキャンライン背景
- モノスペースフォント
- グロー効果のあるボタン

## 使用方法

### 基本的な使い方

1. **ミッション作成**: 新規ミッションを作成し、場所名・担当者・閾値を設定
2. **現場へ移動**: オフライン地図を事前にダウンロード（必要に応じて）
3. **レイヤー表示**: メニュー → レイヤー選択でDID等を表示
4. **計測実施**: 
   - 自動モード: 設定間隔で自動的に計測
   - 手動モード: ボタンタップで任意のポイントを計測
5. **結果確認**: ヒートマップで危険エリアを視覚的に確認
6. **エクスポート**: スクリーンショットを保存して共有

### USB RTK GPS + 磁気センサーを使う場合

1. **ハードウェア準備**:
   - H-RTK F9PとRaspberry Pi Picoを接続（[pico/README.md](pico/README.md)参照）
   - PicoにMicroPythonファームウェアを書き込み

2. **Android接続**:
   - USB OTGケーブルでPico → Android接続
   - 「USB GPS接続完了」トースト表示を確認
   - 「磁気センサー: F9P IST8310 (自動切替)」トースト表示を確認

3. **計測**:
   - RTK Fix状態がリアルタイムで表示
   - 磁気センサーは自動的にIST8310に切り替わり
   - 20Hzで高速更新

## DIDデータの更新

DID（人口集中地区）データは国勢調査に基づき5年ごとに更新されます。最新データは以下のツールで取得できます：

```bash
cd tools
python download_did_data.py
```

詳細は `app/src/main/assets/layers/README.md` を参照してください。

## バージョン履歴

| バージョン | リリース日 | 主な変更点 |
|-----------|-----------|-----------|
| v2.2.0 | 2025-01 | USB RTK GPS対応、外部磁気センサー（IST8310）対応、Raspberry Pi Picoブリッジ対応、ノイズ閾値調整（0-5/5-10/10+μT） |
| v2.1.0 | 2024-12-25 | 地図作図機能追加（ポリゴン/ライン/サークル描画、距離・面積計算） |
| v2.0.0 | 2024-12-25 | GeoJSONマップレイヤー機能追加（DID/空港制限/飛行禁止） |
| v1.0.0 | 2024-12-01 | 初回リリース |

## ライセンス

Proprietary - VISIONOID Inc.

## 謝辞

- [osmdroid](https://github.com/osmdroid/osmdroid) - OpenStreetMap for Android
- [OpenStreetMap](https://www.openstreetmap.org/) - 地図データ
- [国土地理院](https://www.gsi.go.jp/) - DID（人口集中地区）データ
- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) - USB Serial通信
- [MicroPython](https://micropython.org/) - Raspberry Pi Pico ファームウェア
