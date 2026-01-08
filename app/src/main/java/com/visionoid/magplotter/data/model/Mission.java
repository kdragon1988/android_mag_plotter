/**
 * Mission.java
 * 
 * VISIONOID MAG PLOTTER - ミッションエンティティ
 * 
 * 概要:
 *   調査ミッションの情報を格納するデータクラス。
 *   Roomデータベースのエンティティとして使用される。
 * 
 * 主な仕様:
 *   - ミッションの基本情報（名前、担当者、メモ）を保持
 *   - 基準磁場値と閾値を設定可能
 *   - 作成日時と更新日時を自動記録
 * 
 * 制限事項:
 *   - locationName と operatorName は必須
 */
package com.visionoid.magplotter.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * ミッションエンティティクラス
 * 
 * ドローンショー実施場所の磁場調査ミッションを表現する。
 */
@Entity(tableName = "missions")
public class Mission {

    /** ミッションID（自動生成） */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /** 場所名（必須） */
    @NonNull
    @ColumnInfo(name = "location_name")
    private String locationName;

    /** 担当者名（必須） */
    @NonNull
    @ColumnInfo(name = "operator_name")
    private String operatorName;

    /** メモ（任意） */
    @ColumnInfo(name = "memo")
    private String memo;

    /** 基準磁場値（μT） - デフォルト: 46.0（日本平均） */
    @ColumnInfo(name = "reference_mag", defaultValue = "46.0")
    private double referenceMag;

    /** 安全閾値（μT） - デフォルト: 10.0 */
    @ColumnInfo(name = "safe_threshold", defaultValue = "10.0")
    private double safeThreshold;

    /** 危険閾値（μT） - デフォルト: 50.0 */
    @ColumnInfo(name = "danger_threshold", defaultValue = "50.0")
    private double dangerThreshold;

    /** 作成日時（Unixタイムスタンプ） */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** 更新日時（Unixタイムスタンプ） */
    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /** ミッション完了フラグ */
    @ColumnInfo(name = "is_completed", defaultValue = "0")
    private boolean isCompleted;

    /**
     * コンストラクタ
     * 
     * @param locationName 場所名（必須）
     * @param operatorName 担当者名（必須）
     */
    public Mission(@NonNull String locationName, @NonNull String operatorName) {
        this.locationName = locationName;
        this.operatorName = operatorName;
        this.memo = "";
        this.referenceMag = 46.0;
        this.safeThreshold = 10.0;
        this.dangerThreshold = 50.0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isCompleted = false;
    }

    // ==================== Getter/Setter ====================

    /**
     * ミッションIDを取得
     * @return ミッションID
     */
    public long getId() {
        return id;
    }

    /**
     * ミッションIDを設定
     * @param id ミッションID
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * 場所名を取得
     * @return 場所名
     */
    @NonNull
    public String getLocationName() {
        return locationName;
    }

    /**
     * 場所名を設定
     * @param locationName 場所名
     */
    public void setLocationName(@NonNull String locationName) {
        this.locationName = locationName;
    }

    /**
     * 担当者名を取得
     * @return 担当者名
     */
    @NonNull
    public String getOperatorName() {
        return operatorName;
    }

    /**
     * 担当者名を設定
     * @param operatorName 担当者名
     */
    public void setOperatorName(@NonNull String operatorName) {
        this.operatorName = operatorName;
    }

    /**
     * メモを取得
     * @return メモ
     */
    public String getMemo() {
        return memo;
    }

    /**
     * メモを設定
     * @param memo メモ
     */
    public void setMemo(String memo) {
        this.memo = memo;
    }

    /**
     * 基準磁場値を取得
     * @return 基準磁場値（μT）
     */
    public double getReferenceMag() {
        return referenceMag;
    }

    /**
     * 基準磁場値を設定
     * @param referenceMag 基準磁場値（μT）
     */
    public void setReferenceMag(double referenceMag) {
        this.referenceMag = referenceMag;
    }

    /**
     * 安全閾値を取得
     * @return 安全閾値（μT）
     */
    public double getSafeThreshold() {
        return safeThreshold;
    }

    /**
     * 安全閾値を設定
     * @param safeThreshold 安全閾値（μT）
     */
    public void setSafeThreshold(double safeThreshold) {
        this.safeThreshold = safeThreshold;
    }

    /**
     * 危険閾値を取得
     * @return 危険閾値（μT）
     */
    public double getDangerThreshold() {
        return dangerThreshold;
    }

    /**
     * 危険閾値を設定
     * @param dangerThreshold 危険閾値（μT）
     */
    public void setDangerThreshold(double dangerThreshold) {
        this.dangerThreshold = dangerThreshold;
    }

    /**
     * 作成日時を取得
     * @return 作成日時（Unixタイムスタンプ）
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 作成日時を設定
     * @param createdAt 作成日時（Unixタイムスタンプ）
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 更新日時を取得
     * @return 更新日時（Unixタイムスタンプ）
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 更新日時を設定
     * @param updatedAt 更新日時（Unixタイムスタンプ）
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 完了フラグを取得
     * @return 完了フラグ
     */
    public boolean isCompleted() {
        return isCompleted;
    }

    /**
     * 完了フラグを設定
     * @param completed 完了フラグ
     */
    public void setCompleted(boolean completed) {
        isCompleted = completed;
    }

    /**
     * 更新日時を現在時刻に更新
     */
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }
}



