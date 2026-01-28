"""
test_feedback.py

LED (WS2812B) とブザーの動作確認テスト

使い方:
    1. ThonnyでPicoに接続
    2. このファイルを開いて実行（F5）
    3. LEDが緑→黄→オレンジ→赤と変化
    4. ブザーが低音→高音とビープ
"""

from machine import Pin, PWM
from neopixel import NeoPixel
import time

# =============================================================================
# 設定
# =============================================================================

LED_PIN = 15        # WS2812B DINピン
LED_COUNT = 16      # LEDの数（リング型）
BUZZER_PIN = 16     # ブザーピン

# =============================================================================
# 初期化
# =============================================================================

print("=" * 50)
print("LED + ブザー 動作確認テスト")
print("=" * 50)

# LED初期化
print(f"LED初期化: GP{LED_PIN}, {LED_COUNT}個")
np = NeoPixel(Pin(LED_PIN), LED_COUNT)

# ブザー初期化
print(f"ブザー初期化: GP{BUZZER_PIN}")
buzzer = PWM(Pin(BUZZER_PIN))

# =============================================================================
# テスト関数
# =============================================================================

def set_all_leds(r, g, b, brightness=0.25):
    """
    全LEDを同じ色に設定（輝度制限付き）
    """
    r = int(r * brightness)
    g = int(g * brightness)
    b = int(b * brightness)
    for i in range(LED_COUNT):
        np[i] = (r, g, b)
    np.write()

def led_off():
    """
    全LED消灯
    """
    for i in range(LED_COUNT):
        np[i] = (0, 0, 0)
    np.write()

def beep(freq, duration_ms=100):
    """
    ビープ音を鳴らす
    """
    buzzer.freq(freq)
    buzzer.duty_u16(32768)  # 50% duty
    time.sleep_ms(duration_ms)
    buzzer.duty_u16(0)

def buzzer_off():
    """
    ブザー停止
    """
    buzzer.duty_u16(0)

# =============================================================================
# テスト実行
# =============================================================================

print("")
print("テスト1: LED色テスト")
print("-" * 30)

colors = [
    ("緑（安全）", 0, 255, 0),
    ("黄緑（注意）", 128, 255, 0),
    ("オレンジ（警告）", 255, 128, 0),
    ("赤（危険）", 255, 0, 0),
]

for name, r, g, b in colors:
    print(f"  {name}")
    set_all_leds(r, g, b)
    time.sleep(1)

led_off()
print("  LED消灯")
time.sleep(0.5)

print("")
print("テスト2: ブザー音程テスト")
print("-" * 30)

frequencies = [
    ("低音 400Hz", 400),
    ("中低音 600Hz", 600),
    ("中音 800Hz", 800),
    ("中高音 1000Hz", 1000),
    ("高音 1200Hz", 1200),
]

for name, freq in frequencies:
    print(f"  {name}")
    beep(freq, 300)
    time.sleep(0.2)

print("")
print("テスト3: バリオメーター風テスト")
print("-" * 30)
print("  ノイズ増加をシミュレート...")

# ノイズ0→15をシミュレート
for noise in range(0, 16):
    # LED色計算
    if noise <= 5:
        r, g, b = 0, 255, 0  # 緑
        freq = 0
        interval = 0
    elif noise <= 7:
        ratio = (noise - 5) / 2
        r = int(255 * ratio)
        g = 255
        b = 0
        freq = 400 + int(200 * ratio)
        interval = 600
    elif noise <= 10:
        ratio = (noise - 7) / 3
        r = 255
        g = int(255 * (1 - ratio * 0.5))
        b = 0
        freq = 600 + int(400 * ratio)
        interval = 300
    else:
        r, g, b = 255, 0, 0  # 赤
        freq = 1200
        interval = 50
    
    set_all_leds(r, g, b)
    
    if freq > 0:
        beep(freq, 50)
        time.sleep_ms(interval - 50)
    else:
        time.sleep_ms(200)
    
    print(f"  ノイズ {noise}μT: RGB({r},{g},{b}) {freq}Hz")

print("")
print("テスト4: LED個別点灯テスト（バーグラフ）")
print("-" * 30)

for count in range(0, LED_COUNT + 1):
    for i in range(LED_COUNT):
        if i < count:
            # 緑→黄→赤のグラデーション
            ratio = i / (LED_COUNT - 1)
            if ratio < 0.5:
                r = int(255 * ratio * 2)
                g = 255
            else:
                r = 255
                g = int(255 * (1 - (ratio - 0.5) * 2))
            np[i] = (int(r * 0.25), int(g * 0.25), 0)
        else:
            np[i] = (0, 0, 0)
    np.write()
    time.sleep(0.1)

time.sleep(0.5)
led_off()

print("")
print("=" * 50)
print("テスト完了！")
print("=" * 50)
print("")
print("問題がなければ、main.pyを更新して")
print("フィードバック機能を統合します。")
