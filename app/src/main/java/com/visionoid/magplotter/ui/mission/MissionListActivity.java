/**
 * MissionListActivity.java
 * 
 * VISIONOID MAG PLOTTER - ミッション一覧画面
 * 
 * 概要:
 *   アプリのメイン画面。登録済みミッションの一覧を表示し、
 *   新規作成・編集・削除・計測開始の操作を提供。
 * 
 * 主な仕様:
 *   - ミッション一覧をRecyclerViewで表示
 *   - FABで新規ミッション作成
 *   - ミッションタップで計測画面へ遷移
 *   - 長押しで編集・削除メニュー表示
 * 
 * 制限事項:
 *   - スプラッシュ画面からのみ遷移可能
 */
package com.visionoid.magplotter.ui.mission;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.Mission;
import com.visionoid.magplotter.ui.measurement.MeasurementActivity;
import com.visionoid.magplotter.ui.offlinemap.OfflineMapActivity;
import com.visionoid.magplotter.ui.settings.SettingsActivity;

/**
 * ミッション一覧画面アクティビティ
 * 
 * ミッションの一覧表示と各種操作を提供するメイン画面。
 */
public class MissionListActivity extends AppCompatActivity implements MissionAdapter.OnMissionClickListener {

    /** ViewModel */
    private MissionListViewModel viewModel;

    /** アダプター */
    private MissionAdapter adapter;

    // UI要素
    private RecyclerView recyclerView;
    private View emptyView;
    private FloatingActionButton fabCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mission_list);

        // ツールバー設定
        setupToolbar();

        // UI初期化
        initializeViews();

        // ViewModel初期化
        setupViewModel();

        // FABのクリックリスナー
        fabCreate.setOnClickListener(v -> openMissionCreate());
    }

    /**
     * ツールバーを設定
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.title_mission_list);
        }
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        recyclerView = findViewById(R.id.recycler_missions);
        emptyView = findViewById(R.id.empty_view);
        fabCreate = findViewById(R.id.fab_create);

        // RecyclerView設定
        adapter = new MissionAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    /**
     * ViewModelを設定
     */
    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(MissionListViewModel.class);

        // ミッション一覧を監視
        viewModel.getAllMissions().observe(this, missions -> {
            adapter.setMissions(missions);
            
            // 空表示の切り替え
            if (missions == null || missions.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
            }
        });
    }

    /**
     * ミッション作成画面を開く
     */
    private void openMissionCreate() {
        Intent intent = new Intent(this, MissionEditActivity.class);
        startActivity(intent);
    }

    /**
     * ミッション編集画面を開く
     * 
     * @param missionId 編集するミッションのID
     */
    private void openMissionEdit(long missionId) {
        Intent intent = new Intent(this, MissionEditActivity.class);
        intent.putExtra(MissionEditActivity.EXTRA_MISSION_ID, missionId);
        startActivity(intent);
    }

    /**
     * 計測画面を開く
     * 
     * @param missionId 計測するミッションのID
     */
    private void openMeasurement(long missionId) {
        Intent intent = new Intent(this, MeasurementActivity.class);
        intent.putExtra(MeasurementActivity.EXTRA_MISSION_ID, missionId);
        startActivity(intent);
    }

    /**
     * ミッション削除確認ダイアログを表示
     * 
     * @param mission 削除対象のミッション
     */
    private void showDeleteConfirmDialog(Mission mission) {
        new AlertDialog.Builder(this, R.style.SpyTech_AlertDialog)
                .setTitle(R.string.mission_delete_confirm)
                .setMessage(R.string.mission_delete_message)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    viewModel.delete(mission);
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ==================== MissionAdapter.OnMissionClickListener ====================

    @Override
    public void onMissionClick(Mission mission) {
        // ミッションタップ → 計測画面へ
        openMeasurement(mission.getId());
    }

    @Override
    public void onMissionLongClick(Mission mission) {
        // 長押し → アクションメニュー表示
        showMissionActionDialog(mission);
    }

    /**
     * ミッションアクションダイアログを表示
     * 
     * @param mission 対象のミッション
     */
    private void showMissionActionDialog(Mission mission) {
        String[] items = {
                getString(R.string.action_settings), // 編集
                getString(R.string.action_delete)    // 削除
        };

        new AlertDialog.Builder(this, R.style.SpyTech_AlertDialog)
                .setTitle(mission.getLocationName())
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0: // 編集
                            openMissionEdit(mission.getId());
                            break;
                        case 1: // 削除
                            showDeleteConfirmDialog(mission);
                            break;
                    }
                })
                .show();
    }

    // ==================== メニュー ====================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_mission_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        } else if (id == R.id.action_offline_maps) {
            startActivity(new Intent(this, OfflineMapActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}


