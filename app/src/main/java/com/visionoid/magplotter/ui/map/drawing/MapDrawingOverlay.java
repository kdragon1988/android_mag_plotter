/**
 * MapDrawingOverlay.java
 * 
 * VISIONOID MAG PLOTTER - 地図作図オーバーレイ
 * 
 * 概要:
 *   地図上に多角形、線、円を描画するためのカスタムオーバーレイ。
 *   タッチイベントを処理して作図機能を提供。
 * 
 * 主な仕様:
 *   - タップで頂点を追加
 *   - 描画中のプレビュー表示
 *   - 各辺の長さをリアルタイム表示
 *   - 面積・周囲長のリアルタイム計算
 *   - 頂点のドラッグ編集
 * 
 * 制限事項:
 *   - 一度に1つの図形のみ作図可能
 */
package com.visionoid.magplotter.ui.map.drawing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.view.MotionEvent;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import com.visionoid.magplotter.util.GeoCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 地図作図オーバーレイクラス
 */
public class MapDrawingOverlay extends Overlay {

    /** コンテキスト */
    private final Context context;

    /** 現在の作図モード */
    private DrawingMode drawingMode = DrawingMode.NONE;

    /** 描画中の頂点リスト */
    private List<GeoPoint> currentPoints = new ArrayList<>();

    /** 円の中心（円描画時） */
    private GeoPoint circleCenter = null;

    /** 円の半径（メートル） */
    private double circleRadius = 0;

    /** 描画中フラグ */
    private boolean isDrawing = false;

    // ==================== ペイント ====================

    /** 線のペイント */
    private final Paint linePaint;

    /** 塗りつぶしのペイント */
    private final Paint fillPaint;

    /** 頂点のペイント */
    private final Paint vertexPaint;

    /** テキストのペイント */
    private final Paint textPaint;

    /** テキスト背景のペイント */
    private final Paint textBgPaint;

    /** プレビュー線のペイント（破線） */
    private final Paint previewPaint;

    // ==================== 色設定 ====================

    /** 線の色 */
    private int strokeColor = 0xFFFF5722;

    /** 塗りつぶしの色 */
    private int fillColor = 0x40FF5722;

    /** 頂点の色 */
    private int vertexColor = 0xFFFFFFFF;

    // ==================== 編集関連 ====================

    /** ドラッグ中の頂点インデックス */
    private int draggingVertexIndex = -1;

    /** 頂点タッチ判定半径（ピクセル） */
    private static final float VERTEX_TOUCH_RADIUS = 40f;

    /** 頂点描画半径（ピクセル） */
    private static final float VERTEX_RADIUS = 12f;

    // ==================== タップ判定関連 ====================

    /** タッチ開始位置X */
    private float touchDownX = 0;

    /** タッチ開始位置Y */
    private float touchDownY = 0;

    /** タップ判定の移動閾値（ピクセル） */
    private static final float TAP_THRESHOLD = 20f;

    /** スワイプ中フラグ */
    private boolean isSwiping = false;

    // ==================== コールバック ====================

    /** 描画完了コールバック */
    private DrawingCompleteListener drawingCompleteListener;

    /** 計測値更新コールバック */
    private MeasurementUpdateListener measurementUpdateListener;

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public MapDrawingOverlay(Context context) {
        this.context = context;

        // 線のペイント
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(strokeColor);
        linePaint.setStrokeWidth(6f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // 塗りつぶしのペイント
        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(fillColor);
        fillPaint.setStyle(Paint.Style.FILL);

        // 頂点のペイント
        vertexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        vertexPaint.setColor(vertexColor);
        vertexPaint.setStyle(Paint.Style.FILL);

        // テキストのペイント
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(32f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // テキスト背景のペイント
        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(0xCC000000);
        textBgPaint.setStyle(Paint.Style.FILL);

        // プレビュー線のペイント（破線）
        previewPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        previewPaint.setColor(0x80FF5722);
        previewPaint.setStrokeWidth(4f);
        previewPaint.setStyle(Paint.Style.STROKE);
        previewPaint.setPathEffect(new DashPathEffect(new float[]{20, 10}, 0));
    }

    // ==================== 作図モード制御 ====================

    /**
     * 作図モードを設定
     * 
     * @param mode 作図モード
     */
    public void setDrawingMode(DrawingMode mode) {
        this.drawingMode = mode;
        if (mode != DrawingMode.NONE && mode != DrawingMode.EDIT) {
            // 新しい作図開始
            clearCurrentDrawing();
            isDrawing = true;
        }
    }

    /**
     * 作図モードを取得
     * 
     * @return 現在の作図モード
     */
    public DrawingMode getDrawingMode() {
        return drawingMode;
    }

    /**
     * 作図中かどうかを取得
     * 
     * @return 作図中の場合true
     */
    public boolean isDrawing() {
        return isDrawing;
    }

    /**
     * 現在の描画をクリア
     */
    public void clearCurrentDrawing() {
        currentPoints.clear();
        circleCenter = null;
        circleRadius = 0;
        draggingVertexIndex = -1;
        notifyMeasurementUpdate();
    }

    /**
     * 作図を完了
     */
    public void completeDrawing() {
        if (!isDrawing) return;

        isDrawing = false;

        if (drawingCompleteListener != null) {
            switch (drawingMode) {
                case POLYGON:
                    if (currentPoints.size() >= 3) {
                        drawingCompleteListener.onPolygonComplete(new ArrayList<>(currentPoints));
                    }
                    break;
                case POLYLINE:
                    if (currentPoints.size() >= 2) {
                        drawingCompleteListener.onPolylineComplete(new ArrayList<>(currentPoints));
                    }
                    break;
                case CIRCLE:
                    if (circleCenter != null && circleRadius > 0) {
                        drawingCompleteListener.onCircleComplete(circleCenter, circleRadius);
                    }
                    break;
            }
        }

        drawingMode = DrawingMode.NONE;
        clearCurrentDrawing();
    }

    /**
     * 作図をキャンセル
     */
    public void cancelDrawing() {
        isDrawing = false;
        drawingMode = DrawingMode.NONE;
        clearCurrentDrawing();
    }

    /**
     * 最後の頂点を削除（Undo）
     */
    public void undoLastPoint() {
        if (!currentPoints.isEmpty()) {
            currentPoints.remove(currentPoints.size() - 1);
            notifyMeasurementUpdate();
        }
    }

    // ==================== 色設定 ====================

    /**
     * 線の色を設定
     * 
     * @param color 色（ARGB）
     */
    public void setStrokeColor(int color) {
        this.strokeColor = color;
        linePaint.setColor(color);
        previewPaint.setColor((color & 0x00FFFFFF) | 0x80000000);
    }

    /**
     * 塗りつぶしの色を設定
     * 
     * @param color 色（ARGB）
     */
    public void setFillColor(int color) {
        this.fillColor = color;
        fillPaint.setColor(color);
    }

    // ==================== 描画 ====================

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        Projection projection = mapView.getProjection();

        switch (drawingMode) {
            case POLYGON:
                drawPolygon(canvas, projection);
                break;
            case POLYLINE:
                drawPolyline(canvas, projection);
                break;
            case CIRCLE:
                drawCircle(canvas, projection, mapView);
                break;
            case EDIT:
                drawPolygon(canvas, projection);
                break;
        }
    }

    /**
     * 多角形を描画
     */
    private void drawPolygon(Canvas canvas, Projection projection) {
        if (currentPoints.isEmpty()) return;

        Point[] screenPoints = new Point[currentPoints.size()];
        for (int i = 0; i < currentPoints.size(); i++) {
            screenPoints[i] = projection.toPixels(currentPoints.get(i), null);
        }

        // 塗りつぶし（3点以上）
        if (currentPoints.size() >= 3) {
            Path fillPath = new Path();
            fillPath.moveTo(screenPoints[0].x, screenPoints[0].y);
            for (int i = 1; i < screenPoints.length; i++) {
                fillPath.lineTo(screenPoints[i].x, screenPoints[i].y);
            }
            fillPath.close();
            canvas.drawPath(fillPath, fillPaint);
        }

        // 線を描画
        for (int i = 0; i < screenPoints.length; i++) {
            Point p1 = screenPoints[i];
            Point p2 = screenPoints[(i + 1) % screenPoints.length];
            
            // 閉じた線は3点以上の場合のみ
            if (i < screenPoints.length - 1 || currentPoints.size() >= 3) {
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);
                
                // 辺の長さを表示
                GeoPoint g1 = currentPoints.get(i);
                GeoPoint g2 = currentPoints.get((i + 1) % currentPoints.size());
                double distance = GeoCalculator.calculateDistance(g1, g2);
                String distText = GeoCalculator.formatDistanceShort(distance);
                drawEdgeLabel(canvas, p1, p2, distText);
            }
        }

        // 頂点を描画
        for (int i = 0; i < screenPoints.length; i++) {
            drawVertex(canvas, screenPoints[i], i);
        }
    }

    /**
     * ポリラインを描画
     */
    private void drawPolyline(Canvas canvas, Projection projection) {
        if (currentPoints.isEmpty()) return;

        Point[] screenPoints = new Point[currentPoints.size()];
        for (int i = 0; i < currentPoints.size(); i++) {
            screenPoints[i] = projection.toPixels(currentPoints.get(i), null);
        }

        // 線を描画
        for (int i = 0; i < screenPoints.length - 1; i++) {
            Point p1 = screenPoints[i];
            Point p2 = screenPoints[i + 1];
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);
            
            // 辺の長さを表示
            GeoPoint g1 = currentPoints.get(i);
            GeoPoint g2 = currentPoints.get(i + 1);
            double distance = GeoCalculator.calculateDistance(g1, g2);
            String distText = GeoCalculator.formatDistanceShort(distance);
            drawEdgeLabel(canvas, p1, p2, distText);
        }

        // 頂点を描画
        for (int i = 0; i < screenPoints.length; i++) {
            drawVertex(canvas, screenPoints[i], i);
        }
    }

    /**
     * 円を描画
     */
    private void drawCircle(Canvas canvas, Projection projection, MapView mapView) {
        if (circleCenter == null) return;

        Point centerPoint = projection.toPixels(circleCenter, null);

        if (circleRadius > 0) {
            // 半径をピクセルに変換
            GeoPoint edgePoint = calculateDestination(circleCenter, 90, circleRadius);
            Point edgePointScreen = projection.toPixels(edgePoint, null);
            float radiusPx = Math.abs(edgePointScreen.x - centerPoint.x);

            // 塗りつぶし
            canvas.drawCircle(centerPoint.x, centerPoint.y, radiusPx, fillPaint);
            // 線
            canvas.drawCircle(centerPoint.x, centerPoint.y, radiusPx, linePaint);

            // 半径ラベル
            String radiusText = GeoCalculator.formatDistanceShort(circleRadius);
            Point labelPoint = new Point(centerPoint.x + (int) (radiusPx / 2), centerPoint.y);
            drawLabel(canvas, labelPoint, "r=" + radiusText);
        }

        // 中心点を描画
        drawVertex(canvas, centerPoint, 0);
    }

    /**
     * 頂点を描画
     */
    private void drawVertex(Canvas canvas, Point point, int index) {
        // 外枠（黒）
        Paint outerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        outerPaint.setColor(0xFF000000);
        outerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(point.x, point.y, VERTEX_RADIUS + 3, outerPaint);

        // 内側（白）
        canvas.drawCircle(point.x, point.y, VERTEX_RADIUS, vertexPaint);

        // 色付き中心
        Paint centerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        centerPaint.setColor(strokeColor);
        centerPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(point.x, point.y, VERTEX_RADIUS - 4, centerPaint);
    }

    /**
     * 辺のラベルを描画
     */
    private void drawEdgeLabel(Canvas canvas, Point p1, Point p2, String text) {
        int midX = (p1.x + p2.x) / 2;
        int midY = (p1.y + p2.y) / 2;
        drawLabel(canvas, new Point(midX, midY), text);
    }

    /**
     * ラベルを描画
     */
    private void drawLabel(Canvas canvas, Point point, String text) {
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);

        int padding = 8;
        int bgLeft = point.x - bounds.width() / 2 - padding;
        int bgTop = point.y - bounds.height() - padding;
        int bgRight = point.x + bounds.width() / 2 + padding;
        int bgBottom = point.y + padding;

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8, 8, textBgPaint);
        canvas.drawText(text, point.x, point.y, textPaint);
    }

    // ==================== タッチイベント ====================

    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView) {
        if (drawingMode == DrawingMode.NONE) {
            return false;
        }

        Projection projection = mapView.getProjection();
        GeoPoint touchPoint = (GeoPoint) projection.fromPixels((int) event.getX(), (int) event.getY());

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                return handleActionDown(event, mapView, touchPoint);

            case MotionEvent.ACTION_MOVE:
                return handleActionMove(event, mapView, touchPoint);

            case MotionEvent.ACTION_UP:
                return handleActionUp(event, mapView, touchPoint);
        }

        return false;
    }

    /**
     * ACTION_DOWNを処理
     */
    private boolean handleActionDown(MotionEvent event, MapView mapView, GeoPoint touchPoint) {
        Projection projection = mapView.getProjection();

        // タッチ開始位置を記録
        touchDownX = event.getX();
        touchDownY = event.getY();
        isSwiping = false;

        // 編集モード: 頂点のドラッグ開始判定
        if (drawingMode == DrawingMode.EDIT) {
            draggingVertexIndex = findNearestVertexIndex(event, projection);
            return draggingVertexIndex >= 0;
        }

        // 円描画モードで中心未設定の場合は、ACTION_UPで設定するのでここでは何もしない
        // （スワイプと区別するため）

        return false;
    }

    /**
     * ACTION_MOVEを処理
     */
    private boolean handleActionMove(MotionEvent event, MapView mapView, GeoPoint touchPoint) {
        // 移動距離をチェックしてスワイプ判定
        float dx = event.getX() - touchDownX;
        float dy = event.getY() - touchDownY;
        float distance = (float) Math.sqrt(dx * dx + dy * dy);
        
        if (distance > TAP_THRESHOLD) {
            isSwiping = true;
        }

        // 編集モード: 頂点のドラッグ
        if (drawingMode == DrawingMode.EDIT && draggingVertexIndex >= 0) {
            currentPoints.set(draggingVertexIndex, touchPoint);
            notifyMeasurementUpdate();
            mapView.invalidate();
            return true;
        }

        // 円描画モード: 半径の調整（中心設定済みの場合のみ）
        if (drawingMode == DrawingMode.CIRCLE && circleCenter != null) {
            circleRadius = GeoCalculator.calculateDistance(circleCenter, touchPoint);
            notifyMeasurementUpdate();
            mapView.invalidate();
            return true;
        }

        // スワイプ中は地図のスクロールを許可（falseを返す）
        return false;
    }

    /**
     * ACTION_UPを処理
     */
    private boolean handleActionUp(MotionEvent event, MapView mapView, GeoPoint touchPoint) {
        // 編集モード: ドラッグ終了
        if (drawingMode == DrawingMode.EDIT && draggingVertexIndex >= 0) {
            draggingVertexIndex = -1;
            return true;
        }

        // スワイプの場合は点を追加しない
        if (isSwiping) {
            isSwiping = false;
            return false;
        }

        // タップと判定された場合のみ処理

        // ポリゴン/ポリライン: 頂点追加
        if (drawingMode == DrawingMode.POLYGON || drawingMode == DrawingMode.POLYLINE) {
            currentPoints.add(touchPoint);
            notifyMeasurementUpdate();
            mapView.invalidate();
            return true;
        }

        // 円描画モード
        if (drawingMode == DrawingMode.CIRCLE) {
            if (circleCenter == null) {
                // 中心を設定
                circleCenter = touchPoint;
                mapView.invalidate();
                return true;
            } else {
                // 半径を確定
                circleRadius = GeoCalculator.calculateDistance(circleCenter, touchPoint);
                notifyMeasurementUpdate();
                mapView.invalidate();
                return true;
            }
        }

        return false;
    }

    /**
     * 最も近い頂点のインデックスを探す
     */
    private int findNearestVertexIndex(MotionEvent event, Projection projection) {
        float touchX = event.getX();
        float touchY = event.getY();

        for (int i = 0; i < currentPoints.size(); i++) {
            Point screenPoint = projection.toPixels(currentPoints.get(i), null);
            float dx = touchX - screenPoint.x;
            float dy = touchY - screenPoint.y;
            float distance = (float) Math.sqrt(dx * dx + dy * dy);

            if (distance <= VERTEX_TOUCH_RADIUS) {
                return i;
            }
        }

        return -1;
    }

    // ==================== 計測通知 ====================

    /**
     * 計測値更新を通知
     */
    private void notifyMeasurementUpdate() {
        if (measurementUpdateListener == null) return;

        double area = 0;
        double perimeter = 0;
        List<Double> segmentLengths = new ArrayList<>();

        switch (drawingMode) {
            case POLYGON:
            case EDIT:
                if (currentPoints.size() >= 3) {
                    area = GeoCalculator.calculatePolygonArea(currentPoints);
                    perimeter = GeoCalculator.calculatePolygonPerimeter(currentPoints);
                    segmentLengths = GeoCalculator.calculatePolygonSegmentLengths(currentPoints);
                } else if (currentPoints.size() >= 2) {
                    perimeter = GeoCalculator.calculatePolylineLength(currentPoints);
                    segmentLengths = GeoCalculator.calculateSegmentLengths(currentPoints);
                }
                break;

            case POLYLINE:
                if (currentPoints.size() >= 2) {
                    perimeter = GeoCalculator.calculatePolylineLength(currentPoints);
                    segmentLengths = GeoCalculator.calculateSegmentLengths(currentPoints);
                }
                break;

            case CIRCLE:
                if (circleRadius > 0) {
                    area = GeoCalculator.calculateCircleArea(circleRadius);
                    perimeter = GeoCalculator.calculateCirclePerimeter(circleRadius);
                }
                break;
        }

        measurementUpdateListener.onMeasurementUpdate(area, perimeter, segmentLengths);
    }

    // ==================== ユーティリティ ====================

    /**
     * 目的地点を計算（方位角と距離から）
     */
    private GeoPoint calculateDestination(GeoPoint start, double bearing, double distance) {
        double lat1 = Math.toRadians(start.getLatitude());
        double lng1 = Math.toRadians(start.getLongitude());
        double angularDistance = distance / 6371000.0;
        double bearingRad = Math.toRadians(bearing);

        double lat2 = Math.asin(Math.sin(lat1) * Math.cos(angularDistance) +
                Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearingRad));
        double lng2 = lng1 + Math.atan2(
                Math.sin(bearingRad) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2));

        return new GeoPoint(Math.toDegrees(lat2), Math.toDegrees(lng2));
    }

    /**
     * 現在の頂点リストを取得
     * 
     * @return 頂点リストのコピー
     */
    public List<GeoPoint> getCurrentPoints() {
        return new ArrayList<>(currentPoints);
    }

    /**
     * 頂点リストを設定（編集用）
     * 
     * @param points 頂点リスト
     */
    public void setCurrentPoints(List<GeoPoint> points) {
        currentPoints.clear();
        if (points != null) {
            currentPoints.addAll(points);
        }
        notifyMeasurementUpdate();
    }

    /**
     * 円の中心を取得
     * 
     * @return 円の中心
     */
    public GeoPoint getCircleCenter() {
        return circleCenter;
    }

    /**
     * 円の半径を取得
     * 
     * @return 半径（メートル）
     */
    public double getCircleRadius() {
        return circleRadius;
    }

    /**
     * 頂点数を取得
     * 
     * @return 頂点数
     */
    public int getPointCount() {
        return currentPoints.size();
    }

    // ==================== リスナー設定 ====================

    /**
     * 描画完了リスナーを設定
     */
    public void setDrawingCompleteListener(DrawingCompleteListener listener) {
        this.drawingCompleteListener = listener;
    }

    /**
     * 計測値更新リスナーを設定
     */
    public void setMeasurementUpdateListener(MeasurementUpdateListener listener) {
        this.measurementUpdateListener = listener;
    }

    // ==================== リスナーインターフェース ====================

    /**
     * 描画完了リスナー
     */
    public interface DrawingCompleteListener {
        void onPolygonComplete(List<GeoPoint> points);
        void onPolylineComplete(List<GeoPoint> points);
        void onCircleComplete(GeoPoint center, double radius);
    }

    /**
     * 計測値更新リスナー
     */
    public interface MeasurementUpdateListener {
        void onMeasurementUpdate(double area, double perimeter, List<Double> segmentLengths);
    }
}

