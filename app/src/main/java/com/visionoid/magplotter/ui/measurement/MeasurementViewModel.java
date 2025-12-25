/**
 * MeasurementViewModel.java
 * 
 * VISIONOID MAG PLOTTER - 計測画面ViewModel
 * 
 * 概要:
 *   計測画面のビジネスロジックを管理するViewModel。
 *   ミッションデータと計測ポイントの取得・保存を担当。
 * 
 * 主な仕様:
 *   - ミッションの取得
 *   - 計測ポイントの取得・保存
 * 
 * 制限事項:
 *   - AndroidViewModelを継承
 */
package com.visionoid.magplotter.ui.measurement;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.visionoid.magplotter.data.model.MeasurementPoint;
import com.visionoid.magplotter.data.model.Mission;
import com.visionoid.magplotter.data.repository.MissionRepository;

import java.util.List;

/**
 * 計測画面ViewModelクラス
 */
public class MeasurementViewModel extends AndroidViewModel {

    /** リポジトリ */
    private final MissionRepository repository;

    /**
     * コンストラクタ
     * 
     * @param application アプリケーションインスタンス
     */
    public MeasurementViewModel(@NonNull Application application) {
        super(application);
        repository = new MissionRepository(application);
    }

    /**
     * ミッションを取得
     * 
     * @param missionId ミッションID
     * @return ミッションのLiveData
     */
    public LiveData<Mission> getMission(long missionId) {
        return repository.getMissionById(missionId);
    }

    /**
     * 計測ポイントを取得
     * 
     * @param missionId ミッションID
     * @return 計測ポイントのLiveData
     */
    public LiveData<List<MeasurementPoint>> getPoints(long missionId) {
        return repository.getPointsByMissionId(missionId);
    }

    /**
     * 計測ポイント数を取得
     * 
     * @param missionId ミッションID
     * @return ポイント数のLiveData
     */
    public LiveData<Integer> getPointCount(long missionId) {
        return repository.getPointCount(missionId);
    }

    /**
     * 計測ポイントを挿入
     * 
     * @param point 挿入する計測ポイント
     */
    public void insertPoint(MeasurementPoint point) {
        repository.insertPoint(point, null);
    }

    /**
     * ミッションを更新
     * 
     * @param mission 更新するミッション
     */
    public void updateMission(Mission mission) {
        repository.update(mission);
    }
}


