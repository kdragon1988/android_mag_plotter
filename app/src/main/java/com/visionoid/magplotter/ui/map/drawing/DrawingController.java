/**
 * DrawingController.java
 * 
 * VISIONOID MAG PLOTTER - 作図コントローラー
 * 
 * 概要:
 *   MeasurementActivityの作図機能を管理するコントローラークラス。
 *   オーバーレイ管理、ダイアログ表示、データ保存を担当。
 * 
 * 主な仕様:
 *   - 作図モードの切り替え
 *   - 作図ツールバーの表示/非表示
 *   - 図形の保存・編集・削除
 *   - 図形リストダイアログの表示
 * 
 * 制限事項:
 *   - Activity/Fragmentのコンテキストが必要
 */
package com.visionoid.magplotter.ui.map.drawing;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.DrawingShape;
import com.visionoid.magplotter.data.repository.DrawingShapeRepository;
import com.visionoid.magplotter.util.GeoCalculator;

import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.List;

/**
 * 作図コントローラークラス
 */
public class DrawingController implements 
        MapDrawingOverlay.DrawingCompleteListener,
        MapDrawingOverlay.MeasurementUpdateListener,
        SavedShapesOverlay.ShapeTapListener,
        ShapeListAdapter.OnShapeItemClickListener {

    /** アクティビティ */
    private final Activity activity;

    /** ミッションID */
    private final long missionId;

    /** マップビュー */
    private final MapView mapView;

    /** リポジトリ */
    private final DrawingShapeRepository repository;

    /** 作図オーバーレイ */
    private final MapDrawingOverlay drawingOverlay;

    /** 保存済み図形オーバーレイ */
    private final SavedShapesOverlay savedShapesOverlay;

    /** 作図ツールバー */
    private View drawingToolbar;

    // ツールバーUI要素
    private TextView textDrawingMode;
    private TextView textDrawingHint;
    private TextView textAreaValue;
    private TextView textPerimeterValue;
    private TextView textRadiusValue;
    private TextView textPointsValue;
    private LinearLayout layoutArea;
    private LinearLayout layoutPerimeter;
    private LinearLayout layoutRadius;
    private LinearLayout layoutPoints;
    private Button buttonComplete;
    private Button buttonUndo;
    private Button buttonCancel;

    /** 現在の作図モード */
    private DrawingMode currentMode = DrawingMode.NONE;

    /** 選択中の色 */
    private int selectedStrokeColor = 0xFFFF5722;
    private int selectedFillColor = 0x40FF5722;

    /** コールバック */
    private DrawingControllerCallback callback;

    /**
     * コンストラクタ
     * 
     * @param activity アクティビティ
     * @param mapView マップビュー
     * @param missionId ミッションID
     */
    public DrawingController(Activity activity, MapView mapView, long missionId) {
        this.activity = activity;
        this.mapView = mapView;
        this.missionId = missionId;
        this.repository = new DrawingShapeRepository(activity.getApplication());

        // オーバーレイ作成
        this.drawingOverlay = new MapDrawingOverlay(activity);
        this.drawingOverlay.setDrawingCompleteListener(this);
        this.drawingOverlay.setMeasurementUpdateListener(this);

        this.savedShapesOverlay = new SavedShapesOverlay(activity);
        this.savedShapesOverlay.setShapeTapListener(this);

        // オーバーレイを地図に追加
        mapView.getOverlays().add(savedShapesOverlay);
        mapView.getOverlays().add(drawingOverlay);
    }

    /**
     * ツールバーを初期化
     * 
     * @param container ツールバーを追加するコンテナ
     */
    public void initializeToolbar(FrameLayout container) {
        drawingToolbar = LayoutInflater.from(activity)
                .inflate(R.layout.layout_drawing_toolbar, container, false);
        
        // UI要素を取得
        textDrawingMode = drawingToolbar.findViewById(R.id.text_drawing_mode);
        textDrawingHint = drawingToolbar.findViewById(R.id.text_drawing_hint);
        textAreaValue = drawingToolbar.findViewById(R.id.text_area_value);
        textPerimeterValue = drawingToolbar.findViewById(R.id.text_perimeter_value);
        textRadiusValue = drawingToolbar.findViewById(R.id.text_radius_value);
        textPointsValue = drawingToolbar.findViewById(R.id.text_points_value);
        layoutArea = drawingToolbar.findViewById(R.id.layout_area);
        layoutPerimeter = drawingToolbar.findViewById(R.id.layout_perimeter);
        layoutRadius = drawingToolbar.findViewById(R.id.layout_radius);
        layoutPoints = drawingToolbar.findViewById(R.id.layout_points);
        buttonComplete = drawingToolbar.findViewById(R.id.button_complete);
        buttonUndo = drawingToolbar.findViewById(R.id.button_undo);
        buttonCancel = drawingToolbar.findViewById(R.id.button_cancel);

        // ボタンリスナー
        buttonComplete.setOnClickListener(v -> completeDrawing());
        buttonUndo.setOnClickListener(v -> undoLastPoint());
        buttonCancel.setOnClickListener(v -> cancelDrawing());

        container.addView(drawingToolbar);
        drawingToolbar.setVisibility(View.GONE);
    }

    /**
     * 保存済み図形を監視
     * 
     * @param lifecycleOwner ライフサイクルオーナー
     */
    public void observeShapes(LifecycleOwner lifecycleOwner) {
        LiveData<List<DrawingShape>> shapesLiveData = repository.getByMissionIdLive(missionId);
        shapesLiveData.observe(lifecycleOwner, shapes -> {
            savedShapesOverlay.setShapes(shapes);
            mapView.invalidate();
        });
    }

    // ==================== 作図モード制御 ====================

    /**
     * ポリゴン描画を開始
     */
    public void startPolygonDrawing() {
        startDrawing(DrawingMode.POLYGON);
    }

    /**
     * ポリライン描画を開始
     */
    public void startPolylineDrawing() {
        startDrawing(DrawingMode.POLYLINE);
    }

    /**
     * 円描画を開始
     */
    public void startCircleDrawing() {
        startDrawing(DrawingMode.CIRCLE);
    }

    /**
     * 作図を開始
     */
    private void startDrawing(DrawingMode mode) {
        currentMode = mode;
        drawingOverlay.setDrawingMode(mode);
        showToolbar(mode);
        mapView.invalidate();

        if (callback != null) {
            callback.onDrawingStarted(mode);
        }
    }

    /**
     * 作図を完了
     */
    private void completeDrawing() {
        // バリデーション
        switch (currentMode) {
            case POLYGON:
                if (drawingOverlay.getPointCount() < 3) {
                    Toast.makeText(activity, R.string.drawing_min_points_polygon, Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case POLYLINE:
                if (drawingOverlay.getPointCount() < 2) {
                    Toast.makeText(activity, R.string.drawing_min_points_polyline, Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
            case CIRCLE:
                if (drawingOverlay.getCircleRadius() <= 0) {
                    Toast.makeText(activity, R.string.drawing_set_radius, Toast.LENGTH_SHORT).show();
                    return;
                }
                break;
        }

        drawingOverlay.completeDrawing();
    }

    /**
     * 最後の頂点を取り消し
     */
    private void undoLastPoint() {
        drawingOverlay.undoLastPoint();
        mapView.invalidate();
    }

    /**
     * 作図をキャンセル
     */
    public void cancelDrawing() {
        drawingOverlay.cancelDrawing();
        hideToolbar();
        currentMode = DrawingMode.NONE;
        mapView.invalidate();

        if (callback != null) {
            callback.onDrawingCancelled();
        }
    }

    /**
     * 作図中かどうか
     */
    public boolean isDrawing() {
        return drawingOverlay.isDrawing();
    }

    // ==================== ツールバー表示制御 ====================

    /**
     * ツールバーを表示
     */
    private void showToolbar(DrawingMode mode) {
        if (drawingToolbar == null) return;

        drawingToolbar.setVisibility(View.VISIBLE);

        // モード別のUI設定
        switch (mode) {
            case POLYGON:
                textDrawingMode.setText(R.string.drawing_mode_polygon);
                textDrawingHint.setText(R.string.drawing_hint_polygon);
                layoutArea.setVisibility(View.VISIBLE);
                layoutPerimeter.setVisibility(View.VISIBLE);
                layoutRadius.setVisibility(View.GONE);
                layoutPoints.setVisibility(View.VISIBLE);
                TextView perimeterLabel = drawingToolbar.findViewById(R.id.text_perimeter_label);
                perimeterLabel.setText(R.string.drawing_perimeter_label);
                break;

            case POLYLINE:
                textDrawingMode.setText(R.string.drawing_mode_polyline);
                textDrawingHint.setText(R.string.drawing_hint_polyline);
                layoutArea.setVisibility(View.GONE);
                layoutPerimeter.setVisibility(View.VISIBLE);
                layoutRadius.setVisibility(View.GONE);
                layoutPoints.setVisibility(View.VISIBLE);
                TextView distanceLabel = drawingToolbar.findViewById(R.id.text_perimeter_label);
                distanceLabel.setText(R.string.drawing_distance_label);
                break;

            case CIRCLE:
                textDrawingMode.setText(R.string.drawing_mode_circle);
                textDrawingHint.setText(R.string.drawing_hint_circle);
                layoutArea.setVisibility(View.VISIBLE);
                layoutPerimeter.setVisibility(View.GONE);
                layoutRadius.setVisibility(View.VISIBLE);
                layoutPoints.setVisibility(View.GONE);
                break;
        }

        // 初期値をリセット
        textAreaValue.setText("0.0 m²");
        textPerimeterValue.setText("0.0 m");
        textRadiusValue.setText("0.0 m");
        textPointsValue.setText("0");
    }

    /**
     * ツールバーを非表示
     */
    private void hideToolbar() {
        if (drawingToolbar != null) {
            drawingToolbar.setVisibility(View.GONE);
        }
    }

    // ==================== ダイアログ ====================

    /**
     * 図形名入力ダイアログを表示
     */
    private void showNameDialog(String shapeType, String coordinatesJson, double area, double perimeter) {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_shape_name, null);

        EditText editName = dialogView.findViewById(R.id.edit_shape_name);
        LinearLayout layoutDialogArea = dialogView.findViewById(R.id.layout_dialog_area);
        LinearLayout layoutDialogPerimeter = dialogView.findViewById(R.id.layout_dialog_perimeter);
        TextView textDialogArea = dialogView.findViewById(R.id.text_dialog_area);
        TextView textDialogPerimeter = dialogView.findViewById(R.id.text_dialog_perimeter);
        TextView textDialogPerimeterLabel = dialogView.findViewById(R.id.text_dialog_perimeter_label);

        // 計測値表示
        if (area > 0) {
            layoutDialogArea.setVisibility(View.VISIBLE);
            textDialogArea.setText(GeoCalculator.formatArea(area));
        }
        if (perimeter > 0) {
            layoutDialogPerimeter.setVisibility(View.VISIBLE);
            textDialogPerimeter.setText(GeoCalculator.formatDistance(perimeter));
            if (DrawingShape.TYPE_POLYLINE.equals(shapeType)) {
                textDialogPerimeterLabel.setText(R.string.drawing_distance_label);
            }
        }

        // 色選択ボタン
        setupColorSelector(dialogView);

        new MaterialAlertDialogBuilder(activity, R.style.SpyTech_Dialog)
                .setView(dialogView)
                .setPositiveButton(R.string.drawing_save, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (TextUtils.isEmpty(name)) {
                        name = getDefaultShapeName(shapeType);
                    }
                    saveShape(name, shapeType, coordinatesJson, area, perimeter);
                })
                .setNegativeButton(R.string.drawing_cancel, null)
                .show();
    }

    /**
     * 色選択ボタンを設定
     */
    private void setupColorSelector(View dialogView) {
        int[][] colorData = {
                {R.id.color_orange, R.id.check_orange, 0xFFFF5722, 0x40FF5722},
                {R.id.color_blue, R.id.check_blue, 0xFF2196F3, 0x402196F3},
                {R.id.color_green, R.id.check_green, 0xFF4CAF50, 0x404CAF50},
                {R.id.color_red, R.id.check_red, 0xFFF44336, 0x40F44336},
                {R.id.color_purple, R.id.check_purple, 0xFF9C27B0, 0x409C27B0},
                {R.id.color_yellow, R.id.check_yellow, 0xFFFFC107, 0x40FFC107}
        };

        // 全チェックを非表示にして、最初（オレンジ）を選択状態に
        for (int[] data : colorData) {
            ImageView check = dialogView.findViewById(data[1]);
            check.setVisibility(View.GONE);
        }
        dialogView.findViewById(R.id.check_orange).setVisibility(View.VISIBLE);

        for (int[] data : colorData) {
            View colorButton = dialogView.findViewById(data[0]);
            ImageView check = dialogView.findViewById(data[1]);
            int strokeColor = data[2];
            int fillColor = data[3];

            colorButton.setOnClickListener(v -> {
                // 全チェックを非表示
                for (int[] d : colorData) {
                    dialogView.findViewById(d[1]).setVisibility(View.GONE);
                }
                // 選択したものを表示
                check.setVisibility(View.VISIBLE);
                selectedStrokeColor = strokeColor;
                selectedFillColor = fillColor;
            });
        }
    }

    /**
     * デフォルト図形名を取得
     */
    private String getDefaultShapeName(String shapeType) {
        switch (shapeType) {
            case DrawingShape.TYPE_POLYGON:
                return "エリア";
            case DrawingShape.TYPE_POLYLINE:
                return "ライン";
            case DrawingShape.TYPE_CIRCLE:
                return "サークル";
            default:
                return "図形";
        }
    }

    /**
     * 図形を保存
     */
    private void saveShape(String name, String shapeType, String coordinatesJson, double area, double perimeter) {
        DrawingShape shape = new DrawingShape(missionId, name, shapeType, coordinatesJson);
        shape.setArea(area);
        shape.setPerimeter(perimeter);
        shape.setStrokeColor(selectedStrokeColor);
        shape.setFillColor(selectedFillColor);

        repository.insert(shape, id -> {
            activity.runOnUiThread(() -> {
                Toast.makeText(activity, R.string.drawing_saved, Toast.LENGTH_SHORT).show();
                hideToolbar();
                currentMode = DrawingMode.NONE;
                mapView.invalidate();

                if (callback != null) {
                    callback.onShapeSaved(shape);
                }
            });
        });
    }

    /**
     * 図形リストダイアログを表示
     */
    public void showShapeListDialog() {
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_shape_list, null);
        RecyclerView recyclerView = dialogView.findViewById(R.id.recycler_shapes);
        View layoutEmpty = dialogView.findViewById(R.id.layout_empty);
        TextView textCount = dialogView.findViewById(R.id.text_shape_count);

        recyclerView.setLayoutManager(new LinearLayoutManager(activity));
        ShapeListAdapter adapter = new ShapeListAdapter(activity);
        adapter.setOnShapeItemClickListener(this);
        recyclerView.setAdapter(adapter);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.SpyTech_Dialog)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        // データを監視
        repository.getByMissionIdLive(missionId).observe((LifecycleOwner) activity, shapes -> {
            adapter.setShapes(shapes);
            textCount.setText(String.valueOf(shapes != null ? shapes.size() : 0));

            if (shapes == null || shapes.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
            }
        });

        dialog.show();
    }

    /**
     * 削除確認ダイアログを表示
     */
    private void showDeleteConfirmDialog(DrawingShape shape) {
        new MaterialAlertDialogBuilder(activity, R.style.SpyTech_Dialog)
                .setTitle(R.string.drawing_delete_confirm)
                .setMessage(activity.getString(R.string.drawing_delete_message, shape.getName()))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    repository.deleteById(shape.getId());
                    Toast.makeText(activity, R.string.drawing_deleted, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ==================== コールバック実装 ====================

    @Override
    public void onPolygonComplete(List<GeoPoint> points) {
        String coordinatesJson = DrawingShapeRepository.toCoordinatesJson(points);
        double area = GeoCalculator.calculatePolygonArea(points);
        double perimeter = GeoCalculator.calculatePolygonPerimeter(points);
        showNameDialog(DrawingShape.TYPE_POLYGON, coordinatesJson, area, perimeter);
    }

    @Override
    public void onPolylineComplete(List<GeoPoint> points) {
        String coordinatesJson = DrawingShapeRepository.toCoordinatesJson(points);
        double perimeter = GeoCalculator.calculatePolylineLength(points);
        showNameDialog(DrawingShape.TYPE_POLYLINE, coordinatesJson, 0, perimeter);
    }

    @Override
    public void onCircleComplete(GeoPoint center, double radius) {
        String coordinatesJson = DrawingShapeRepository.toCircleJson(center, radius);
        double area = GeoCalculator.calculateCircleArea(radius);
        double perimeter = GeoCalculator.calculateCirclePerimeter(radius);
        showNameDialog(DrawingShape.TYPE_CIRCLE, coordinatesJson, area, perimeter);
    }

    @Override
    public void onMeasurementUpdate(double area, double perimeter, List<Double> segmentLengths) {
        activity.runOnUiThread(() -> {
            if (textAreaValue != null) {
                textAreaValue.setText(GeoCalculator.formatArea(area));
            }
            if (textPerimeterValue != null) {
                textPerimeterValue.setText(GeoCalculator.formatDistance(perimeter));
            }
            if (textRadiusValue != null && currentMode == DrawingMode.CIRCLE) {
                double radius = drawingOverlay.getCircleRadius();
                textRadiusValue.setText(GeoCalculator.formatDistance(radius));
            }
            if (textPointsValue != null) {
                textPointsValue.setText(String.valueOf(drawingOverlay.getPointCount()));
            }
        });
    }

    @Override
    public void onShapeTapped(DrawingShape shape) {
        if (shape != null) {
            savedShapesOverlay.setSelectedShapeId(shape.getId());
        } else {
            savedShapesOverlay.setSelectedShapeId(-1);
        }
        mapView.invalidate();
    }

    @Override
    public void onShapeClick(DrawingShape shape) {
        // 図形をタップしたら地図上でハイライト
        savedShapesOverlay.setSelectedShapeId(shape.getId());
        mapView.invalidate();

        // 重心にズーム
        List<GeoPoint> points = DrawingShapeRepository.parseCoordinatesJson(shape.getCoordinatesJson());
        if (!points.isEmpty()) {
            GeoPoint center = GeoCalculator.calculateCentroid(points);
            if (center != null) {
                mapView.getController().animateTo(center);
            }
        }
    }

    @Override
    public void onVisibilityToggle(DrawingShape shape) {
        repository.updateVisibility(shape.getId(), !shape.isVisible());
    }

    @Override
    public void onEditClick(DrawingShape shape) {
        // 編集ダイアログを表示（名前変更のみ）
        View dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_shape_name, null);
        EditText editName = dialogView.findViewById(R.id.edit_shape_name);
        editName.setText(shape.getName());

        // 計測情報を表示
        LinearLayout layoutDialogArea = dialogView.findViewById(R.id.layout_dialog_area);
        LinearLayout layoutDialogPerimeter = dialogView.findViewById(R.id.layout_dialog_perimeter);
        TextView textDialogArea = dialogView.findViewById(R.id.text_dialog_area);
        TextView textDialogPerimeter = dialogView.findViewById(R.id.text_dialog_perimeter);

        if (shape.getArea() > 0) {
            layoutDialogArea.setVisibility(View.VISIBLE);
            textDialogArea.setText(shape.getAreaDisplayString());
        }
        if (shape.getPerimeter() > 0) {
            layoutDialogPerimeter.setVisibility(View.VISIBLE);
            textDialogPerimeter.setText(shape.getPerimeterDisplayString());
        }

        // 現在の色を選択状態にする
        setupColorSelectorWithCurrentColor(dialogView, shape.getStrokeColor());

        new MaterialAlertDialogBuilder(activity, R.style.SpyTech_Dialog)
                .setTitle(R.string.drawing_edit)
                .setView(dialogView)
                .setPositiveButton(R.string.drawing_save, (dialog, which) -> {
                    String name = editName.getText().toString().trim();
                    if (!TextUtils.isEmpty(name)) {
                        repository.updateName(shape.getId(), name);
                        repository.updateColors(shape.getId(), selectedStrokeColor, selectedFillColor);
                    }
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /**
     * 色選択ボタンを設定（現在の色を選択状態にする）
     */
    private void setupColorSelectorWithCurrentColor(View dialogView, int currentStrokeColor) {
        int[][] colorData = {
                {R.id.color_orange, R.id.check_orange, 0xFFFF5722, 0x40FF5722},
                {R.id.color_blue, R.id.check_blue, 0xFF2196F3, 0x402196F3},
                {R.id.color_green, R.id.check_green, 0xFF4CAF50, 0x404CAF50},
                {R.id.color_red, R.id.check_red, 0xFFF44336, 0x40F44336},
                {R.id.color_purple, R.id.check_purple, 0xFF9C27B0, 0x409C27B0},
                {R.id.color_yellow, R.id.check_yellow, 0xFFFFC107, 0x40FFC107}
        };

        // 全チェックを非表示
        for (int[] data : colorData) {
            ImageView check = dialogView.findViewById(data[1]);
            check.setVisibility(View.GONE);
        }

        // 現在の色を選択状態に
        for (int[] data : colorData) {
            if (data[2] == currentStrokeColor) {
                dialogView.findViewById(data[1]).setVisibility(View.VISIBLE);
                selectedStrokeColor = data[2];
                selectedFillColor = data[3];
                break;
            }
        }

        // 該当なければオレンジをデフォルトに
        boolean found = false;
        for (int[] data : colorData) {
            if (data[2] == currentStrokeColor) {
                found = true;
                break;
            }
        }
        if (!found) {
            dialogView.findViewById(R.id.check_orange).setVisibility(View.VISIBLE);
            selectedStrokeColor = 0xFFFF5722;
            selectedFillColor = 0x40FF5722;
        }

        // クリックリスナー設定
        for (int[] data : colorData) {
            View colorButton = dialogView.findViewById(data[0]);
            ImageView check = dialogView.findViewById(data[1]);
            int strokeColor = data[2];
            int fillColor = data[3];

            colorButton.setOnClickListener(v -> {
                for (int[] d : colorData) {
                    dialogView.findViewById(d[1]).setVisibility(View.GONE);
                }
                check.setVisibility(View.VISIBLE);
                selectedStrokeColor = strokeColor;
                selectedFillColor = fillColor;
            });
        }
    }

    @Override
    public void onDeleteClick(DrawingShape shape) {
        showDeleteConfirmDialog(shape);
    }

    // ==================== リスナー設定 ====================

    /**
     * コールバックを設定
     */
    public void setCallback(DrawingControllerCallback callback) {
        this.callback = callback;
    }

    /**
     * コールバックインターフェース
     */
    public interface DrawingControllerCallback {
        void onDrawingStarted(DrawingMode mode);
        void onDrawingCancelled();
        void onShapeSaved(DrawingShape shape);
    }
}


