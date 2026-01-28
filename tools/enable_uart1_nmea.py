#!/usr/bin/env python3
"""
enable_uart1_nmea.py

F9PのUART1でNMEA出力を有効化するスクリプト

使用方法:
    python3 enable_uart1_nmea.py /dev/cu.usbmodem2101
"""

import serial
import sys
import time

def calculate_checksum(payload):
    """UBXチェックサムを計算"""
    ck_a = 0
    ck_b = 0
    for byte in payload:
        ck_a = (ck_a + byte) & 0xFF
        ck_b = (ck_b + ck_a) & 0xFF
    return ck_a, ck_b

def create_ubx_cfg_msg(msg_class, msg_id, uart1_rate):
    """
    UBX-CFG-MSG コマンドを作成
    特定のメッセージのUART1出力レートを設定
    """
    # UBX-CFG-MSG (0x06 0x01)
    # Payload: msgClass, msgID, rate[6] (I2C, UART1, UART2, USB, SPI, reserved)
    payload = bytes([
        msg_class, msg_id,
        0,          # I2C rate
        uart1_rate, # UART1 rate (1 = every solution)
        0,          # UART2 rate
        1,          # USB rate
        0,          # SPI rate
        0           # reserved
    ])
    
    header = bytes([0x06, 0x01])  # CFG-MSG class and ID
    length = len(payload)
    
    # チェックサム計算（class, id, length, payloadに対して）
    checksum_data = header + bytes([length & 0xFF, (length >> 8) & 0xFF]) + payload
    ck_a, ck_b = calculate_checksum(checksum_data)
    
    # 完全なUBXメッセージ
    ubx_msg = bytes([0xB5, 0x62]) + checksum_data + bytes([ck_a, ck_b])
    return ubx_msg

def create_ubx_cfg_save():
    """
    UBX-CFG-CFG コマンドを作成（設定をFlashに永続保存）
    """
    # UBX-CFG-CFG (0x06 0x09)
    # clearMask, saveMask, loadMask, deviceMask (各4バイト, deviceMaskは1バイト)
    payload = bytes([
        0x00, 0x00, 0x00, 0x00,  # clearMask - クリアしない
        0xFF, 0xFF, 0x00, 0x00,  # saveMask - 全設定を保存
        0x00, 0x00, 0x00, 0x00,  # loadMask - ロードしない
        0x17                     # deviceMask - BBR + Flash + EEPROM + SPI Flash
    ])
    
    header = bytes([0x06, 0x09])  # CFG-CFG class and ID
    length = len(payload)
    
    checksum_data = header + bytes([length & 0xFF, (length >> 8) & 0xFF]) + payload
    ck_a, ck_b = calculate_checksum(checksum_data)
    
    ubx_msg = bytes([0xB5, 0x62]) + checksum_data + bytes([ck_a, ck_b])
    return ubx_msg

def main():
    if len(sys.argv) < 2:
        print("使用方法: python3 enable_uart1_nmea.py <シリアルポート>")
        print("例: python3 enable_uart1_nmea.py /dev/cu.usbmodem2101")
        sys.exit(1)
    
    port = sys.argv[1]
    baudrate = 115200
    
    print(f"F9Pに接続中: {port} @ {baudrate}bps")
    
    try:
        ser = serial.Serial(port, baudrate, timeout=1)
        time.sleep(0.5)
        
        # NMEA メッセージクラス: 0xF0
        # GGA: 0xF0 0x00
        # RMC: 0xF0 0x04
        # VTG: 0xF0 0x05
        # GSA: 0xF0 0x02
        # GSV: 0xF0 0x03
        
        messages = [
            (0xF0, 0x00, "GGA"),
            (0xF0, 0x04, "RMC"),
            (0xF0, 0x05, "VTG"),
        ]
        
        for msg_class, msg_id, name in messages:
            print(f"UART1で{name}を有効化中...")
            cmd = create_ubx_cfg_msg(msg_class, msg_id, 1)
            ser.write(cmd)
            ser.flush()
            time.sleep(0.3)  # 待ち時間を長くする
            
            # ACK待ち
            response = ser.read(200)
            if b'\xb5\x62\x05\x01' in response:
                print(f"  {name}: OK (ACK受信)")
            elif b'\xb5\x62\x05\x00' in response:
                print(f"  {name}: NAK (設定拒否)")
            else:
                print(f"  {name}: 送信完了 (応答: {len(response)}bytes)")
        
        # 設定を保存
        print("設定を保存中...")
        cmd = create_ubx_cfg_save()
        ser.write(cmd)
        time.sleep(0.5)
        
        response = ser.read(100)
        if b'\xb5\x62\x05\x01' in response:
            print("設定保存: OK (ACK受信)")
        else:
            print("設定保存: 送信完了 (ACK未確認)")
        
        ser.close()
        print("\n完了！F9PのUART1でNMEA出力が有効になりました。")
        print("PicoをAndroidに接続して確認してください。")
        
    except serial.SerialException as e:
        print(f"エラー: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
