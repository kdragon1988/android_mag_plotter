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
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
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
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.MeasurementPoint;
import com.visionoid.magplotter.data.model.Mission;

import org.osmdroid.api.IMapController;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
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

    // UI要素
    private TextView textMagValue;
    private TextView textNoiseValue;
    private TextView textGpsStatus;
    private TextView textAccuracy;
    private TextView textPointCount;
    private TextView textIntervalValue;
    private SwitchMaterial switchAutoMode;
    private SeekBar seekBarInterval;
    private Button buttonMeasure;
    private Button buttonStartStop;
    private View panelStatus;

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
        initializeMap();
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
        switchAutoMode = findViewById(R.id.switch_auto_mode);
        seekBarInterval = findViewById(R.id.seekbar_interval);
        buttonMeasure = findViewById(R.id.button_measure);
        buttonStartStop = findViewById(R.id.button_start_stop);
        panelStatus = findViewById(R.id.panel_status);

        // 初期値設定
        updateIntervalDisplay(measurementIntervalMs);
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
     * 地図上の現在位置を更新
     */
    private void updateMapLocation() {
        if (currentLocation != null) {
            GeoPoint geoPoint = new GeoPoint(currentLocation.getLatitude(), currentLocation.getLongitude());
            currentLocationMarker.setPosition(geoPoint);

            // 初回のみセンタリング
            if (mapView.getMapCenter().getLatitude() == 0 && mapView.getMapCenter().getLongitude() == 0) {
                mapController.setCenter(geoPoint);
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
     * 磁場値UIを更新
     */
    private void updateMagUI() {
        textMagValue.setText(String.format(Locale.US, "%.1f", currentMagStrength));
        textNoiseValue.setText(String.format(Locale.US, "%.1f", currentNoise));

        // ノイズレベルに応じた色分け
        if (currentMission != null) {
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
    }

    /**
     * 位置情報更新を停止
     */
    private void stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback);
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
        }
        return super.onOptionsItemSelected(item);
    }
}

