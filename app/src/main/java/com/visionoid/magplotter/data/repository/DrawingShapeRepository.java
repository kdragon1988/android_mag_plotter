/**
 * DrawingShapeRepository.java
 * 
 * VISIONOID MAG PLOTTER - 作図シェイプリポジトリ
 * 
 * 概要:
 *   DrawingShapeエンティティに対するリポジトリクラス。
 *   DAOをラップし、バックグラウンドスレッドでの実行を管理。
 * 
 * 主な仕様:
 *   - 非同期でのデータベース操作
 *   - LiveDataによるリアクティブ更新
 *   - JSON形式の座標データのパース
 * 
 * 制限事項:
 *   - アプリケーションコンテキストが必要
 */
package com.visionoid.magplotter.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.visionoid.magplotter.data.dao.DrawingShapeDao;
import com.visionoid.magplotter.data.db.AppDatabase;
import com.visionoid.magplotter.data.model.DrawingShape;
import com.visionoid.magplotter.util.GeoCalculator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.util.GeoPoint;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 作図シェイプリポジトリクラス
 */
public class DrawingShapeRepository {

    /** DAO */
    private final DrawingShapeDao drawingShapeDao;

    /** バックグラウンド実行用Executor */
    private final ExecutorService executorService;

    /**
     * コンストラクタ
     * 
     * @param application アプリケーション
     */
    public DrawingShapeRepository(Application application) {
        AppDatabase database = AppDatabase.getInstance(application);
        this.drawingShapeDao = database.drawingShapeDao();
        this.executorService = Executors.newSingleThreadExecutor();
    }

    // ==================== 挿入 ====================

    /**
     * シェイプを挿入
     * 
     * @param shape シェイプ
     * @param callback 完了コールバック（挿入されたID）
     */
    public void insert(DrawingShape shape, InsertCallback callback) {
        executorService.execute(() -> {
            // 面積・周囲長を計算
            calculateAndSetMetrics(shape);
            long id = drawingShapeDao.insert(shape);
            if (callback != null) {
                callback.onInserted(id);
            }
        });
    }

    /**
     * シェイプを挿入（同期）
     * 
     * @param shape シェイプ
     * @return 挿入されたID
     */
    public long insertSync(DrawingShape shape) {
        calculateAndSetMetrics(shape);
        return drawingShapeDao.insert(shape);
    }

    // ==================== 更新 ====================

    /**
     * シェイプを更新
     * 
     * @param shape シェイプ
     */
    public void update(DrawingShape shape) {
        executorService.execute(() -> {
            shape.updateTimestamp();
            calculateAndSetMetrics(shape);
            drawingShapeDao.update(shape);
        });
    }

    /**
     * シェイプの座標を更新
     * 
     * @param shapeId シェイプID
     * @param coordinatesJson 座標JSON
     */
    public void updateCoordinates(long shapeId, String coordinatesJson) {
        executorService.execute(() -> {
            DrawingShape shape = drawingShapeDao.getById(shapeId);
            if (shape != null) {
                shape.setCoordinatesJson(coordinatesJson);
                calculateAndSetMetrics(shape);
                drawingShapeDao.updateCoordinates(
                        shapeId,
                        coordinatesJson,
                        shape.getArea(),
                        shape.getPerimeter(),
                        System.currentTimeMillis()
                );
            }
        });
    }

    /**
     * シェイプ名を更新
     * 
     * @param shapeId シェイプID
     * @param name 新しい名前
     */
    public void updateName(long shapeId, String name) {
        executorService.execute(() -> {
            drawingShapeDao.updateName(shapeId, name, System.currentTimeMillis());
        });
    }

    /**
     * シェイプの表示状態を更新
     * 
     * @param shapeId シェイプID
     * @param visible 表示状態
     */
    public void updateVisibility(long shapeId, boolean visible) {
        executorService.execute(() -> {
            drawingShapeDao.updateVisibility(shapeId, visible, System.currentTimeMillis());
        });
    }

    /**
     * シェイプの色を更新
     * 
     * @param shapeId シェイプID
     * @param fillColor 塗りつぶし色
     * @param strokeColor 線の色
     */
    public void updateColors(long shapeId, int fillColor, int strokeColor) {
        executorService.execute(() -> {
            drawingShapeDao.updateColors(shapeId, fillColor, strokeColor, System.currentTimeMillis());
        });
    }

    // ==================== 削除 ====================

    /**
     * シェイプを削除
     * 
     * @param shape シェイプ
     */
    public void delete(DrawingShape shape) {
        executorService.execute(() -> {
            drawingShapeDao.delete(shape);
        });
    }

    /**
     * IDでシェイプを削除
     * 
     * @param shapeId シェイプID
     */
    public void deleteById(long shapeId) {
        executorService.execute(() -> {
            drawingShapeDao.deleteById(shapeId);
        });
    }

    /**
     * ミッションの全シェイプを削除
     * 
     * @param missionId ミッションID
     */
    public void deleteByMissionId(long missionId) {
        executorService.execute(() -> {
            drawingShapeDao.deleteByMissionId(missionId);
        });
    }

    // ==================== 取得 ====================

    /**
     * ミッションの全シェイプを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return シェイプリストのLiveData
     */
    public LiveData<List<DrawingShape>> getByMissionIdLive(long missionId) {
        return drawingShapeDao.getByMissionIdLive(missionId);
    }

    /**
     * ミッションの表示中シェイプを取得（LiveData）
     * 
     * @param missionId ミッションID
     * @return 表示中シェイプリストのLiveData
     */
    public LiveData<List<DrawingShape>> getVisibleByMissionIdLive(long missionId) {
        return drawingShapeDao.getVisibleByMissionIdLive(missionId);
    }

    /**
     * IDでシェイプを取得（LiveData）
     * 
     * @param shapeId シェイプID
     * @return シェイプのLiveData
     */
    public LiveData<DrawingShape> getByIdLive(long shapeId) {
        return drawingShapeDao.getByIdLive(shapeId);
    }

    // ==================== 座標パース ====================

    /**
     * 座標JSONをGeoPointリストにパース
     * 
     * @param coordinatesJson 座標JSON
     * @return GeoPointリスト
     */
    public static List<GeoPoint> parseCoordinatesJson(String coordinatesJson) {
        List<GeoPoint> points = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(coordinatesJson);
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject pointJson = jsonArray.getJSONObject(i);
                double lat = pointJson.getDouble("lat");
                double lng = pointJson.getDouble("lng");
                points.add(new GeoPoint(lat, lng));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return points;
    }

    /**
     * GeoPointリストを座標JSONに変換
     * 
     * @param points GeoPointリスト
     * @return 座標JSON
     */
    public static String toCoordinatesJson(List<GeoPoint> points) {
        JSONArray jsonArray = new JSONArray();
        try {
            for (GeoPoint point : points) {
                JSONObject pointJson = new JSONObject();
                pointJson.put("lat", point.getLatitude());
                pointJson.put("lng", point.getLongitude());
                jsonArray.put(pointJson);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonArray.toString();
    }

    /**
     * 円の座標JSONをパース
     * 
     * @param coordinatesJson 座標JSON
     * @return [0]: 中心GeoPoint, [1]: 半径（メートル）をDouble.valueOf()でラップ
     */
    public static Object[] parseCircleJson(String coordinatesJson) {
        try {
            JSONObject json = new JSONObject(coordinatesJson);
            JSONObject centerJson = json.getJSONObject("center");
            double lat = centerJson.getDouble("lat");
            double lng = centerJson.getDouble("lng");
            double radius = json.getDouble("radius");
            return new Object[]{new GeoPoint(lat, lng), radius};
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 円の座標JSONを生成
     * 
     * @param center 中心座標
     * @param radius 半径（メートル）
     * @return 座標JSON
     */
    public static String toCircleJson(GeoPoint center, double radius) {
        try {
            JSONObject json = new JSONObject();
            JSONObject centerJson = new JSONObject();
            centerJson.put("lat", center.getLatitude());
            centerJson.put("lng", center.getLongitude());
            json.put("center", centerJson);
            json.put("radius", radius);
            return json.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{}";
        }
    }

    // ==================== メトリクス計算 ====================

    /**
     * シェイプの面積・周囲長を計算して設定
     * 
     * @param shape シェイプ
     */
    private void calculateAndSetMetrics(DrawingShape shape) {
        switch (shape.getShapeType()) {
            case DrawingShape.TYPE_POLYGON:
                List<GeoPoint> polygonPoints = parseCoordinatesJson(shape.getCoordinatesJson());
                if (polygonPoints.size() >= 3) {
                    shape.setArea(GeoCalculator.calculatePolygonArea(polygonPoints));
                    shape.setPerimeter(GeoCalculator.calculatePolygonPerimeter(polygonPoints));
                }
                break;

            case DrawingShape.TYPE_POLYLINE:
                List<GeoPoint> linePoints = parseCoordinatesJson(shape.getCoordinatesJson());
                if (linePoints.size() >= 2) {
                    shape.setArea(0.0);
                    shape.setPerimeter(GeoCalculator.calculatePolylineLength(linePoints));
                }
                break;

            case DrawingShape.TYPE_CIRCLE:
                Object[] circleData = parseCircleJson(shape.getCoordinatesJson());
                if (circleData != null) {
                    double radius = (Double) circleData[1];
                    shape.setArea(GeoCalculator.calculateCircleArea(radius));
                    shape.setPerimeter(GeoCalculator.calculateCirclePerimeter(radius));
                }
                break;
        }
    }

    // ==================== コールバックインターフェース ====================

    /**
     * 挿入完了コールバック
     */
    public interface InsertCallback {
        /**
         * 挿入完了時に呼ばれる
         * 
         * @param id 挿入されたシェイプのID
         */
        void onInserted(long id);
    }
}


