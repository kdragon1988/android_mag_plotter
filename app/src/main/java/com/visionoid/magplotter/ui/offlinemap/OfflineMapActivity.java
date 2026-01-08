/**
 * OfflineMapActivity.java
 * 
 * VISIONOID MAG PLOTTER - オフライン地図管理画面
 * 
 * 概要:
 *   オフライン使用のための地図タイルをダウンロード・管理する画面。
 *   計測現場でインターネット接続がない場合に備えて、
 *   事前に地図データをダウンロードしておく。
 * 
 * 主な仕様:
 *   - 地図上でエリアを選択
 *   - 選択エリアのタイルをダウンロード
 *   - ダウンロード済みタイルの確認・削除
 * 
 * 制限事項:
 *   - osmdroidのタイルキャッシュ機能を使用
 *   - ズームレベル15-19のタイルをダウンロード
 */
package com.visionoid.magplotter.ui.offlinemap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.visionoid.magplotter.R;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.modules.SqliteArchiveTileWriter;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Polygon;

import java.io.File;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * オフライン地図管理画面アクティビティ
 */
public class OfflineMapActivity extends AppCompatActivity {

    /** パーミッションリクエストコード */
    private static final int REQUEST_LOCATION_PERMISSION = 1001;

    /** ダウンロードズームレベル範囲 */
    private static final int MIN_ZOOM = 15;
    private static final int MAX_ZOOM = 19;

    // 位置情報
    private FusedLocationProviderClient fusedLocationClient;

    // 地図関連
    private MapView mapView;
    private IMapController mapController;
    private CacheManager cacheManager;
    private Polygon selectionOverlay;
    private BoundingBox selectedArea;

    // UI要素
    private Button buttonDownload;
    private Button buttonClear;
    private TextView textStatus;
    private TextView textCacheSize;
    private ProgressBar progressBar;
    private View selectionInstructions;

    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isDownloading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_map);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        setupToolbar();
        initializeViews();
        initializeMap();
        updateCacheInfo();
    }

    /**
     * ツールバーを設定
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_offline_map);
        }
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        mapView = findViewById(R.id.map_view);
        buttonDownload = findViewById(R.id.button_download);
        buttonClear = findViewById(R.id.button_clear);
        textStatus = findViewById(R.id.text_status);
        textCacheSize = findViewById(R.id.text_cache_size);
        progressBar = findViewById(R.id.progress_bar);
        selectionInstructions = findViewById(R.id.selection_instructions);

        buttonDownload.setOnClickListener(v -> startDownload());
        buttonClear.setOnClickListener(v -> showClearConfirmDialog());
    }

    /**
     * 地図を初期化
     */
    private void initializeMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapController = mapView.getController();
        mapController.setZoom(15.0);

        // キャッシュマネージャー初期化
        cacheManager = new CacheManager(mapView);

        // 選択オーバーレイ
        selectionOverlay = new Polygon();
        selectionOverlay.setFillColor(Color.argb(50, 0, 245, 255));
        selectionOverlay.setStrokeColor(Color.argb(200, 0, 245, 255));
        selectionOverlay.setStrokeWidth(2);
        mapView.getOverlays().add(selectionOverlay);

        // 地図の移動を監視して選択エリアを更新
        mapView.addOnFirstLayoutListener((v, left, top, right, bottom) -> {
            updateSelectionOverlay();
            moveToCurrentLocation();
        });

        // スクロール/ズーム時にオーバーレイを更新
        mapView.setOnTouchListener((v, event) -> {
            handler.postDelayed(this::updateSelectionOverlay, 100);
            return false;
        });
    }

    /**
     * 現在位置に移動
     */
    private void moveToCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
            return;
        }

        fusedLocationClient.getLastLocation().addOnSuccessListener(this, location -> {
            if (location != null) {
                GeoPoint geoPoint = new GeoPoint(location.getLatitude(), location.getLongitude());
                mapController.setCenter(geoPoint);
                mapController.setZoom(16.0);
                updateSelectionOverlay();
            }
        });
    }

    /**
     * 選択オーバーレイを更新
     */
    private void updateSelectionOverlay() {
        BoundingBox bbox = mapView.getBoundingBox();
        selectedArea = bbox;

        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(bbox.getLatNorth(), bbox.getLonWest()));
        points.add(new GeoPoint(bbox.getLatNorth(), bbox.getLonEast()));
        points.add(new GeoPoint(bbox.getLatSouth(), bbox.getLonEast()));
        points.add(new GeoPoint(bbox.getLatSouth(), bbox.getLonWest()));

        selectionOverlay.setPoints(points);
        mapView.invalidate();
    }

    /**
     * キャッシュ情報を更新
     */
    private void updateCacheInfo() {
        File cacheDir = Configuration.getInstance().getOsmdroidTileCache();
        if (cacheDir != null && cacheDir.exists()) {
            long size = getDirSize(cacheDir);
            textCacheSize.setText(String.format(getString(R.string.offline_map_size), formatSize(size)));
        } else {
            textCacheSize.setText(String.format(getString(R.string.offline_map_size), "0 KB"));
        }
    }

    /**
     * ダウンロードを開始
     */
    private void startDownload() {
        if (selectedArea == null) {
            Toast.makeText(this, "Please select an area first", Toast.LENGTH_SHORT).show();
            return;
        }

        if (isDownloading) {
            return;
        }

        isDownloading = true;
        buttonDownload.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setProgress(0);
        textStatus.setText(R.string.offline_map_downloading);
        textStatus.setTextColor(getColor(R.color.accent_cyan));

        // タイルダウンロード開始
        cacheManager.downloadAreaAsync(
                this,
                selectedArea,
                MIN_ZOOM,
                MAX_ZOOM,
                new CacheManager.CacheManagerCallback() {
                    @Override
                    public void onTaskComplete() {
                        handler.post(() -> {
                            isDownloading = false;
                            buttonDownload.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            textStatus.setText(R.string.offline_map_complete);
                            textStatus.setTextColor(getColor(R.color.status_safe));
                            updateCacheInfo();
                            Toast.makeText(OfflineMapActivity.this,
                                    R.string.offline_map_complete, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onTaskFailed(int errors) {
                        handler.post(() -> {
                            isDownloading = false;
                            buttonDownload.setEnabled(true);
                            progressBar.setVisibility(View.GONE);
                            textStatus.setText(R.string.offline_map_error);
                            textStatus.setTextColor(getColor(R.color.status_danger));
                            Toast.makeText(OfflineMapActivity.this,
                                    R.string.offline_map_error, Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {
                        handler.post(() -> {
                            progressBar.setProgress(progress);
                            textStatus.setText(String.format(getString(R.string.offline_map_progress), progress));
                        });
                    }

                    @Override
                    public void downloadStarted() {
                        handler.post(() -> {
                            textStatus.setText(R.string.offline_map_downloading);
                        });
                    }

                    @Override
                    public void setPossibleTilesInArea(int total) {
                        handler.post(() -> {
                            progressBar.setMax(100);
                        });
                    }
                }
        );
    }

    /**
     * キャッシュクリア確認ダイアログを表示
     */
    private void showClearConfirmDialog() {
        new AlertDialog.Builder(this, R.style.SpyTech_AlertDialog)
                .setTitle(R.string.offline_map_delete)
                .setMessage("Delete all offline map tiles?")
                .setPositiveButton(R.string.action_delete, (dialog, which) -> clearCache())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * キャッシュをクリア
     */
    private void clearCache() {
        File cacheDir = Configuration.getInstance().getOsmdroidTileCache();
        if (cacheDir != null && cacheDir.exists()) {
            deleteDir(cacheDir);
            updateCacheInfo();
            Toast.makeText(this, "Cache cleared", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * ディレクトリのサイズを取得
     */
    private long getDirSize(File dir) {
        long size = 0;
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        size += getDirSize(file);
                    } else {
                        size += file.length();
                    }
                }
            }
        }
        return size;
    }

    /**
     * ディレクトリを削除
     */
    private void deleteDir(File dir) {
        if (dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDir(file);
                }
            }
        }
        dir.delete();
    }

    /**
     * サイズをフォーマット
     */
    private String formatSize(long size) {
        DecimalFormat df = new DecimalFormat("#.##");
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return df.format(size / 1024.0) + " KB";
        } else if (size < 1024 * 1024 * 1024) {
            return df.format(size / (1024.0 * 1024.0)) + " MB";
        } else {
            return df.format(size / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                moveToCurrentLocation();
            }
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}



