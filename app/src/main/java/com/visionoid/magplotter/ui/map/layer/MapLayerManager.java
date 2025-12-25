/**
 * MapLayerManager.java
 * 
 * VISIONOID MAG PLOTTER - マップレイヤー管理
 * 
 * 概要:
 *   osmdroidのMapViewに飛行制限区域レイヤーを追加・削除・表示切替する。
 *   SharedPreferencesと連携してレイヤーの表示状態を永続化。
 * 
 * 主な仕様:
 *   - レイヤーの追加・削除
 *   - レイヤーの表示/非表示切り替え
 *   - 表示スタイルの変更
 *   - 設定の永続化
 * 
 * 制限事項:
 *   - MapViewのライフサイクルに依存
 */
package com.visionoid.magplotter.ui.map.layer;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * マップレイヤー管理クラス
 * 
 * 飛行制限区域などのレイヤーをMapViewに追加・管理する。
 */
public class MapLayerManager {

    /** ログタグ */
    private static final String TAG = "MapLayerManager";

    /** SharedPreferencesキー: 表示スタイル */
    public static final String PREF_LAYER_STYLE = "layer_display_style";

    /** SharedPreferencesキー: レイヤー表示状態のプレフィックス */
    private static final String PREF_LAYER_VISIBLE_PREFIX = "layer_visible_";

    /** コンテキスト */
    private final Context context;

    /** 対象のMapView */
    private final MapView mapView;

    /** SharedPreferences */
    private final SharedPreferences preferences;

    /** レイヤータイプごとのPolygonリスト */
    private final Map<LayerType, List<Polygon>> layerPolygons;

    /** レイヤータイプごとの表示状態 */
    private final Map<LayerType, Boolean> layerVisibility;

    /** 現在の表示スタイル */
    private LayerDisplayStyle currentStyle;

    /** レイヤー変更リスナー */
    @Nullable
    private OnLayerChangeListener layerChangeListener;

    /**
     * レイヤー変更リスナーインターフェース
     */
    public interface OnLayerChangeListener {
        /**
         * レイヤーの表示状態が変更された
         * 
         * @param layerType 変更されたレイヤータイプ
         * @param isVisible 新しい表示状態
         */
        void onLayerVisibilityChanged(@NonNull LayerType layerType, boolean isVisible);

        /**
         * 表示スタイルが変更された
         * 
         * @param style 新しい表示スタイル
         */
        void onDisplayStyleChanged(@NonNull LayerDisplayStyle style);
    }

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     * @param mapView 対象のMapView
     */
    public MapLayerManager(@NonNull Context context, @NonNull MapView mapView) {
        this.context = context.getApplicationContext();
        this.mapView = mapView;
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.layerPolygons = new EnumMap<>(LayerType.class);
        this.layerVisibility = new EnumMap<>(LayerType.class);

        // 表示スタイルを読み込み
        String styleId = preferences.getString(PREF_LAYER_STYLE, LayerDisplayStyle.FILLED.getId());
        this.currentStyle = LayerDisplayStyle.fromId(styleId);

        // 各レイヤーの表示状態を読み込み
        for (LayerType type : LayerType.values()) {
            boolean visible = preferences.getBoolean(type.getVisibilityPrefKey(), false);
            layerVisibility.put(type, visible);
        }

        Log.d(TAG, "MapLayerManager初期化完了: スタイル=" + currentStyle.getId());
    }

    /**
     * レイヤー変更リスナーを設定
     * 
     * @param listener リスナー
     */
    public void setOnLayerChangeListener(@Nullable OnLayerChangeListener listener) {
        this.layerChangeListener = listener;
    }

    /**
     * GeoJSONデータからレイヤーを追加
     * 
     * @param layerType レイヤータイプ
     * @param geoJson GeoJSON文字列
     */
    public void addLayer(@NonNull LayerType layerType, @NonNull String geoJson) {
        int geoJsonLen = geoJson != null ? geoJson.length() : 0;
        Log.d(TAG, "=== addLayer開始 ===");
        Log.d(TAG, "layerType: " + layerType.getId());
        Log.d(TAG, "geoJson length: " + geoJsonLen + " bytes");
        
        // GeoJSONの先頭200文字をログに出力（デバッグ用）
        if (geoJsonLen > 0) {
            String preview = geoJson.substring(0, Math.min(200, geoJsonLen));
            Log.d(TAG, "geoJson preview: " + preview);
        } else {
            Log.e(TAG, "ERROR: geoJson is empty!");
        }
        
        // 既存のレイヤーを削除
        removeLayer(layerType);

        // GeoJSONをパース
        List<Polygon> polygons = GeoJsonParser.parse(
                geoJson,
                layerType.getFillColor(),
                layerType.getStrokeColor(),
                currentStyle
        );

        Log.d(TAG, "GeoJsonParser結果: " + polygons.size() + " polygons");

        if (polygons.isEmpty()) {
            Log.w(TAG, "レイヤーデータが空です: " + layerType.getId() + 
                    " - 正式なデータをダウンロードしてください");
            // 空のリストでも保存（データなし状態を記録）
            layerPolygons.put(layerType, polygons);
            return;
        }

        // レイヤーを保存
        layerPolygons.put(layerType, polygons);

        // 表示状態に応じてMapViewに追加
        boolean visible = isLayerVisible(layerType);
        Log.d(TAG, "レイヤー表示状態: " + visible);
        
        if (visible) {
            addPolygonsToMap(polygons);
            Log.d(TAG, "MapViewにPolygon追加完了: overlays count=" + mapView.getOverlays().size());
        }

        Log.d(TAG, "レイヤー追加完了: " + layerType.getId() + " (" + polygons.size() + " polygons)");
    }

    /**
     * レイヤーを削除
     * 
     * @param layerType レイヤータイプ
     */
    public void removeLayer(@NonNull LayerType layerType) {
        List<Polygon> polygons = layerPolygons.remove(layerType);
        if (polygons != null) {
            removePolygonsFromMap(polygons);
            Log.d(TAG, "レイヤー削除: " + layerType.getId());
        }
    }

    /**
     * 全レイヤーを削除
     */
    public void removeAllLayers() {
        for (LayerType type : LayerType.values()) {
            removeLayer(type);
        }
    }

    /**
     * レイヤーの表示/非表示を切り替え
     * 
     * @param layerType レイヤータイプ
     * @param visible 表示状態
     */
    public void setLayerVisibility(@NonNull LayerType layerType, boolean visible) {
        boolean currentVisibility = layerVisibility.getOrDefault(layerType, false);
        if (currentVisibility == visible) {
            return;  // 変更なし
        }

        layerVisibility.put(layerType, visible);

        // SharedPreferencesに保存
        preferences.edit()
                .putBoolean(layerType.getVisibilityPrefKey(), visible)
                .apply();

        // MapViewの表示を更新
        List<Polygon> polygons = layerPolygons.get(layerType);
        if (polygons != null) {
            if (visible) {
                addPolygonsToMap(polygons);
            } else {
                removePolygonsFromMap(polygons);
            }
        }

        // リスナーに通知
        if (layerChangeListener != null) {
            layerChangeListener.onLayerVisibilityChanged(layerType, visible);
        }

        Log.d(TAG, "レイヤー表示切替: " + layerType.getId() + " -> " + visible);
    }

    /**
     * レイヤーの表示/非表示をトグル
     * 
     * @param layerType レイヤータイプ
     * @return 新しい表示状態
     */
    public boolean toggleLayerVisibility(@NonNull LayerType layerType) {
        boolean newVisibility = !isLayerVisible(layerType);
        setLayerVisibility(layerType, newVisibility);
        return newVisibility;
    }

    /**
     * レイヤーが表示中か確認
     * 
     * @param layerType レイヤータイプ
     * @return 表示中ならtrue
     */
    public boolean isLayerVisible(@NonNull LayerType layerType) {
        return layerVisibility.getOrDefault(layerType, false);
    }

    /**
     * レイヤーデータが読み込み済みか確認
     * 
     * @param layerType レイヤータイプ
     * @return 読み込み済みならtrue
     */
    public boolean isLayerLoaded(@NonNull LayerType layerType) {
        List<Polygon> polygons = layerPolygons.get(layerType);
        return polygons != null && !polygons.isEmpty();
    }

    /**
     * レイヤーのPolygon数を取得
     * 
     * @param layerType レイヤータイプ
     * @return Polygon数（読み込まれていない場合は0）
     */
    public int getLayerPolygonCount(@NonNull LayerType layerType) {
        List<Polygon> polygons = layerPolygons.get(layerType);
        return polygons != null ? polygons.size() : 0;
    }

    /**
     * 表示スタイルを変更
     * 
     * @param style 新しい表示スタイル
     */
    public void setDisplayStyle(@NonNull LayerDisplayStyle style) {
        if (currentStyle == style) {
            return;  // 変更なし
        }

        currentStyle = style;

        // SharedPreferencesに保存
        preferences.edit()
                .putString(PREF_LAYER_STYLE, style.getId())
                .apply();

        // 全レイヤーのスタイルを更新
        refreshAllLayerStyles();

        // リスナーに通知
        if (layerChangeListener != null) {
            layerChangeListener.onDisplayStyleChanged(style);
        }

        Log.d(TAG, "表示スタイル変更: " + style.getId());
    }

    /**
     * 現在の表示スタイルを取得
     * 
     * @return 現在の表示スタイル
     */
    @NonNull
    public LayerDisplayStyle getDisplayStyle() {
        return currentStyle;
    }

    /**
     * 全レイヤーのスタイルを再適用
     */
    private void refreshAllLayerStyles() {
        for (Map.Entry<LayerType, List<Polygon>> entry : layerPolygons.entrySet()) {
            LayerType type = entry.getKey();
            List<Polygon> polygons = entry.getValue();

            // 一度削除して再追加（スタイル変更を反映）
            if (isLayerVisible(type)) {
                removePolygonsFromMap(polygons);
            }

            // 新しいスタイルで再パース（キャッシュされたGeoJSONが必要なため、
            // 実際にはPolygonのスタイルプロパティを直接変更）
            for (Polygon polygon : polygons) {
                applyStyleToPolygon(polygon, type);
            }

            if (isLayerVisible(type)) {
                addPolygonsToMap(polygons);
            }
        }

        mapView.invalidate();
    }

    /**
     * Polygonにスタイルを適用
     * 
     * @param polygon 対象Polygon
     * @param type レイヤータイプ
     */
    private void applyStyleToPolygon(@NonNull Polygon polygon, @NonNull LayerType type) {
        int fillColor = type.getFillColor();
        int strokeColor = type.getStrokeColor();

        switch (currentStyle) {
            case FILLED:
                polygon.setFillColor(fillColor);
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(2.0f);
                break;

            case BORDER_ONLY:
                polygon.setFillColor(android.graphics.Color.TRANSPARENT);
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(3.0f);
                break;

            case HATCHED:
                polygon.setFillColor(android.graphics.Color.argb(50,
                        android.graphics.Color.red(fillColor),
                        android.graphics.Color.green(fillColor),
                        android.graphics.Color.blue(fillColor)));
                polygon.setStrokeColor(strokeColor);
                polygon.setStrokeWidth(4.0f);
                break;
        }
    }

    /**
     * PolygonリストをMapViewに追加
     * 
     * @param polygons Polygonリスト
     */
    private void addPolygonsToMap(@NonNull List<Polygon> polygons) {
        // ヒートマップや現在位置マーカーの下に配置するため、先頭付近に追加
        int insertIndex = Math.min(mapView.getOverlays().size(), 1);
        
        for (Polygon polygon : polygons) {
            if (!mapView.getOverlays().contains(polygon)) {
                mapView.getOverlays().add(insertIndex, polygon);
            }
        }
        mapView.invalidate();
    }

    /**
     * PolygonリストをMapViewから削除
     * 
     * @param polygons Polygonリスト
     */
    private void removePolygonsFromMap(@NonNull List<Polygon> polygons) {
        for (Polygon polygon : polygons) {
            mapView.getOverlays().remove(polygon);
        }
        mapView.invalidate();
    }

    /**
     * 各レイヤータイプの表示状態リストを取得
     * 
     * @return レイヤータイプと表示状態のマップ
     */
    @NonNull
    public Map<LayerType, Boolean> getLayerVisibilityMap() {
        return new EnumMap<>(layerVisibility);
    }

    /**
     * 読み込み済みのレイヤータイプリストを取得
     * 
     * @return 読み込み済みのレイヤータイプリスト
     */
    @NonNull
    public List<LayerType> getLoadedLayers() {
        List<LayerType> loaded = new ArrayList<>();
        for (LayerType type : LayerType.values()) {
            if (isLayerLoaded(type)) {
                loaded.add(type);
            }
        }
        return loaded;
    }

    /**
     * 指定レイヤーの最初のPolygonの中心座標を取得
     * 
     * @param layerType レイヤータイプ
     * @return 中心座標、レイヤーが存在しない場合はnull
     */
    @Nullable
    public GeoPoint getLayerCenter(@NonNull LayerType layerType) {
        List<Polygon> polygons = layerPolygons.get(layerType);
        if (polygons == null || polygons.isEmpty()) {
            return null;
        }

        // 最初のPolygonの中心を計算
        Polygon firstPolygon = polygons.get(0);
        List<GeoPoint> points = firstPolygon.getActualPoints();
        if (points == null || points.isEmpty()) {
            return null;
        }

        double latSum = 0;
        double lonSum = 0;
        for (GeoPoint point : points) {
            latSum += point.getLatitude();
            lonSum += point.getLongitude();
        }

        double centerLat = latSum / points.size();
        double centerLon = lonSum / points.size();

        return new GeoPoint(centerLat, centerLon);
    }

    /**
     * 指定レイヤーの境界ボックスを取得
     * 
     * @param layerType レイヤータイプ
     * @return 境界ボックス、レイヤーが存在しない場合はnull
     */
    @Nullable
    public BoundingBox getLayerBounds(@NonNull LayerType layerType) {
        List<Polygon> polygons = layerPolygons.get(layerType);
        if (polygons == null || polygons.isEmpty()) {
            return null;
        }

        double minLat = Double.MAX_VALUE;
        double maxLat = -Double.MAX_VALUE;
        double minLon = Double.MAX_VALUE;
        double maxLon = -Double.MAX_VALUE;

        for (Polygon polygon : polygons) {
            List<GeoPoint> points = polygon.getActualPoints();
            if (points != null) {
                for (GeoPoint point : points) {
                    minLat = Math.min(minLat, point.getLatitude());
                    maxLat = Math.max(maxLat, point.getLatitude());
                    minLon = Math.min(minLon, point.getLongitude());
                    maxLon = Math.max(maxLon, point.getLongitude());
                }
            }
        }

        if (minLat == Double.MAX_VALUE) {
            return null;
        }

        return new BoundingBox(maxLat, maxLon, minLat, minLon);
    }

    /**
     * 指定レイヤーの範囲にマップをズーム
     * 
     * @param layerType レイヤータイプ
     * @return ズーム成功ならtrue
     */
    public boolean zoomToLayer(@NonNull LayerType layerType) {
        GeoPoint center = getLayerCenter(layerType);
        if (center != null) {
            mapView.getController().animateTo(center);
            mapView.getController().setZoom(14.0);
            Log.d(TAG, "レイヤーにズーム: " + layerType.getId() + " -> " + center);
            return true;
        }
        return false;
    }
}

