/**
 * LayerDisplayStyle.java
 * 
 * VISIONOID MAG PLOTTER - レイヤー表示スタイル定義
 * 
 * 概要:
 *   マップレイヤーの表示スタイルを定義するEnum。
 *   塗りつぶし、境界線のみ、ハッチングの3種類。
 * 
 * 主な仕様:
 *   - FILLED: 半透明塗りつぶし
 *   - BORDER_ONLY: 境界線のみ
 *   - HATCHED: 斜線パターン
 * 
 * 制限事項:
 *   - ハッチングはカスタム描画が必要
 */
package com.visionoid.magplotter.ui.map.layer;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.visionoid.magplotter.R;

/**
 * レイヤー表示スタイルを定義するEnum
 */
public enum LayerDisplayStyle {

    /**
     * 塗りつぶし（半透明）
     */
    FILLED(
            "filled",
            R.string.layer_style_filled
    ),

    /**
     * 境界線のみ
     */
    BORDER_ONLY(
            "border_only",
            R.string.layer_style_border
    ),

    /**
     * ハッチング（斜線パターン）
     */
    HATCHED(
            "hatched",
            R.string.layer_style_hatched
    );

    /** スタイルID（SharedPreferences保存用） */
    private final String id;

    /** 表示名のリソースID */
    @StringRes
    private final int nameResId;

    /**
     * コンストラクタ
     * 
     * @param id スタイルID
     * @param nameResId 表示名のリソースID
     */
    LayerDisplayStyle(@NonNull String id, @StringRes int nameResId) {
        this.id = id;
        this.nameResId = nameResId;
    }

    /**
     * スタイルIDを取得
     * 
     * @return スタイルID
     */
    @NonNull
    public String getId() {
        return id;
    }

    /**
     * 表示名のリソースIDを取得
     * 
     * @return リソースID
     */
    @StringRes
    public int getNameResId() {
        return nameResId;
    }

    /**
     * IDからLayerDisplayStyleを取得
     * 
     * @param id スタイルID
     * @return 対応するLayerDisplayStyle、見つからない場合はFILLED
     */
    @NonNull
    public static LayerDisplayStyle fromId(@NonNull String id) {
        for (LayerDisplayStyle style : values()) {
            if (style.id.equals(id)) {
                return style;
            }
        }
        return FILLED;  // デフォルト
    }
}

