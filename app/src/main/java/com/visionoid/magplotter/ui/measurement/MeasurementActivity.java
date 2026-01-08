/**
 * MeasurementActivity.java
 * 
 * VISIONOID MAG PLOTTER - 計測画面
 * 
 * 概要:
 *   磁場計測のメイン画面。GPS位置情報と磁気センサーを使用し、
 *   計測データをリアルタイムで地図上にヒートマップ表示する。
 * 
 * 主な仕様:
 *   - GPS位置のリアルタイム取得
 *   - 磁気センサーによる磁場強度計測
 *   - 自動計測モード（0.1〜2秒間隔）
 *   - 手動計測モード（ボタンタップで記録）
 *   - ヒートマップ表示
 *   - スクリーンショット保存
 * 
 * 制限事項:
 *   - 位置情報パーミッションが必要
 *   - 磁気センサー搭載端末のみ対応
 */
package com.visionoid.magplotter.ui.measurement;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.layer.LayerDataRepository;
import com.visionoid.magplotter.data.model.MeasurementPoint;
import com.visionoid.magplotter.data.model.Mission;
import com.visionoid.magplotter.ui.map.layer.LayerDisplayStyle;
import com.visionoid.magplotter.ui.map.layer.LayerType;
import com.visionoid.magplotter.ui.map.layer.MapLayerManager;
import com.visionoid.magplotter.ui.view.NoiseLevelGauge;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;

import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.AdapterView;

import com.visionoid.magplotter.ui.map.drawing.DrawingController;
import com.visionoid.magplotter.ui.map.drawing.DrawingMode;
import com.visionoid.magplotter.gps.GpsFixStatus;
import com.visionoid.magplotter.gps.GpsLocation;
import com.visionoid.magplotter.gps.GpsSourceType;
import com.visionoid.magplotter.gps.UsbGpsManager;

/**
 * 計測画面アクティビティ
 * 
 * 磁場計測とヒートマップ表示を行うメイン画面。
 */
public class MeasurementActivity extends AppCompatActivity implements SensorEventListener {

    /** Intent Extra: ミッションID */
    public static final String EXTRA_MISSION_ID = "extra_mission_id";

    /** パーミッションリクエストコード */
    private static final int REQUEST_LOCATION_PERMISSION = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    /** ViewModel */
    private MeasurementViewModel viewModel;

    /** ミッションID */
    private long missionId;

    /** 現在のミッション */
    private Mission currentMission;

    // センサー関連
    private SensorManager sensorManager;
    private Sensor magneticSensor;
    private float[] magneticValues = new float[3];
    private double currentMagStrength = 0;
    private double currentNoise = 0;

    // 位置情報関連
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private float currentAccuracy = 0;
    
    // GNSS（サテライト）関連
    private LocationManager locationManager;
    private GnssStatus.Callback gnssStatusCallback;
    private int satelliteCount = 0;        // 捕捉中のサテライト数
    private int usedSatelliteCount = 0;    // 測位に使用中のサテライト数

    // 計測制御
    private Handler measurementHandler;
    private Runnable measurementRunnable;
    private boolean isAutoMeasuring = false;
    private int measurementIntervalMs = 1000; // デフォルト1秒

    // 地図関連
    private MapView mapView;
    private IMapController mapController;
    private Marker currentLocationMarker;
    private List<Polygon> heatmapPolygons = new ArrayList<>();
    
    /** 地図の初期センタリングが完了したかどうか */
    private boolean isInitialCenterSet = false;
    
    /** 現在の地図タイプ（0: 標準, 1: 衛星, 2: 地形） */
    private int currentMapType = 0;
    
    // レイヤー関連
    private MapLayerManager mapLayerManager;
    private LayerDataRepository layerDataRepository;
    
    // 作図関連
    private DrawingController drawingController;
    private FrameLayout containerDrawingToolbar;
    
    // USB GPS関連
    private UsbGpsManager usbGpsManager;
    private GpsSourceType currentGpsSource = GpsSourceType.AUTO;
    private GpsLocation currentUsbGpsLocation;
    private boolean isUsbGpsConnected = false;
    
    /** 磁気センサーソース（true: USB GPS, false: 内蔵） */
    private boolean useUsbMagneticSensor = false;
    
    /** USB GPS磁気データ */
    private float usbMagX = 0, usbMagY = 0, usbMagZ = 0, usbMagTotal = 0;
    
    /** Esri World Imagery（衛星写真）タイルソース */
    private static final OnlineTileSourceBase ESRI_WORLD_IMAGERY = new XYTileSource(
            "EsriWorldImagery",
            0, 19, 256, ".jpg",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}
    ) {
        @Override
        public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex)
                    + "/" + MapTileIndex.getY(pMapTileIndex)
                    + "/" + MapTileIndex.getX(pMapTileIndex)
                    + mImageFilenameEnding;
        }
    };
    
    /** OpenTopoMap（地形図）タイルソース */
    private static final OnlineTileSourceBase OPEN_TOPO_MAP = new XYTileSource(
            "OpenTopoMap",
            0, 17, 256, ".png",
            new String[]{"https://a.tile.opentopomap.org/", "https://b.tile.opentopomap.org/", "https://c.tile.opentopomap.org/"}
    ) {
        @Override
        public String getTileURLString(long pMapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(pMapTileIndex)
                    + "/" + MapTileIndex.getX(pMapTileIndex)
                    + "/" + MapTileIndex.getY(pMapTileIndex)
                    + mImageFilenameEnding;
        }
    };

    // UI要素
    private TextView textMagValue;
    private TextView textNoiseValue;
    private TextView textGpsStatus;
    private TextView textAccuracy;
    private TextView textPointCount;
    private TextView textIntervalValue;
    private TextView textSatelliteCount;
    private SwitchMaterial switchAutoMode;
    private SeekBar seekBarInterval;
    private Button buttonMeasure;
    private Button buttonStartStop;
    private View panelStatus;
    
    // 統計表示UI要素
    private TextView textMagMax;
    private TextView textMagAvg;
    private TextView textNoiseMax;
    private TextView textNoiseAvg;
    
    // レベルゲージ
    private NoiseLevelGauge noiseLevelGauge;
    
    // RTK GPS UI要素
    private View panelRtkInfo;
    private TextView textGpsFixStatus;
    private TextView textGpsHdop;
    private TextView textGpsSource;

    /** 日付フォーマット（スクリーンショット用） */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_measurement);

        // ミッションID取得
        missionId = getIntent().getLongExtra(EXTRA_MISSION_ID, -1);
        if (missionId == -1) {
            Toast.makeText(this, "Mission not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 初期化
        setupToolbar();
        initializeViews();
        initializeSensors();
        initializeLocation();
        initializeUsbGps();  // USB GPS初期化
        initializeMap();
        initializeMapLayers();
        initializeDrawing();
        setupViewModel();
        setupListeners();

        measurementHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * ツールバーを設定
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_measurement);
        }
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        mapView = findViewById(R.id.map_view);
        textMagValue = findViewById(R.id.text_mag_value);
        textNoiseValue = findViewById(R.id.text_noise_value);
        textGpsStatus = findViewById(R.id.text_gps_status);
        textAccuracy = findViewById(R.id.text_accuracy);
        textPointCount = findViewById(R.id.text_point_count);
        textIntervalValue = findViewById(R.id.text_interval_value);
        textSatelliteCount = findViewById(R.id.text_satellite_count);
        switchAutoMode = findViewById(R.id.switch_auto_mode);
        seekBarInterval = findViewById(R.id.seekbar_interval);
        buttonMeasure = findViewById(R.id.button_measure);
        buttonStartStop = findViewById(R.id.button_start_stop);
        panelStatus = findViewById(R.id.panel_status);
        
        // 統計表示UI要素
        textMagMax = findViewById(R.id.text_mag_max);
        textMagAvg = findViewById(R.id.text_mag_avg);
        textNoiseMax = findViewById(R.id.text_noise_max);
        textNoiseAvg = findViewById(R.id.text_noise_avg);
        
        // レベルゲージ
        noiseLevelGauge = findViewById(R.id.noise_level_gauge);
        
        // 作図ツールバーコンテナ
        containerDrawingToolbar = findViewById(R.id.container_drawing_toolbar);
        
        // RTK GPS UI要素
        panelRtkInfo = findViewById(R.id.panel_rtk_info);
        textGpsFixStatus = findViewById(R.id.text_gps_fix_status);
        textGpsHdop = findViewById(R.id.text_gps_hdop);
        textGpsSource = findViewById(R.id.text_gps_source);

        // 初期値設定
        updateIntervalDisplay(measurementIntervalMs);
        updateSatelliteDisplay();
        updateStatisticsDisplay(new MeasurementViewModel.MagStatistics());
    }

    /**
     * センサーを初期化
     */
    private void initializeSensors() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        magneticSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (magneticSensor == null) {
            Toast.makeText(this, R.string.measurement_no_sensor, Toast.LENGTH_LONG).show();
            buttonMeasure.setEnabled(false);
            buttonStartStop.setEnabled(false);
        }
    }

    /**
     * 位置情報を初期化
     */
    private void initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                Location location = locationResult.getLastLocation();
                if (location != null) {
                    currentLocation = location;
                    currentAccuracy = location.getAccuracy();
                    updateLocationUI();
                    updateMapLocation();
                }
            }
        };
        
        // GNSS（サテライト）ステータスコールバック
        gnssStatusCallback = new GnssStatus.Callback() {
            @Override
            public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                int total = status.getSatelliteCount();
                int used = 0;
                
                // 測位に使用されているサテライトをカウント
                for (int i = 0; i < total; i++) {
                    if (status.usedInFix(i)) {
                        used++;
                    }
                }
                
                satelliteCount = total;
                usedSatelliteCount = used;
                
                runOnUiThread(() -> updateSatelliteDisplay());
            }
            
            @Override
            public void onStarted() {
                Log.d("MeasurementActivity", "GNSS started");
            }
            
            @Override
            public void onStopped() {
                Log.d("MeasurementActivity", "GNSS stopped");
            }
        };
    }

    /**
     * USB GPS（RTK GPS）を初期化
     */
    private void initializeUsbGps() {
        usbGpsManager = new UsbGpsManager(this);
        
        // 接続状態リスナー
        usbGpsManager.setOnConnectionStateListener(new UsbGpsManager.OnConnectionStateListener() {
            @Override
            public void onConnectionStateChanged(boolean connected, String deviceName) {
                isUsbGpsConnected = connected;
                runOnUiThread(() -> {
                    if (connected) {
                        Toast.makeText(MeasurementActivity.this, 
                                getString(R.string.gps_usb_connected) + ": " + deviceName, 
                                Toast.LENGTH_SHORT).show();
                        // RTK情報パネルを表示
                        if (panelRtkInfo != null) {
                            panelRtkInfo.setVisibility(View.VISIBLE);
                        }
                        updateGpsSourceDisplay();
                    } else {
                        Toast.makeText(MeasurementActivity.this, 
                                R.string.gps_usb_disconnected, 
                                Toast.LENGTH_SHORT).show();
                        // 自動モードで内蔵GPSに戻る場合のみパネルを非表示
                        if (currentGpsSource == GpsSourceType.AUTO && panelRtkInfo != null) {
                            panelRtkInfo.setVisibility(View.GONE);
                        }
                        updateGpsSourceDisplay();
                    }
                });
            }
            
            @Override
            public void onError(String message) {
                runOnUiThread(() -> {
                    Toast.makeText(MeasurementActivity.this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
        
        // 位置情報リスナー
        usbGpsManager.setOnUsbGpsLocationListener(location -> {
            currentUsbGpsLocation = location;
            runOnUiThread(() -> {
                // USB GPSからの位置情報を処理
                if (shouldUseUsbGps()) {
                    updateLocationFromUsbGps(location);
                }
            });
        });
        
        // 磁気センサーリスナー
        usbGpsManager.setOnUsbMagneticListener((magX, magY, magZ, totalField) -> {
            usbMagX = magX;
            usbMagY = magY;
            usbMagZ = magZ;
            usbMagTotal = totalField;
            
            runOnUiThread(() -> {
                // USB GPS磁気センサーを使用中の場合、UIを更新
                if (useUsbMagneticSensor) {
                    updateMagneticDisplay(totalField, calculateNoise(magX, magY, magZ));
                }
            });
        });
        
        // 自動モードの場合、USB GPSデバイスの検出を試みる
        if (currentGpsSource == GpsSourceType.AUTO && usbGpsManager.hasUsbGpsDevice()) {
            Log.d("MeasurementActivity", "USB GPSデバイスを検出しました。接続を試みます。");
            usbGpsManager.connect();
        }
    }
    
    /**
     * ノイズを計算（簡易的な標準偏差）
     */
    private double calculateNoise(float x, float y, float z) {
        // 磁場成分から簡易的なノイズ推定
        double mean = (x + y + z) / 3.0;
        double variance = (Math.pow(x - mean, 2) + Math.pow(y - mean, 2) + Math.pow(z - mean, 2)) / 3.0;
        return Math.sqrt(variance);
    }
    
    /**
     * 磁場表示を更新
     */
    private void updateMagneticDisplay(double magStrength, double noise) {
        if (textMagValue != null) {
            textMagValue.setText(String.format(Locale.getDefault(), "%.1f", magStrength));
        }
        if (textNoiseValue != null) {
            textNoiseValue.setText(String.format(Locale.getDefault(), "%.1f", noise));
            // ノイズレベルに応じた色
            int color;
            if (noise < 10) {
                color = ContextCompat.getColor(this, R.color.status_safe);
            } else if (noise < 30) {
                color = ContextCompat.getColor(this, R.color.status_warning);
            } else {
                color = ContextCompat.getColor(this, R.color.status_danger);
            }
            textNoiseValue.setTextColor(color);
        }
        if (noiseLevelGauge != null) {
            noiseLevelGauge.setNoiseValue(noise);
        }
    }

    /**
     * USB GPSを使用するべきかどうかを判定
     * @return USB GPSを使用する場合true
     */
    private boolean shouldUseUsbGps() {
        if (currentGpsSource == GpsSourceType.USB) {
            return true;
        }
        if (currentGpsSource == GpsSourceType.AUTO && isUsbGpsConnected) {
            return true;
        }
        return false;
    }

    /**
     * USB GPSからの位置情報でUIを更新
     * @param location USB GPS位置情報
     */
    private void updateLocationFromUsbGps(GpsLocation location) {
        if (location == null || !location.isValid()) {
            return;
        }
        
        // 現在位置を更新（内蔵GPSと同様に処理）
        currentLocation = location.toAndroidLocation();
        currentAccuracy = location.getHorizontalAccuracy();
        
        // 位置情報UIを更新
        updateLocationUI();
        updateMapLocation();
        
        // RTK GPS固有の情報を更新
        updateRtkInfoDisplay(location);
    }

    /**
     * RTK GPS情報パネルを更新
     * @param location GPS位置情報
     */
    private void updateRtkInfoDisplay(GpsLocation location) {
        if (panelRtkInfo == null || location == null) {
            return;
        }
        
        // Fix状態
        if (textGpsFixStatus != null) {
            GpsFixStatus fixStatus = location.getFixStatus();
            textGpsFixStatus.setText(fixStatus.getEnglishLabel());
            
            // Fix状態に応じて色を変更
            int color;
            switch (fixStatus) {
                case RTK_FIX:
                    color = ContextCompat.getColor(this, R.color.status_safe);
                    break;
                case RTK_FLOAT:
                    color = ContextCompat.getColor(this, R.color.status_warning);
                    break;
                case FIX_3D:
                case DGPS:
                    color = ContextCompat.getColor(this, R.color.accent_cyan);
                    break;
                default:
                    color = ContextCompat.getColor(this, R.color.status_danger);
                    break;
            }
            textGpsFixStatus.setTextColor(color);
        }
        
        // HDOP
        if (textGpsHdop != null) {
            float hdop = location.getHdop();
            textGpsHdop.setText(String.format(Locale.getDefault(), "%.1f", hdop));
        }
        
        // ソース
        updateGpsSourceDisplay();
    }

    /**
     * GPSソース表示を更新
     */
    private void updateGpsSourceDisplay() {
        if (textGpsSource == null) {
            return;
        }
        
        String sourceText;
        int sourceColor;
        
        if (shouldUseUsbGps() && isUsbGpsConnected) {
            sourceText = "USB";
            sourceColor = ContextCompat.getColor(this, R.color.accent_orange);
        } else {
            sourceText = "INT";
            sourceColor = ContextCompat.getColor(this, R.color.accent_cyan);
        }
        
        textGpsSource.setText(sourceText);
        textGpsSource.setTextColor(sourceColor);
    }

    /**
     * 地図を初期化
     */
    private void initializeMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapController = mapView.getController();
        mapController.setZoom(18.0);

        // 現在位置マーカー
        currentLocationMarker = new Marker(mapView);
        currentLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        currentLocationMarker.setTitle("Current Location");
        mapView.getOverlays().add(currentLocationMarker);
    }

    /**
     * マップレイヤーを初期化
     */
    private void initializeMapLayers() {
        mapLayerManager = new MapLayerManager(this, mapView);
        layerDataRepository = new LayerDataRepository(this);

        // 保存された表示状態に基づいてレイヤーを読み込み
        for (LayerType layerType : LayerType.values()) {
            if (mapLayerManager.isLayerVisible(layerType)) {
                loadLayerData(layerType);
            }
        }
    }

    /**
     * 作図機能を初期化
     */
    private void initializeDrawing() {
        drawingController = new DrawingController(this, mapView, missionId);
        drawingController.initializeToolbar(containerDrawingToolbar);
        drawingController.observeShapes(this);
        
        // コールバック設定
        drawingController.setCallback(new DrawingController.DrawingControllerCallback() {
            @Override
            public void onDrawingStarted(DrawingMode mode) {
                // 作図中は計測ボタンを無効化
                buttonMeasure.setEnabled(false);
                buttonStartStop.setEnabled(false);
            }

            @Override
            public void onDrawingCancelled() {
                // 作図キャンセル時に計測ボタンを有効化
                buttonMeasure.setEnabled(true);
                buttonStartStop.setEnabled(true);
            }

            @Override
            public void onShapeSaved(com.visionoid.magplotter.data.model.DrawingShape shape) {
                // 作図完了時に計測ボタンを有効化
                buttonMeasure.setEnabled(true);
                buttonStartStop.setEnabled(true);
            }
        });
    }

    /**
     * レイヤーデータを読み込み
     * 
     * @param layerType レイヤータイプ
     */
    private void loadLayerData(LayerType layerType) {
        Log.d("MeasurementActivity", "loadLayerData開始: " + layerType.getId());
        Toast.makeText(this, "レイヤー読み込み中: " + getString(layerType.getNameResId()), Toast.LENGTH_SHORT).show();
        
        layerDataRepository.getLayerData(layerType, false, new LayerDataRepository.DataCallback() {
            @Override
            public void onSuccess(String geoJson, boolean fromCache) {
                final int geoJsonSize = geoJson != null ? geoJson.length() : 0;
                Log.d("MeasurementActivity", "レイヤーデータ取得成功: " + layerType.getId() + 
                        ", fromCache=" + fromCache + ", size=" + geoJsonSize);
                
                runOnUiThread(() -> {
                    // デバッグ: データサイズを表示
                    Toast.makeText(MeasurementActivity.this, 
                            "GeoJSON取得: " + (geoJsonSize / 1024) + "KB", 
                            Toast.LENGTH_SHORT).show();
                    
                    if (geoJson == null || geoJsonSize == 0) {
                        Toast.makeText(MeasurementActivity.this, 
                                "エラー: GeoJSONが空です", Toast.LENGTH_LONG).show();
                        mapLayerManager.setLayerVisibility(layerType, false);
                        return;
                    }
                    
                    mapLayerManager.addLayer(layerType, geoJson);
                    
                    // デバッグ: パース結果を表示
                    boolean isLoaded = mapLayerManager.isLayerLoaded(layerType);
                    Object center = mapLayerManager.getLayerCenter(layerType);
                    int polygonCount = mapLayerManager.getLayerPolygonCount(layerType);
                    
                    Toast.makeText(MeasurementActivity.this, 
                            "パース結果: loaded=" + isLoaded + ", center=" + (center != null) + ", polygons=" + polygonCount, 
                            Toast.LENGTH_LONG).show();
                    
                    // レイヤーにデータがあるか確認
                    if (isLoaded && center != null && polygonCount > 0) {
                        Toast.makeText(MeasurementActivity.this, 
                                getString(layerType.getNameResId()) + " 読み込み完了 (" + polygonCount + "件)", 
                                Toast.LENGTH_SHORT).show();
                    } else {
                        // データが空の場合
                        showNoLayerDataDialog(layerType);
                        mapLayerManager.setLayerVisibility(layerType, false);
                    }
                });
            }

            @Override
            public void onError(String error) {
                Log.e("MeasurementActivity", "レイヤーデータ取得失敗: " + layerType.getId() + ", error=" + error);
                runOnUiThread(() -> {
                    Toast.makeText(MeasurementActivity.this, 
                            "読み込みエラー: " + error, 
                            Toast.LENGTH_LONG).show();
                    // 読み込み失敗時は表示状態をOFFに戻す
                    mapLayerManager.setLayerVisibility(layerType, false);
                });
            }

            @Override
            public void onProgress(int progress) {
                // 進捗表示（必要に応じて実装）
            }
        });
    }

    /**
     * ViewModelを設定
     */
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MeasurementViewModel.class);

        // ミッションを監視
        viewModel.getMission(missionId).observe(this, mission -> {
            if (mission != null) {
                currentMission = mission;
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(mission.getLocationName());
                }
            }
        });

        // 計測ポイントを監視
        viewModel.getPoints(missionId).observe(this, points -> {
            updatePointCount(points != null ? points.size() : 0);
            updateHeatmap(points);
            // 統計を更新
            viewModel.updateStatistics(points);
            
            // 初回のみ: 最初の計測点があればその位置にセンタリング
            if (!isInitialCenterSet && points != null && !points.isEmpty()) {
                MeasurementPoint firstPoint = points.get(0);
                GeoPoint firstPointLocation = new GeoPoint(
                        firstPoint.getLatitude(), 
                        firstPoint.getLongitude()
                );
                mapController.setCenter(firstPointLocation);
                isInitialCenterSet = true;
            }
        });
        
        // 統計データを監視
        viewModel.getMagStatistics().observe(this, statistics -> {
            if (statistics != null) {
                updateStatisticsDisplay(statistics);
            }
        });
    }

    /**
     * リスナーを設定
     */
    private void setupListeners() {
        // 自動モード切り替え
        switchAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            seekBarInterval.setEnabled(isChecked);
            buttonMeasure.setVisibility(isChecked ? View.GONE : View.VISIBLE);
            buttonStartStop.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        // インターバルスライダー
        seekBarInterval.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // 0.1秒 〜 2秒 (progress: 0-19)
                measurementIntervalMs = 100 + (progress * 100);
                updateIntervalDisplay(measurementIntervalMs);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // 手動計測ボタン
        buttonMeasure.setOnClickListener(v -> recordMeasurement(MeasurementPoint.MODE_MANUAL));

        // 自動計測開始/停止ボタン
        buttonStartStop.setOnClickListener(v -> {
            if (isAutoMeasuring) {
                stopAutoMeasurement();
            } else {
                startAutoMeasurement();
            }
        });
    }

    /**
     * 自動計測を開始
     */
    private void startAutoMeasurement() {
        if (currentLocation == null) {
            Toast.makeText(this, R.string.measurement_waiting_gps, Toast.LENGTH_SHORT).show();
            return;
        }

        isAutoMeasuring = true;
        buttonStartStop.setText(R.string.measurement_stop);
        buttonStartStop.setBackgroundResource(R.drawable.bg_button_danger);
        switchAutoMode.setEnabled(false);
        seekBarInterval.setEnabled(false);

        measurementRunnable = new Runnable() {
            @Override
            public void run() {
                if (isAutoMeasuring) {
                    recordMeasurement(MeasurementPoint.MODE_AUTO);
                    measurementHandler.postDelayed(this, measurementIntervalMs);
                }
            }
        };
        measurementHandler.post(measurementRunnable);
    }

    /**
     * 自動計測を停止
     */
    private void stopAutoMeasurement() {
        isAutoMeasuring = false;
        buttonStartStop.setText(R.string.measurement_start);
        buttonStartStop.setBackgroundResource(R.drawable.bg_button_primary);
        switchAutoMode.setEnabled(true);
        seekBarInterval.setEnabled(true);

        if (measurementRunnable != null) {
            measurementHandler.removeCallbacks(measurementRunnable);
        }
    }

    /**
     * 計測データを記録
     * 
     * @param mode 計測モード（AUTO / MANUAL）
     */
    private void recordMeasurement(String mode) {
        if (currentLocation == null) {
            Toast.makeText(this, R.string.measurement_waiting_gps, Toast.LENGTH_SHORT).show();
            return;
        }

        if (currentMission == null) {
            return;
        }

        MeasurementPoint point = new MeasurementPoint(
                missionId,
                currentLocation.getLatitude(),
                currentLocation.getLongitude(),
                currentAccuracy,
                magneticValues[0],
                magneticValues[1],
                magneticValues[2],
                currentMission.getReferenceMag(),
                mode
        );

        viewModel.insertPoint(point);

        // フィードバック
        if (mode.equals(MeasurementPoint.MODE_MANUAL)) {
            Toast.makeText(this, "Point recorded", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ヒートマップを更新
     * 
     * @param points 計測ポイントリスト
     */
    private void updateHeatmap(List<MeasurementPoint> points) {
        if (points == null || points.isEmpty() || currentMission == null) {
            return;
        }

        // 既存のヒートマップを削除
        for (Polygon polygon : heatmapPolygons) {
            mapView.getOverlays().remove(polygon);
        }
        heatmapPolygons.clear();

        // ノイズ値でソート（低い順 → 高いノイズ値が上のレイヤーになる）
        List<MeasurementPoint> sortedPoints = new ArrayList<>(points);
        sortedPoints.sort((p1, p2) -> Double.compare(p1.getNoiseValue(), p2.getNoiseValue()));

        // 各ポイントに円を描画（直径1m = 半径0.5m、縁をぼかすグラデーション効果）
        for (MeasurementPoint point : sortedPoints) {
            int baseColor = getHeatmapColor(point.getNoiseValue());
            GeoPoint center = new GeoPoint(point.getLatitude(), point.getLongitude());
            
            // グラデーション効果：外側から内側へ複数の円を重ねる
            double baseRadius = 0.5; // 半径0.5メートル（直径1m）
            int layers = 5; // レイヤー数
            
            for (int layer = layers - 1; layer >= 0; layer--) {
                // 外側ほど大きく、透明度が高い
                double layerRadius = baseRadius * (1.0 + layer * 0.3);
                int alpha = (int) (Color.alpha(baseColor) * (1.0 - layer * 0.2));
                int layerColor = Color.argb(
                        Math.max(alpha, 20),
                        Color.red(baseColor),
                        Color.green(baseColor),
                        Color.blue(baseColor)
                );
                
                Polygon circle = createCircle(center, layerRadius, layerColor);
                heatmapPolygons.add(circle);
                mapView.getOverlays().add(circle);
            }
        }

        // 現在位置マーカーを最前面に
        mapView.getOverlays().remove(currentLocationMarker);
        mapView.getOverlays().add(currentLocationMarker);

        mapView.invalidate();
    }

    /**
     * ノイズ値からヒートマップの色を取得
     * 
     * @param noiseValue ノイズ値
     * @return 色（ARGB）
     */
    private int getHeatmapColor(double noiseValue) {
        if (currentMission == null) {
            return Color.argb(128, 0, 255, 136); // 緑
        }

        double safeThreshold = currentMission.getSafeThreshold();
        double dangerThreshold = currentMission.getDangerThreshold();

        if (noiseValue < safeThreshold) {
            // 安全（緑）
            return Color.argb(128, 0, 255, 136);
        } else if (noiseValue < dangerThreshold) {
            // 警告（黄）
            float ratio = (float) ((noiseValue - safeThreshold) / (dangerThreshold - safeThreshold));
            int r = (int) (255 * ratio);
            int g = (int) (255 * (1 - ratio * 0.5));
            return Color.argb(128, r, g, 61);
        } else {
            // 危険（赤）
            return Color.argb(160, 255, 0, 85);
        }
    }

    /**
     * 地図上に円を作成
     * 
     * @param center 中心座標
     * @param radiusMeters 半径（メートル）
     * @param color 塗りつぶし色
     * @return Polygonオブジェクト
     */
    private Polygon createCircle(GeoPoint center, double radiusMeters, int color) {
        int numberOfPoints = 32;
        List<GeoPoint> circlePoints = new ArrayList<>();

        for (int i = 0; i < numberOfPoints; i++) {
            double angle = Math.toRadians((360.0 / numberOfPoints) * i);
            double dx = radiusMeters * Math.cos(angle);
            double dy = radiusMeters * Math.sin(angle);
            double lat = center.getLatitude() + (dy / 111320.0);
            double lng = center.getLongitude() + (dx / (111320.0 * Math.cos(Math.toRadians(center.getLatitude()))));
            circlePoints.add(new GeoPoint(lat, lng));
        }

        Polygon polygon = new Polygon();
        polygon.setPoints(circlePoints);
        polygon.setFillColor(color);
        polygon.setStrokeColor(Color.TRANSPARENT);
        polygon.setStrokeWidth(0);

        return polygon;
    }

    /**
     * 位置情報UIを更新
     */
    private void updateLocationUI() {
        if (currentLocation != null) {
            textGpsStatus.setText(String.format(Locale.US, "%.6f, %.6f",
                    currentLocation.getLatitude(), currentLocation.getLongitude()));
            textAccuracy.setText(String.format(Locale.US, "%.1fm", currentAccuracy));
            textGpsStatus.setTextColor(getColor(R.color.status_safe));
        } else {
            textGpsStatus.setText(R.string.measurement_waiting_gps);
            textGpsStatus.setTextColor(getColor(R.color.status_warning));
        }
    }
    
    /**
     * サテライト数表示を更新
     * 
     * 形式: "使用中/捕捉中" (例: "8/12")
     */
    private void updateSatelliteDisplay() {
        if (textSatelliteCount == null) return;
        
        if (satelliteCount > 0) {
            // 使用中/捕捉中 の形式で表示
            textSatelliteCount.setText(String.format(Locale.US, "%d/%d", 
                    usedSatelliteCount, satelliteCount));
            
            // サテライト数に応じて色を変更
            if (usedSatelliteCount >= 6) {
                // 良好（6衛星以上で精度良好）
                textSatelliteCount.setTextColor(getColor(R.color.status_safe));
            } else if (usedSatelliteCount >= 4) {
                // 測位可能（4衛星以上で3D測位）
                textSatelliteCount.setTextColor(getColor(R.color.status_warning));
            } else {
                // 測位困難
                textSatelliteCount.setTextColor(getColor(R.color.status_danger));
            }
        } else {
            textSatelliteCount.setText("--");
            textSatelliteCount.setTextColor(getColor(R.color.text_secondary));
        }
    }

    /**
     * 地図上の現在位置を更新
     */
    private void updateMapLocation() {
        if (currentLocation != null) {
            GeoPoint geoPoint = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            currentLocationMarker.setPosition(geoPoint);

            // 初回のみセンタリング（計測点がない場合のみ現在位置にセンタリング）
            // 計測点がある場合は setupViewModel() で最初の計測点にセンタリング済み
            if (!isInitialCenterSet && 
                mapView.getMapCenter().getLatitude() == 0 && 
                mapView.getMapCenter().getLongitude() == 0) {
                mapController.setCenter(geoPoint);
                isInitialCenterSet = true;
            }

            mapView.invalidate();
        }
    }

    /**
     * インターバル表示を更新
     * 
     * @param intervalMs インターバル（ミリ秒）
     */
    private void updateIntervalDisplay(int intervalMs) {
        textIntervalValue.setText(String.format(Locale.US, "%.1fs", intervalMs / 1000.0));
    }

    /**
     * ポイント数表示を更新
     * 
     * @param count ポイント数
     */
    private void updatePointCount(int count) {
        textPointCount.setText(String.format(Locale.US, getString(R.string.mission_points_count), count));
    }
    
    /**
     * 統計表示を更新
     * 
     * @param statistics 磁場統計データ
     */
    private void updateStatisticsDisplay(MeasurementViewModel.MagStatistics statistics) {
        if (statistics.pointCount == 0) {
            // データがない場合は "--" を表示
            textMagMax.setText("--");
            textMagAvg.setText("--");
            textNoiseMax.setText("--");
            textNoiseAvg.setText("--");
        } else {
            // MAG FIELDの統計
            textMagMax.setText(String.format(Locale.US, "%.1f", statistics.magFieldMax));
            textMagAvg.setText(String.format(Locale.US, "%.1f", statistics.magFieldAvg));
            
            // NOISEの統計
            textNoiseMax.setText(String.format(Locale.US, "%.1f", statistics.noiseMax));
            textNoiseAvg.setText(String.format(Locale.US, "%.1f", statistics.noiseAvg));
            
            // NOISEのMAXに応じて色を変更
            if (currentMission != null) {
                if (statistics.noiseMax < currentMission.getSafeThreshold()) {
                    textNoiseMax.setTextColor(getColor(R.color.status_safe));
                } else if (statistics.noiseMax < currentMission.getDangerThreshold()) {
                    textNoiseMax.setTextColor(getColor(R.color.status_warning));
                } else {
                    textNoiseMax.setTextColor(getColor(R.color.status_danger));
                }
            }
        }
    }

    /**
     * 磁場値UIを更新
     */
    private void updateMagUI() {
        textMagValue.setText(String.format(Locale.US, "%.1f", currentMagStrength));
        textNoiseValue.setText(String.format(Locale.US, "%.1f", currentNoise));

        // ノイズレベルに応じた色分け
        if (currentMission != null) {
            // レベルゲージを更新
            noiseLevelGauge.setThresholds(
                    currentMission.getSafeThreshold(),
                    currentMission.getDangerThreshold()
            );
            noiseLevelGauge.setNoiseValue(currentNoise);
            
            if (currentNoise < currentMission.getSafeThreshold()) {
                textNoiseValue.setTextColor(getColor(R.color.status_safe));
                panelStatus.setBackgroundResource(R.drawable.bg_status_safe);
            } else if (currentNoise < currentMission.getDangerThreshold()) {
                textNoiseValue.setTextColor(getColor(R.color.status_warning));
                panelStatus.setBackgroundResource(R.drawable.bg_status_warning);
            } else {
                textNoiseValue.setTextColor(getColor(R.color.status_danger));
                panelStatus.setBackgroundResource(R.drawable.bg_status_danger);
            }
        }
    }

    /**
     * スクリーンショットを保存
     */
    private void saveScreenshot() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            return;
        }

        // 地図ビューのスクリーンショットを取得
        mapView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(mapView.getDrawingCache());
        mapView.setDrawingCacheEnabled(false);

        // ファイル名
        String fileName = "MAGPLOT_" + dateFormat.format(new Date()) + ".png";

        // 保存
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/VisionoidMagPlotter");

            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri != null) {
                try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                Toast.makeText(this, R.string.success_screenshot_saved, Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, R.string.error_screenshot_failed, Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    // ==================== SensorEventListener ====================

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magneticValues[0] = event.values[0];
            magneticValues[1] = event.values[1];
            magneticValues[2] = event.values[2];

            // 総磁場強度を計算
            currentMagStrength = Math.sqrt(
                    magneticValues[0] * magneticValues[0] +
                    magneticValues[1] * magneticValues[1] +
                    magneticValues[2] * magneticValues[2]
            );

            // ノイズ値を計算
            if (currentMission != null) {
                currentNoise = Math.abs(currentMagStrength - currentMission.getReferenceMag());
            }

            updateMagUI();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // 必要に応じて精度変更を処理
    }

    // ==================== ライフサイクル ====================

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();

        // センサー登録
        if (magneticSensor != null) {
            sensorManager.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI);
        }

        // 位置情報取得開始
        startLocationUpdates();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();

        // センサー解除
        sensorManager.unregisterListener(this);

        // 位置情報取得停止
        stopLocationUpdates();

        // 自動計測停止
        if (isAutoMeasuring) {
            stopAutoMeasurement();
        }
    }

    /**
     * 位置情報更新を開始
     */
    private void startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setWaitForAccurateLocation(false)
                .setMinUpdateIntervalMillis(500)
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
        
        // GNSSステータス監視を開始
        startGnssStatusUpdates();
    }

    /**
     * 位置情報更新を停止
     */
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
        
        // GNSSステータス監視を停止
        stopGnssStatusUpdates();
    }
    
    /**
     * GNSSステータス監視を開始
     */
    private void startGnssStatusUpdates() {
        if (locationManager == null || gnssStatusCallback == null) {
            return;
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        
        try {
            locationManager.registerGnssStatusCallback(gnssStatusCallback, new Handler(Looper.getMainLooper()));
            Log.d("MeasurementActivity", "GNSSステータス監視開始");
        } catch (Exception e) {
            Log.e("MeasurementActivity", "GNSSステータス監視開始に失敗: " + e.getMessage());
        }
    }
    
    /**
     * GNSSステータス監視を停止
     */
    private void stopGnssStatusUpdates() {
        if (locationManager == null || gnssStatusCallback == null) {
            return;
        }
        
        try {
            locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            Log.d("MeasurementActivity", "GNSSステータス監視停止");
        } catch (Exception e) {
            Log.e("MeasurementActivity", "GNSSステータス監視停止に失敗: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates();
            } else {
                Toast.makeText(this, R.string.error_permission_location, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                saveScreenshot();
            } else {
                Toast.makeText(this, R.string.error_permission_storage, Toast.LENGTH_LONG).show();
            }
        }
    }

    // ==================== メニュー ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_measurement, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (id == R.id.action_screenshot) {
            saveScreenshot();
            return true;
        } else if (id == R.id.action_center) {
            if (currentLocation != null) {
                GeoPoint geoPoint = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
                mapController.animateTo(geoPoint);
            }
            return true;
        } else if (id == R.id.action_map_type) {
            showMapTypeDialog();
            return true;
        } else if (id == R.id.action_layers) {
            showLayerSelectionDialog();
            return true;
        } else if (id == R.id.action_draw_polygon) {
            drawingController.startPolygonDrawing();
            return true;
        } else if (id == R.id.action_draw_polyline) {
            drawingController.startPolylineDrawing();
            return true;
        } else if (id == R.id.action_draw_circle) {
            drawingController.startCircleDrawing();
            return true;
        } else if (id == R.id.action_shape_list) {
            drawingController.showShapeListDialog();
            return true;
        } else if (id == R.id.action_gps_source_auto) {
            setGpsSource(GpsSourceType.AUTO);
            return true;
        } else if (id == R.id.action_gps_source_internal) {
            setGpsSource(GpsSourceType.INTERNAL);
            return true;
        } else if (id == R.id.action_gps_source_usb) {
            setGpsSource(GpsSourceType.USB);
            return true;
        } else if (id == R.id.action_mag_source_internal) {
            setMagneticSource(false);
            return true;
        } else if (id == R.id.action_mag_source_usb) {
            setMagneticSource(true);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * GPSソースを設定
     * @param sourceType GPSソース種別
     */
    private void setGpsSource(GpsSourceType sourceType) {
        try {
            currentGpsSource = sourceType;
            Log.d("MeasurementActivity", "GPSソースを変更: " + sourceType.getDisplayName());
            
            switch (sourceType) {
                case AUTO:
                    // 自動モード：USB GPSがあれば使用、なければ内蔵GPS
                    if (usbGpsManager != null) {
                        try {
                            if (usbGpsManager.hasUsbGpsDevice() && !isUsbGpsConnected) {
                                usbGpsManager.connect();
                            }
                        } catch (Exception e) {
                            Log.e("MeasurementActivity", "USB GPS接続エラー: " + e.getMessage());
                        }
                    }
                    Toast.makeText(this, R.string.gps_source_auto, Toast.LENGTH_SHORT).show();
                    break;
                    
                case INTERNAL:
                    // 内蔵GPSのみ使用
                    if (usbGpsManager != null && isUsbGpsConnected) {
                        try {
                            usbGpsManager.disconnect();
                        } catch (Exception e) {
                            Log.e("MeasurementActivity", "USB GPS切断エラー: " + e.getMessage());
                        }
                    }
                    if (panelRtkInfo != null) {
                        panelRtkInfo.setVisibility(View.GONE);
                    }
                    Toast.makeText(this, R.string.gps_source_internal, Toast.LENGTH_SHORT).show();
                    break;
                    
                case USB:
                    // USB GPSのみ使用
                    if (usbGpsManager != null) {
                        try {
                            if (usbGpsManager.hasUsbGpsDevice()) {
                                if (!isUsbGpsConnected) {
                                    usbGpsManager.connect();
                                }
                                if (panelRtkInfo != null) {
                                    panelRtkInfo.setVisibility(View.VISIBLE);
                                }
                            } else {
                                Toast.makeText(this, R.string.gps_usb_not_found, Toast.LENGTH_LONG).show();
                            }
                        } catch (Exception e) {
                            Log.e("MeasurementActivity", "USB GPS接続エラー: " + e.getMessage());
                            Toast.makeText(this, "USB GPS接続エラー: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Toast.makeText(this, R.string.gps_usb_not_found, Toast.LENGTH_LONG).show();
                    }
                    break;
            }
            
            updateGpsSourceDisplay();
        } catch (Exception e) {
            Log.e("MeasurementActivity", "GPSソース設定エラー: " + e.getMessage(), e);
            Toast.makeText(this, "GPSソース設定エラー", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 磁気センサーソースを設定
     * @param useUsb USB GPS磁気センサーを使用する場合true
     */
    private void setMagneticSource(boolean useUsb) {
        try {
            if (useUsb) {
                // USB GPS磁気センサーを使用
                if (usbGpsManager != null && isUsbGpsConnected) {
                    useUsbMagneticSensor = true;
                    usbGpsManager.enableMagneticSensor();
                    Toast.makeText(this, R.string.mag_usb_enabled, Toast.LENGTH_SHORT).show();
                    Log.d("MeasurementActivity", "磁気センサーソース: USB GPS (IST8310)");
                } else {
                    Toast.makeText(this, R.string.mag_usb_not_connected, Toast.LENGTH_LONG).show();
                }
            } else {
                // 内蔵センサーを使用
                useUsbMagneticSensor = false;
                Toast.makeText(this, R.string.mag_source_internal, Toast.LENGTH_SHORT).show();
                Log.d("MeasurementActivity", "磁気センサーソース: 内蔵センサー");
            }
        } catch (Exception e) {
            Log.e("MeasurementActivity", "磁気センサーソース設定エラー: " + e.getMessage(), e);
            Toast.makeText(this, "磁気センサー設定エラー", Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 地図タイプ選択ダイアログを表示
     */
    private void showMapTypeDialog() {
        String[] mapTypes = {
                getString(R.string.map_type_standard),
                getString(R.string.map_type_satellite),
                getString(R.string.map_type_terrain)
        };
        
        new MaterialAlertDialogBuilder(this, R.style.SpyTech_Dialog)
                .setTitle(R.string.action_map_type)
                .setSingleChoiceItems(mapTypes, currentMapType, (dialog, which) -> {
                    currentMapType = which;
                    updateMapTileSource();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
    
    /**
     * 地図タイルソースを更新
     */
    private void updateMapTileSource() {
        switch (currentMapType) {
            case 0: // 標準地図
                mapView.setTileSource(TileSourceFactory.MAPNIK);
                Toast.makeText(this, R.string.map_type_standard, Toast.LENGTH_SHORT).show();
                break;
            case 1: // 衛星写真
                mapView.setTileSource(ESRI_WORLD_IMAGERY);
                Toast.makeText(this, R.string.map_type_satellite, Toast.LENGTH_SHORT).show();
                break;
            case 2: // 地形図
                mapView.setTileSource(OPEN_TOPO_MAP);
                Toast.makeText(this, R.string.map_type_terrain, Toast.LENGTH_SHORT).show();
                break;
        }
        mapView.invalidate();
    }

    /**
     * レイヤー選択ダイアログを表示
     */
    private void showLayerSelectionDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_layer_selection, null);

        // スイッチを取得
        SwitchMaterial switchDid = dialogView.findViewById(R.id.switch_layer_did);
        SwitchMaterial switchAirport = dialogView.findViewById(R.id.switch_layer_airport);
        SwitchMaterial switchNoFly = dialogView.findViewById(R.id.switch_layer_no_fly);
        Spinner spinnerStyle = dialogView.findViewById(R.id.spinner_layer_style);

        // 現在の状態を設定
        switchDid.setChecked(mapLayerManager.isLayerVisible(LayerType.DID));
        switchAirport.setChecked(mapLayerManager.isLayerVisible(LayerType.AIRPORT_RESTRICTION));
        switchNoFly.setChecked(mapLayerManager.isLayerVisible(LayerType.NO_FLY_ZONE));

        // スタイル選択スピナーを設定
        String[] styleNames = {
                getString(R.string.layer_style_filled),
                getString(R.string.layer_style_border),
                getString(R.string.layer_style_hatched)
        };
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, styleNames);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStyle.setAdapter(styleAdapter);

        // 現在のスタイルを選択
        LayerDisplayStyle currentStyle = mapLayerManager.getDisplayStyle();
        spinnerStyle.setSelection(currentStyle.ordinal());

        // スタイル変更リスナー
        spinnerStyle.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                LayerDisplayStyle newStyle = LayerDisplayStyle.values()[position];
                if (newStyle != mapLayerManager.getDisplayStyle()) {
                    mapLayerManager.setDisplayStyle(newStyle);
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        // ダイアログを表示
        new MaterialAlertDialogBuilder(this, R.style.SpyTech_Dialog)
                .setTitle(R.string.layer_title)
                .setView(dialogView)
                .setPositiveButton(R.string.action_confirm, (dialog, which) -> {
                    // レイヤー表示状態を更新
                    updateLayerVisibility(LayerType.DID, switchDid.isChecked());
                    updateLayerVisibility(LayerType.AIRPORT_RESTRICTION, switchAirport.isChecked());
                    updateLayerVisibility(LayerType.NO_FLY_ZONE, switchNoFly.isChecked());
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * レイヤーの表示状態を更新
     * 
     * @param layerType レイヤータイプ
     * @param visible 表示するかどうか
     */
    private void updateLayerVisibility(LayerType layerType, boolean visible) {
        boolean currentlyVisible = mapLayerManager.isLayerVisible(layerType);
        
        if (visible && !currentlyVisible) {
            // 表示ONにする場合、データがなければ読み込む
            mapLayerManager.setLayerVisibility(layerType, true);
            if (!mapLayerManager.isLayerLoaded(layerType)) {
                loadLayerData(layerType);
            }
        } else if (!visible && currentlyVisible) {
            // 表示OFFにする
            mapLayerManager.setLayerVisibility(layerType, false);
        }
    }

    /**
     * レイヤーの範囲にズームするか確認するダイアログを表示
     * 
     * @param layerType レイヤータイプ
     */
    private void showZoomToLayerDialog(LayerType layerType) {
        new MaterialAlertDialogBuilder(this, R.style.SpyTech_Dialog)
                .setTitle(getString(layerType.getNameResId()))
                .setMessage("レイヤーの表示位置に移動しますか？")
                .setPositiveButton("移動", (dialog, which) -> {
                    mapLayerManager.zoomToLayer(layerType);
                })
                .setNegativeButton("現在位置のまま", null)
                .show();
    }

    /**
     * レイヤーデータがない場合のダイアログを表示
     * 
     * @param layerType レイヤータイプ
     */
    private void showNoLayerDataDialog(LayerType layerType) {
        String message = getString(layerType.getNameResId()) + "のデータがありません。\n\n" +
                "正確なデータを表示するには、公式データをダウンロードしてアプリに組み込む必要があります。\n\n" +
                "詳細は assets/layers/README.md を参照してください。";
        
        new MaterialAlertDialogBuilder(this, R.style.SpyTech_Dialog)
                .setTitle(R.string.layer_no_data)
                .setMessage(message)
                .setPositiveButton(R.string.action_confirm, null)
                .show();
    }

    /**
     * 戻るボタンの処理
     * 
     * 作図中の場合はキャンセルする。
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        // 作図中の場合はキャンセル
        if (drawingController != null && drawingController.isDrawing()) {
            drawingController.cancelDrawing();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (layerDataRepository != null) {
            layerDataRepository.shutdown();
        }
        // USB GPSリソースを解放
        if (usbGpsManager != null) {
            usbGpsManager.release();
            usbGpsManager = null;
        }
    }
}

