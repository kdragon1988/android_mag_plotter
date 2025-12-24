/**
 * MissionListViewModel.java
 * 
 * VISIONOID MAG PLOTTER - ミッション一覧ViewModel
 * 
 * 概要:
 *   ミッション一覧画面のビジネスロジックを管理するViewModel。
 *   リポジトリを経由してデータにアクセスし、UIにLiveDataを提供。
 * 
 * 主な仕様:
 *   - 全ミッションの取得・監視
 *   - ミッションの削除
 * 
 * 制限事項:
 *   - AndroidViewModelを継承（Applicationコンテキストが必要）
 */
package com.visionoid.magplotter.ui.mission;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.visionoid.magplotter.data.model.Mission;
import com.visionoid.magplotter.data.repository.MissionRepository;

import java.util.List;

/**
 * ミッション一覧ViewModelクラス
 */
public class MissionListViewModel extends AndroidViewModel {

    /** リポジトリ */
    private final MissionRepository repository;

    /** 全ミッション（LiveData） */
    private final LiveData<List<Mission>> allMissions;

    /**
     * コンストラクタ
     * 
     * @param application アプリケーションインスタンス
     */
    public MissionListViewModel(@NonNull Application application) {
        super(application);
        repository = new MissionRepository(application);
        allMissions = repository.getAllMissions();
    }

    /**
     * 全ミッションを取得
     * 
     * @return 全ミッションのLiveData
     */
    public LiveData<List<Mission>> getAllMissions() {
        return allMissions;
    }

    /**
     * アクティブなミッションを取得
     * 
     * @return アクティブなミッションのLiveData
     */
    public LiveData<List<Mission>> getActiveMissions() {
        return repository.getActiveMissions();
    }

    /**
     * 完了済みミッションを取得
     * 
     * @return 完了済みミッションのLiveData
     */
    public LiveData<List<Mission>> getCompletedMissions() {
        return repository.getCompletedMissions();
    }

    /**
     * ミッションを削除
     * 
     * @param mission 削除するミッション
     */
    public void delete(Mission mission) {
        repository.delete(mission);
    }

    /**
     * 場所名で検索
     * 
     * @param keyword 検索キーワード
     * @return マッチするミッションのLiveData
     */
    public LiveData<List<Mission>> searchByLocationName(String keyword) {
        return repository.searchByLocationName(keyword);
    }
}

