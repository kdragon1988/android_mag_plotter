/**
 * GpsLocation.java
 * 
 * VISIONOID MAG PLOTTER - GPS位置情報データクラス
 * 
 * 概要:
 *   GPS位置情報を保持するデータクラス。内蔵GPS・RTK GPS両方で使用。
 *   標準のLocationクラスに加えて、RTK固有の情報も保持。
 * 
 * 主な仕様:
 *   - 緯度・経度・高度
 *   - 精度（水平・垂直）
 *   - Fix状態（RTK対応）
 *   - 衛星数
 *   - HDOP/VDOP
 */
package com.visionoid.magplotter.gps;

import android.location.Location;

/**
 * GPS位置情報
 * 
 * 内蔵GPS・RTK GPS両方の位置情報を統一的に扱うデータクラス。
 */
public class GpsLocation {

    /** 緯度（度） */
    private double latitude;
    
    /** 経度（度） */
    private double longitude;
    
    /** 高度（メートル） */
    private double altitude;
    
    /** 水平精度（メートル） */
    private float horizontalAccuracy;
    
    /** 垂直精度（メートル） */
    private float verticalAccuracy;
    
    /** Fix状態 */
    private GpsFixStatus fixStatus;
    
    /** 使用中の衛星数 */
    private int satellitesUsed;
    
    /** 捕捉中の衛星数 */
    private int satellitesInView;
    
    /** HDOP（水平精度低下率） */
    private float hdop;
    
    /** VDOP（垂直精度低下率） */
    private float vdop;
    
    /** PDOP（位置精度低下率） */
    private float pdop;
    
    /** タイムスタンプ（ミリ秒） */
    private long timestamp;
    
    /** 位置情報ソース（"internal" or "usb"） */
    private String source;
    
    /** 速度（m/s） */
    private float speed;
    
    /** 方位（度） */
    private float bearing;

    /**
     * デフォルトコンストラクタ
     */
    public GpsLocation() {
        this.fixStatus = GpsFixStatus.NO_FIX;
        this.timestamp = System.currentTimeMillis();
        this.source = "unknown";
    }

    /**
     * Android Locationからの変換コンストラクタ
     * 
     * @param location Android Location
     */
    public GpsLocation(Location location) {
        this();
        if (location != null) {
            this.latitude = location.getLatitude();
            this.longitude = location.getLongitude();
            this.altitude = location.getAltitude();
            this.horizontalAccuracy = location.getAccuracy();
            this.verticalAccuracy = location.hasVerticalAccuracy() ? location.getVerticalAccuracyMeters() : 0;
            this.speed = location.getSpeed();
            this.bearing = location.getBearing();
            this.timestamp = location.getTime();
            this.source = "internal";
            this.fixStatus = GpsFixStatus.FIX_3D;
        }
    }

    // === Getters ===

    /**
     * 緯度を取得
     * @return 緯度（度）
     */
    public double getLatitude() {
        return latitude;
    }

    /**
     * 経度を取得
     * @return 経度（度）
     */
    public double getLongitude() {
        return longitude;
    }

    /**
     * 高度を取得
     * @return 高度（メートル）
     */
    public double getAltitude() {
        return altitude;
    }

    /**
     * 水平精度を取得
     * @return 水平精度（メートル）
     */
    public float getHorizontalAccuracy() {
        return horizontalAccuracy;
    }

    /**
     * 垂直精度を取得
     * @return 垂直精度（メートル）
     */
    public float getVerticalAccuracy() {
        return verticalAccuracy;
    }

    /**
     * Fix状態を取得
     * @return Fix状態
     */
    public GpsFixStatus getFixStatus() {
        return fixStatus;
    }

    /**
     * 使用中の衛星数を取得
     * @return 使用中の衛星数
     */
    public int getSatellitesUsed() {
        return satellitesUsed;
    }

    /**
     * 捕捉中の衛星数を取得
     * @return 捕捉中の衛星数
     */
    public int getSatellitesInView() {
        return satellitesInView;
    }

    /**
     * HDOPを取得
     * @return HDOP
     */
    public float getHdop() {
        return hdop;
    }

    /**
     * VDOPを取得
     * @return VDOP
     */
    public float getVdop() {
        return vdop;
    }

    /**
     * PDOPを取得
     * @return PDOP
     */
    public float getPdop() {
        return pdop;
    }

    /**
     * タイムスタンプを取得
     * @return タイムスタンプ（ミリ秒）
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * ソースを取得
     * @return 位置情報ソース
     */
    public String getSource() {
        return source;
    }

    /**
     * 速度を取得
     * @return 速度（m/s）
     */
    public float getSpeed() {
        return speed;
    }

    /**
     * 方位を取得
     * @return 方位（度）
     */
    public float getBearing() {
        return bearing;
    }

    // === Setters ===

    /**
     * 緯度を設定
     * @param latitude 緯度（度）
     */
    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    /**
     * 経度を設定
     * @param longitude 経度（度）
     */
    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    /**
     * 高度を設定
     * @param altitude 高度（メートル）
     */
    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    /**
     * 水平精度を設定
     * @param horizontalAccuracy 水平精度（メートル）
     */
    public void setHorizontalAccuracy(float horizontalAccuracy) {
        this.horizontalAccuracy = horizontalAccuracy;
    }

    /**
     * 垂直精度を設定
     * @param verticalAccuracy 垂直精度（メートル）
     */
    public void setVerticalAccuracy(float verticalAccuracy) {
        this.verticalAccuracy = verticalAccuracy;
    }

    /**
     * Fix状態を設定
     * @param fixStatus Fix状態
     */
    public void setFixStatus(GpsFixStatus fixStatus) {
        this.fixStatus = fixStatus;
    }

    /**
     * 使用中の衛星数を設定
     * @param satellitesUsed 使用中の衛星数
     */
    public void setSatellitesUsed(int satellitesUsed) {
        this.satellitesUsed = satellitesUsed;
    }

    /**
     * 捕捉中の衛星数を設定
     * @param satellitesInView 捕捉中の衛星数
     */
    public void setSatellitesInView(int satellitesInView) {
        this.satellitesInView = satellitesInView;
    }

    /**
     * HDOPを設定
     * @param hdop HDOP
     */
    public void setHdop(float hdop) {
        this.hdop = hdop;
    }

    /**
     * VDOPを設定
     * @param vdop VDOP
     */
    public void setVdop(float vdop) {
        this.vdop = vdop;
    }

    /**
     * PDOPを設定
     * @param pdop PDOP
     */
    public void setPdop(float pdop) {
        this.pdop = pdop;
    }

    /**
     * タイムスタンプを設定
     * @param timestamp タイムスタンプ（ミリ秒）
     */
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * ソースを設定
     * @param source 位置情報ソース
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * 速度を設定
     * @param speed 速度（m/s）
     */
    public void setSpeed(float speed) {
        this.speed = speed;
    }

    /**
     * 方位を設定
     * @param bearing 方位（度）
     */
    public void setBearing(float bearing) {
        this.bearing = bearing;
    }

    // === Utility Methods ===

    /**
     * 有効な位置情報かどうかを判定
     * @return 有効な場合true
     */
    public boolean isValid() {
        return fixStatus != null && fixStatus.isValid() && 
               latitude != 0.0 && longitude != 0.0;
    }

    /**
     * RTK測位中かどうかを判定
     * @return RTK測位中の場合true
     */
    public boolean isRtk() {
        return fixStatus != null && fixStatus.isRtk();
    }

    /**
     * Android Locationに変換
     * @return Android Location
     */
    public Location toAndroidLocation() {
        Location location = new Location(source);
        location.setLatitude(latitude);
        location.setLongitude(longitude);
        location.setAltitude(altitude);
        location.setAccuracy(horizontalAccuracy);
        location.setSpeed(speed);
        location.setBearing(bearing);
        location.setTime(timestamp);
        return location;
    }

    @Override
    public String toString() {
        return String.format(
            "GpsLocation{lat=%.7f, lon=%.7f, alt=%.1f, acc=%.2f, fix=%s, sat=%d, hdop=%.1f, src=%s}",
            latitude, longitude, altitude, horizontalAccuracy,
            fixStatus != null ? fixStatus.getEnglishLabel() : "null",
            satellitesUsed, hdop, source
        );
    }
}

