/**
 * MissionRepository.java
 * 
 * VISIONOID MAG PLOTTER - ミッションリポジトリ
 * 
 * 概要:
 *   ミッションデータのアクセスを抽象化するリポジトリクラス。
 *   ViewModelからのデータアクセスを仲介し、データソースの詳細を隠蔽。
 * 
 * 主な仕様:
 *   - DAOを経由したデータベースアクセス
 *   - バックグラウンドスレッドでの非同期処理
 *   - LiveDataを使用したリアクティブなデータ提供
 * 
 * 制限事項:
 *   - Applicationコンテキストを使用してインスタンス化する必要がある
 */
package com.visionoid.magplotter.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.visionoid.magplotter.data.dao.MeasurementPointDao;
import com.visionoid.magplotter.data.dao.MissionDao;
import com.visionoid.magplotter.data.db.AppDatabase;
import com.visionoid.magplotter.data.model.MeasurementPoint;
import com.visionoid.magplotter.data.model.Mission;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * ミッションリポジトリクラス
 * 
 * ミッションと計測ポイントのデータアクセスを管理。
 */
public class MissionRepository {

    /** ミッションDAO */
    private final MissionDao missionDao;

    /** 計測ポイントDAO */
    private final MeasurementPointDao measurementPointDao;

    /** 全ミッション（LiveData） */
    private final LiveData<List<Mission>> allMissions;

    /**
     * コンストラクタ
     * 
     * @param application アプリケーションインスタンス
     */
    public MissionRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        missionDao = database.missionDao();
        measurementPointDao = database.measurementPointDao();
        allMissions = missionDao.getAllMissionsLive();
    }

    // ==================== ミッション操作 ====================

    /**
     * 全ミッションを取得（LiveData）
     * 
     * @return 全ミッションのLiveData
     */
    public LiveData<List<Mission>> getAllMissions() {
        return allMissions;
    }

    /**
     * アクティブなミッションを取得（LiveData）
     * 
     * @return アクティブなミッションのLiveData
     */
    public LiveData<List<Mission>> getActiveMissions() {
        return missionDao.getActiveMissionsLive();
    }

    /**
     * 完了済みミッションを取得（LiveData）
     * 
     * @return 完了済みミッションのLiveData
     */
    public LiveData<List<Mission>> getCompletedMissions() {
        return missionDao.getCompletedMissionsLive();
    }

    /**
     * IDでミッションを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return ミッションのLiveData
     */
    public LiveData<Mission> getMissionById(long missionId) {
        return missionDao.getMissionByIdLive(missionId);
    }

    /**
     * IDでミッションを同期取得
     * 
     * @param missionId ミッションID
     * @return ミッション
     */
    public Mission getMissionByIdSync(long missionId) {
        Future<Mission> future = AppDatabase.databaseWriteExecutor.submit(
                () -> missionDao.getMissionById(missionId)
        );
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * ミッションを挿入
     * 
     * @param mission 挿入するミッション
     * @param callback 挿入完了時のコールバック（挿入されたID）
     */
    public void insert(Mission mission, InsertCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long id = missionDao.insert(mission);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    /**
     * ミッションを挿入（同期）
     * 
     * @param mission 挿入するミッション
     * @return 挿入されたミッションのID
     */
    public long insertSync(Mission mission) {
        Callable<Long> callable = () -> missionDao.insert(mission);
        Future<Long> future = AppDatabase.databaseWriteExecutor.submit(callable);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * ミッションを更新
     * 
     * @param mission 更新するミッション
     */
    public void update(Mission mission) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            mission.updateTimestamp();
            missionDao.update(mission);
        });
    }

    /**
     * ミッションを削除
     * 
     * @param mission 削除するミッション
     */
    public void delete(Mission mission) {
        AppDatabase.databaseWriteExecutor.execute(() -> missionDao.delete(mission));
    }

    /**
     * IDでミッションを削除
     * 
     * @param missionId 削除するミッションのID
     */
    public void deleteById(long missionId) {
        AppDatabase.databaseWriteExecutor.execute(() -> missionDao.deleteById(missionId));
    }

    /**
     * 場所名で検索
     * 
     * @param keyword 検索キーワード
     * @return マッチするミッションのLiveData
     */
    public LiveData<List<Mission>> searchByLocationName(String keyword) {
        return missionDao.searchByLocationName(keyword);
    }

    // ==================== 計測ポイント操作 ====================

    /**
     * ミッションに紐づく計測ポイントを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 計測ポイントのLiveData
     */
    public LiveData<List<MeasurementPoint>> getPointsByMissionId(long missionId) {
        return measurementPointDao.getPointsByMissionIdLive(missionId);
    }

    /**
     * ミッションに紐づく計測ポイントを同期取得
     * 
     * @param missionId ミッションID
     * @return 計測ポイントリスト
     */
    public List<MeasurementPoint> getPointsByMissionIdSync(long missionId) {
        Future<List<MeasurementPoint>> future = AppDatabase.databaseWriteExecutor.submit(
                () -> measurementPointDao.getPointsByMissionId(missionId)
        );
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 計測ポイント数を取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 計測ポイント数のLiveData
     */
    public LiveData<Integer> getPointCount(long missionId) {
        return measurementPointDao.getPointCountByMissionIdLive(missionId);
    }

    /**
     * 最新の計測ポイントを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 最新の計測ポイントのLiveData
     */
    public LiveData<MeasurementPoint> getLatestPoint(long missionId) {
        return measurementPointDao.getLatestPointByMissionIdLive(missionId);
    }

    /**
     * 計測ポイントを挿入
     * 
     * @param point 挿入する計測ポイント
     * @param callback 挿入完了時のコールバック
     */
    public void insertPoint(MeasurementPoint point, InsertCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long id = measurementPointDao.insert(point);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    /**
     * 計測ポイントを挿入（同期）
     * 
     * @param point 挿入する計測ポイント
     * @return 挿入されたポイントのID
     */
    public long insertPointSync(MeasurementPoint point) {
        Callable<Long> callable = () -> measurementPointDao.insert(point);
        Future<Long> future = AppDatabase.databaseWriteExecutor.submit(callable);
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return -1;
        }
    }

    /**
     * 複数の計測ポイントを一括挿入
     * 
     * @param points 挿入する計測ポイントリスト
     */
    public void insertAllPoints(List<MeasurementPoint> points) {
        AppDatabase.databaseWriteExecutor.execute(() -> measurementPointDao.insertAll(points));
    }

    /**
     * 計測ポイントを削除
     * 
     * @param point 削除する計測ポイント
     */
    public void deletePoint(MeasurementPoint point) {
        AppDatabase.databaseWriteExecutor.execute(() -> measurementPointDao.delete(point));
    }

    /**
     * ミッションの全計測ポイントを削除
     * 
     * @param missionId ミッションID
     */
    public void deleteAllPointsByMissionId(long missionId) {
        AppDatabase.databaseWriteExecutor.execute(
                () -> measurementPointDao.deleteByMissionId(missionId)
        );
    }

    /**
     * ミッションの統計情報を取得
     * 
     * @param missionId ミッションID
     * @return 統計情報（最大、最小、平均ノイズ値）
     */
    public MissionStatistics getStatistics(long missionId) {
        Future<MissionStatistics> future = AppDatabase.databaseWriteExecutor.submit(() -> {
            Double maxNoise = measurementPointDao.getMaxNoiseByMissionId(missionId);
            Double minNoise = measurementPointDao.getMinNoiseByMissionId(missionId);
            Double avgNoise = measurementPointDao.getAvgNoiseByMissionId(missionId);
            int count = measurementPointDao.getPointCountByMissionId(missionId);
            return new MissionStatistics(
                    maxNoise != null ? maxNoise : 0,
                    minNoise != null ? minNoise : 0,
                    avgNoise != null ? avgNoise : 0,
                    count
            );
        });
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
            return new MissionStatistics(0, 0, 0, 0);
        }
    }

    // ==================== コールバック/データクラス ====================

    /**
     * 挿入完了コールバックインターフェース
     */
    public interface InsertCallback {
        /**
         * 挿入完了時に呼び出される
         * @param id 挿入されたレコードのID
         */
        void onInserted(long id);
    }

    /**
     * ミッション統計情報クラス
     */
    public static class MissionStatistics {
        /** 最大ノイズ値 */
        public final double maxNoise;
        /** 最小ノイズ値 */
        public final double minNoise;
        /** 平均ノイズ値 */
        public final double avgNoise;
        /** 計測ポイント数 */
        public final int pointCount;

        /**
         * コンストラクタ
         * 
         * @param maxNoise 最大ノイズ値
         * @param minNoise 最小ノイズ値
         * @param avgNoise 平均ノイズ値
         * @param pointCount 計測ポイント数
         */
        public MissionStatistics(double maxNoise, double minNoise, double avgNoise, int pointCount) {
            this.maxNoise = maxNoise;
            this.minNoise = minNoise;
            this.avgNoise = avgNoise;
            this.pointCount = pointCount;
        }
    }
}



