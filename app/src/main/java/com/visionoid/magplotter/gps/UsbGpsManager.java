/**
 * UsbGpsManager.java
 * 
 * VISIONOID MAG PLOTTER - USB GPS管理クラス
 * 
 * 概要:
 *   USB接続のRTK GPSデバイス（H-RTK F9Pなど）を管理するクラス。
 *   USB-Serial通信を行い、NMEAデータを受信・パースする。
 * 
 * 主な仕様:
 *   - USBデバイスの検出・接続
 *   - シリアル通信（115200baud）
 *   - NMEAデータの受信とパース
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

        // NMEAパーサーのリスナーを設定
        nmeaParser.setOnLocationParsedListener(location -> {
            mainHandler.post(() -> {
                if (locationListener != null) {
                    locationListener.onLocationUpdated(location);
                }
            });
        });

        // UBXパーサーの磁気データリスナーを設定
        ubxParser.setOnMagneticDataListener((magX, magY, magZ, totalField) -> {
            mainHandler.post(() -> {
                if (magneticListener != null) {
                    magneticListener.onMagneticData(magX, magY, magZ, totalField);
                }
            });
        });

        // USBイベントレシーバーを登録
        registerUsbReceiver();
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
     * 
     * @return 見つかったデバイスのリスト
     */
    public List<UsbSerialDriver> findAvailableDevices() {
        try {
            if (usbManager == null) {
                Log.w(TAG, "UsbManagerがnullです");
                return new ArrayList<>();
            }
            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            return drivers != null ? drivers : new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "USBデバイス検索エラー: " + e.getMessage());
            return new ArrayList<>();
        }
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
     * 
     * @param device USBデバイス
     * @return 接続が成功した場合true
     */
    private boolean connectToDevice(UsbDevice device) {
        try {
            List<UsbSerialDriver> drivers = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager);
            UsbSerialDriver targetDriver = null;
            
            for (UsbSerialDriver driver : drivers) {
                if (driver.getDevice().equals(device)) {
                    targetDriver = driver;
                    break;
                }
            }

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
            Log.d(TAG, "USB GPS接続成功: " + device.getDeviceName());
            notifyConnectionStateChanged(true, device.getDeviceName());

            return true;

        } catch (IOException e) {
            Log.e(TAG, "USB GPS接続エラー: " + e.getMessage());
            notifyError("USB GPS接続エラー: " + e.getMessage());
            disconnect();
            return false;
        }
    }

    /**
     * USB GPSデバイスから切断
     */
    public void disconnect() {
        isConnected = false;

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
        // UBXバイナリデータをパース（磁気センサーデータ取得用）
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

