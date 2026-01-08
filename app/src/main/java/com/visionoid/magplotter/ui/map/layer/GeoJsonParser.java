/**
 * GeoJsonParser.java
 * 
 * VISIONOID MAG PLOTTER - GeoJSONパーサー
 * 
 * 概要:
 *   GeoJSON形式のデータをosmdroidのPolygonオブジェクトに変換する。
 *   FeatureCollectionおよび単一のFeatureに対応。
 *   大きなファイル（50MB以上）にも対応するためストリーミングパーサーを使用。
 * 
 * 主な仕様:
 *   - GeoJSON FeatureCollectionのパース
 *   - Polygon/MultiPolygon geometryの変換
 *   - osmdroid Polygonへの変換
 *   - ストリーミングパーサー（JsonReader）による省メモリ処理
 * 
 * 制限事項:
 *   - Point, LineString等のgeometryタイプは非対応
 *   - 3D座標（altitude）は無視
 */
package com.visionoid.magplotter.ui.map.layer;

import android.graphics.Color;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.overlay.Polygon;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * GeoJSONパーサークラス
 * 
 * GeoJSON形式のデータをosmdroidのPolygonに変換する。
 * 大きなファイルに対応するためストリーミングパーサーを使用。
 */
public class GeoJsonParser {

    /** ログタグ */
    private static final String TAG = "GeoJsonParser";

    /** デフォルト塗りつぶし色 */
    @ColorInt
    private static final int DEFAULT_FILL_COLOR = Color.argb(100, 255, 0, 0);

    /** デフォルト境界線色 */
    @ColorInt
    private static final int DEFAULT_STROKE_COLOR = Color.argb(200, 255, 0, 0);

    /** デフォルト境界線幅 */
    private static final float DEFAULT_STROKE_WIDTH = 2.0f;

    /** 大きなファイルと判断するサイズ（100MB - 事実上無効化して従来パーサーを使用） */
    private static final int LARGE_FILE_THRESHOLD = 100 * 1024 * 1024;

    /**
     * GeoJSON文字列をPolygonリストに変換
     * 
     * @param geoJson GeoJSON文字列
     * @param fillColor 塗りつぶし色
     * @param strokeColor 境界線色
     * @param displayStyle 表示スタイル
     * @return Polygonリスト
     */
    @NonNull
    public static List<Polygon> parse(
            @NonNull String geoJson,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        Log.d(TAG, "GeoJSONパース開始: サイズ=" + geoJson.length() + " bytes");
        
        // 大きなファイルはストリーミングパーサーを使用
        if (geoJson.length() > LARGE_FILE_THRESHOLD) {
            Log.d(TAG, "大きなファイルのためストリーミングパーサーを使用");
            return parseStreaming(geoJson, fillColor, strokeColor, displayStyle);
        }
        
        // 小さなファイルは従来のJSONObjectパーサーを使用
        return parseWithJsonObject(geoJson, fillColor, strokeColor, displayStyle);
    }

    /**
     * JSONObjectを使った従来のパース方式（小さなファイル用）
     */
    @NonNull
    private static List<Polygon> parseWithJsonObject(
            @NonNull String geoJson,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        List<Polygon> polygons = new ArrayList<>();
        Log.d(TAG, "JSONObjectパース開始...");

        try {
            Log.d(TAG, "JSONObject生成中... (サイズ: " + geoJson.length() + " bytes)");
            JSONObject root = new JSONObject(geoJson);
            String type = root.optString("type", "");
            Log.d(TAG, "GeoJSONタイプ: " + type);

            if ("FeatureCollection".equals(type)) {
                // FeatureCollectionの場合
                JSONArray features = root.optJSONArray("features");
                int featureCount = features != null ? features.length() : 0;
                Log.d(TAG, "FeatureCollection: " + featureCount + " features");
                
                if (features != null) {
                    for (int i = 0; i < features.length(); i++) {
                        JSONObject feature = features.optJSONObject(i);
                        if (feature != null) {
                            List<Polygon> featurePolygons = parseFeature(
                                    feature, fillColor, strokeColor, displayStyle);
                            polygons.addAll(featurePolygons);
                        }
                        
                        // 進捗ログ（1000件ごと）
                        if (i > 0 && i % 1000 == 0) {
                            Log.d(TAG, "パース進捗: " + i + "/" + featureCount + " (" + polygons.size() + " polygons)");
                        }
                    }
                }
            } else if ("Feature".equals(type)) {
                // 単一Featureの場合
                Log.d(TAG, "単一Feature");
                List<Polygon> featurePolygons = parseFeature(
                        root, fillColor, strokeColor, displayStyle);
                polygons.addAll(featurePolygons);
            } else if ("Polygon".equals(type) || "MultiPolygon".equals(type)) {
                // 直接Geometry指定の場合
                Log.d(TAG, "直接Geometry: " + type);
                List<Polygon> geometryPolygons = parseGeometry(
                        root, fillColor, strokeColor, displayStyle);
                polygons.addAll(geometryPolygons);
            } else {
                Log.w(TAG, "未知のGeoJSONタイプ: " + type);
            }

        } catch (JSONException e) {
            Log.e(TAG, "GeoJSONのパースに失敗(JSONException): " + e.getMessage(), e);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "メモリ不足でパースに失敗: " + e.getMessage(), e);
            // メモリ不足の場合は空のリストを返す（ストリーミングも同様に失敗する可能性が高いため）
            return new ArrayList<>();
        } catch (Exception e) {
            Log.e(TAG, "GeoJSONのパースに失敗(予期せぬエラー): " + e.getMessage(), e);
        }

        Log.d(TAG, "パース完了: " + polygons.size() + " 個のPolygon");
        return polygons;
    }

    /**
     * ストリーミングパーサーを使ったパース方式（大きなファイル用）
     */
    @NonNull
    private static List<Polygon> parseStreaming(
            @NonNull String geoJson,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        List<Polygon> polygons = new ArrayList<>();
        int featureCount = 0;
        int successCount = 0;
        int skipCount = 0;
        
        Log.d(TAG, "ストリーミングパース開始...");
        
        try (JsonReader reader = new JsonReader(new StringReader(geoJson))) {
            reader.setLenient(true);
            reader.beginObject();
            
            while (reader.hasNext()) {
                String name = reader.nextName();
                Log.d(TAG, "ルートプロパティ: " + name);
                
                if ("features".equals(name)) {
                    reader.beginArray();
                    Log.d(TAG, "features配列を開始");
                    
                    while (reader.hasNext()) {
                        try {
                            Polygon polygon = readFeatureStreaming(reader, fillColor, strokeColor, displayStyle);
                            if (polygon != null) {
                                polygons.add(polygon);
                                successCount++;
                            } else {
                                skipCount++;
                            }
                            featureCount++;
                            
                            // 進捗ログ（100件ごとに詳細、1000件ごとに概要）
                            if (featureCount == 1) {
                                Log.d(TAG, "最初のFeature処理完了: polygon=" + (polygon != null));
                            }
                            if (featureCount % 1000 == 0) {
                                Log.d(TAG, "ストリーミングパース進捗: " + featureCount + " features, " + 
                                        successCount + " polygons, " + skipCount + " skipped");
                            }
                        } catch (Exception e) {
                            Log.w(TAG, "Feature " + featureCount + " のパースをスキップ: " + e.getMessage());
                            skipCount++;
                            featureCount++;
                            // エラー発生時は残りのfeatureをスキップせずに続行を試みる
                        }
                    }
                    
                    reader.endArray();
                    Log.d(TAG, "features配列を終了");
                } else {
                    reader.skipValue();
                }
            }
            
            reader.endObject();
            
        } catch (IOException e) {
            Log.e(TAG, "ストリーミングパースIO例外: " + e.getMessage(), e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "ストリーミングパース状態例外: " + e.getMessage(), e);
        } catch (OutOfMemoryError e) {
            Log.e(TAG, "ストリーミングパースでもメモリ不足: " + e.getMessage(), e);
        }

        Log.d(TAG, "ストリーミングパース完了: " + polygons.size() + " 個のPolygon (" + 
                featureCount + " features処理, " + skipCount + " skipped)");
        return polygons;
    }

    /**
     * ストリーミングでFeatureを読み込み
     */
    @Nullable
    private static Polygon readFeatureStreaming(
            @NonNull JsonReader reader,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) throws IOException {
        Polygon polygon = null;
        
        reader.beginObject();
        
        while (reader.hasNext()) {
            String name = reader.nextName();
            
            if ("geometry".equals(name)) {
                if (reader.peek() == JsonToken.NULL) {
                    reader.nextNull();
                } else {
                    polygon = readGeometryStreaming(reader, fillColor, strokeColor, displayStyle);
                }
            } else {
                reader.skipValue();
            }
        }
        
        reader.endObject();
        return polygon;
    }

    /**
     * ストリーミングでGeometryを読み込み
     * 座標を直接パースする（Polygon形式を前提）
     */
    @Nullable
    private static Polygon readGeometryStreaming(
            @NonNull JsonReader reader,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) throws IOException {
        List<GeoPoint> points = null;
        
        reader.beginObject();
        
        while (reader.hasNext()) {
            String name = reader.nextName();
            
            if ("coordinates".equals(name)) {
                // 座標を直接読み込み（Polygon形式として処理）
                points = readPolygonCoordinatesStreaming(reader);
            } else {
                reader.skipValue();
            }
        }
        
        reader.endObject();
        
        if (points != null && points.size() >= 3) {
            Polygon polygon = new Polygon();
            polygon.setPoints(points);
            applyDisplayStyle(polygon, fillColor, strokeColor, displayStyle);
            return polygon;
        }
        
        return null;
    }
    
    /**
     * Polygon座標をストリーミングで読み込み
     * 形式: [[[lon,lat],[lon,lat],...], [[hole],...]]
     * 外周リングのみ取得（穴は無視）
     */
    @NonNull
    private static List<GeoPoint> readPolygonCoordinatesStreaming(@NonNull JsonReader reader) throws IOException {
        List<GeoPoint> points = new ArrayList<>();
        
        // Polygon coordinates: [ [outer_ring], [hole1], [hole2], ... ]
        // outer_ring: [ [lon, lat], [lon, lat], ... ]
        
        reader.beginArray(); // coordinates配列の開始
        
        // 最初の配列（外周リング）を処理
        if (reader.hasNext() && reader.peek() == JsonToken.BEGIN_ARRAY) {
            reader.beginArray(); // outer_ring配列の開始
            
            // 各座標点を読み込み
            while (reader.hasNext()) {
                if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                    reader.beginArray(); // [lon, lat]の開始
                    
                    double lon = 0, lat = 0;
                    int index = 0;
                    while (reader.hasNext()) {
                        if (reader.peek() == JsonToken.NUMBER) {
                            double value = reader.nextDouble();
                            if (index == 0) lon = value;
                            else if (index == 1) lat = value;
                            index++;
                        } else {
                            reader.skipValue();
                        }
                    }
                    
                    reader.endArray(); // [lon, lat]の終了
                    
                    // 有効な座標をポイントとして追加
                    if (lat >= -90 && lat <= 90 && lon >= -180 && lon <= 180) {
                        points.add(new GeoPoint(lat, lon));
                    }
                } else {
                    reader.skipValue();
                }
            }
            
            reader.endArray(); // outer_ring配列の終了
        }
        
        // 残りの配列（穴）をスキップ
        while (reader.hasNext()) {
            reader.skipValue();
        }
        
        reader.endArray(); // coordinates配列の終了
        
        return points;
    }

    /**
     * JsonReaderの現在の値を安全にスキップ
     */
    private static void skipValue(@NonNull JsonReader reader) {
        try {
            reader.skipValue();
        } catch (IOException e) {
            Log.w(TAG, "skipValue失敗: " + e.getMessage());
        }
    }

    /**
     * Featureオブジェクトをパース
     * 
     * @param feature JSONObject
     * @param fillColor 塗りつぶし色
     * @param strokeColor 境界線色
     * @param displayStyle 表示スタイル
     * @return Polygonリスト
     */
    @NonNull
    private static List<Polygon> parseFeature(
            @NonNull JSONObject feature,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        JSONObject geometry = feature.optJSONObject("geometry");
        if (geometry == null) {
            return new ArrayList<>();
        }
        return parseGeometry(geometry, fillColor, strokeColor, displayStyle);
    }

    /**
     * Geometryオブジェクトをパース
     * 
     * @param geometry JSONObject
     * @param fillColor 塗りつぶし色
     * @param strokeColor 境界線色
     * @param displayStyle 表示スタイル
     * @return Polygonリスト
     */
    @NonNull
    private static List<Polygon> parseGeometry(
            @NonNull JSONObject geometry,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        List<Polygon> polygons = new ArrayList<>();
        String geometryType = geometry.optString("type", "");
        JSONArray coordinates = geometry.optJSONArray("coordinates");

        if (coordinates == null) {
            return polygons;
        }

        try {
            if ("Polygon".equals(geometryType)) {
                Polygon polygon = parsePolygonCoordinates(
                        coordinates, fillColor, strokeColor, displayStyle);
                if (polygon != null) {
                    polygons.add(polygon);
                }
            } else if ("MultiPolygon".equals(geometryType)) {
                for (int i = 0; i < coordinates.length(); i++) {
                    JSONArray polygonCoords = coordinates.optJSONArray(i);
                    if (polygonCoords != null) {
                        Polygon polygon = parsePolygonCoordinates(
                                polygonCoords, fillColor, strokeColor, displayStyle);
                        if (polygon != null) {
                            polygons.add(polygon);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Geometryのパースに失敗: " + e.getMessage(), e);
        }

        return polygons;
    }

    /**
     * Polygon座標配列をosmdroid Polygonに変換
     * 
     * @param coordinates 座標配列（外周 + 内周リング）
     * @param fillColor 塗りつぶし色
     * @param strokeColor 境界線色
     * @param displayStyle 表示スタイル
     * @return osmdroid Polygon
     */
    @Nullable
    private static Polygon parsePolygonCoordinates(
            @NonNull JSONArray coordinates,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        if (coordinates.length() == 0) {
            return null;
        }

        try {
            // 外周リング（最初の配列）
            JSONArray outerRing = coordinates.optJSONArray(0);
            if (outerRing == null || outerRing.length() < 3) {
                return null;
            }

            List<GeoPoint> points = parseCoordinateRing(outerRing);
            if (points.isEmpty()) {
                return null;
            }

            Polygon polygon = new Polygon();
            polygon.setPoints(points);

            // 表示スタイルに応じた設定
            applyDisplayStyle(polygon, fillColor, strokeColor, displayStyle);

            // 内周リング（穴）があれば追加
            if (coordinates.length() > 1) {
                List<List<GeoPoint>> holes = new ArrayList<>();
                for (int i = 1; i < coordinates.length(); i++) {
                    JSONArray holeRing = coordinates.optJSONArray(i);
                    if (holeRing != null) {
                        List<GeoPoint> holePoints = parseCoordinateRing(holeRing);
                        if (!holePoints.isEmpty()) {
                            holes.add(holePoints);
                        }
                    }
                }
                if (!holes.isEmpty()) {
                    polygon.setHoles(holes);
                }
            }

            return polygon;

        } catch (Exception e) {
            Log.e(TAG, "Polygon座標のパースに失敗: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 座標リング（配列）をGeoPointリストに変換
     * 
     * @param ring 座標配列
     * @return GeoPointリスト
     */
    @NonNull
    private static List<GeoPoint> parseCoordinateRing(@NonNull JSONArray ring) {
        List<GeoPoint> points = new ArrayList<>();

        for (int i = 0; i < ring.length(); i++) {
            JSONArray coord = ring.optJSONArray(i);
            if (coord != null && coord.length() >= 2) {
                double longitude = coord.optDouble(0, 0);
                double latitude = coord.optDouble(1, 0);
                
                // 座標が有効な範囲かチェック
                if (latitude >= -90 && latitude <= 90 && longitude >= -180 && longitude <= 180) {
                    points.add(new GeoPoint(latitude, longitude));
                }
            }
        }

        return points;
    }

    /**
     * 表示スタイルをPolygonに適用
     * 
     * @param polygon 対象Polygon
     * @param fillColor 塗りつぶし色
     * @param strokeColor 境界線色
     * @param displayStyle 表示スタイル
     */
    private static void applyDisplayStyle(
            @NonNull Polygon polygon,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @NonNull LayerDisplayStyle displayStyle
    ) {
        switch (displayStyle) {
            case FILLED:
                polygon.setFillColor(fillColor);
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(DEFAULT_STROKE_WIDTH);
                break;

            case BORDER_ONLY:
                polygon.setFillColor(Color.TRANSPARENT);
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(DEFAULT_STROKE_WIDTH * 1.5f);
                break;

            case HATCHED:
                // ハッチングは半透明塗りつぶし + 太めの境界線で表現
                // 本格的なハッチングはカスタムオーバーレイが必要
                polygon.setFillColor(Color.argb(50, 
                        Color.red(fillColor), 
                        Color.green(fillColor), 
                        Color.blue(fillColor)));
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(DEFAULT_STROKE_WIDTH * 2.0f);
                break;
        }
    }

    /**
     * デフォルト設定でGeoJSONをパース
     * 
     * @param geoJson GeoJSON文字列
     * @return Polygonリスト
     */
    @NonNull
    public static List<Polygon> parseWithDefaults(@NonNull String geoJson) {
        return parse(geoJson, DEFAULT_FILL_COLOR, DEFAULT_STROKE_COLOR, LayerDisplayStyle.FILLED);
    }
}


