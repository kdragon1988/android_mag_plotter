/**
 * MissionDao.java
 * 
 * VISIONOID MAG PLOTTER - ミッションデータアクセスオブジェクト
 * 
 * 概要:
 *   ミッションテーブルへのCRUD操作を定義するインターフェース。
 *   Roomライブラリによって実装が自動生成される。
 * 
 * 主な仕様:
 *   - ミッションの作成、読取、更新、削除
 *   - LiveDataを使用したリアクティブなデータ取得
 * 
 * 制限事項:
 *   - データベース操作はメインスレッド以外で実行する必要がある
 */
package com.visionoid.magplotter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.visionoid.magplotter.data.model.Mission;

import java.util.List;

/**
 * ミッションDAO（Data Access Object）インターフェース
 */
@Dao
public interface MissionDao {

    /**
     * ミッションを挿入
     * 
     * @param mission 挿入するミッション
     * @return 挿入されたミッションのID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Mission mission);

    /**
     * ミッションを更新
     * 
     * @param mission 更新するミッション
     */
    @Update
    void update(Mission mission);

    /**
     * ミッションを削除
     * 
     * @param mission 削除するミッション
     */
    @Delete
    void delete(Mission mission);

    /**
     * IDでミッションを削除
     * 
     * @param missionId 削除するミッションのID
     */
    @Query("DELETE FROM missions WHERE id = :missionId")
    void deleteById(long missionId);

    /**
     * 全ミッションを削除
     */
    @Query("DELETE FROM missions")
    void deleteAll();

    /**
     * IDでミッションを取得
     * 
     * @param missionId ミッションID
     * @return ミッション
     */
    @Query("SELECT * FROM missions WHERE id = :missionId")
    Mission getMissionById(long missionId);

    /**
     * IDでミッションを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return ミッション（LiveData）
     */
    @Query("SELECT * FROM missions WHERE id = :missionId")
    LiveData<Mission> getMissionByIdLive(long missionId);

    /**
     * 全ミッションを取得（作成日時の降順）
     * 
     * @return ミッションリスト
     */
    @Query("SELECT * FROM missions ORDER BY created_at DESC")
    List<Mission> getAllMissions();

    /**
     * 全ミッションを取得（LiveData、作成日時の降順）
     * 
     * @return ミッションリスト（LiveData）
     */
    @Query("SELECT * FROM missions ORDER BY created_at DESC")
    LiveData<List<Mission>> getAllMissionsLive();

    /**
     * 未完了のミッションを取得
     * 
     * @return 未完了ミッションリスト
     */
    @Query("SELECT * FROM missions WHERE is_completed = 0 ORDER BY created_at DESC")
    LiveData<List<Mission>> getActiveMissionsLive();

    /**
     * 完了済みのミッションを取得
     * 
     * @return 完了済みミッションリスト
     */
    @Query("SELECT * FROM missions WHERE is_completed = 1 ORDER BY updated_at DESC")
    LiveData<List<Mission>> getCompletedMissionsLive();

    /**
     * ミッションの件数を取得
     * 
     * @return ミッション件数
     */
    @Query("SELECT COUNT(*) FROM missions")
    int getMissionCount();

    /**
     * 場所名で検索
     * 
     * @param keyword 検索キーワード
     * @return マッチするミッションリスト
     */
    @Query("SELECT * FROM missions WHERE location_name LIKE '%' || :keyword || '%' ORDER BY created_at DESC")
    LiveData<List<Mission>> searchByLocationName(String keyword);
}

