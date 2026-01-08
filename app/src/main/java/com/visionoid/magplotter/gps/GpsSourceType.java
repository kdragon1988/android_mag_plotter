/**
 * GpsSourceType.java
 * 
 * VISIONOID MAG PLOTTER - GPSソース種別
 * 
 * 概要:
 *   位置情報の取得元を定義する列挙型。
 *   内蔵GPS、USB RTK GPS、自動検出をサポート。
 */
package com.visionoid.magplotter.gps;

/**
 * GPSソース種別
 */
public enum GpsSourceType {
    /** 自動検出（USB GPS優先、なければ内蔵GPS） */
    AUTO("auto", "自動検出"),
    
    /** 内蔵GPS */
    INTERNAL("internal", "内蔵GPS"),
    
    /** USB RTK GPS */
    USB("usb", "USB RTK GPS");

    /** ソースID */
    private final String id;
    
    /** 表示名 */
    private final String displayName;

    /**
     * コンストラクタ
     * @param id ソースID
     * @param displayName 表示名
     */
    GpsSourceType(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    /**
     * ソースIDを取得
     * @return ソースID
     */
    public String getId() {
        return id;
    }

    /**
     * 表示名を取得
     * @return 表示名
     */
    public String getDisplayName() {
        return displayName;
    }
}

