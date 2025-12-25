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
 *   - 磁場統計値（MAX/AVG）の計算
 * 
 * 制限事項:
 *   - AndroidViewModelを継承
 */
package com.visionoid.magplotter.ui.measurement;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

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
    
    /** 磁場統計データのLiveData */
    private MutableLiveData<MagStatistics> magStatistics = new MutableLiveData<>(new MagStatistics());

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
     * 磁場統計データクラス
     * 
     * MAG FIELDとNOISEの最大値・平均値を保持する。
     */
    public static class MagStatistics {
        /** MAG FIELDの最大値 */
        public double magFieldMax = 0.0;
        /** MAG FIELDの平均値 */
        public double magFieldAvg = 0.0;
        /** NOISEの最大値 */
        public double noiseMax = 0.0;
        /** NOISEの平均値 */
        public double noiseAvg = 0.0;
        /** 計測ポイント数 */
        public int pointCount = 0;
        
        /**
         * デフォルトコンストラクタ
         */
        public MagStatistics() {
        }
        
        /**
         * コンストラクタ
         * 
         * @param magFieldMax MAG FIELDの最大値
         * @param magFieldAvg MAG FIELDの平均値
         * @param noiseMax NOISEの最大値
         * @param noiseAvg NOISEの平均値
         * @param pointCount 計測ポイント数
         */
        public MagStatistics(double magFieldMax, double magFieldAvg, 
                            double noiseMax, double noiseAvg, int pointCount) {
            this.magFieldMax = magFieldMax;
            this.magFieldAvg = magFieldAvg;
            this.noiseMax = noiseMax;
            this.noiseAvg = noiseAvg;
            this.pointCount = pointCount;
        }
    }
    
    /**
     * 磁場統計を取得
     * 
     * @return 磁場統計のLiveData
     */
    public LiveData<MagStatistics> getMagStatistics() {
        return magStatistics;
    }
    
    /**
     * 計測ポイントリストから統計を計算して更新
     * 
     * @param points 計測ポイントリスト
     */
    public void updateStatistics(List<MeasurementPoint> points) {
        if (points == null || points.isEmpty()) {
            magStatistics.setValue(new MagStatistics());
            return;
        }
        
        double magFieldMax = Double.MIN_VALUE;
        double magFieldSum = 0.0;
        double noiseMax = Double.MIN_VALUE;
        double noiseSum = 0.0;
        int count = points.size();
        
        for (MeasurementPoint point : points) {
            // MAG FIELD（totalMag）の統計
            double totalMag = point.getTotalMag();
            if (totalMag > magFieldMax) {
                magFieldMax = totalMag;
            }
            magFieldSum += totalMag;
            
            // NOISE（noiseValue）の統計
            double noiseValue = point.getNoiseValue();
            if (noiseValue > noiseMax) {
                noiseMax = noiseValue;
            }
            noiseSum += noiseValue;
        }
        
        double magFieldAvg = magFieldSum / count;
        double noiseAvg = noiseSum / count;
        
        magStatistics.setValue(new MagStatistics(magFieldMax, magFieldAvg, noiseMax, noiseAvg, count));
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


