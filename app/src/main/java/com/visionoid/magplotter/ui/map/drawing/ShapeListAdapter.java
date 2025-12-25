/**
 * ShapeListAdapter.java
 * 
 * VISIONOID MAG PLOTTER - 図形リストアダプター
 * 
 * 概要:
 *   図形リストダイアログ用のRecyclerViewアダプター。
 * 
 * 主な仕様:
 *   - 図形の名称、タイプ、計測値を表示
 *   - 表示/非表示トグル
 *   - 編集・削除ボタン
 */
package com.visionoid.magplotter.ui.map.drawing;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.DrawingShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 図形リストアダプタークラス
 */
public class ShapeListAdapter extends RecyclerView.Adapter<ShapeListAdapter.ShapeViewHolder> {

    /** コンテキスト */
    private final Context context;

    /** 図形リスト */
    private List<DrawingShape> shapes = new ArrayList<>();

    /** アイテムクリックリスナー */
    private OnShapeItemClickListener listener;

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public ShapeListAdapter(Context context) {
        this.context = context;
    }

    /**
     * 図形リストを設定
     * 
     * @param shapes 図形リスト
     */
    public void setShapes(List<DrawingShape> shapes) {
        this.shapes = shapes != null ? shapes : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * リスナーを設定
     * 
     * @param listener リスナー
     */
    public void setOnShapeItemClickListener(OnShapeItemClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ShapeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_shape, parent, false);
        return new ShapeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ShapeViewHolder holder, int position) {
        DrawingShape shape = shapes.get(position);
        holder.bind(shape);
    }

    @Override
    public int getItemCount() {
        return shapes.size();
    }

    /**
     * ViewHolderクラス
     */
    class ShapeViewHolder extends RecyclerView.ViewHolder {

        private final View colorIndicator;
        private final TextView textName;
        private final TextView textType;
        private final TextView textMetrics;
        private final ImageButton buttonVisibility;
        private final ImageButton buttonEdit;
        private final ImageButton buttonDelete;

        ShapeViewHolder(@NonNull View itemView) {
            super(itemView);
            colorIndicator = itemView.findViewById(R.id.view_color_indicator);
            textName = itemView.findViewById(R.id.text_shape_name);
            textType = itemView.findViewById(R.id.text_shape_type);
            textMetrics = itemView.findViewById(R.id.text_shape_metrics);
            buttonVisibility = itemView.findViewById(R.id.button_visibility);
            buttonEdit = itemView.findViewById(R.id.button_edit);
            buttonDelete = itemView.findViewById(R.id.button_delete);
        }

        void bind(DrawingShape shape) {
            // 名称
            textName.setText(shape.getName());

            // 色インジケータ
            GradientDrawable drawable = (GradientDrawable) colorIndicator.getBackground();
            drawable.setColor(shape.getStrokeColor());

            // タイプ
            String typeText;
            switch (shape.getShapeType()) {
                case DrawingShape.TYPE_POLYGON:
                    typeText = "POLYGON";
                    break;
                case DrawingShape.TYPE_POLYLINE:
                    typeText = "LINE";
                    break;
                case DrawingShape.TYPE_CIRCLE:
                    typeText = "CIRCLE";
                    break;
                default:
                    typeText = "SHAPE";
            }
            textType.setText(typeText);

            // 計測値
            StringBuilder metrics = new StringBuilder();
            if (shape.getArea() > 0) {
                metrics.append(shape.getAreaDisplayString());
            }
            if (shape.getPerimeter() > 0) {
                if (metrics.length() > 0) metrics.append(" / ");
                if (shape.isPolyline()) {
                    metrics.append(shape.getPerimeterDisplayString());
                } else {
                    metrics.append("周囲: ").append(shape.getPerimeterDisplayString());
                }
            }
            textMetrics.setText(metrics.toString());

            // 表示/非表示アイコン
            updateVisibilityIcon(shape.isVisible());

            // クリックリスナー
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onShapeClick(shape);
                }
            });

            buttonVisibility.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onVisibilityToggle(shape);
                }
            });

            buttonEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditClick(shape);
                }
            });

            buttonDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteClick(shape);
                }
            });
        }

        private void updateVisibilityIcon(boolean isVisible) {
            if (isVisible) {
                buttonVisibility.setImageResource(android.R.drawable.ic_menu_view);
                buttonVisibility.setAlpha(1.0f);
            } else {
                buttonVisibility.setImageResource(android.R.drawable.ic_menu_view);
                buttonVisibility.setAlpha(0.3f);
            }
        }
    }

    /**
     * アイテムクリックリスナーインターフェース
     */
    public interface OnShapeItemClickListener {
        void onShapeClick(DrawingShape shape);
        void onVisibilityToggle(DrawingShape shape);
        void onEditClick(DrawingShape shape);
        void onDeleteClick(DrawingShape shape);
    }
}

