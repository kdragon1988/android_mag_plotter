/**
 * MeasurementPointDao.java
 * 
 * VISIONOID MAG PLOTTER - 計測ポイントデータアクセスオブジェクト
 * 
 * 概要:
 *   計測ポイントテーブルへのCRUD操作を定義するインターフェース。
 *   Roomライブラリによって実装が自動生成される。
 * 
 * 主な仕様:
 *   - 計測ポイントの作成、読取、削除
 *   - ミッションに紐づく計測ポイントの一括取得
 *   - 統計情報の算出
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

import com.visionoid.magplotter.data.model.MeasurementPoint;

import java.util.List;

/**
 * 計測ポイントDAO（Data Access Object）インターフェース
 */
@Dao
public interface MeasurementPointDao {

    /**
     * 計測ポイントを挿入
     * 
     * @param point 挿入する計測ポイント
     * @return 挿入されたポイントのID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(MeasurementPoint point);

    /**
     * 複数の計測ポイントを一括挿入
     * 
     * @param points 挿入する計測ポイントリスト
     * @return 挿入されたポイントのIDリスト
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<MeasurementPoint> points);

    /**
     * 計測ポイントを削除
     * 
     * @param point 削除する計測ポイント
     */
    @Delete
    void delete(MeasurementPoint point);

    /**
     * IDで計測ポイントを削除
     * 
     * @param pointId 削除するポイントのID
     */
    @Query("DELETE FROM measurement_points WHERE id = :pointId")
    void deleteById(long pointId);

    /**
     * ミッションに紐づく全計測ポイントを削除
     * 
     * @param missionId ミッションID
     */
    @Query("DELETE FROM measurement_points WHERE mission_id = :missionId")
    void deleteByMissionId(long missionId);

    /**
     * IDで計測ポイントを取得
     * 
     * @param pointId ポイントID
     * @return 計測ポイント
     */
    @Query("SELECT * FROM measurement_points WHERE id = :pointId")
    MeasurementPoint getPointById(long pointId);

    /**
     * ミッションに紐づく全計測ポイントを取得
     * 
     * @param missionId ミッションID
     * @return 計測ポイントリスト
     */
    @Query("SELECT * FROM measurement_points WHERE mission_id = :missionId ORDER BY timestamp ASC")
    List<MeasurementPoint> getPointsByMissionId(long missionId);

    /**
     * ミッションに紐づく全計測ポイントを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 計測ポイントリスト（LiveData）
     */
    @Query("SELECT * FROM measurement_points WHERE mission_id = :missionId ORDER BY timestamp ASC")
    LiveData<List<MeasurementPoint>> getPointsByMissionIdLive(long missionId);

    /**
     * ミッションの計測ポイント数を取得
     * 
     * @param missionId ミッションID
     * @return 計測ポイント数
     */
    @Query("SELECT COUNT(*) FROM measurement_points WHERE mission_id = :missionId")
    int getPointCountByMissionId(long missionId);

    /**
     * ミッションの計測ポイント数を取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 計測ポイント数（LiveData）
     */
    @Query("SELECT COUNT(*) FROM measurement_points WHERE mission_id = :missionId")
    LiveData<Integer> getPointCountByMissionIdLive(long missionId);

    /**
     * ミッションの最大ノイズ値を取得
     * 
     * @param missionId ミッションID
     * @return 最大ノイズ値
     */
    @Query("SELECT MAX(noise_value) FROM measurement_points WHERE mission_id = :missionId")
    Double getMaxNoiseByMissionId(long missionId);

    /**
     * ミッションの最小ノイズ値を取得
     * 
     * @param missionId ミッションID
     * @return 最小ノイズ値
     */
    @Query("SELECT MIN(noise_value) FROM measurement_points WHERE mission_id = :missionId")
    Double getMinNoiseByMissionId(long missionId);

    /**
     * ミッションの平均ノイズ値を取得
     * 
     * @param missionId ミッションID
     * @return 平均ノイズ値
     */
    @Query("SELECT AVG(noise_value) FROM measurement_points WHERE mission_id = :missionId")
    Double getAvgNoiseByMissionId(long missionId);

    /**
     * 最新の計測ポイントを取得
     * 
     * @param missionId ミッションID
     * @return 最新の計測ポイント
     */
    @Query("SELECT * FROM measurement_points WHERE mission_id = :missionId ORDER BY timestamp DESC LIMIT 1")
    MeasurementPoint getLatestPointByMissionId(long missionId);

    /**
     * 最新の計測ポイントを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 最新の計測ポイント（LiveData）
     */
    @Query("SELECT * FROM measurement_points WHERE mission_id = :missionId ORDER BY timestamp DESC LIMIT 1")
    LiveData<MeasurementPoint> getLatestPointByMissionIdLive(long missionId);

    /**
     * 指定範囲内の計測ポイントを取得
     * 
     * @param missionId ミッションID
     * @param minLat 最小緯度
     * @param maxLat 最大緯度
     * @param minLng 最小経度
     * @param maxLng 最大経度
     * @return 範囲内の計測ポイントリスト
     */
    @Query("SELECT * FROM measurement_points WHERE mission_id = :missionId " +
           "AND latitude BETWEEN :minLat AND :maxLat " +
           "AND longitude BETWEEN :minLng AND :maxLng")
    List<MeasurementPoint> getPointsInBounds(long missionId, double minLat, double maxLat,
                                             double minLng, double maxLng);
}



