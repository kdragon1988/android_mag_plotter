/**
 * UsbGpsManager.java
 * 
 * VISIONOID MAG PLOTTER - USB GPS管理クラス
 * 
 * 概要:
 *   USB接続のRTK GPSデバイス（H-RTK F9Pなど）を管理するクラス。
 *   USB-Serial通信を行い、NMEAデータを受信・パースする。
 *   Raspberry Pi Pico経由でのF9P接続にも対応。
 * 
 * 主な仕様:
 *   - USBデバイスの検出・接続
 *   - シリアル通信（115200baud）
 *   - NMEAデータの受信とパース
 *   - UBXデータの受信とパース（磁気センサー対応）
 *   - Raspberry Pi Pico (USB CDC) の認識
 *   - 位置情報のコールバック
 * 
 * 制限事項:
 *   - USB OTG対応端末のみ
 *   - ユーザーによるUSB接続許可が必要
 */
package com.visionoid.magplotter.gps;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.hoho.android.usbserial.driver.CdcAcmSerialDriver;
import com.hoho.android.usbserial.driver.ProbeTable;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

/**
 * USB GPS管理クラス
 * 
 * USB接続のGPSデバイスとのシリアル通信を管理する。
 */
public class UsbGpsManager implements SerialInputOutputManager.Listener {

    private static final String TAG = "UsbGpsManager";
    
    /** USB接続許可リクエストのアクション */
    private static final String ACTION_USB_PERMISSION = "com.visionoid.magplotter.USB_PERMISSION";
    
    /** デフォルトボーレート（H-RTK F9P: 115200） */
    private static final int DEFAULT_BAUD_RATE = 115200;
    
    /** データビット数 */
    private static final int DATA_BITS = 8;
    
    /** 読み取りバッファサイズ */
    private static final int READ_BUFFER_SIZE = 4096;

    // === Raspberry Pi Pico USB識別子 ===
    
    /** Raspberry Pi Foundation Vendor ID */
    private static final int PICO_VENDOR_ID = 0x2E8A;
    
    /** Raspberry Pi Pico MicroPython USB Serial Product ID */
    private static final int PICO_PRODUCT_ID_MICROPYTHON = 0x0005;
    
    /** Raspberry Pi Pico 2 (RP2350) MicroPython USB Serial Product ID */
    private static final int PICO2_PRODUCT_ID_MICROPYTHON = 0x000B;
    
    /** Raspberry Pi Pico USB Serial (stdio) Product ID */
    private static final int PICO_PRODUCT_ID_STDIO = 0x000A;
    
    /** Raspberry Pi Pico 2 USB Serial (stdio) Product ID */
    private static final int PICO2_PRODUCT_ID_STDIO = 0x000F;

    /** コンテキスト */
    private final Context context;
    
    /** USBマネージャー */
    private final UsbManager usbManager;
    
    /** NMEAパーサー */
    private final NmeaParser nmeaParser;
    
    /** UBXパーサー */
    private final UbxParser ubxParser;
    
    /** メインスレッドハンドラー */
    private final Handler mainHandler;

    /** USBシリアルポート */
    private UsbSerialPort serialPort;
    
    /** USBデバイス接続 */
    private UsbDeviceConnection deviceConnection;
    
    /** シリアルI/Oマネージャー */
    private SerialInputOutputManager ioManager;
    
    /** 受信バッファ */
    private final StringBuilder receiveBuffer = new StringBuilder();

    /** 接続状態リスナー */
    private OnConnectionStateListener connectionListener;
    
    /** 位置情報リスナー */
    private OnUsbGpsLocationListener locationListener;
    
    /** 磁気センサーリスナー */
    private OnUsbMagneticListener magneticListener;
    
    /** 接続中フラグ */
    private boolean isConnected = false;
    
    /** 磁気センサー有効フラグ */
    private boolean magneticSensorEnabled = false;

    /** Pico接続フラグ（Pico経由のF9P接続） */
    private boolean isPicoConnected = false;

    /** カスタムUSBシリアルプローバー（Pico対応） */
    private final UsbSerialProber customProber;

    /**
     * 接続状態リスナー
     */
    public interface OnConnectionStateListener {
        /**
         * 接続状態が変更された時に呼ばれる
         * @param connected 接続中の場合true
         * @param deviceName デバイス名
         */
        void onConnectionStateChanged(boolean connected, String deviceName);
        
        /**
         * エラーが発生した時に呼ばれる
         * @param message エラーメッセージ
         */
        void onError(String message);
    }

    /**
     * 位置情報リスナー
     */
    public interface OnUsbGpsLocationListener {
        /**
         * 位置情報が更新された時に呼ばれる
         * @param location 位置情報
         */
        void onLocationUpdated(GpsLocation location);
    }

    /**
     * 磁気センサーリスナー
     */
    public interface OnUsbMagneticListener {
        /**
         * 磁気センサーデータが更新された時に呼ばれる
         * @param magX X軸磁場（μT）
         * @param magY Y軸磁場（μT）
         * @param magZ Z軸磁場（μT）
         * @param totalField 総磁場強度（μT）
         */
        void onMagneticData(float magX, float magY, float magZ, float totalField);
    }

    /**
     * USB接続イベントのレシーバー
     */
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            Log.d(TAG, "USB許可が付与されました: " + device.getDeviceName());
                            connectToDevice(device);
                        }
                    } else {
                        Log.w(TAG, "USB許可が拒否されました");
                        notifyError("USB許可が拒否されました");
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && serialPort != null) {
                    Log.d(TAG, "USBデバイスが切断されました: " + device.getDeviceName());
                    disconnect();
                }
            }
        }
    };

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public UsbGpsManager(Context context) {
        this.context = context.getApplicationContext();
        this.usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        this.nmeaParser = new NmeaParser();
        this.ubxParser = new UbxParser();
        this.mainHandler = new Handler(Looper.getMainLooper());

        // カスタムプローバーを作成（Raspberry Pi Pico対応）
        this.customProber = createCustomProber();

        // NMEAパーサーのリスナーを設定（スロットリング付き）
        nmeaParser.setOnLocationParsedListener(location -> {
            long now = System.currentTimeMillis();
            if (now - lastLocationNotifyTime < LOCATION_NOTIFY_INTERVAL) {
                return; // スロットリング
            }
            lastLocationNotifyTime = now;
            
            if (locationListener != null) {
                locationListener.onLocationUpdated(location);
            }
        });

        // UBXパーサーの磁気データリスナーを設定（Pico未使用時のみ有効）
        ubxParser.setOnMagneticDataListener((magX, magY, magZ, totalField) -> {
            // Pico接続時は$PIMAGを使用するのでスキップ
            if (isPicoConnected) return;
            
            if (magneticListener != null) {
                magneticListener.onMagneticData(magX, magY, magZ, totalField);
            }
        });

        // USBイベントレシーバーを登録
        registerUsbReceiver();
    }

    /**
     * Raspberry Pi Pico対応のカスタムプローバーを作成
     * 
     * @return カスタムUSBシリアルプローバー
     */
    private UsbSerialProber createCustomProber() {
        ProbeTable customTable = new ProbeTable();
        
        // Raspberry Pi Pico (RP2040) MicroPython USB Serial
        customTable.addProduct(PICO_VENDOR_ID, PICO_PRODUCT_ID_MICROPYTHON, CdcAcmSerialDriver.class);
        
        // Raspberry Pi Pico (RP2040) USB Serial (stdio)
        customTable.addProduct(PICO_VENDOR_ID, PICO_PRODUCT_ID_STDIO, CdcAcmSerialDriver.class);
        
        // Raspberry Pi Pico 2 (RP2350) MicroPython USB Serial
        customTable.addProduct(PICO_VENDOR_ID, PICO2_PRODUCT_ID_MICROPYTHON, CdcAcmSerialDriver.class);
        
        // Raspberry Pi Pico 2 (RP2350) USB Serial (stdio)
        customTable.addProduct(PICO_VENDOR_ID, PICO2_PRODUCT_ID_STDIO, CdcAcmSerialDriver.class);
        
        return new UsbSerialProber(customTable);
    }

    /**
     * USBレシーバーを登録
     */
    private void registerUsbReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(usbReceiver, filter);
        }
    }

    /**
     * 接続状態リスナーを設定
     * @param listener リスナー
     */
    public void setOnConnectionStateListener(OnConnectionStateListener listener) {
        this.connectionListener = listener;
    }

    /**
     * 位置情報リスナーを設定
     * @param listener リスナー
     */
    public void setOnUsbGpsLocationListener(OnUsbGpsLocationListener listener) {
        this.locationListener = listener;
    }

    /**
     * 磁気センサーリスナーを設定
     * @param listener リスナー
     */
    public void setOnUsbMagneticListener(OnUsbMagneticListener listener) {
        this.magneticListener = listener;
    }

    /**
     * 磁気センサーを有効化
     * 接続後に呼び出すことで、ESF-RAW/ESF-MEASメッセージを有効化する
     */
    public void enableMagneticSensor() {
        if (!isConnected || serialPort == null) {
            Log.w(TAG, "磁気センサー有効化失敗: 未接続");
            return;
        }

        try {
            // ESF-RAWメッセージを有効化
            byte[] enableEsfRaw = UbxParser.buildEnableEsfRawCommand();
            serialPort.write(enableEsfRaw, 1000);
            Log.d(TAG, "ESF-RAWメッセージ有効化コマンド送信");

            // ESF-MEASメッセージを有効化
            byte[] enableEsfMeas = UbxParser.buildEnableEsfMeasCommand();
            serialPort.write(enableEsfMeas, 1000);
            Log.d(TAG, "ESF-MEASメッセージ有効化コマンド送信");

            magneticSensorEnabled = true;
        } catch (IOException e) {
            Log.e(TAG, "磁気センサー有効化コマンド送信エラー: " + e.getMessage());
        }
    }

    /**
     * 磁気センサーが有効かどうかを取得
     * @return 有効な場合true
     */
    public boolean isMagneticSensorEnabled() {
        return magneticSensorEnabled;
    }

    /**
     * 利用可能なUSB GPSデバイスを検索
     * デフォルトプローバーとカスタムプローバー（Pico対応）の両方を使用
     * 
     * @return 見つかったデバイスのリスト
     */
    public List<UsbSerialDriver> findAvailableDevices() {
        try {
            if (usbManager == null) {
                Log.w(TAG, "UsbManagerがnullです");
                return new ArrayList<>();
            }
            
            List<UsbSerialDriver> allDrivers = new ArrayList<>();
            
            // デフォルトプローバーで検索（通常のUSBシリアルデバイス）
            List<UsbSerialDriver> defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            if (defaultDrivers != null) {
                allDrivers.addAll(defaultDrivers);
            }
            
            // カスタムプローバーで検索（Raspberry Pi Pico）
            List<UsbSerialDriver> customDrivers = customProber.findAllDrivers(usbManager);
            if (customDrivers != null) {
                for (UsbSerialDriver driver : customDrivers) {
                    // 重複を避ける
                    boolean isDuplicate = false;
                    for (UsbSerialDriver existing : allDrivers) {
                        if (existing.getDevice().getDeviceId() == driver.getDevice().getDeviceId()) {
                            isDuplicate = true;
                            break;
                        }
                    }
                    if (!isDuplicate) {
                        allDrivers.add(driver);
                        Log.d(TAG, "Pico検出: VID=" + String.format("0x%04X", driver.getDevice().getVendorId()) 
                                + ", PID=" + String.format("0x%04X", driver.getDevice().getProductId()));
                    }
                }
            }
            
            return allDrivers;
        } catch (Exception e) {
            Log.e(TAG, "USBデバイス検索エラー: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * 指定されたデバイスがRaspberry Pi Picoかどうかを判定
     * 
     * @param device USBデバイス
     * @return Picoの場合true
     */
    private boolean isPicoDevice(UsbDevice device) {
        if (device == null) {
            return false;
        }
        int vendorId = device.getVendorId();
        int productId = device.getProductId();
        
        // デバッグログ
        Log.d(TAG, String.format("USBデバイス: VID=0x%04X, PID=0x%04X", vendorId, productId));
        
        if (vendorId != PICO_VENDOR_ID) {
            return false;
        }
        
        // Pico (RP2040) または Pico 2 (RP2350) のProduct IDをチェック
        return productId == PICO_PRODUCT_ID_MICROPYTHON || 
               productId == PICO_PRODUCT_ID_STDIO ||
               productId == PICO2_PRODUCT_ID_MICROPYTHON ||
               productId == PICO2_PRODUCT_ID_STDIO;
    }

    /**
     * Pico経由で接続されているかどうかを取得
     * 
     * @return Pico経由の場合true
     */
    public boolean isPicoConnected() {
        return isPicoConnected;
    }

    /**
     * USB GPSデバイスが接続されているか確認
     * 
     * @return デバイスが存在する場合true
     */
    public boolean hasUsbGpsDevice() {
        try {
            return !findAvailableDevices().isEmpty();
        } catch (Exception e) {
            Log.e(TAG, "USBデバイス確認エラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * 最初に見つかったUSB GPSデバイスに接続
     * 
     * @return 接続処理を開始した場合true
     */
    public boolean connect() {
        try {
            List<UsbSerialDriver> drivers = findAvailableDevices();
            if (drivers.isEmpty()) {
                Log.w(TAG, "USB GPSデバイスが見つかりません");
                notifyError("USB GPSデバイスが見つかりません");
                return false;
            }

            UsbSerialDriver driver = drivers.get(0);
            UsbDevice device = driver.getDevice();
            
            if (usbManager == null) {
                Log.e(TAG, "UsbManagerがnullです");
                notifyError("USBマネージャーの初期化に失敗しました");
                return false;
            }

            // 接続許可をリクエスト
            if (!usbManager.hasPermission(device)) {
                Log.d(TAG, "USB許可をリクエスト中: " + device.getDeviceName());
                Intent intent = new Intent(ACTION_USB_PERMISSION);
                intent.setPackage(context.getPackageName());
                int flags;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    flags = PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
                } else {
                    flags = PendingIntent.FLAG_UPDATE_CURRENT;
                }
                PendingIntent permissionIntent = PendingIntent.getBroadcast(
                        context, 0, intent, flags);
                usbManager.requestPermission(device, permissionIntent);
                return true;
            }

            // すでに許可がある場合は直接接続
            return connectToDevice(device);
        } catch (Exception e) {
            Log.e(TAG, "USB GPS接続開始エラー: " + e.getMessage(), e);
            notifyError("USB GPS接続エラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * 指定されたUSBデバイスに接続
     * Raspberry Pi Pico経由の場合は自動的にESFメッセージを有効化
     * 
     * @param device USBデバイス
     * @return 接続が成功した場合true
     */
    private boolean connectToDevice(UsbDevice device) {
        try {
            // Picoかどうかを判定
            isPicoConnected = isPicoDevice(device);
            
            // バッファをリセット
            picoBuffer.setLength(0);
            picoDataCount = 0;
            pimagReceiveCount = 0;
            
            // ドライバを検索（デフォルト + カスタム）
            UsbSerialDriver targetDriver = findDriverForDevice(device);

            if (targetDriver == null) {
                Log.e(TAG, "対応するドライバが見つかりません");
                notifyError("対応するUSBドライバが見つかりません");
                return false;
            }

            // デバイスを開く
            deviceConnection = usbManager.openDevice(device);
            if (deviceConnection == null) {
                Log.e(TAG, "USBデバイスを開けません");
                notifyError("USBデバイスを開けません");
                return false;
            }

            // シリアルポートを取得
            serialPort = targetDriver.getPorts().get(0);
            serialPort.open(deviceConnection);
            serialPort.setParameters(DEFAULT_BAUD_RATE, DATA_BITS, 
                    UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

            // I/Oマネージャーを開始
            ioManager = new SerialInputOutputManager(serialPort, this);
            Executors.newSingleThreadExecutor().submit(ioManager);

            isConnected = true;
            
            // デバイス名を生成
            String deviceName = generateDeviceName(device);
            Log.d(TAG, "USB GPS接続成功: " + deviceName);
            notifyConnectionStateChanged(true, deviceName);

            // Pico経由の場合、ソフトリセットを送信してmain.pyを起動
            if (isPicoConnected) {
                Log.d(TAG, "Raspberry Pi Pico検出 - ソフトリセット送信");
                // MicroPythonをリセットしてmain.pyを自動実行させる
                try {
                    // Ctrl+C (0x03) で実行中のプログラムを停止
                    serialPort.write(new byte[]{0x03}, 100);
                    Thread.sleep(50);
                    // Ctrl+C をもう一度（確実に停止）
                    serialPort.write(new byte[]{0x03}, 100);
                    Thread.sleep(50);
                    // Ctrl+B (0x02) で通常REPLモードに戻る（raw REPLから抜ける）
                    serialPort.write(new byte[]{0x02}, 100);
                    Thread.sleep(100);
                    // Ctrl+D (0x04) でソフトリセット → main.py自動実行
                    serialPort.write(new byte[]{0x04}, 100);
                    Log.d(TAG, "ソフトリセット送信完了");
                } catch (Exception e) {
                    Log.e(TAG, "ソフトリセット送信エラー: " + e.getMessage());
                }
                // Pico経由では$PIMAGを使用するのでESFコマンドは送信しない
            }

            return true;

        } catch (IOException e) {
            Log.e(TAG, "USB GPS接続エラー: " + e.getMessage());
            notifyError("USB GPS接続エラー: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * 指定されたデバイスに対応するドライバを検索
     * 
     * @param device USBデバイス
     * @return 対応するドライバ（見つからない場合null）
     */
    private UsbSerialDriver findDriverForDevice(UsbDevice device) {
        // デフォルトプローバーで検索
        List<UsbSerialDriver> defaultDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
        if (defaultDrivers != null) {
            for (UsbSerialDriver driver : defaultDrivers) {
                if (driver.getDevice().equals(device)) {
                    return driver;
                }
            }
        }
        
        // カスタムプローバーで検索（Pico対応）
        List<UsbSerialDriver> customDrivers = customProber.findAllDrivers(usbManager);
        if (customDrivers != null) {
            for (UsbSerialDriver driver : customDrivers) {
                if (driver.getDevice().equals(device)) {
                    return driver;
                }
            }
        }
        
        return null;
    }

    /**
     * デバイス名を生成
     * 
     * @param device USBデバイス
     * @return デバイス名
     */
    private String generateDeviceName(UsbDevice device) {
        if (isPicoDevice(device)) {
            return "Raspberry Pi Pico (F9P Bridge)";
        }
        String productName = device.getProductName();
        if (productName != null && !productName.isEmpty()) {
            return productName;
        }
        return device.getDeviceName();
    }

    /**
     * USB GPSデバイスから切断
     */
    public void disconnect() {
        isConnected = false;
        isPicoConnected = false;
        magneticSensorEnabled = false;

        if (ioManager != null) {
            ioManager.setListener(null);
            ioManager.stop();
            ioManager = null;
        }

        if (serialPort != null) {
            try {
                serialPort.close();
            } catch (IOException e) {
                Log.e(TAG, "シリアルポートのクローズに失敗: " + e.getMessage());
            }
            serialPort = null;
        }

        if (deviceConnection != null) {
            deviceConnection.close();
            deviceConnection = null;
        }

        nmeaParser.reset();
        ubxParser.reset();
        receiveBuffer.setLength(0);

        Log.d(TAG, "USB GPS切断完了");
        notifyConnectionStateChanged(false, null);
    }

    /**
     * 接続状態を取得
     * @return 接続中の場合true
     */
    public boolean isConnected() {
        return isConnected;
    }

    /**
     * 現在の位置情報を取得
     * @return 現在の位置情報（未接続の場合null）
     */
    public GpsLocation getCurrentLocation() {
        if (!isConnected) {
            return null;
        }
        return nmeaParser.getCurrentLocation();
    }

    /**
     * リソースを解放
     */
    public void release() {
        disconnect();
        try {
            context.unregisterReceiver(usbReceiver);
        } catch (IllegalArgumentException e) {
            // 未登録の場合は無視
        }
    }

    // === SerialInputOutputManager.Listener ===

    @Override
    public void onNewData(byte[] data) {
        // Pico接続時は$PIMAGのみを軽量に検出（他のデータは破棄）
        if (isPicoConnected) {
            processPicoData(data);
            return;
        }
        
        // F9P直接接続時のみUBXとNMEAをパース
        if (magneticSensorEnabled) {
            ubxParser.parse(data);
        }

        // 受信データをバッファに追加（NMEAテキスト用）
        String received = new String(data);
        receiveBuffer.append(received);

        // 改行で分割してNMEAセンテンスをパース
        String buffer = receiveBuffer.toString();
        int lastNewline = buffer.lastIndexOf('\n');
        
        if (lastNewline > 0) {
            String complete = buffer.substring(0, lastNewline);
            receiveBuffer.setLength(0);
            receiveBuffer.append(buffer.substring(lastNewline + 1));

            // 各行をパース
            String[] lines = complete.split("\n");
            for (String line : lines) {
                line = line.trim();
                if (line.startsWith("$")) {
                    nmeaParser.parseSentence(line);
                }
            }
        }

        // バッファが大きくなりすぎた場合はクリア
        if (receiveBuffer.length() > READ_BUFFER_SIZE) {
            receiveBuffer.setLength(0);
        }
    }
    
    /** Pico用の軽量バッファ */
    private StringBuilder picoBuffer = new StringBuilder(256);
    
    /** Picoデータ受信カウンター */
    private int picoDataCount = 0;
    
    /**
     * Pico接続時の軽量データ処理
     * $PIMAGメッセージのみを検出して処理する
     */
    private void processPicoData(byte[] data) {
        String received = new String(data);
        picoBuffer.append(received);
        
        // デバッグ: データ受信状況（最初の10回のみ）
        picoDataCount++;
        if (picoDataCount <= 10) {
            // 受信データの先頭50文字を表示
            String preview = received.length() > 50 ? received.substring(0, 50) : received;
            preview = preview.replace("\r", "\\r").replace("\n", "\\n");
            Log.d(TAG, "Pico受信[" + picoDataCount + "]: " + data.length + "bytes [" + preview + "]");
        }
        
        // $PIMAGを検索
        int pimagStart = picoBuffer.indexOf("$PIMAG");
        if (pimagStart < 0) {
            // $PIMAGがなければバッファをクリア（先頭256バイトのみ保持）
            if (picoBuffer.length() > 256) {
                picoBuffer.delete(0, picoBuffer.length() - 128);
            }
            return;
        }
        
        // 改行を検索
        int newlinePos = picoBuffer.indexOf("\n", pimagStart);
        if (newlinePos < 0) {
            return; // まだ完全なメッセージではない
        }
        
        // $PIMAGメッセージを抽出
        String pimagLine = picoBuffer.substring(pimagStart, newlinePos).trim();
        
        // バッファをクリア
        picoBuffer.delete(0, newlinePos + 1);
        
        // パース
        pimagReceiveCount++;
        if (pimagReceiveCount <= 3 || pimagReceiveCount % 100 == 0) {
            Log.i(TAG, "PIMAG[" + pimagReceiveCount + "]: " + pimagLine);
        }
        parsePicoMagneticMessage(pimagLine);
    }

    /**
     * Picoからの磁気センサーメッセージをパース
     * フォーマット: $PIMAG,magX,magY,magZ,totalField*XX
     * 
     * @param sentence NMEAセンテンス
     */
    private void parsePicoMagneticMessage(String sentence) {
        try {
            // スロットリング: 通知間隔を制限してUIフリーズを防止
            long now = System.currentTimeMillis();
            if (now - lastMagNotifyTime < MAG_NOTIFY_INTERVAL) {
                return; // 間隔内はスキップ
            }
            lastMagNotifyTime = now;
            
            // チェックサムを除去
            String data = sentence;
            if (sentence.contains("*")) {
                data = sentence.substring(0, sentence.indexOf('*'));
            }

            // $PIMAGを除去してカンマで分割
            String[] parts = data.substring(1).split(",");
            if (parts.length < 5) {
                return; // パース失敗は無視
            }

            // PIMAG,magX,magY,magZ,totalField
            float magX = Float.parseFloat(parts[1]);
            float magY = Float.parseFloat(parts[2]);
            float magZ = Float.parseFloat(parts[3]);
            float totalField = Float.parseFloat(parts[4]);

            // リスナーに直接通知（UIスレッドを使わない）
            if (magneticListener != null) {
                Log.d(TAG, "磁気リスナー通知: " + totalField + "μT");
                magneticListener.onMagneticData(magX, magY, magZ, totalField);
            } else {
                Log.w(TAG, "磁気リスナーがnull");
            }

        } catch (NumberFormatException e) {
            Log.e(TAG, "PIMAGパースエラー: " + sentence + " - " + e.getMessage());
        }
    }

    /** PIMAGデータ受信カウンター（デバッグ用） */
    private int pimagReceiveCount = 0;
    
    /** 磁気データ通知の最終時刻（スロットリング用） */
    private long lastMagNotifyTime = 0;
    
    /** 磁気データ通知間隔（ミリ秒） */
    private static final long MAG_NOTIFY_INTERVAL = 50; // 20Hz
    
    /** 位置情報通知の最終時刻（スロットリング用） */
    private long lastLocationNotifyTime = 0;
    
    /** 位置情報通知間隔（ミリ秒） */
    private static final long LOCATION_NOTIFY_INTERVAL = 500; // 2Hz

    /**
     * PIMAG受信状況を取得（デバッグ用）
     * @return 受信カウント
     */
    public int getPimagReceiveCount() {
        return pimagReceiveCount;
    }

    @Override
    public void onRunError(Exception e) {
        Log.e(TAG, "シリアル通信エラー: " + e.getMessage());
        mainHandler.post(() -> {
            notifyError("シリアル通信エラー: " + e.getMessage());
            disconnect();
        });
    }

    // === Private Helper Methods ===

    /**
     * 接続状態変更を通知
     */
    private void notifyConnectionStateChanged(boolean connected, String deviceName) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onConnectionStateChanged(connected, deviceName));
        }
    }

    /**
     * エラーを通知
     */
    private void notifyError(String message) {
        if (connectionListener != null) {
            mainHandler.post(() -> connectionListener.onError(message));
        }
    }
}

