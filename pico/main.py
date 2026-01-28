"""
main.py

H-RTK F9P GPS Bridge for Raspberry Pi Pico

概要:
    H-RTK F9P (GPS + コンパス) からUART経由でNMEA/UBXデータを受信し、
    USB CDC経由でAndroidデバイスへ転送するブリッジファームウェア。
    ノイズ値に応じたLED・ブザーフィードバック機能付き（v2.3.0）。

主な仕様:
    - UART0 (115200bps) でF9Pと通信
    - USB CDC経由でAndroidへデータをパススルー転送
    - 起動時にF9PのESF-RAW/ESF-MEASメッセージを有効化
    - IST8310磁気センサーからI2C経由でデータ取得
    - WS2812B LEDリング（16個）によるノイズ可視化
    - パッシブブザーによるバリオメーター風警告音

制限事項:
    - Raspberry Pi Pico (RP2040/RP2350) 専用
    - MicroPython v1.20以降推奨

ピンアサイン（USBコネクタを上にして）:
    - GP0 (右列 1番目): UART0 TX → F9P RX
    - GP1 (右列 2番目): UART0 RX ← F9P TX
    - GP4 (右列 6番目): I2C0 SDA ← F9P SDA
    - GP5 (右列 7番目): I2C0 SCL ← F9P SCL
    - GP15 (右列 20番目): WS2812B DIN
    - GP16 (左列 21番目): ブザー Signal
    - VBUS (左列 1番目): 5V電源 → F9P 5V, LED 5V
    - GND (左列 3番目): グランド（共通）
"""

from machine import UART, Pin, I2C, PWM
from neopixel import NeoPixel
import sys
import time
import select
import micropython


# =============================================================================
# 定数定義
# =============================================================================

# UART設定
UART_ID = 0
# H-RTK F9P (Holybro) デフォルト: 115200bps
UART_BAUDRATE = 115200
UART_TX_PIN = 0  # GP0
UART_RX_PIN = 1  # GP1

# I2C設定（将来拡張用）
I2C_ID = 0
I2C_SDA_PIN = 4  # GP4
I2C_SCL_PIN = 5  # GP5
I2C_FREQ = 400000  # 400kHz

# バッファサイズ
UART_BUFFER_SIZE = 512
USB_BUFFER_SIZE = 64

# LED設定（状態表示用）
LED_PIN = 25  # Pico内蔵LED

# WS2812B LEDリング設定
NEOPIXEL_PIN = 15       # GP15
NEOPIXEL_COUNT = 16     # LED数
NEOPIXEL_BRIGHTNESS = 0.25  # 輝度制限（電流対策）

# パッシブブザー設定
BUZZER_PIN = 16         # GP16

# フィードバック設定
REFERENCE_MAG = 46.0    # 基準磁場値（μT、日本平均）

# ノイズ閾値（μT）
NOISE_THRESHOLD_SAFE = 5.0      # 安全上限
NOISE_THRESHOLD_CAUTION = 6.0   # 注意開始
NOISE_THRESHOLD_WARNING = 8.0   # 警告開始
NOISE_THRESHOLD_DANGER = 10.0   # 危険開始

# IST8310 磁気センサー設定
IST8310_ADDR = 0x0E
IST8310_REG_STAT1 = 0x02   # ステータスレジスタ
IST8310_REG_DATAX_L = 0x03  # X軸データ（下位バイト）
IST8310_REG_CNTL1 = 0x0A   # コントロールレジスタ1
IST8310_REG_CNTL2 = 0x0B   # コントロールレジスタ2

# 磁気センサー読み取り間隔（ミリ秒）
MAG_READ_INTERVAL_MS = 50  # 20Hz


# =============================================================================
# UBXメッセージ生成
# =============================================================================

class UbxCommand:
    """
    u-blox UBXプロトコルのコマンド生成クラス
    """
    
    # UBX同期文字
    SYNC_CHAR_1 = 0xB5
    SYNC_CHAR_2 = 0x62
    
    # メッセージクラス
    CLASS_CFG = 0x06  # Configuration
    CLASS_ESF = 0x10  # External Sensor Fusion
    
    # メッセージID
    ID_CFG_MSG = 0x01  # Message configuration
    ID_ESF_RAW = 0x03  # Raw sensor data
    ID_ESF_MEAS = 0x02  # Sensor measurements
    
    @staticmethod
    def calculateChecksum(payload):
        """
        UBXチェックサムを計算
        
        Args:
            payload: チェックサム対象のバイト列（Class, ID, Length, Payload）
        
        Returns:
            tuple: (CK_A, CK_B)
        """
        ckA = 0
        ckB = 0
        for b in payload:
            ckA = (ckA + b) & 0xFF
            ckB = (ckB + ckA) & 0xFF
        return (ckA, ckB)
    
    @staticmethod
    def buildMessage(msgClass, msgId, payload=None):
        """
        UBXメッセージを生成
        
        Args:
            msgClass: メッセージクラス
            msgId: メッセージID
            payload: ペイロード（バイト列）
        
        Returns:
            bytes: UBXメッセージ
        """
        if payload is None:
            payload = bytes()
        
        payloadLen = len(payload)
        
        # ヘッダー + ペイロード
        msg = bytes([
            msgClass,
            msgId,
            payloadLen & 0xFF,
            (payloadLen >> 8) & 0xFF
        ]) + payload
        
        # チェックサム計算
        ckA, ckB = UbxCommand.calculateChecksum(msg)
        
        # 完全なメッセージを構築
        return bytes([
            UbxCommand.SYNC_CHAR_1,
            UbxCommand.SYNC_CHAR_2
        ]) + msg + bytes([ckA, ckB])
    
    @staticmethod
    def buildEnableEsfRawCommand():
        """
        ESF-RAWメッセージを有効化するコマンドを生成
        
        Returns:
            bytes: UBX-CFG-MSG コマンド
        """
        # UBX-CFG-MSG: ESF-RAW (0x10, 0x03) を1Hz出力に設定
        payload = bytes([
            UbxCommand.CLASS_ESF,  # msgClass
            UbxCommand.ID_ESF_RAW,  # msgID
            0x01  # rate (1Hz)
        ])
        return UbxCommand.buildMessage(UbxCommand.CLASS_CFG, UbxCommand.ID_CFG_MSG, payload)
    
    @staticmethod
    def buildEnableEsfMeasCommand():
        """
        ESF-MEASメッセージを有効化するコマンドを生成
        
        Returns:
            bytes: UBX-CFG-MSG コマンド
        """
        # UBX-CFG-MSG: ESF-MEAS (0x10, 0x02) を1Hz出力に設定
        payload = bytes([
            UbxCommand.CLASS_ESF,   # msgClass
            UbxCommand.ID_ESF_MEAS,  # msgID
            0x01  # rate (1Hz)
        ])
        return UbxCommand.buildMessage(UbxCommand.CLASS_CFG, UbxCommand.ID_CFG_MSG, payload)


# =============================================================================
# IST8310 磁気センサークラス
# =============================================================================

class IST8310:
    """
    IST8310 3軸磁気センサードライバ
    
    H-RTK F9Pに内蔵されている磁気センサー（コンパス）を制御する。
    """
    
    def __init__(self, i2c, addr=IST8310_ADDR):
        """
        コンストラクタ
        
        Args:
            i2c: I2Cオブジェクト
            addr: I2Cアドレス（デフォルト: 0x0E）
        """
        self.i2c = i2c
        self.addr = addr
        self.initialized = False
    
    def initialize(self):
        """
        センサーを初期化
        
        Returns:
            bool: 初期化成功時True
        """
        try:
            # デバイスの存在確認
            devices = self.i2c.scan()
            if self.addr not in devices:
                print(f"IST8310が見つかりません (addr=0x{self.addr:02X})")
                return False
            
            # ソフトリセット
            self.i2c.writeto_mem(self.addr, IST8310_REG_CNTL2, bytes([0x01]))
            time.sleep(0.05)
            
            # 連続測定モード（200Hz）を設定
            # CNTL1: bit0 = 1 (シングル測定開始)
            self.i2c.writeto_mem(self.addr, IST8310_REG_CNTL1, bytes([0x01]))
            time.sleep(0.01)
            
            self.initialized = True
            print("IST8310初期化完了")
            return True
            
        except Exception as e:
            print(f"IST8310初期化エラー: {e}")
            return False
    
    def triggerMeasurement(self):
        """
        測定をトリガー（シングル測定モード）
        """
        try:
            self.i2c.writeto_mem(self.addr, IST8310_REG_CNTL1, bytes([0x01]))
        except Exception:
            pass
    
    def readMagneticField(self):
        """
        磁場データを読み取り
        
        Returns:
            tuple: (magX, magY, magZ) in μT, またはNone（エラー時）
        """
        if not self.initialized:
            return None
        
        try:
            # ステータス確認（データ準備完了待ち）
            stat = self.i2c.readfrom_mem(self.addr, IST8310_REG_STAT1, 1)[0]
            if not (stat & 0x01):  # DRDY bit
                return None
            
            # 6バイト読み取り（X_L, X_H, Y_L, Y_H, Z_L, Z_H）
            data = self.i2c.readfrom_mem(self.addr, IST8310_REG_DATAX_L, 6)
            
            # リトルエンディアンで符号付き16ビットに変換
            rawX = int.from_bytes(data[0:2], 'little')
            rawY = int.from_bytes(data[2:4], 'little')
            rawZ = int.from_bytes(data[4:6], 'little')
            
            # 符号付きに変換
            if rawX > 32767:
                rawX -= 65536
            if rawY > 32767:
                rawY -= 65536
            if rawZ > 32767:
                rawZ -= 65536
            
            # μTに変換（IST8310の感度: 0.3 μT/LSB）
            magX = rawX * 0.3
            magY = rawY * 0.3
            magZ = rawZ * 0.3
            
            # 次の測定をトリガー
            self.triggerMeasurement()
            
            return (magX, magY, magZ)
            
        except Exception as e:
            return None
    
    def calculateNmeaChecksum(self, sentence):
        """
        NMEAチェックサムを計算
        
        Args:
            sentence: $と*を除いたセンテンス本体
        
        Returns:
            str: 2桁の16進チェックサム
        """
        checksum = 0
        for char in sentence:
            checksum ^= ord(char)
        return f"{checksum:02X}"
    
    def formatAsNmea(self, magX, magY, magZ):
        """
        磁気データをNMEA風フォーマットに変換
        
        フォーマット: $PIMAG,magX,magY,magZ,totalField*XX
        
        Args:
            magX, magY, magZ: 磁場値（μT）
        
        Returns:
            str: NMEAセンテンス
        """
        totalField = (magX**2 + magY**2 + magZ**2) ** 0.5
        body = f"PIMAG,{magX:.2f},{magY:.2f},{magZ:.2f},{totalField:.2f}"
        checksum = self.calculateNmeaChecksum(body)
        return f"${body}*{checksum}\r\n"


# =============================================================================
# ノイズフィードバッククラス
# =============================================================================

class NoiseFeedback:
    """
    ノイズ値に基づく視覚・聴覚フィードバック制御クラス
    
    バリオメーター方式:
        - 安全時: 緑LED、無音
        - 危険に近づくにつれ: LED色が黄→オレンジ→赤に変化
        - ビープ音が速く・高くなる
    """
    
    def __init__(self):
        """
        コンストラクタ
        """
        # NeoPixel LED初期化
        self.np = NeoPixel(Pin(NEOPIXEL_PIN), NEOPIXEL_COUNT)
        
        # ブザー初期化（PWM）
        self.buzzer = PWM(Pin(BUZZER_PIN))
        self.buzzer.duty_u16(0)  # 初期状態は無音
        
        # フィードバック有効フラグ
        self.ledEnabled = True
        self.buzzerEnabled = True
        
        # ビープ状態管理
        self.lastBeepTime = 0
        self.beepState = False  # True=ON, False=OFF
        self.currentFreq = 0
        self.currentInterval = 0
        self.beepDuration = 50  # ビープON時間（ms）
        
        # 起動メロディー＆LEDアニメーション
        self.playStartupSequence()
        
        print("フィードバック初期化完了 (LED + ブザー)")
    
    def playStartupSequence(self):
        """
        起動時のメロディーとLEDアニメーション
        ファミリーマート入店音風
        """
        # ファミマ入店音: シ ソ レ ソ ラ レ (休) レ (休) ラ シ ラ レ ソ
        # 周波数定義 (オクターブ4-5)
        B4 = 494   # シ
        G4 = 392   # ソ
        D4 = 294   # レ (低)
        A4 = 440   # ラ
        D5 = 587   # レ (高)
        REST = 0   # 休符
        
        # (周波数, 長さms)
        melody = [
            # 前半: シ ソ レ ソ ラ レ
            (B4, 150),
            (G4, 150),
            (D4, 150),
            (G4, 150),
            (A4, 150),
            (D5, 150),
            # 休符
            (REST, 100),
            # レ
            (D5, 150),
            # 休符
            (REST, 100),
            # 後半: ラ シ ラ レ ソ
            (A4, 200),
            (B4, 200),
            (A4, 200),
            (D4, 200),  # 低いレ
            (G4, 400),  # 最後は長め
        ]
        
        # LED色（ファミマカラー: 緑＆青＆白）
        colors = [
            (0, 255, 100),    # 緑っぽい
            (0, 200, 255),    # シアン
            (100, 255, 100),  # 明るい緑
            (0, 150, 255),    # 青っぽい
        ]
        
        noteIndex = 0
        for freq, duration in melody:
            if freq > 0:
                # LED更新（音符ごとに色を変化）
                color = colors[noteIndex % len(colors)]
                ledCount = min((noteIndex + 1) * 2, NEOPIXEL_COUNT)
                for j in range(NEOPIXEL_COUNT):
                    if j < ledCount:
                        r = int(color[0] * NEOPIXEL_BRIGHTNESS)
                        g = int(color[1] * NEOPIXEL_BRIGHTNESS)
                        b = int(color[2] * NEOPIXEL_BRIGHTNESS)
                        self.np[j] = (r, g, b)
                    else:
                        self.np[j] = (0, 0, 0)
                self.np.write()
                
                # ビープ音
                self.buzzer.freq(freq)
                self.buzzer.duty_u16(32768)
                time.sleep_ms(duration)
                self.buzzer.duty_u16(0)
                noteIndex += 1
            else:
                # 休符
                time.sleep_ms(duration)
            
            time.sleep_ms(20)  # 音符間の隙間
        
        # フェードアウト
        time.sleep_ms(100)
        for brightness in range(10, 0, -1):
            for j in range(NEOPIXEL_COUNT):
                self.np[j] = (0, int(25 * brightness * 0.1), int(15 * brightness * 0.1))
            self.np.write()
            time.sleep_ms(40)
        
        # 消灯
        self.setAllLeds(0, 0, 0)
    
    def setAllLeds(self, r, g, b):
        """
        全LEDを同じ色に設定（輝度制限付き）
        
        Args:
            r, g, b: RGB値 (0-255)
        """
        r = int(r * NEOPIXEL_BRIGHTNESS)
        g = int(g * NEOPIXEL_BRIGHTNESS)
        b = int(b * NEOPIXEL_BRIGHTNESS)
        for i in range(NEOPIXEL_COUNT):
            self.np[i] = (r, g, b)
        self.np.write()
    
    def setLedBar(self, level, maxLevel=16):
        """
        LEDをバーグラフ表示
        
        Args:
            level: 点灯レベル (0-maxLevel)
            maxLevel: 最大レベル
        """
        count = int((level / maxLevel) * NEOPIXEL_COUNT)
        count = min(count, NEOPIXEL_COUNT)
        
        for i in range(NEOPIXEL_COUNT):
            if i < count:
                # 緑→黄→赤のグラデーション
                ratio = i / (NEOPIXEL_COUNT - 1)
                if ratio < 0.5:
                    r = int(255 * ratio * 2)
                    g = 255
                else:
                    r = 255
                    g = int(255 * (1 - (ratio - 0.5) * 2))
                self.np[i] = (int(r * NEOPIXEL_BRIGHTNESS), 
                              int(g * NEOPIXEL_BRIGHTNESS), 0)
            else:
                self.np[i] = (0, 0, 0)
        self.np.write()
    
    def calculateColor(self, noise):
        """
        ノイズ値に応じたLED色を計算
        
        Args:
            noise: ノイズ値（μT）
        
        Returns:
            tuple: (R, G, B)
        """
        if noise <= NOISE_THRESHOLD_SAFE:
            return (0, 255, 0)  # 緑
        elif noise <= NOISE_THRESHOLD_CAUTION:
            # 緑→黄緑
            ratio = (noise - NOISE_THRESHOLD_SAFE) / (NOISE_THRESHOLD_CAUTION - NOISE_THRESHOLD_SAFE)
            return (int(128 * ratio), 255, 0)
        elif noise <= NOISE_THRESHOLD_WARNING:
            # 黄緑→オレンジ
            ratio = (noise - NOISE_THRESHOLD_CAUTION) / (NOISE_THRESHOLD_WARNING - NOISE_THRESHOLD_CAUTION)
            return (int(128 + 127 * ratio), int(255 * (1 - ratio * 0.5)), 0)
        elif noise <= NOISE_THRESHOLD_DANGER:
            # オレンジ→赤
            ratio = (noise - NOISE_THRESHOLD_WARNING) / (NOISE_THRESHOLD_DANGER - NOISE_THRESHOLD_WARNING)
            return (255, int(128 * (1 - ratio)), 0)
        else:
            return (255, 0, 0)  # 赤
    
    def calculateBeepParams(self, noise):
        """
        ノイズ値に応じたビープパラメータを計算
        
        Args:
            noise: ノイズ値（μT）
        
        Returns:
            tuple: (周波数Hz, 間隔ms) - 周波数0は無音
        """
        if noise <= NOISE_THRESHOLD_SAFE:
            return (0, 0)  # 無音
        elif noise <= NOISE_THRESHOLD_CAUTION:
            return (400, 700)  # 低音、遅い
        elif noise <= 7.0:
            return (600, 500)  # 中低音
        elif noise <= NOISE_THRESHOLD_WARNING:
            return (800, 300)  # 中音
        elif noise <= 9.0:
            return (1000, 150)  # 中高音、速い
        elif noise <= NOISE_THRESHOLD_DANGER:
            return (1100, 100)  # 高音
        else:
            return (1200, 50)  # 最高音、最速
    
    def update(self, noise):
        """
        ノイズ値に応じてLEDとブザーを更新
        
        Args:
            noise: ノイズ値（μT）
        """
        # LED更新
        if self.ledEnabled:
            r, g, b = self.calculateColor(noise)
            self.setAllLeds(r, g, b)
        
        # ブザーパラメータ更新
        freq, interval = self.calculateBeepParams(noise)
        self.currentFreq = freq
        self.currentInterval = interval
    
    def processBeep(self):
        """
        ビープ音の非同期処理（メインループから呼び出す）
        
        ブロッキングしないように、状態管理でON/OFFを切り替える
        """
        if not self.buzzerEnabled or self.currentFreq == 0:
            # 無音
            if self.beepState:
                self.buzzer.duty_u16(0)
                self.beepState = False
            return
        
        currentTime = time.ticks_ms()
        
        if self.beepState:
            # 現在ON → duration経過でOFF
            if time.ticks_diff(currentTime, self.lastBeepTime) >= self.beepDuration:
                self.buzzer.duty_u16(0)
                self.beepState = False
                self.lastBeepTime = currentTime
        else:
            # 現在OFF → interval経過でON
            waitTime = self.currentInterval - self.beepDuration
            if waitTime < 10:
                waitTime = 10
            if time.ticks_diff(currentTime, self.lastBeepTime) >= waitTime:
                self.buzzer.freq(self.currentFreq)
                self.buzzer.duty_u16(32768)  # 50% duty
                self.beepState = True
                self.lastBeepTime = currentTime
    
    def setEnabled(self, led=True, buzzer=True):
        """
        フィードバックの有効/無効を設定
        
        Args:
            led: LED有効フラグ
            buzzer: ブザー有効フラグ
        """
        self.ledEnabled = led
        self.buzzerEnabled = buzzer
        
        if not led:
            self.setAllLeds(0, 0, 0)
        if not buzzer:
            self.buzzer.duty_u16(0)
            self.beepState = False
    
    def off(self):
        """
        全フィードバックを停止
        """
        self.setAllLeds(0, 0, 0)
        self.buzzer.duty_u16(0)
        self.beepState = False


# =============================================================================
# GPSブリッジクラス
# =============================================================================

class GpsBridge:
    """
    F9P GPSモジュールとAndroid間のブリッジクラス
    """
    
    def __init__(self):
        """
        コンストラクタ
        """
        # UART初期化（F9P接続用）
        self.uart = UART(
            UART_ID,
            baudrate=UART_BAUDRATE,
            tx=Pin(UART_TX_PIN),
            rx=Pin(UART_RX_PIN),
            bits=8,
            parity=None,
            stop=1,
            rxbuf=UART_BUFFER_SIZE
        )
        
        # I2C初期化（将来拡張用）
        self.i2c = I2C(
            I2C_ID,
            sda=Pin(I2C_SDA_PIN),
            scl=Pin(I2C_SCL_PIN),
            freq=I2C_FREQ
        )
        
        # LED初期化
        self.led = Pin(LED_PIN, Pin.OUT)
        self.ledState = False
        self.lastLedToggle = 0
        
        # 統計情報
        self.bytesReceived = 0
        self.bytesSent = 0
        self.startTime = time.ticks_ms()
        
        # USB入力用ポーラー
        self.usbPoller = select.poll()
        self.usbPoller.register(sys.stdin, select.POLLIN)
        
        # 磁気センサー
        self.magSensor = IST8310(self.i2c)
        self.lastMagRead = 0
        
        # ノイズフィードバック（LED + ブザー）
        self.feedback = NoiseFeedback()
        self.lastNoise = 0.0
    
    def initializeF9p(self):
        """
        F9Pの初期化（NMEAメッセージ有効化 + 磁気センサー初期化）
        """
        print("F9P初期化中...")
        
        # 少し待機してF9Pの起動を待つ
        time.sleep(1)
        
        # NMEA GGAメッセージをUART1で有効化
        # UBX-CFG-MSG: msgClass=0xF0, msgID=0x00 (GGA), rate[6]
        gga_cmd = self.buildCfgMsgCommand(0xF0, 0x00, 1)
        self.uart.write(gga_cmd)
        print("NMEA GGA有効化コマンド送信")
        time.sleep(0.2)
        
        # NMEA RMCメッセージをUART1で有効化
        rmc_cmd = self.buildCfgMsgCommand(0xF0, 0x04, 1)
        self.uart.write(rmc_cmd)
        print("NMEA RMC有効化コマンド送信")
        time.sleep(0.2)
        
        # NMEA VTGメッセージをUART1で有効化
        vtg_cmd = self.buildCfgMsgCommand(0xF0, 0x05, 1)
        self.uart.write(vtg_cmd)
        print("NMEA VTG有効化コマンド送信")
        time.sleep(0.2)
        
        # IST8310磁気センサーを初期化
        if self.magSensor.initialize():
            print("磁気センサー(IST8310)有効")
        else:
            print("磁気センサー初期化失敗 - I2C経由の読み取りは無効")
        
        print("F9P初期化完了")
    
    def buildCfgMsgCommand(self, msgClass, msgId, uart1Rate):
        """
        UBX-CFG-MSG コマンドを作成
        
        Args:
            msgClass: メッセージクラス (例: 0xF0 for NMEA)
            msgId: メッセージID (例: 0x00 for GGA)
            uart1Rate: UART1の出力レート (0=無効, 1=毎回)
        
        Returns:
            bytes: UBXコマンド
        """
        # Payload: msgClass, msgID, rate[6] (I2C, UART1, UART2, USB, SPI, reserved)
        payload = bytes([
            msgClass, msgId,
            0,          # I2C rate
            uart1Rate,  # UART1 rate
            0,          # UART2 rate
            1,          # USB rate
            0,          # SPI rate
            0           # reserved
        ])
        
        # Header: class=0x06, id=0x01 (CFG-MSG)
        header = bytes([0x06, 0x01])
        length = len(payload)
        
        # チェックサム計算
        checksumData = header + bytes([length & 0xFF, (length >> 8) & 0xFF]) + payload
        ckA = 0
        ckB = 0
        for b in checksumData:
            ckA = (ckA + b) & 0xFF
            ckB = (ckB + ckA) & 0xFF
        
        # 完全なUBXメッセージ
        return bytes([0xB5, 0x62]) + checksumData + bytes([ckA, ckB])
    
    def toggleLed(self):
        """
        LEDを切り替え（データ受信表示）
        """
        currentTime = time.ticks_ms()
        if time.ticks_diff(currentTime, self.lastLedToggle) > 100:
            self.ledState = not self.ledState
            self.led.value(self.ledState)
            self.lastLedToggle = currentTime
    
    def processUartData(self):
        """
        UARTからデータを読み取り、USBへ転送
        
        Returns:
            bool: データを処理した場合True
        """
        available = self.uart.any()
        if available:
            # UARTからデータを読み取り
            data = self.uart.read(UART_BUFFER_SIZE)
            if data:
                # USB CDC経由でAndroidへ転送
                sys.stdout.buffer.write(data)
                self.bytesReceived += len(data)
                self.toggleLed()
                return True
        return False
    
    def processUsbData(self):
        """
        USBからデータを読み取り、UARTへ転送（設定コマンド用）
        
        Returns:
            bool: データを処理した場合True
        """
        # 非ブロッキングでUSB入力をチェック
        events = self.usbPoller.poll(0)
        if events:
            try:
                data = sys.stdin.buffer.read(USB_BUFFER_SIZE)
                if data:
                    # UARTへ転送（AndroidからF9Pへのコマンド）
                    self.uart.write(data)
                    self.bytesSent += len(data)
                    return True
            except Exception:
                pass
        return False
    
    def processMagneticSensor(self):
        """
        磁気センサーを読み取り、NMEAフォーマットでUSBへ送信
        ノイズ値を計算してフィードバックを更新
        
        Returns:
            bool: データを送信した場合True
        """
        currentTime = time.ticks_ms()
        
        # 読み取り間隔をチェック
        if time.ticks_diff(currentTime, self.lastMagRead) < MAG_READ_INTERVAL_MS:
            return False
        
        self.lastMagRead = currentTime
        
        # 磁気データを読み取り
        magData = self.magSensor.readMagneticField()
        if magData:
            magX, magY, magZ = magData
            
            # 総磁場強度を計算
            totalField = (magX**2 + magY**2 + magZ**2) ** 0.5
            
            # ノイズ値を計算（基準値との偏差）
            noise = abs(totalField - REFERENCE_MAG)
            self.lastNoise = noise
            
            # フィードバック更新（LED色とビープパラメータ）
            self.feedback.update(noise)
            
            # NMEAフォーマットで送信
            nmea = self.magSensor.formatAsNmea(magX, magY, magZ)
            sys.stdout.buffer.write(nmea.encode())
            return True
        
        return False
    
    def run(self):
        """
        メインループ
        """
        print("=" * 50)
        print("H-RTK F9P GPS Bridge v2.3.0")
        print("  + LED/Buzzer Feedback")
        print("=" * 50)
        print(f"UART: {UART_BAUDRATE}bps (GP{UART_TX_PIN}/GP{UART_RX_PIN})")
        print(f"I2C: {I2C_FREQ}Hz (GP{I2C_SDA_PIN}/GP{I2C_SCL_PIN})")
        print(f"LED: WS2812B x{NEOPIXEL_COUNT} (GP{NEOPIXEL_PIN})")
        print(f"Buzzer: PWM (GP{BUZZER_PIN})")
        print(f"基準磁場: {REFERENCE_MAG}μT")
        print("=" * 50)
        
        # F9P初期化
        self.initializeF9p()
        
        # I2Cデバイススキャン（デバッグ用）
        devices = self.i2c.scan()
        if devices:
            print(f"I2Cデバイス検出: {[hex(d) for d in devices]}")
        else:
            print("I2Cデバイスなし（正常）")
        
        print("")
        print("ブリッジ開始... (UART <-> USB CDC)")
        print("")
        
        # LED点灯（起動完了表示）
        self.led.value(1)
        time.sleep(0.5)
        self.led.value(0)
        
        # デバッグ用タイマー
        lastStatusTime = time.ticks_ms()
        loopCount = 0
        
        # メインループ
        while True:
            loopCount += 1
            
            # UARTデータ処理（F9P → Android）
            self.processUartData()
            
            # USBデータ処理（Android → F9P）
            self.processUsbData()
            
            # 磁気センサー処理（I2C → Android）
            self.processMagneticSensor()
            
            # ビープ音の非同期処理
            self.feedback.processBeep()
            
            # 5秒ごとにステータス表示（最初の30秒のみ）
            currentTime = time.ticks_ms()
            if time.ticks_diff(currentTime, lastStatusTime) > 5000:
                elapsed = time.ticks_diff(currentTime, self.startTime) // 1000
                if elapsed <= 30:
                    print(f"[STATUS] {elapsed}秒経過: UART受信={self.bytesReceived}bytes, ループ={loopCount}")
                lastStatusTime = currentTime


# =============================================================================
# エントリーポイント
# =============================================================================

def main():
    """
    メイン関数
    """
    # USBからのCtrl+C (0x03) によるKeyboardInterruptを即座に無効化
    # これにより、USB経由でバイナリデータを受信してもプログラムが終了しない
    # 注意: Thonnyでの開発時はBOOTSELモードでリセットが必要
    micropython.kbd_intr(-1)
    
    bridge = None
    try:
        bridge = GpsBridge()
        bridge.run()
    except KeyboardInterrupt:
        print("\n終了")
    except Exception as e:
        print(f"エラー: {e}")
        # エラー時はLEDを赤点滅
        try:
            np = NeoPixel(Pin(NEOPIXEL_PIN), NEOPIXEL_COUNT)
            buzzer = PWM(Pin(BUZZER_PIN))
            buzzer.duty_u16(0)
            for _ in range(5):
                for i in range(NEOPIXEL_COUNT):
                    np[i] = (64, 0, 0)
                np.write()
                time.sleep(0.2)
                for i in range(NEOPIXEL_COUNT):
                    np[i] = (0, 0, 0)
                np.write()
                time.sleep(0.2)
        except:
            pass
    finally:
        # クリーンアップ
        if bridge and hasattr(bridge, 'feedback'):
            bridge.feedback.off()


if __name__ == "__main__":
    main()
