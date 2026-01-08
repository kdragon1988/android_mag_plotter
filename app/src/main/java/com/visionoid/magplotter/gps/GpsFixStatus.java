/**
 * GpsFixStatus.java
 * 
 * VISIONOID MAG PLOTTER - GPS Fix状態の列挙型
 * 
 * 概要:
 *   GPSの測位状態を表す列挙型。内蔵GPS・RTK GPSの両方で使用。
 * 
 * 主な仕様:
 *   - NO_FIX: 測位不可
 *   - FIX_2D: 2D測位（高度なし）
 *   - FIX_3D: 3D測位（高度あり）
 *   - DGPS: 補正測位
 *   - RTK_FLOAT: RTKフロート解
 *   - RTK_FIX: RTK固定解（センチメートル精度）
 */
package com.visionoid.magplotter.gps;

/**
 * GPS Fix状態
 * 
 * RTK GPSの状態を含む測位品質を表す。
 */
public enum GpsFixStatus {
    /** 測位不可 */
    NO_FIX(0, "No Fix", "測位不可"),
    
    /** 2D測位（高度なし） */
    FIX_2D(1, "2D Fix", "2D測位"),
    
    /** 3D測位（高度あり） */
    FIX_3D(2, "3D Fix", "3D測位"),
    
    /** DGPS補正測位 */
    DGPS(3, "DGPS", "DGPS補正"),
    
    /** RTKフロート解 */
    RTK_FLOAT(4, "RTK Float", "RTKフロート"),
    
    /** RTK固定解（センチメートル精度） */
    RTK_FIX(5, "RTK Fix", "RTK固定");

    /** Fix品質値 */
    private final int qualityValue;
    
    /** 英語表記 */
    private final String englishLabel;
    
    /** 日本語表記 */
    private final String japaneseLabel;

    /**
     * コンストラクタ
     * 
     * @param qualityValue Fix品質値
     * @param englishLabel 英語表記
     * @param japaneseLabel 日本語表記
     */
    GpsFixStatus(int qualityValue, String englishLabel, String japaneseLabel) {
        this.qualityValue = qualityValue;
        this.englishLabel = englishLabel;
        this.japaneseLabel = japaneseLabel;
    }

    /**
     * Fix品質値を取得
     * 
     * @return Fix品質値
     */
    public int getQualityValue() {
        return qualityValue;
    }

    /**
     * 英語表記を取得
     * 
     * @return 英語表記
     */
    public String getEnglishLabel() {
        return englishLabel;
    }

    /**
     * 日本語表記を取得
     * 
     * @return 日本語表記
     */
    public String getJapaneseLabel() {
        return japaneseLabel;
    }

    /**
     * NMEAのFix品質値からGpsFixStatusを取得
     * 
     * @param nmeaQuality NMEAのFix品質値（GGAメッセージの6番目フィールド）
     * @return 対応するGpsFixStatus
     */
    public static GpsFixStatus fromNmeaQuality(int nmeaQuality) {
        switch (nmeaQuality) {
            case 0:
                return NO_FIX;
            case 1:
                return FIX_3D;  // Standard GPS
            case 2:
                return DGPS;
            case 4:
                return RTK_FIX;
            case 5:
                return RTK_FLOAT;
            default:
                return NO_FIX;
        }
    }

    /**
     * RTKが有効かどうかを判定
     * 
     * @return RTK測位中の場合true
     */
    public boolean isRtk() {
        return this == RTK_FLOAT || this == RTK_FIX;
    }

    /**
     * 有効な測位かどうかを判定
     * 
     * @return 測位中の場合true
     */
    public boolean isValid() {
        return this != NO_FIX;
    }
}

