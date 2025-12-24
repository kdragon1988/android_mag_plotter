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
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.preference.PreferenceManager;

import com.google.android.material.textfield.TextInputEditText;
import com.visionoid.magplotter.R;

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
            preferences.edit()
                    .putFloat(PREF_DEFAULT_REFERENCE_MAG, referenceMag)
                    .putFloat(PREF_DEFAULT_SAFE_THRESHOLD, safeThreshold)
                    .putFloat(PREF_DEFAULT_DANGER_THRESHOLD, dangerThreshold)
                    .putInt(PREF_DEFAULT_INTERVAL, intervalMs)
                    .apply();

            Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show();

        } catch (NumberFormatException e) {
            Toast.makeText(this, R.string.error_invalid_value, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * デフォルト値にリセット
     */
    private void resetToDefaults() {
        editReferenceMag.setText(String.valueOf(DEFAULT_REFERENCE_MAG));
        editSafeThreshold.setText(String.valueOf(DEFAULT_SAFE_THRESHOLD));
        editDangerThreshold.setText(String.valueOf(DEFAULT_DANGER_THRESHOLD));
        editInterval.setText(String.valueOf(DEFAULT_INTERVAL / 1000.0f));

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

