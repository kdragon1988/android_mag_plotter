/**
 * GeoCalculator.java
 * 
 * VISIONOID MAG PLOTTER - 地理計算ユーティリティ
 * 
 * 概要:
 *   地図上の図形に関する各種計算を行うユーティリティクラス。
 *   距離、面積、周囲長などの計算機能を提供。
 * 
 * 主な仕様:
 *   - Haversine公式による2点間距離計算
 *   - 測地線公式による多角形面積計算
 *   - 円の面積・周囲長計算
 *   - 各辺の長さ計算
 * 
 * 制限事項:
 *   - WGS84座標系を前提
 *   - 地球を球体として近似（高精度が必要な場合は楕円体補正が必要）
 */
package com.visionoid.magplotter.util;

import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;

/**
 * 地理計算ユーティリティクラス
 */
public final class GeoCalculator {

    /** 地球の平均半径（メートル） */
    private static final double EARTH_RADIUS = 6371000.0;

    /** プライベートコンストラクタ（インスタンス化禁止） */
    private GeoCalculator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== 距離計算 ====================

    /**
     * 2点間の距離を計算（Haversine公式）
     * 
     * @param lat1 始点の緯度（度）
     * @param lng1 始点の経度（度）
     * @param lat2 終点の緯度（度）
     * @param lng2 終点の経度（度）
     * @return 距離（メートル）
     */
    public static double calculateDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLng / 2) * Math.sin(dLng / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }

    /**
     * 2点間の距離を計算（GeoPoint版）
     * 
     * @param point1 始点
     * @param point2 終点
     * @return 距離（メートル）
     */
    public static double calculateDistance(GeoPoint point1, GeoPoint point2) {
        return calculateDistance(
                point1.getLatitude(), point1.getLongitude(),
                point2.getLatitude(), point2.getLongitude()
        );
    }

    // ==================== ポリライン計算 ====================

    /**
     * ポリラインの総距離を計算
     * 
     * @param points ポイントリスト
     * @return 総距離（メートル）
     */
    public static double calculatePolylineLength(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double totalLength = 0.0;
        for (int i = 0; i < points.size() - 1; i++) {
            totalLength += calculateDistance(points.get(i), points.get(i + 1));
        }
        return totalLength;
    }

    /**
     * ポリラインの各辺の長さを計算
     * 
     * @param points ポイントリスト
     * @return 各辺の長さリスト（メートル）
     */
    public static List<Double> calculateSegmentLengths(List<GeoPoint> points) {
        List<Double> lengths = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return lengths;
        }

        for (int i = 0; i < points.size() - 1; i++) {
            lengths.add(calculateDistance(points.get(i), points.get(i + 1)));
        }
        return lengths;
    }

    // ==================== ポリゴン計算 ====================

    /**
     * 多角形の面積を計算（Shoelace公式 + 球面補正）
     * 
     * @param points ポイントリスト（閉じていなくてもOK）
     * @return 面積（平方メートル）
     */
    public static double calculatePolygonArea(List<GeoPoint> points) {
        if (points == null || points.size() < 3) {
            return 0.0;
        }

        // 測地線面積計算（球面上のShoelace公式）
        double area = 0.0;
        int n = points.size();

        for (int i = 0; i < n; i++) {
            GeoPoint p1 = points.get(i);
            GeoPoint p2 = points.get((i + 1) % n);

            double lat1 = Math.toRadians(p1.getLatitude());
            double lat2 = Math.toRadians(p2.getLatitude());
            double dLng = Math.toRadians(p2.getLongitude() - p1.getLongitude());

            area += dLng * (2 + Math.sin(lat1) + Math.sin(lat2));
        }

        area = Math.abs(area * EARTH_RADIUS * EARTH_RADIUS / 2.0);

        return area;
    }

    /**
     * 多角形の周囲長を計算
     * 
     * @param points ポイントリスト（閉じていなくてもOK）
     * @return 周囲長（メートル）
     */
    public static double calculatePolygonPerimeter(List<GeoPoint> points) {
        if (points == null || points.size() < 2) {
            return 0.0;
        }

        double perimeter = 0.0;
        int n = points.size();

        for (int i = 0; i < n; i++) {
            perimeter += calculateDistance(points.get(i), points.get((i + 1) % n));
        }

        return perimeter;
    }

    /**
     * 多角形の各辺の長さを計算（閉じたポリゴンとして）
     * 
     * @param points ポイントリスト
     * @return 各辺の長さリスト（メートル）
     */
    public static List<Double> calculatePolygonSegmentLengths(List<GeoPoint> points) {
        List<Double> lengths = new ArrayList<>();
        if (points == null || points.size() < 2) {
            return lengths;
        }

        int n = points.size();
        for (int i = 0; i < n; i++) {
            lengths.add(calculateDistance(points.get(i), points.get((i + 1) % n)));
        }
        return lengths;
    }

    // ==================== 円計算 ====================

    /**
     * 円の面積を計算
     * 
     * @param radius 半径（メートル）
     * @return 面積（平方メートル）
     */
    public static double calculateCircleArea(double radius) {
        return Math.PI * radius * radius;
    }

    /**
     * 円の周囲長を計算
     * 
     * @param radius 半径（メートル）
     * @return 周囲長（メートル）
     */
    public static double calculateCirclePerimeter(double radius) {
        return 2 * Math.PI * radius;
    }

    // ==================== ユーティリティ ====================

    /**
     * 多角形の重心を計算
     * 
     * @param points ポイントリスト
     * @return 重心のGeoPoint
     */
    public static GeoPoint calculateCentroid(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return null;
        }

        double sumLat = 0.0;
        double sumLng = 0.0;

        for (GeoPoint point : points) {
            sumLat += point.getLatitude();
            sumLng += point.getLongitude();
        }

        return new GeoPoint(sumLat / points.size(), sumLng / points.size());
    }

    /**
     * 辺の中点を計算
     * 
     * @param point1 始点
     * @param point2 終点
     * @return 中点のGeoPoint
     */
    public static GeoPoint calculateMidpoint(GeoPoint point1, GeoPoint point2) {
        double midLat = (point1.getLatitude() + point2.getLatitude()) / 2.0;
        double midLng = (point1.getLongitude() + point2.getLongitude()) / 2.0;
        return new GeoPoint(midLat, midLng);
    }

    /**
     * 辺の方位角を計算
     * 
     * @param point1 始点
     * @param point2 終点
     * @return 方位角（度、北を0として時計回り）
     */
    public static double calculateBearing(GeoPoint point1, GeoPoint point2) {
        double lat1 = Math.toRadians(point1.getLatitude());
        double lat2 = Math.toRadians(point2.getLatitude());
        double dLng = Math.toRadians(point2.getLongitude() - point1.getLongitude());

        double y = Math.sin(dLng) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLng);

        double bearing = Math.toDegrees(Math.atan2(y, x));
        return (bearing + 360) % 360;
    }

    // ==================== フォーマット ====================

    /**
     * 距離を読みやすい文字列に変換
     * 
     * @param meters 距離（メートル）
     * @return フォーマット済み文字列
     */
    public static String formatDistance(double meters) {
        if (meters < 1) {
            return String.format("%.1f cm", meters * 100);
        } else if (meters < 1000) {
            return String.format("%.1f m", meters);
        } else {
            return String.format("%.2f km", meters / 1000);
        }
    }

    /**
     * 面積を読みやすい文字列に変換
     * 
     * @param squareMeters 面積（平方メートル）
     * @return フォーマット済み文字列
     */
    public static String formatArea(double squareMeters) {
        if (squareMeters < 1) {
            return String.format("%.1f cm²", squareMeters * 10000);
        } else if (squareMeters < 10000) {
            return String.format("%.1f m²", squareMeters);
        } else {
            return String.format("%.2f ha", squareMeters / 10000);
        }
    }

    /**
     * 短い距離表示（地図上の辺ラベル用）
     * 
     * @param meters 距離（メートル）
     * @return フォーマット済み文字列（短縮版）
     */
    public static String formatDistanceShort(double meters) {
        if (meters < 100) {
            return String.format("%.1fm", meters);
        } else if (meters < 1000) {
            return String.format("%.0fm", meters);
        } else {
            return String.format("%.2fkm", meters / 1000);
        }
    }
}


