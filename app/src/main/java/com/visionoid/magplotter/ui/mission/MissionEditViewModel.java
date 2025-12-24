/**
 * MissionEditViewModel.java
 * 
 * VISIONOID MAG PLOTTER - ミッション作成/編集ViewModel
 * 
 * 概要:
 *   ミッション作成/編集画面のビジネスロジックを管理するViewModel。
 * 
 * 主な仕様:
 *   - ミッションの取得
 *   - ミッションの挿入・更新
 * 
 * 制限事項:
 *   - AndroidViewModelを継承
 */
package com.visionoid.magplotter.ui.mission;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.visionoid.magplotter.data.model.Mission;
import com.visionoid.magplotter.data.repository.MissionRepository;

/**
 * ミッション作成/編集ViewModelクラス
 */
public class MissionEditViewModel extends AndroidViewModel {

    /** リポジトリ */
    private final MissionRepository repository;

    /**
     * コンストラクタ
     * 
     * @param application アプリケーションインスタンス
     */
    public MissionEditViewModel(@NonNull Application application) {
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
     * ミッションを挿入
     * 
     * @param mission 挿入するミッション
     */
    public void insert(Mission mission) {
        repository.insert(mission, null);
    }

    /**
     * ミッションを更新
     * 
     * @param mission 更新するミッション
     */
    public void update(Mission mission) {
        repository.update(mission);
    }
}


