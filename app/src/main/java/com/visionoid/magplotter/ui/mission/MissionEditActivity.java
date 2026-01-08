/**
 * MissionEditActivity.java
 * 
 * VISIONOID MAG PLOTTER - ミッション作成/編集画面
 * 
 * 概要:
 *   新規ミッションの作成、または既存ミッションの編集を行う画面。
 *   場所名、担当者、メモ、基準磁場値、閾値を設定可能。
 * 
 * 主な仕様:
 *   - 新規作成モードと編集モードを自動判定
 *   - 入力バリデーション
 *   - 保存時に自動でタイムスタンプ更新
 * 
 * 制限事項:
 *   - 編集モードの場合はEXTRA_MISSION_IDが必要
 */
package com.visionoid.magplotter.ui.mission;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.Mission;

/**
 * ミッション作成/編集画面アクティビティ
 */
public class MissionEditActivity extends AppCompatActivity {

    /** Intent Extra: ミッションID（編集モード用） */
    public static final String EXTRA_MISSION_ID = "extra_mission_id";

    /** ViewModel */
    private MissionEditViewModel viewModel;

    /** 編集モードフラグ */
    private boolean isEditMode = false;

    /** 編集中のミッションID */
    private long missionId = -1;

    /** 編集中のミッション */
    private Mission currentMission;

    // UI要素
    private TextInputLayout layoutLocationName;
    private TextInputLayout layoutOperator;
    private TextInputLayout layoutMemo;
    private TextInputLayout layoutReferenceMag;
    private TextInputLayout layoutSafeThreshold;
    private TextInputLayout layoutDangerThreshold;

    private TextInputEditText editLocationName;
    private TextInputEditText editOperator;
    private TextInputEditText editMemo;
    private TextInputEditText editReferenceMag;
    private TextInputEditText editSafeThreshold;
    private TextInputEditText editDangerThreshold;

    private Button buttonSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mission_edit);

        // IntentからミッションIDを取得
        missionId = getIntent().getLongExtra(EXTRA_MISSION_ID, -1);
        isEditMode = missionId != -1;

        // ツールバー設定
        setupToolbar();

        // UI初期化
        initializeViews();

        // ViewModel初期化
        setupViewModel();

        // 保存ボタンリスナー
        buttonSave.setOnClickListener(v -> saveMission());
    }

    /**
     * ツールバーを設定
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(isEditMode ? R.string.title_mission_edit : R.string.title_mission_create);
        }
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        layoutLocationName = findViewById(R.id.layout_location_name);
        layoutOperator = findViewById(R.id.layout_operator);
        layoutMemo = findViewById(R.id.layout_memo);
        layoutReferenceMag = findViewById(R.id.layout_reference_mag);
        layoutSafeThreshold = findViewById(R.id.layout_safe_threshold);
        layoutDangerThreshold = findViewById(R.id.layout_danger_threshold);

        editLocationName = findViewById(R.id.edit_location_name);
        editOperator = findViewById(R.id.edit_operator);
        editMemo = findViewById(R.id.edit_memo);
        editReferenceMag = findViewById(R.id.edit_reference_mag);
        editSafeThreshold = findViewById(R.id.edit_safe_threshold);
        editDangerThreshold = findViewById(R.id.edit_danger_threshold);

        buttonSave = findViewById(R.id.button_save);

        // デフォルト値を設定
        if (!isEditMode) {
            editReferenceMag.setText("46.0");
            editSafeThreshold.setText("10.0");
            editDangerThreshold.setText("50.0");
        }
    }

    /**
     * ViewModelを設定
     */
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MissionEditViewModel.class);

        // 編集モードの場合、ミッションデータを取得
        if (isEditMode) {
            viewModel.getMission(missionId).observe(this, mission -> {
                if (mission != null) {
                    currentMission = mission;
                    populateFields(mission);
                }
            });
        }
    }

    /**
     * フィールドにミッションデータを反映
     * 
     * @param mission 反映するミッション
     */
    private void populateFields(Mission mission) {
        editLocationName.setText(mission.getLocationName());
        editOperator.setText(mission.getOperatorName());
        editMemo.setText(mission.getMemo());
        editReferenceMag.setText(String.valueOf(mission.getReferenceMag()));
        editSafeThreshold.setText(String.valueOf(mission.getSafeThreshold()));
        editDangerThreshold.setText(String.valueOf(mission.getDangerThreshold()));
    }

    /**
     * ミッションを保存
     */
    private void saveMission() {
        // バリデーション
        if (!validateInput()) {
            return;
        }

        // 入力値を取得
        String locationName = editLocationName.getText().toString().trim();
        String operator = editOperator.getText().toString().trim();
        String memo = editMemo.getText().toString().trim();
        double referenceMag = Double.parseDouble(editReferenceMag.getText().toString().trim());
        double safeThreshold = Double.parseDouble(editSafeThreshold.getText().toString().trim());
        double dangerThreshold = Double.parseDouble(editDangerThreshold.getText().toString().trim());

        if (isEditMode && currentMission != null) {
            // 更新
            currentMission.setLocationName(locationName);
            currentMission.setOperatorName(operator);
            currentMission.setMemo(memo);
            currentMission.setReferenceMag(referenceMag);
            currentMission.setSafeThreshold(safeThreshold);
            currentMission.setDangerThreshold(dangerThreshold);
            viewModel.update(currentMission);
            Toast.makeText(this, R.string.success_mission_saved, Toast.LENGTH_SHORT).show();
        } else {
            // 新規作成
            Mission newMission = new Mission(locationName, operator);
            newMission.setMemo(memo);
            newMission.setReferenceMag(referenceMag);
            newMission.setSafeThreshold(safeThreshold);
            newMission.setDangerThreshold(dangerThreshold);
            viewModel.insert(newMission);
            Toast.makeText(this, R.string.success_mission_created, Toast.LENGTH_SHORT).show();
        }

        finish();
    }

    /**
     * 入力バリデーション
     * 
     * @return バリデーション結果
     */
    private boolean validateInput() {
        boolean isValid = true;

        // 場所名
        String locationName = editLocationName.getText().toString().trim();
        if (TextUtils.isEmpty(locationName)) {
            layoutLocationName.setError(getString(R.string.error_required_field));
            isValid = false;
        } else {
            layoutLocationName.setError(null);
        }

        // 担当者
        String operator = editOperator.getText().toString().trim();
        if (TextUtils.isEmpty(operator)) {
            layoutOperator.setError(getString(R.string.error_required_field));
            isValid = false;
        } else {
            layoutOperator.setError(null);
        }

        // 基準磁場値
        try {
            String refMagStr = editReferenceMag.getText().toString().trim();
            if (TextUtils.isEmpty(refMagStr)) {
                layoutReferenceMag.setError(getString(R.string.error_required_field));
                isValid = false;
            } else {
                double refMag = Double.parseDouble(refMagStr);
                if (refMag <= 0) {
                    layoutReferenceMag.setError(getString(R.string.error_invalid_value));
                    isValid = false;
                } else {
                    layoutReferenceMag.setError(null);
                }
            }
        } catch (NumberFormatException e) {
            layoutReferenceMag.setError(getString(R.string.error_invalid_value));
            isValid = false;
        }

        // 安全閾値
        try {
            String safeStr = editSafeThreshold.getText().toString().trim();
            if (TextUtils.isEmpty(safeStr)) {
                layoutSafeThreshold.setError(getString(R.string.error_required_field));
                isValid = false;
            } else {
                double safe = Double.parseDouble(safeStr);
                if (safe < 0) {
                    layoutSafeThreshold.setError(getString(R.string.error_invalid_value));
                    isValid = false;
                } else {
                    layoutSafeThreshold.setError(null);
                }
            }
        } catch (NumberFormatException e) {
            layoutSafeThreshold.setError(getString(R.string.error_invalid_value));
            isValid = false;
        }

        // 危険閾値
        try {
            String dangerStr = editDangerThreshold.getText().toString().trim();
            if (TextUtils.isEmpty(dangerStr)) {
                layoutDangerThreshold.setError(getString(R.string.error_required_field));
                isValid = false;
            } else {
                double danger = Double.parseDouble(dangerStr);
                if (danger < 0) {
                    layoutDangerThreshold.setError(getString(R.string.error_invalid_value));
                    isValid = false;
                } else {
                    layoutDangerThreshold.setError(null);
                }
            }
        } catch (NumberFormatException e) {
            layoutDangerThreshold.setError(getString(R.string.error_invalid_value));
            isValid = false;
        }

        return isValid;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        // 変更がある場合は確認ダイアログを表示
        if (hasChanges()) {
            new AlertDialog.Builder(this, R.style.SpyTech_AlertDialog)
                    .setTitle(R.string.dialog_exit_confirm)
                    .setMessage(R.string.dialog_exit_message)
                    .setPositiveButton(R.string.action_confirm, (dialog, which) -> super.onBackPressed())
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 変更があるかチェック
     * 
     * @return 変更がある場合true
     */
    private boolean hasChanges() {
        String locationName = editLocationName.getText().toString().trim();
        String operator = editOperator.getText().toString().trim();

        if (isEditMode && currentMission != null) {
            return !locationName.equals(currentMission.getLocationName())
                    || !operator.equals(currentMission.getOperatorName());
        } else {
            return !TextUtils.isEmpty(locationName) || !TextUtils.isEmpty(operator);
        }
    }
}



