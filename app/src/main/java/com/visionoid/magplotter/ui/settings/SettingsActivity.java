/**
 * SettingsActivity.java
 * 
 * VISIONOID MAG PLOTTER - 設定画面
 * 
 * 概要:
 *   アプリの各種設定を行う画面。
 *   デフォルトの閾値設定、計測設定などを変更可能。
 * 
 * 主な仕様:
 *   - 安全閾値のデフォルト設定
 *   - 危険閾値のデフォルト設定
 *   - デフォルト計測間隔の設定
 *   - アプリ情報の表示
 * 
 * 制限事項:
 *   - 設定はSharedPreferencesに保存
 */
package com.visionoid.magplotter.ui.settings;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.AdapterView;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.ui.map.layer.LayerDisplayStyle;
import com.visionoid.magplotter.ui.map.layer.LayerType;
import com.visionoid.magplotter.ui.map.layer.MapLayerManager;

/**
 * 設定画面アクティビティ
 */
public class SettingsActivity extends AppCompatActivity {

    /** SharedPreferencesキー */
    public static final String PREF_DEFAULT_REFERENCE_MAG = "default_reference_mag";
    public static final String PREF_DEFAULT_SAFE_THRESHOLD = "default_safe_threshold";
    public static final String PREF_DEFAULT_DANGER_THRESHOLD = "default_danger_threshold";
    public static final String PREF_DEFAULT_INTERVAL = "default_interval";

    /** デフォルト値 */
    public static final float DEFAULT_REFERENCE_MAG = 46.0f;
    public static final float DEFAULT_SAFE_THRESHOLD = 10.0f;
    public static final float DEFAULT_DANGER_THRESHOLD = 50.0f;
    public static final int DEFAULT_INTERVAL = 1000;

    private SharedPreferences preferences;

    // UI要素
    private TextInputEditText editReferenceMag;
    private TextInputEditText editSafeThreshold;
    private TextInputEditText editDangerThreshold;
    private TextInputEditText editInterval;
    private Button buttonSave;
    private Button buttonReset;
    
    // レイヤー設定UI要素
    private Spinner spinnerLayerStyle;
    private SwitchMaterial switchDefaultDid;
    private SwitchMaterial switchDefaultAirport;
    private SwitchMaterial switchDefaultNoFly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        setupToolbar();
        initializeViews();
        loadSettings();
        setupListeners();
    }

    /**
     * ツールバーを設定
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_settings);
        }
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        editReferenceMag = findViewById(R.id.edit_default_reference_mag);
        editSafeThreshold = findViewById(R.id.edit_default_safe_threshold);
        editDangerThreshold = findViewById(R.id.edit_default_danger_threshold);
        editInterval = findViewById(R.id.edit_default_interval);
        buttonSave = findViewById(R.id.button_save);
        buttonReset = findViewById(R.id.button_reset);
        
        // レイヤー設定
        spinnerLayerStyle = findViewById(R.id.spinner_layer_style);
        switchDefaultDid = findViewById(R.id.switch_default_did);
        switchDefaultAirport = findViewById(R.id.switch_default_airport);
        switchDefaultNoFly = findViewById(R.id.switch_default_no_fly);
        
        // スタイル選択スピナーを設定
        setupLayerStyleSpinner();
    }
    
    /**
     * レイヤースタイル選択スピナーを設定
     */
    private void setupLayerStyleSpinner() {
        String[] styleNames = {
                getString(R.string.layer_style_filled),
                getString(R.string.layer_style_border),
                getString(R.string.layer_style_hatched)
        };
        ArrayAdapter<String> styleAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, styleNames);
        styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerLayerStyle.setAdapter(styleAdapter);
    }

    /**
     * 設定を読み込み
     */
    private void loadSettings() {
        float referenceMag = preferences.getFloat(PREF_DEFAULT_REFERENCE_MAG, DEFAULT_REFERENCE_MAG);
        float safeThreshold = preferences.getFloat(PREF_DEFAULT_SAFE_THRESHOLD, DEFAULT_SAFE_THRESHOLD);
        float dangerThreshold = preferences.getFloat(PREF_DEFAULT_DANGER_THRESHOLD, DEFAULT_DANGER_THRESHOLD);
        int interval = preferences.getInt(PREF_DEFAULT_INTERVAL, DEFAULT_INTERVAL);

        editReferenceMag.setText(String.valueOf(referenceMag));
        editSafeThreshold.setText(String.valueOf(safeThreshold));
        editDangerThreshold.setText(String.valueOf(dangerThreshold));
        editInterval.setText(String.valueOf(interval / 1000.0f));
        
        // レイヤー設定を読み込み
        loadLayerSettings();
    }
    
    /**
     * レイヤー設定を読み込み
     */
    private void loadLayerSettings() {
        // 表示スタイル
        String styleId = preferences.getString(MapLayerManager.PREF_LAYER_STYLE, 
                LayerDisplayStyle.FILLED.getId());
        LayerDisplayStyle currentStyle = LayerDisplayStyle.fromId(styleId);
        spinnerLayerStyle.setSelection(currentStyle.ordinal());
        
        // デフォルト表示状態
        switchDefaultDid.setChecked(
                preferences.getBoolean(LayerType.DID.getVisibilityPrefKey(), false));
        switchDefaultAirport.setChecked(
                preferences.getBoolean(LayerType.AIRPORT_RESTRICTION.getVisibilityPrefKey(), false));
        switchDefaultNoFly.setChecked(
                preferences.getBoolean(LayerType.NO_FLY_ZONE.getVisibilityPrefKey(), false));
    }

    /**
     * リスナーを設定
     */
    private void setupListeners() {
        buttonSave.setOnClickListener(v -> saveSettings());
        buttonReset.setOnClickListener(v -> resetToDefaults());
    }

    /**
     * 設定を保存
     */
    private void saveSettings() {
        try {
            float referenceMag = Float.parseFloat(editReferenceMag.getText().toString().trim());
            float safeThreshold = Float.parseFloat(editSafeThreshold.getText().toString().trim());
            float dangerThreshold = Float.parseFloat(editDangerThreshold.getText().toString().trim());
            float intervalSec = Float.parseFloat(editInterval.getText().toString().trim());
            int intervalMs = (int) (intervalSec * 1000);

            // バリデーション
            if (referenceMag <= 0 || safeThreshold < 0 || dangerThreshold < 0) {
                Toast.makeText(this, R.string.error_invalid_value, Toast.LENGTH_SHORT).show();
                return;
            }

            if (safeThreshold >= dangerThreshold) {
                Toast.makeText(this, "Safe threshold must be less than danger threshold", Toast.LENGTH_SHORT).show();
                return;
            }

            if (intervalMs < 100 || intervalMs > 2000) {
                Toast.makeText(this, "Interval must be between 0.1 and 2.0 seconds", Toast.LENGTH_SHORT).show();
                return;
            }

            // 保存
            SharedPreferences.Editor editor = preferences.edit()
                    .putFloat(PREF_DEFAULT_REFERENCE_MAG, referenceMag)
                    .putFloat(PREF_DEFAULT_SAFE_THRESHOLD, safeThreshold)
                    .putFloat(PREF_DEFAULT_DANGER_THRESHOLD, dangerThreshold)
                    .putInt(PREF_DEFAULT_INTERVAL, intervalMs);
            
            // レイヤー設定を保存
            saveLayerSettings(editor);
            
            editor.apply();

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_value, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * レイヤー設定を保存
     * 
     * @param editor SharedPreferences.Editor
     */
    private void saveLayerSettings(SharedPreferences.Editor editor) {
        // 表示スタイル
        int stylePosition = spinnerLayerStyle.getSelectedItemPosition();
        LayerDisplayStyle selectedStyle = LayerDisplayStyle.values()[stylePosition];
        editor.putString(MapLayerManager.PREF_LAYER_STYLE, selectedStyle.getId());
        
        // デフォルト表示状態
        editor.putBoolean(LayerType.DID.getVisibilityPrefKey(), switchDefaultDid.isChecked());
        editor.putBoolean(LayerType.AIRPORT_RESTRICTION.getVisibilityPrefKey(), switchDefaultAirport.isChecked());
        editor.putBoolean(LayerType.NO_FLY_ZONE.getVisibilityPrefKey(), switchDefaultNoFly.isChecked());
    }

    /**
     * デフォルト値にリセット
     */
    private void resetToDefaults() {
        editReferenceMag.setText(String.valueOf(DEFAULT_REFERENCE_MAG));
        editSafeThreshold.setText(String.valueOf(DEFAULT_SAFE_THRESHOLD));
        editDangerThreshold.setText(String.valueOf(DEFAULT_DANGER_THRESHOLD));
        editInterval.setText(String.valueOf(DEFAULT_INTERVAL / 1000.0f));
        
        // レイヤー設定もリセット
        spinnerLayerStyle.setSelection(LayerDisplayStyle.FILLED.ordinal());
        switchDefaultDid.setChecked(false);
        switchDefaultAirport.setChecked(false);
        switchDefaultNoFly.setChecked(false);

        Toast.makeText(this, "Reset to defaults", Toast.LENGTH_SHORT).show();
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

