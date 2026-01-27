"""
main.py

H-RTK F9P GPS Bridge for Raspberry Pi Pico

概要:
    H-RTK F9P (GPS + コンパス) からUART経由でNMEA/UBXデータを受信し、
    USB CDC経由でAndroidデバイスへ転送するブリッジファームウェア。

主な仕様:
    - UART0 (115200bps) でF9Pと通信
    - USB CDC経由でAndroidへデータをパススルー転送
    - 起動時にF9PのESF-RAW/ESF-MEASメッセージを有効化
    - I2C0は将来の拡張用に予約

制限事項:
    - Raspberry Pi Pico (RP2040) 専用
    - MicroPython v1.20以降推奨

ピンアサイン（USBコネクタを上にして右列から）:
    - GP0 (右列 1番目): UART0 TX → F9P RX
    - GP1 (右列 2番目): UART0 RX ← F9P TX
    - GP4 (右列 6番目): I2C0 SDA (将来拡張用)
    - GP5 (右列 7番目): I2C0 SCL (将来拡張用)
    - VBUS (左列 1番目): 5V電源 → F9P 5V
    - GND (左列 3番目): グランド → F9P GND
"""

from machine import UART, Pin, I2C
import sys
import time
import select


# =============================================================================
# 定数定義
# =============================================================================

# UART設定
UART_ID = 0
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
        
        # USB入力用ポーラー
        self.usbPoller = select.poll()
        self.usbPoller.register(sys.stdin, select.POLLIN)
        
        # 磁気センサー
        self.magSensor = IST8310(self.i2c)
        self.lastMagRead = 0
    
    def initializeF9p(self):
        """
        F9Pの初期化（ESFメッセージの有効化 + 磁気センサー初期化）
        """
        print("F9P初期化中...")
        
        # 少し待機してF9Pの起動を待つ
        time.sleep(1)
        
        # ESF-RAWメッセージを有効化（念のため）
        cmd = UbxCommand.buildEnableEsfRawCommand()
        self.uart.write(cmd)
        print("ESF-RAW有効化コマンド送信")
        time.sleep(0.1)
        
        # ESF-MEASメッセージを有効化（念のため）
        cmd = UbxCommand.buildEnableEsfMeasCommand()
        self.uart.write(cmd)
        print("ESF-MEAS有効化コマンド送信")
        time.sleep(0.1)
        
        # IST8310磁気センサーを初期化
        if self.magSensor.initialize():
            print("磁気センサー(IST8310)有効")
        else:
            print("磁気センサー初期化失敗 - I2C経由の読み取りは無効")
        
        print("F9P初期化完了")
    
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
        if self.uart.any():
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
        print("H-RTK F9P GPS Bridge")
        print("=" * 50)
        print(f"UART: {UART_BAUDRATE}bps (GP{UART_TX_PIN}/GP{UART_RX_PIN})")
        print(f"I2C: {I2C_FREQ}Hz (GP{I2C_SDA_PIN}/GP{I2C_SCL_PIN})")
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
        
        # メインループ
        while True:
            # UARTデータ処理（F9P → Android）
            self.processUartData()
            
            # USBデータ処理（Android → F9P）
            self.processUsbData()
            
            # 磁気センサー処理（I2C → Android）
            self.processMagneticSensor()


# =============================================================================
# エントリーポイント
# =============================================================================

def main():
    """
    メイン関数
    """
    try:
        bridge = GpsBridge()
        bridge.run()
    except KeyboardInterrupt:
        print("\n終了")
    except Exception as e:
        print(f"エラー: {e}")
        # エラー時はLEDを高速点滅
        led = Pin(LED_PIN, Pin.OUT)
        for _ in range(10):
            led.toggle()
            time.sleep(0.1)


if __name__ == "__main__":
    main()
