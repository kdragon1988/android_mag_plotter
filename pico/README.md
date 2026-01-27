# H-RTK F9P GPS Bridge for Raspberry Pi Pico

H-RTK F9P (GPS + コンパス) を Raspberry Pi Pico 経由で Android に接続するためのファームウェア。

## 概要

```
H-RTK F9P (GPS + Compass)
        │
        ├── UART (TX/RX) ──► Raspberry Pi Pico ──► USB CDC ──► Android
        └── I2C (SDA/SCL) ──► (将来拡張用)
```

## 必要な部品

- **H-RTK F9P** (u-blox ZED-F9Pベース、GPS + コンパス搭載)
- **Raspberry Pi Pico** (RP2040)
- **ジャンパーワイヤー** (5本: 5V, GND, TX, RX, SDA, SCL)
- **USB-C ケーブル** (Pico - Android接続用)

## 配線図

```
        H-RTK F9P                    Raspberry Pi Pico (USBを上にして)
    ┌─────────────┐              ┌─────────────────────────┐
    │  USB-C      │              │         [USB]           │
    │             │              │      ──────────► Android│
    │  5V  ───────┼──────────────┼─► VBUS (左上)           │
    │  RX  ───────┼──────────────┼─► GP0  (右列 1番目)     │
    │  TX  ───────┼──────────────┼─► GP1  (右列 2番目)     │
    │  SCL ───────┼──────────────┼─► GP5  (右列 7番目)     │
    │  SDA ───────┼──────────────┼─► GP4  (右列 6番目)     │
    │  KEY        │              │                         │
    │  LED        │              │                         │
    │  3V3        │              │                         │
    │  BUZ-       │              │                         │
    │  GND ───────┼──────────────┼─► GND  (左列 GND)       │
    └─────────────┘              └─────────────────────────┘
```

### ピン対応表

| F9P Pin | Pico GPIO | Pico 位置 | 機能 |
|---------|-----------|-----------|------|
| 5V | VBUS | 左列 上から1番目 | 電源 5V |
| RX | GP0 (UART0 TX) | 右列 上から1番目 | Pico → F9P (コマンド送信) |
| TX | GP1 (UART0 RX) | 右列 上から2番目 | F9P → Pico (データ受信) |
| SCL | GP5 (I2C0 SCL) | 右列 上から7番目 | I2C クロック (将来用) |
| SDA | GP4 (I2C0 SDA) | 右列 上から6番目 | I2C データ (将来用) |
| GND | GND | 左列 上から3番目 | グランド |

### Picoピン配置図（USBコネクタを上にした場合）

```
                         [USB コネクタ]
                    ┌─────────────────┐
      VBUS (5V) ◄───┤ 40            1 ├───► GP0 (TX)
           VSYS ────┤ 39            2 ├───► GP1 (RX)
   F9P GND ─────►───┤ GND           3 ├──── GND
         3V3_EN ────┤ 37            4 ├──── GP2
       3V3(OUT) ────┤ 36            5 ├──── GP3
       ADC_VREF ────┤ 35            6 ├───► GP4 (SDA)
          GP28  ────┤ 34            7 ├───► GP5 (SCL)
           GND  ────┤ 33            8 ├──── GND
          GP27  ────┤ 32            9 ├──── GP6
          GP26  ────┤ 31           10 ├──── GP7
            :       │  :            : │
                    └─────────────────┘
```

## セットアップ手順

### 1. MicroPythonのインストール

1. [Raspberry Pi Pico MicroPython](https://micropython.org/download/rp2-pico/) から最新の `.uf2` ファイルをダウンロード
2. PicoのBOOTSELボタンを押しながらUSBケーブルでPCに接続
3. 表示されるドライブ (RPI-RP2) に `.uf2` ファイルをドラッグ&ドロップ
4. Picoが自動的に再起動

### 2. ファームウェアの書き込み

#### Thonny IDEを使用する場合

1. [Thonny IDE](https://thonny.org/) をインストール
2. Thonnyを起動し、右下のインタープリタ選択で「MicroPython (Raspberry Pi Pico)」を選択
3. `main.py` を開き、Picoに保存（ファイル → 名前を付けて保存 → Raspberry Pi Pico）

#### rshellを使用する場合（コマンドライン）

```bash
# rshellのインストール
pip install rshell

# Picoに接続
rshell -p /dev/tty.usbmodem*  # macOS
rshell -p /dev/ttyACM0        # Linux

# ファイルをコピー
cp main.py /pyboard/
```

### 3. 配線

上記の配線図に従って、F9PとPicoを接続してください。

**注意事項:**
- 5V電源はPicoのVBUS（USB電源）から供給されます
- USB接続時のみ動作します
- 配線を間違えると機器が破損する可能性があります

### 4. 動作確認

1. PicoをPCにUSB接続
2. シリアルターミナル（115200bps）で接続
3. 以下のようなメッセージが表示されれば成功:

```
==================================================
H-RTK F9P GPS Bridge
==================================================
UART: 115200bps (GP0/GP1)
I2C: 400000Hz (GP4/GP5)
==================================================
F9P初期化中...
ESF-RAW有効化コマンド送信
ESF-MEAS有効化コマンド送信
F9P初期化完了
I2Cデバイスなし（正常）

ブリッジ開始... (UART <-> USB CDC)
```

4. F9Pが接続されていれば、NMEAセンテンス（`$GNGGA,...`）が流れてきます

## Androidとの接続

1. Pico + F9P を Android に USB OTG ケーブルで接続
2. MAG PLOTTER アプリを起動
3. GPS設定で「USB GPS」を選択
4. 自動的にPicoを認識し、F9Pのデータが取得されます

## トラブルシューティング

### NMEAデータが表示されない

- F9PのTX/RX配線が正しいか確認（TX→GP1, RX→GP0）
- F9Pの電源（5V）が供給されているか確認
- F9PのボーレートがデフォルトNMEA（115200bps）か確認

### LEDが点滅しない

- main.pyがPicoに正しく保存されているか確認
- MicroPythonが正しくインストールされているか確認

### Androidで認識されない

- USB OTGケーブルを使用しているか確認
- Androidの設定でUSBアクセスを許可

## 技術仕様

- **通信プロトコル**: NMEA 0183 + u-blox UBX
- **UART設定**: 115200bps, 8N1
- **データフロー**: F9P → UART → Pico → USB CDC → Android
- **磁気データ**: UBX ESF-RAW / ESF-MEAS メッセージ経由

## ライセンス

MIT License
