/**
 * MissionAdapter.java
 * 
 * VISIONOID MAG PLOTTER - ミッション一覧アダプター
 * 
 * 概要:
 *   ミッション一覧のRecyclerViewアダプター。
 *   スパイテック風のカードデザインでミッションを表示。
 * 
 * 主な仕様:
 *   - ミッション情報をカード形式で表示
 *   - クリック・長押しイベントをコールバック
 *   - ステータスに応じた色分け表示
 * 
 * 制限事項:
 *   - ViewBindingを使用
 */
package com.visionoid.magplotter.ui.mission;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.visionoid.magplotter.R;
import com.visionoid.magplotter.data.model.Mission;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ミッション一覧アダプタークラス
 */
public class MissionAdapter extends RecyclerView.Adapter<MissionAdapter.MissionViewHolder> {

    /** ミッションリスト */
    private List<Mission> missions = new ArrayList<>();

    /** クリックリスナー */
    private final OnMissionClickListener listener;

    /** 日付フォーマット */
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault());

    /**
     * クリックリスナーインターフェース
     */
    public interface OnMissionClickListener {
        /**
         * ミッションがクリックされた時
         * @param mission クリックされたミッション
         */
        void onMissionClick(Mission mission);

        /**
         * ミッションが長押しされた時
         * @param mission 長押しされたミッション
         */
        void onMissionLongClick(Mission mission);
    }

    /**
     * コンストラクタ
     * 
     * @param listener クリックリスナー
     */
    public MissionAdapter(OnMissionClickListener listener) {
        this.listener = listener;
    }

    /**
     * ミッションリストを設定
     * 
     * @param missions 新しいミッションリスト
     */
    public void setMissions(List<Mission> missions) {
        this.missions = missions != null ? missions : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MissionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_mission, parent, false);
        return new MissionViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MissionViewHolder holder, int position) {
        Mission mission = missions.get(position);
        holder.bind(mission);
    }

    @Override
    public int getItemCount() {
        return missions.size();
    }

    /**
     * ミッション表示用ViewHolder
     */
    class MissionViewHolder extends RecyclerView.ViewHolder {

        private final TextView textLocationName;
        private final TextView textOperator;
        private final TextView textDate;
        private final TextView textStatus;
        private final View statusIndicator;
        private final View cardContainer;

        /**
         * コンストラクタ
         * 
         * @param itemView アイテムビュー
         */
        MissionViewHolder(@NonNull View itemView) {
            super(itemView);
            textLocationName = itemView.findViewById(R.id.text_location_name);
            textOperator = itemView.findViewById(R.id.text_operator);
            textDate = itemView.findViewById(R.id.text_date);
            textStatus = itemView.findViewById(R.id.text_status);
            statusIndicator = itemView.findViewById(R.id.status_indicator);
            cardContainer = itemView.findViewById(R.id.card_container);
        }

        /**
         * ミッションデータをバインド
         * 
         * @param mission バインドするミッション
         */
        void bind(Mission mission) {
            // 場所名
            textLocationName.setText(mission.getLocationName());

            // 担当者
            textOperator.setText(mission.getOperatorName());

            // 日付
            String dateStr = dateFormat.format(new Date(mission.getCreatedAt()));
            textDate.setText(dateStr);

            // ステータス
            if (mission.isCompleted()) {
                textStatus.setText(R.string.mission_status_completed);
                textStatus.setTextColor(itemView.getContext().getColor(R.color.status_safe));
                statusIndicator.setBackgroundColor(itemView.getContext().getColor(R.color.status_safe));
            } else {
                textStatus.setText(R.string.mission_status_active);
                textStatus.setTextColor(itemView.getContext().getColor(R.color.accent_cyan));
                statusIndicator.setBackgroundColor(itemView.getContext().getColor(R.color.accent_cyan));
            }

            // クリックリスナー
            cardContainer.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onMissionClick(mission);
                }
            });

            // 長押しリスナー
            cardContainer.setOnLongClickListener(v -> {
                if (listener != null) {
                    listener.onMissionLongClick(mission);
                    return true;
                }
                return false;
            });
        }
    }
}


