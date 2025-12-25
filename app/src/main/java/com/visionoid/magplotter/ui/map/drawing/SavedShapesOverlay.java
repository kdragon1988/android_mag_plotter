/**
 * SavedShapesOverlay.java
 * 
 * VISIONOID MAG PLOTTER - 保存済み図形オーバーレイ
 * 
 * 概要:
 *   データベースに保存された図形を地図上に表示するオーバーレイ。
 *   名称、面積、各辺の長さをラベル表示。
 * 
 * 主な仕様:
 *   - 複数の図形を同時表示
 *   - 図形ごとに色分け可能
 *   - タップで図形選択
 *   - 名称・計測値のラベル表示
 * 
 * 制限事項:
 *   - 大量の図形表示時はパフォーマンスに注意
 */
package com.visionoid.magplotter.ui.map.drawing;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.view.MotionEvent;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay;

import com.visionoid.magplotter.data.model.DrawingShape;
import com.visionoid.magplotter.data.repository.DrawingShapeRepository;
import com.visionoid.magplotter.util.GeoCalculator;

import java.util.ArrayList;
import java.util.List;

/**
 * 保存済み図形オーバーレイクラス
 */
public class SavedShapesOverlay extends Overlay {

    /** コンテキスト */
    private final Context context;

    /** 表示する図形リスト */
    private List<DrawingShape> shapes = new ArrayList<>();

    /** 選択中の図形ID */
    private long selectedShapeId = -1;

    /** 辺の長さ表示フラグ */
    private boolean showEdgeLengths = true;

    /** 名称表示フラグ */
    private boolean showNames = true;

    /** 面積表示フラグ */
    private boolean showArea = true;

    // ==================== ペイント ====================

    /** テキストのペイント */
    private final Paint textPaint;

    /** 名称テキストのペイント */
    private final Paint namePaint;

    /** テキスト背景のペイント */
    private final Paint textBgPaint;

    /** 選択枠のペイント */
    private final Paint selectionPaint;

    // ==================== コールバック ====================

    /** 図形タップリスナー */
    private ShapeTapListener shapeTapListener;

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public SavedShapesOverlay(Context context) {
        this.context = context;

        // テキストのペイント
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(28f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // 名称テキストのペイント
        namePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        namePaint.setColor(Color.WHITE);
        namePaint.setTextSize(36f);
        namePaint.setTextAlign(Paint.Align.CENTER);
        namePaint.setTypeface(Typeface.DEFAULT_BOLD);

        // テキスト背景のペイント
        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setColor(0xCC000000);
        textBgPaint.setStyle(Paint.Style.FILL);

        // 選択枠のペイント
        selectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        selectionPaint.setColor(0xFFFFFFFF);
        selectionPaint.setStrokeWidth(8f);
        selectionPaint.setStyle(Paint.Style.STROKE);
    }

    // ==================== 図形管理 ====================

    /**
     * 図形リストを設定
     * 
     * @param shapes 図形リスト
     */
    public void setShapes(List<DrawingShape> shapes) {
        this.shapes = shapes != null ? shapes : new ArrayList<>();
    }

    /**
     * 図形を追加
     * 
     * @param shape 図形
     */
    public void addShape(DrawingShape shape) {
        if (shape != null) {
            shapes.add(shape);
        }
    }

    /**
     * 図形を削除
     * 
     * @param shapeId 図形ID
     */
    public void removeShape(long shapeId) {
        shapes.removeIf(shape -> shape.getId() == shapeId);
        if (selectedShapeId == shapeId) {
            selectedShapeId = -1;
        }
    }

    /**
     * 全図形をクリア
     */
    public void clearShapes() {
        shapes.clear();
        selectedShapeId = -1;
    }

    /**
     * 選択中の図形IDを設定
     * 
     * @param shapeId 図形ID（-1で選択解除）
     */
    public void setSelectedShapeId(long shapeId) {
        this.selectedShapeId = shapeId;
    }

    /**
     * 選択中の図形IDを取得
     * 
     * @return 選択中の図形ID
     */
    public long getSelectedShapeId() {
        return selectedShapeId;
    }

    // ==================== 表示設定 ====================

    /**
     * 辺の長さ表示を設定
     */
    public void setShowEdgeLengths(boolean show) {
        this.showEdgeLengths = show;
    }

    /**
     * 名称表示を設定
     */
    public void setShowNames(boolean show) {
        this.showNames = show;
    }

    /**
     * 面積表示を設定
     */
    public void setShowArea(boolean show) {
        this.showArea = show;
    }

    // ==================== 描画 ====================

    @Override
    public void draw(Canvas canvas, MapView mapView, boolean shadow) {
        if (shadow) return;

        Projection projection = mapView.getProjection();

        for (DrawingShape shape : shapes) {
            if (!shape.isVisible()) continue;

            boolean isSelected = shape.getId() == selectedShapeId;
            
            switch (shape.getShapeType()) {
                case DrawingShape.TYPE_POLYGON:
                    drawPolygon(canvas, projection, shape, isSelected);
                    break;
                case DrawingShape.TYPE_POLYLINE:
                    drawPolyline(canvas, projection, shape, isSelected);
                    break;
                case DrawingShape.TYPE_CIRCLE:
                    drawCircle(canvas, projection, shape, isSelected);
                    break;
            }
        }
    }

    /**
     * 多角形を描画
     */
    private void drawPolygon(Canvas canvas, Projection projection, DrawingShape shape, boolean isSelected) {
        List<GeoPoint> points = DrawingShapeRepository.parseCoordinatesJson(shape.getCoordinatesJson());
        if (points.size() < 3) return;

        Point[] screenPoints = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            screenPoints[i] = projection.toPixels(points.get(i), null);
        }

        // ペイント作成
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(shape.getFillColor());
        fillPaint.setStyle(Paint.Style.FILL);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(shape.getStrokeColor());
        linePaint.setStrokeWidth(shape.getStrokeWidth());
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // 塗りつぶし
        Path fillPath = new Path();
        fillPath.moveTo(screenPoints[0].x, screenPoints[0].y);
        for (int i = 1; i < screenPoints.length; i++) {
            fillPath.lineTo(screenPoints[i].x, screenPoints[i].y);
        }
        fillPath.close();
        canvas.drawPath(fillPath, fillPaint);

        // 選択枠
        if (isSelected) {
            canvas.drawPath(fillPath, selectionPaint);
        }

        // 線を描画
        for (int i = 0; i < screenPoints.length; i++) {
            Point p1 = screenPoints[i];
            Point p2 = screenPoints[(i + 1) % screenPoints.length];
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);

            // 辺の長さを表示
            if (showEdgeLengths) {
                GeoPoint g1 = points.get(i);
                GeoPoint g2 = points.get((i + 1) % points.size());
                double distance = GeoCalculator.calculateDistance(g1, g2);
                String distText = GeoCalculator.formatDistanceShort(distance);
                drawEdgeLabel(canvas, p1, p2, distText);
            }
        }

        // 重心に名称と面積を表示
        GeoPoint centroid = GeoCalculator.calculateCentroid(points);
        if (centroid != null) {
            Point centroidScreen = projection.toPixels(centroid, null);
            drawShapeInfo(canvas, centroidScreen, shape);
        }
    }

    /**
     * ポリラインを描画
     */
    private void drawPolyline(Canvas canvas, Projection projection, DrawingShape shape, boolean isSelected) {
        List<GeoPoint> points = DrawingShapeRepository.parseCoordinatesJson(shape.getCoordinatesJson());
        if (points.size() < 2) return;

        Point[] screenPoints = new Point[points.size()];
        for (int i = 0; i < points.size(); i++) {
            screenPoints[i] = projection.toPixels(points.get(i), null);
        }

        // ペイント作成
        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(shape.getStrokeColor());
        linePaint.setStrokeWidth(shape.getStrokeWidth());
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        // 選択時の太い線
        if (isSelected) {
            Paint selectLinePaint = new Paint(linePaint);
            selectLinePaint.setStrokeWidth(shape.getStrokeWidth() + 6);
            selectLinePaint.setColor(0xFFFFFFFF);
            for (int i = 0; i < screenPoints.length - 1; i++) {
                canvas.drawLine(screenPoints[i].x, screenPoints[i].y,
                        screenPoints[i + 1].x, screenPoints[i + 1].y, selectLinePaint);
            }
        }

        // 線を描画
        for (int i = 0; i < screenPoints.length - 1; i++) {
            Point p1 = screenPoints[i];
            Point p2 = screenPoints[i + 1];
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);

            // 辺の長さを表示
            if (showEdgeLengths) {
                GeoPoint g1 = points.get(i);
                GeoPoint g2 = points.get(i + 1);
                double distance = GeoCalculator.calculateDistance(g1, g2);
                String distText = GeoCalculator.formatDistanceShort(distance);
                drawEdgeLabel(canvas, p1, p2, distText);
            }
        }

        // 中点に名称と総距離を表示
        int midIndex = points.size() / 2;
        Point midPoint = screenPoints[midIndex];
        drawShapeInfo(canvas, midPoint, shape);
    }

    /**
     * 円を描画
     */
    private void drawCircle(Canvas canvas, Projection projection, DrawingShape shape, boolean isSelected) {
        Object[] circleData = DrawingShapeRepository.parseCircleJson(shape.getCoordinatesJson());
        if (circleData == null) return;

        GeoPoint center = (GeoPoint) circleData[0];
        double radius = (Double) circleData[1];

        Point centerPoint = projection.toPixels(center, null);

        // 半径をピクセルに変換
        GeoPoint edgePoint = calculateDestination(center, 90, radius);
        Point edgePointScreen = projection.toPixels(edgePoint, null);
        float radiusPx = Math.abs(edgePointScreen.x - centerPoint.x);

        // ペイント作成
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setColor(shape.getFillColor());
        fillPaint.setStyle(Paint.Style.FILL);

        Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(shape.getStrokeColor());
        linePaint.setStrokeWidth(shape.getStrokeWidth());
        linePaint.setStyle(Paint.Style.STROKE);

        // 塗りつぶし
        canvas.drawCircle(centerPoint.x, centerPoint.y, radiusPx, fillPaint);

        // 選択枠
        if (isSelected) {
            canvas.drawCircle(centerPoint.x, centerPoint.y, radiusPx, selectionPaint);
        }

        // 線
        canvas.drawCircle(centerPoint.x, centerPoint.y, radiusPx, linePaint);

        // 半径ラベル
        if (showEdgeLengths) {
            String radiusText = "r=" + GeoCalculator.formatDistanceShort(radius);
            Point labelPoint = new Point(centerPoint.x + (int) (radiusPx / 2), centerPoint.y);
            drawLabel(canvas, labelPoint, radiusText, textPaint, textBgPaint);
        }

        // 中心に名称と面積を表示
        drawShapeInfo(canvas, centerPoint, shape);
    }

    /**
     * 図形情報（名称・面積）を描画
     */
    private void drawShapeInfo(Canvas canvas, Point point, DrawingShape shape) {
        List<String> lines = new ArrayList<>();

        if (showNames && !shape.getName().isEmpty()) {
            lines.add(shape.getName());
        }

        if (showArea && shape.getArea() > 0) {
            lines.add(shape.getAreaDisplayString());
        }

        if (!showArea && shape.getPerimeter() > 0) {
            lines.add("Total: " + shape.getPerimeterDisplayString());
        }

        if (lines.isEmpty()) return;

        // 複数行のラベルを描画
        drawMultiLineLabel(canvas, point, lines);
    }

    /**
     * 複数行ラベルを描画
     */
    private void drawMultiLineLabel(Canvas canvas, Point point, List<String> lines) {
        if (lines.isEmpty()) return;

        float lineHeight = namePaint.getTextSize() + 8;
        float totalHeight = lineHeight * lines.size();
        float maxWidth = 0;

        for (String line : lines) {
            float width = namePaint.measureText(line);
            maxWidth = Math.max(maxWidth, width);
        }

        int padding = 12;
        float bgLeft = point.x - maxWidth / 2 - padding;
        float bgTop = point.y - totalHeight / 2 - padding;
        float bgRight = point.x + maxWidth / 2 + padding;
        float bgBottom = point.y + totalHeight / 2 + padding;

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 12, 12, textBgPaint);

        float y = point.y - totalHeight / 2 + lineHeight - 8;
        for (int i = 0; i < lines.size(); i++) {
            Paint paint = (i == 0) ? namePaint : textPaint;
            canvas.drawText(lines.get(i), point.x, y, paint);
            y += lineHeight;
        }
    }

    /**
     * 辺のラベルを描画
     */
    private void drawEdgeLabel(Canvas canvas, Point p1, Point p2, String text) {
        int midX = (p1.x + p2.x) / 2;
        int midY = (p1.y + p2.y) / 2;
        drawLabel(canvas, new Point(midX, midY), text, textPaint, textBgPaint);
    }

    /**
     * ラベルを描画
     */
    private void drawLabel(Canvas canvas, Point point, String text, Paint textPaint, Paint bgPaint) {
        Rect bounds = new Rect();
        textPaint.getTextBounds(text, 0, text.length(), bounds);

        int padding = 8;
        int bgLeft = point.x - bounds.width() / 2 - padding;
        int bgTop = point.y - bounds.height() - padding;
        int bgRight = point.x + bounds.width() / 2 + padding;
        int bgBottom = point.y + padding;

        canvas.drawRoundRect(bgLeft, bgTop, bgRight, bgBottom, 8, 8, bgPaint);
        canvas.drawText(text, point.x, point.y, textPaint);
    }

    // ==================== タッチイベント ====================

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e, MapView mapView) {
        if (shapeTapListener == null) return false;

        Projection projection = mapView.getProjection();
        GeoPoint tapPoint = (GeoPoint) projection.fromPixels((int) e.getX(), (int) e.getY());

        // タップした図形を検索
        for (int i = shapes.size() - 1; i >= 0; i--) {
            DrawingShape shape = shapes.get(i);
            if (!shape.isVisible()) continue;

            if (isPointInShape(tapPoint, shape, projection, e.getX(), e.getY())) {
                shapeTapListener.onShapeTapped(shape);
                return true;
            }
        }

        // 図形外をタップした場合
        shapeTapListener.onShapeTapped(null);
        return false;
    }

    /**
     * 点が図形内にあるか判定
     */
    private boolean isPointInShape(GeoPoint point, DrawingShape shape, Projection projection, float x, float y) {
        switch (shape.getShapeType()) {
            case DrawingShape.TYPE_POLYGON:
                List<GeoPoint> polygonPoints = DrawingShapeRepository.parseCoordinatesJson(shape.getCoordinatesJson());
                return isPointInPolygon(point, polygonPoints);

            case DrawingShape.TYPE_POLYLINE:
                List<GeoPoint> linePoints = DrawingShapeRepository.parseCoordinatesJson(shape.getCoordinatesJson());
                return isPointNearPolyline(point, linePoints, projection, x, y, 30f);

            case DrawingShape.TYPE_CIRCLE:
                Object[] circleData = DrawingShapeRepository.parseCircleJson(shape.getCoordinatesJson());
                if (circleData != null) {
                    GeoPoint center = (GeoPoint) circleData[0];
                    double radius = (Double) circleData[1];
                    double distance = GeoCalculator.calculateDistance(point, center);
                    return distance <= radius;
                }
                return false;
        }
        return false;
    }

    /**
     * 点がポリゴン内にあるか判定（Ray casting）
     */
    private boolean isPointInPolygon(GeoPoint point, List<GeoPoint> polygon) {
        if (polygon.size() < 3) return false;

        boolean inside = false;
        int n = polygon.size();
        double x = point.getLongitude();
        double y = point.getLatitude();

        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = polygon.get(i).getLongitude();
            double yi = polygon.get(i).getLatitude();
            double xj = polygon.get(j).getLongitude();
            double yj = polygon.get(j).getLatitude();

            if (((yi > y) != (yj > y)) && (x < (xj - xi) * (y - yi) / (yj - yi) + xi)) {
                inside = !inside;
            }
        }

        return inside;
    }

    /**
     * 点がポリライン近くにあるか判定
     */
    private boolean isPointNearPolyline(GeoPoint point, List<GeoPoint> polyline, 
            Projection projection, float touchX, float touchY, float threshold) {
        if (polyline.size() < 2) return false;

        for (int i = 0; i < polyline.size() - 1; i++) {
            Point p1 = projection.toPixels(polyline.get(i), null);
            Point p2 = projection.toPixels(polyline.get(i + 1), null);
            float distance = pointToSegmentDistance(touchX, touchY, p1.x, p1.y, p2.x, p2.y);
            if (distance <= threshold) {
                return true;
            }
        }
        return false;
    }

    /**
     * 点から線分への距離を計算
     */
    private float pointToSegmentDistance(float px, float py, float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float lengthSquared = dx * dx + dy * dy;

        if (lengthSquared == 0) {
            return (float) Math.sqrt((px - x1) * (px - x1) + (py - y1) * (py - y1));
        }

        float t = Math.max(0, Math.min(1, ((px - x1) * dx + (py - y1) * dy) / lengthSquared));
        float nearestX = x1 + t * dx;
        float nearestY = y1 + t * dy;

        return (float) Math.sqrt((px - nearestX) * (px - nearestX) + (py - nearestY) * (py - nearestY));
    }

    /**
     * 目的地点を計算
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

    // ==================== リスナー ====================

    /**
     * 図形タップリスナーを設定
     */
    public void setShapeTapListener(ShapeTapListener listener) {
        this.shapeTapListener = listener;
    }

    /**
     * 図形タップリスナーインターフェース
     */
    public interface ShapeTapListener {
        void onShapeTapped(DrawingShape shape);
    }
}

