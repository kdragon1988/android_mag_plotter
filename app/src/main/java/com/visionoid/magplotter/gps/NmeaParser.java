/**
 * NmeaParser.java
 * 
 * VISIONOID MAG PLOTTER - NMEAパーサー
 * 
 * 概要:
 *   NMEA 0183プロトコルのセンテンスをパースするクラス。
 *   GGA、RMC、GSA、GSVなどの主要なセンテンスに対応。
 * 
 * 主な仕様:
 *   - GGA: 位置、時刻、Fix品質、衛星数、HDOP
 *   - RMC: 位置、時刻、速度、方位
 *   - GSA: Fix種別、衛星ID、DOP値
 *   - GSV: 衛星詳細情報
 * 
 * 制限事項:
 *   - チェックサム検証は省略可能
 */
package com.visionoid.magplotter.gps;

import android.util.Log;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * NMEAセンテンスパーサー
 * 
 * GPS/GNSSデバイスからのNMEA出力をパースして位置情報を抽出する。
 */
public class NmeaParser {

    private static final String TAG = "NmeaParser";

    /** 現在の位置情報 */
    private GpsLocation currentLocation;
    
    /** パース成功時のリスナー */
    private OnLocationParsedListener locationListener;

    /**
     * 位置情報パース完了リスナー
     */
    public interface OnLocationParsedListener {
        /**
         * 位置情報がパースされた時に呼ばれる
         * @param location パースされた位置情報
         */
        void onLocationParsed(GpsLocation location);
    }

    /**
     * コンストラクタ
     */
    public NmeaParser() {
        this.currentLocation = new GpsLocation();
        this.currentLocation.setSource("usb");
    }

    /**
     * 位置情報リスナーを設定
     * @param listener リスナー
     */
    public void setOnLocationParsedListener(OnLocationParsedListener listener) {
        this.locationListener = listener;
    }

    /**
     * NMEAセンテンスをパース
     * 
     * @param sentence NMEAセンテンス（例: "$GPGGA,123519,4807.038,N,01131.000,E,1,08,0.9,545.4,M,47.0,M,,*47"）
     * @return パースが成功した場合true
     */
    public boolean parseSentence(String sentence) {
        if (sentence == null || sentence.isEmpty()) {
            return false;
        }

        // 前後の空白を除去
        sentence = sentence.trim();

        // $で始まらない場合は無効
        if (!sentence.startsWith("$")) {
            return false;
        }

        try {
            // チェックサムを除去（存在する場合）
            String data = sentence;
            if (sentence.contains("*")) {
                int asteriskIndex = sentence.indexOf('*');
                data = sentence.substring(0, asteriskIndex);
            }

            // センテンスタイプを判定
            String[] parts = data.substring(1).split(",");
            if (parts.length < 1) {
                return false;
            }

            String sentenceType = parts[0];

            // センテンスタイプに応じてパース
            if (sentenceType.endsWith("GGA")) {
                return parseGga(parts);
            } else if (sentenceType.endsWith("RMC")) {
                return parseRmc(parts);
            } else if (sentenceType.endsWith("GSA")) {
                return parseGsa(parts);
            } else if (sentenceType.endsWith("VTG")) {
                return parseVtg(parts);
            }

            return false;

        } catch (Exception e) {
            Log.e(TAG, "NMEAパースエラー: " + sentence + " - " + e.getMessage());
            return false;
        }
    }

    /**
     * GGAセンテンスをパース（位置・時刻・Fix品質）
     * 
     * フォーマット:
     * $GPGGA,hhmmss.ss,llll.ll,a,yyyyy.yy,a,x,xx,x.x,x.x,M,x.x,M,x.x,xxxx*hh
     *   0: センテンスID
     *   1: UTC時刻
     *   2: 緯度
     *   3: N/S
     *   4: 経度
     *   5: E/W
     *   6: Fix品質（0=無効, 1=GPS, 2=DGPS, 4=RTK Fix, 5=RTK Float）
     *   7: 使用衛星数
     *   8: HDOP
     *   9: 高度
     *   10: 高度単位（M）
     *   11: ジオイド高
     *   12: ジオイド単位（M）
     *   13: DGPS更新時間
     *   14: DGPS基地局ID
     */
    private boolean parseGga(String[] parts) {
        try {
            // 緯度をパース
            if (parts.length > 3 && !parts[2].isEmpty()) {
                double latitude = parseLatitude(parts[2], parts[3]);
                currentLocation.setLatitude(latitude);
            }

            // 経度をパース
            if (parts.length > 5 && !parts[4].isEmpty()) {
                double longitude = parseLongitude(parts[4], parts[5]);
                currentLocation.setLongitude(longitude);
            }

            // Fix品質をパース
            if (parts.length > 6 && !parts[6].isEmpty()) {
                int quality = Integer.parseInt(parts[6]);
                currentLocation.setFixStatus(GpsFixStatus.fromNmeaQuality(quality));
            }

            // 使用衛星数をパース
            if (parts.length > 7 && !parts[7].isEmpty()) {
                int satellites = Integer.parseInt(parts[7]);
                currentLocation.setSatellitesUsed(satellites);
            }

            // HDOPをパース
            if (parts.length > 8 && !parts[8].isEmpty()) {
                float hdop = Float.parseFloat(parts[8]);
                currentLocation.setHdop(hdop);
                // HDOPから概算精度を計算（約3m × HDOP）
                currentLocation.setHorizontalAccuracy(hdop * 3.0f);
            }

            // 高度をパース
            if (parts.length > 9 && !parts[9].isEmpty()) {
                double altitude = Double.parseDouble(parts[9]);
                currentLocation.setAltitude(altitude);
            }

            currentLocation.setTimestamp(System.currentTimeMillis());
            notifyLocationUpdated();
            return true;

        } catch (NumberFormatException e) {
            Log.e(TAG, "GGAパースエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * RMCセンテンスをパース（推奨最小データ）
     * 
     * フォーマット:
     * $GPRMC,hhmmss.ss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,x.x,a*hh
     *   0: センテンスID
     *   1: UTC時刻
     *   2: ステータス（A=有効, V=無効）
     *   3: 緯度
     *   4: N/S
     *   5: 経度
     *   6: E/W
     *   7: 速度（ノット）
     *   8: 方位（度）
     *   9: 日付
     *   10: 磁気偏差
     *   11: 磁気偏差方向
     */
    private boolean parseRmc(String[] parts) {
        try {
            // ステータスチェック
            if (parts.length > 2 && "V".equals(parts[2])) {
                currentLocation.setFixStatus(GpsFixStatus.NO_FIX);
                return true;
            }

            // 緯度をパース
            if (parts.length > 4 && !parts[3].isEmpty()) {
                double latitude = parseLatitude(parts[3], parts[4]);
                currentLocation.setLatitude(latitude);
            }

            // 経度をパース
            if (parts.length > 6 && !parts[5].isEmpty()) {
                double longitude = parseLongitude(parts[5], parts[6]);
                currentLocation.setLongitude(longitude);
            }

            // 速度をパース（ノット→m/s変換）
            if (parts.length > 7 && !parts[7].isEmpty()) {
                float speedKnots = Float.parseFloat(parts[7]);
                float speedMs = speedKnots * 0.514444f;  // 1 knot = 0.514444 m/s
                currentLocation.setSpeed(speedMs);
            }

            // 方位をパース
            if (parts.length > 8 && !parts[8].isEmpty()) {
                float bearing = Float.parseFloat(parts[8]);
                currentLocation.setBearing(bearing);
            }

            return true;

        } catch (NumberFormatException e) {
            Log.e(TAG, "RMCパースエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * GSAセンテンスをパース（DOP値と衛星ID）
     * 
     * フォーマット:
     * $GPGSA,A,3,04,05,...,2.5,1.3,2.1*hh
     *   0: センテンスID
     *   1: 選択モード（A=自動, M=手動）
     *   2: Fix種別（1=無効, 2=2D, 3=3D）
     *   3-14: 使用衛星ID（最大12個）
     *   15: PDOP
     *   16: HDOP
     *   17: VDOP
     */
    private boolean parseGsa(String[] parts) {
        try {
            // Fix種別をパース
            if (parts.length > 2 && !parts[2].isEmpty()) {
                int fixType = Integer.parseInt(parts[2]);
                if (fixType == 1 && currentLocation.getFixStatus() == GpsFixStatus.NO_FIX) {
                    currentLocation.setFixStatus(GpsFixStatus.NO_FIX);
                } else if (fixType == 2 && !currentLocation.getFixStatus().isRtk()) {
                    currentLocation.setFixStatus(GpsFixStatus.FIX_2D);
                } else if (fixType == 3 && !currentLocation.getFixStatus().isRtk()) {
                    currentLocation.setFixStatus(GpsFixStatus.FIX_3D);
                }
            }

            // PDOP, HDOP, VDOPをパース
            if (parts.length > 15 && !parts[15].isEmpty()) {
                float pdop = Float.parseFloat(parts[15]);
                currentLocation.setPdop(pdop);
            }
            if (parts.length > 16 && !parts[16].isEmpty()) {
                float hdop = Float.parseFloat(parts[16]);
                currentLocation.setHdop(hdop);
            }
            if (parts.length > 17 && !parts[17].isEmpty()) {
                // VDOPの末尾にチェックサムが含まれる可能性があるため処理
                String vdopStr = parts[17].split("\\*")[0];
                float vdop = Float.parseFloat(vdopStr);
                currentLocation.setVdop(vdop);
            }

            return true;

        } catch (NumberFormatException e) {
            Log.e(TAG, "GSAパースエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * VTGセンテンスをパース（速度と方位）
     * 
     * フォーマット:
     * $GPVTG,054.7,T,034.4,M,005.5,N,010.2,K*hh
     *   0: センテンスID
     *   1: 真方位
     *   2: T（真方位）
     *   3: 磁気方位
     *   4: M（磁気方位）
     *   5: 速度（ノット）
     *   6: N（ノット）
     *   7: 速度（km/h）
     *   8: K（km/h）
     */
    private boolean parseVtg(String[] parts) {
        try {
            // 速度をパース（km/h→m/s変換）
            if (parts.length > 7 && !parts[7].isEmpty()) {
                float speedKmh = Float.parseFloat(parts[7]);
                float speedMs = speedKmh / 3.6f;
                currentLocation.setSpeed(speedMs);
            }

            // 方位をパース
            if (parts.length > 1 && !parts[1].isEmpty()) {
                float bearing = Float.parseFloat(parts[1]);
                currentLocation.setBearing(bearing);
            }

            return true;

        } catch (NumberFormatException e) {
            Log.e(TAG, "VTGパースエラー: " + e.getMessage());
            return false;
        }
    }

    /**
     * NMEA形式の緯度をパース
     * 
     * @param value NMEA緯度値（例: "4807.038"）
     * @param direction 方向（"N" or "S"）
     * @return 度単位の緯度
     */
    private double parseLatitude(String value, String direction) {
        // NMEA形式: DDMM.MMMM
        double raw = Double.parseDouble(value);
        int degrees = (int) (raw / 100);
        double minutes = raw - (degrees * 100);
        double latitude = degrees + (minutes / 60.0);
        
        if ("S".equalsIgnoreCase(direction)) {
            latitude = -latitude;
        }
        
        return latitude;
    }

    /**
     * NMEA形式の経度をパース
     * 
     * @param value NMEA経度値（例: "01131.000"）
     * @param direction 方向（"E" or "W"）
     * @return 度単位の経度
     */
    private double parseLongitude(String value, String direction) {
        // NMEA形式: DDDMM.MMMM
        double raw = Double.parseDouble(value);
        int degrees = (int) (raw / 100);
        double minutes = raw - (degrees * 100);
        double longitude = degrees + (minutes / 60.0);
        
        if ("W".equalsIgnoreCase(direction)) {
            longitude = -longitude;
        }
        
        return longitude;
    }

    /**
     * 位置情報更新をリスナーに通知
     */
    private void notifyLocationUpdated() {
        if (locationListener != null && currentLocation.isValid()) {
            locationListener.onLocationParsed(currentLocation);
        }
    }

    /**
     * 現在の位置情報を取得
     * @return 現在の位置情報
     */
    public GpsLocation getCurrentLocation() {
        return currentLocation;
    }

    /**
     * 位置情報をリセット
     */
    public void reset() {
        currentLocation = new GpsLocation();
        currentLocation.setSource("usb");
    }
}

