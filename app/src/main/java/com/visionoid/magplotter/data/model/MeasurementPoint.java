/**
 * MeasurementPoint.java
 * 
 * VISIONOID MAG PLOTTER - 計測ポイントエンティティ
 * 
 * 概要:
 *   磁場計測データを格納するデータクラス。
 *   各計測ポイントの位置情報と磁場データを保持する。
 * 
 * 主な仕様:
 *   - GPS座標（緯度・経度）を記録
 *   - 磁場の3軸成分（X, Y, Z）を記録
 *   - 総磁場強度とノイズ値を計算して保存
 *   - 計測日時を自動記録
 * 
 * 制限事項:
 *   - 必ずミッションに紐づく必要がある（外部キー制約）
 */
package com.visionoid.magplotter.data.model;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 計測ポイントエンティティクラス
 * 
 * 磁場計測の個々のデータポイントを表現する。
 */
@Entity(
    tableName = "measurement_points",
    foreignKeys = @ForeignKey(
        entity = Mission.class,
        parentColumns = "id",
        childColumns = "mission_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {
        @Index(value = "mission_id")
    }
)
public class MeasurementPoint {

    /** ポイントID（自動生成） */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /** 紐づくミッションID */
    @ColumnInfo(name = "mission_id")
    private long missionId;

    /** 緯度 */
    @ColumnInfo(name = "latitude")
    private double latitude;

    /** 経度 */
    @ColumnInfo(name = "longitude")
    private double longitude;

    /** GPS精度（メートル） */
    @ColumnInfo(name = "accuracy")
    private float accuracy;

    /** 磁場X軸成分（μT） */
    @ColumnInfo(name = "mag_x")
    private float magX;

    /** 磁場Y軸成分（μT） */
    @ColumnInfo(name = "mag_y")
    private float magY;

    /** 磁場Z軸成分（μT） */
    @ColumnInfo(name = "mag_z")
    private float magZ;

    /** 総磁場強度（μT） - √(x² + y² + z²) */
    @ColumnInfo(name = "total_mag")
    private double totalMag;

    /** ノイズ値（μT） - |totalMag - referenceMag| */
    @ColumnInfo(name = "noise_value")
    private double noiseValue;

    /** 計測日時（Unixタイムスタンプ） */
    @ColumnInfo(name = "timestamp")
    private long timestamp;

    /** 計測モード（AUTO / MANUAL） */
    @ColumnInfo(name = "measurement_mode")
    private String measurementMode;

    /**
     * コンストラクタ
     * 
     * @param missionId 紐づくミッションID
     * @param latitude 緯度
     * @param longitude 経度
     * @param accuracy GPS精度（メートル）
     * @param magX 磁場X軸成分（μT）
     * @param magY 磁場Y軸成分（μT）
     * @param magZ 磁場Z軸成分（μT）
     * @param referenceMag 基準磁場値（μT）
     * @param measurementMode 計測モード
     */
    public MeasurementPoint(long missionId, double latitude, double longitude,
                           float accuracy, float magX, float magY, float magZ,
                           double referenceMag, String measurementMode) {
        this.missionId = missionId;
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracy = accuracy;
        this.magX = magX;
        this.magY = magY;
        this.magZ = magZ;
        this.measurementMode = measurementMode;
        this.timestamp = System.currentTimeMillis();
        
        // 総磁場強度を計算: √(x² + y² + z²)
        this.totalMag = Math.sqrt(magX * magX + magY * magY + magZ * magZ);
        
        // ノイズ値を計算: |totalMag - referenceMag|
        this.noiseValue = Math.abs(this.totalMag - referenceMag);
    }

    /**
     * デフォルトコンストラクタ（Room用）
     */
    public MeasurementPoint() {
    }

    // ==================== Getter/Setter ====================

    /**
     * ポイントIDを取得
     * @return ポイントID
     */
    public long getId() {
        return id;
    }

    /**
     * ポイントIDを設定
     * @param id ポイントID
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * ミッションIDを取得
     * @return ミッションID
     */
    public long getMissionId() {
        return missionId;
    }

    /**
     * ミッションIDを設定
     * @param missionId ミッションID
     */
    public void setMissionId(long missionId) {
        this.missionId = missionId;
    }

    /**
     * 緯度を取得
     * @return 緯度
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 緯度を設定
     * @param latitude 緯度
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * 経度を取得
     * @return 経度
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * 経度を設定
     * @param longitude 経度
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * GPS精度を取得
     * @return GPS精度（メートル）
     */
    public float getAccuracy() {
        return accuracy;
    }

    /**
     * GPS精度を設定
     * @param accuracy GPS精度（メートル）
     */
    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    /**
     * 磁場X軸成分を取得
     * @return 磁場X軸成分（μT）
     */
    public float getMagX() {
        return magX;
    }

    /**
     * 磁場X軸成分を設定
     * @param magX 磁場X軸成分（μT）
     */
    public void setMagX(float magX) {
        this.magX = magX;
    }

    /**
     * 磁場Y軸成分を取得
     * @return 磁場Y軸成分（μT）
     */
    public float getMagY() {
        return magY;
    }

    /**
     * 磁場Y軸成分を設定
     * @param magY 磁場Y軸成分（μT）
     */
    public void setMagY(float magY) {
        this.magY = magY;
    }

    /**
     * 磁場Z軸成分を取得
     * @return 磁場Z軸成分（μT）
     */
    public float getMagZ() {
        return magZ;
    }

    /**
     * 磁場Z軸成分を設定
     * @param magZ 磁場Z軸成分（μT）
     */
    public void setMagZ(float magZ) {
        this.magZ = magZ;
    }

    /**
     * 総磁場強度を取得
     * @return 総磁場強度（μT）
     */
    public double getTotalMag() {
        return totalMag;
    }

    /**
     * 総磁場強度を設定
     * @param totalMag 総磁場強度（μT）
     */
    public void setTotalMag(double totalMag) {
        this.totalMag = totalMag;
    }

    /**
     * ノイズ値を取得
     * @return ノイズ値（μT）
     */
    public double getNoiseValue() {
        return noiseValue;
    }

    /**
     * ノイズ値を設定
     * @param noiseValue ノイズ値（μT）
     */
    public void setNoiseValue(double noiseValue) {
        this.noiseValue = noiseValue;
    }

    /**
     * 計測日時を取得
     * @return 計測日時（Unixタイムスタンプ）
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * 計測日時を設定
     * @param timestamp 計測日時（Unixタイムスタンプ）
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * 計測モードを取得
     * @return 計測モード（AUTO / MANUAL）
     */
    public String getMeasurementMode() {
        return measurementMode;
    }

    /**
     * 計測モードを設定
     * @param measurementMode 計測モード（AUTO / MANUAL）
     */
    public void setMeasurementMode(String measurementMode) {
        this.measurementMode = measurementMode;
    }

    /**
     * 総磁場強度を再計算
     */
    public void recalculateTotalMag() {
        this.totalMag = Math.sqrt(magX * magX + magY * magY + magZ * magZ);
    }

    /**
     * ノイズ値を再計算
     * @param referenceMag 基準磁場値（μT）
     */
    public void recalculateNoiseValue(double referenceMag) {
        this.noiseValue = Math.abs(this.totalMag - referenceMag);
    }

    /** 計測モード定数: 自動計測 */
    public static final String MODE_AUTO = "AUTO";
    
    /** 計測モード定数: 手動計測 */
    public static final String MODE_MANUAL = "MANUAL";
}

