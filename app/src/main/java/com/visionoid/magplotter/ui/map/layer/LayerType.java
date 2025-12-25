/**
 * LayerType.java
 * 
 * VISIONOID MAG PLOTTER - マップレイヤー種別定義
 * 
 * 概要:
 *   ドローン飛行制限区域のレイヤー種別を定義するEnum。
 *   各レイヤーの名称、色、データソースURLなどを管理。
 * 
 * 主な仕様:
 *   - 人口密集区域（DID）: 国土数値情報A16
 *   - 空港制限区域: 国土交通省航空局データ
 *   - 小型無人機等飛行禁止区域: 警察庁データ
 * 
 * データソース:
 *   - DID: https://nlftp.mlit.go.jp/ksj/gml/datalist/KsjTmplt-A16-v2_3.html
 *   - 空港: https://www.mlit.go.jp/koku/koku_tk2_000023.html
 *   - 禁止区域: https://www.npa.go.jp/bureau/security/kogatamujinki/shitei.html
 */
package com.visionoid.magplotter.ui.map.layer;

import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.visionoid.magplotter.R;

/**
 * マップレイヤー種別を定義するEnum
 */
public enum LayerType {

    /**
     * 人口集中地区（DID: Densely Inhabited District）
     * 国勢調査に基づく人口集中地区
     * データソース: 国土数値情報 A16
     */
    DID(
            "did",
            R.string.layer_name_did,
            Color.argb(80, 255, 107, 53),   // オレンジ系（半透明）
            Color.argb(180, 255, 107, 53),
            null,  // オンラインダウンロード不要（アセットから読み込み）
            "did_cache.json",
            "layers/did_japan.geojson"  // アセットファイルパス
    ),

    /**
     * 空港等周辺の飛行禁止区域
     * 小型無人機等飛行禁止法に基づく空港周辺区域
     * データソース: 国土交通省航空局
     */
    AIRPORT_RESTRICTION(
            "airport",
            R.string.layer_name_airport,
            Color.argb(80, 255, 0, 85),     // 赤系（半透明）
            Color.argb(180, 255, 0, 85),
            null,
            "airport_cache.json",
            "layers/airport_restriction.geojson"
    ),

    /**
     * 小型無人機等飛行禁止区域
     * 重要施設周辺の飛行禁止空域（国会議事堂、皇居、原子力事業所など）
     * データソース: 警察庁
     */
    NO_FLY_ZONE(
            "no_fly",
            R.string.layer_name_no_fly,
            Color.argb(100, 255, 0, 0),     // 赤（やや不透明）
            Color.argb(200, 255, 0, 0),
            null,
            "no_fly_cache.json",
            "layers/no_fly_zone.geojson"
    );

    /** レイヤーID（SharedPreferences保存用） */
    private final String id;

    /** 表示名のリソースID */
    @StringRes
    private final int nameResId;

    /** 塗りつぶし色 */
    @ColorInt
    private final int fillColor;

    /** 境界線色 */
    @ColorInt
    private final int strokeColor;

    /** データソースURL（オンラインダウンロード用、nullの場合はアセットのみ） */
    @Nullable
    private final String dataSourceUrl;

    /** キャッシュファイル名 */
    private final String cacheFileName;

    /** アセットファイルパス */
    @Nullable
    private final String assetFilePath;

    /**
     * コンストラクタ
     * 
     * @param id レイヤーID
     * @param nameResId 表示名のリソースID
     * @param fillColor 塗りつぶし色（ARGB）
     * @param strokeColor 境界線色（ARGB）
     * @param dataSourceUrl データソースURL（nullの場合はアセットのみ使用）
     * @param cacheFileName キャッシュファイル名
     * @param assetFilePath アセットファイルパス
     */
    LayerType(
            @NonNull String id,
            @StringRes int nameResId,
            @ColorInt int fillColor,
            @ColorInt int strokeColor,
            @Nullable String dataSourceUrl,
            @NonNull String cacheFileName,
            @Nullable String assetFilePath
    ) {
        this.id = id;
        this.nameResId = nameResId;
        this.fillColor = fillColor;
        this.strokeColor = strokeColor;
        this.dataSourceUrl = dataSourceUrl;
        this.cacheFileName = cacheFileName;
        this.assetFilePath = assetFilePath;
    }

    /**
     * レイヤーIDを取得
     * 
     * @return レイヤーID
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
     * 塗りつぶし色を取得
     * 
     * @return 色（ARGB）
     */
    @ColorInt
    public int getFillColor() {
        return fillColor;
    }

    /**
     * 境界線色を取得
     * 
     * @return 色（ARGB）
     */
    @ColorInt
    public int getStrokeColor() {
        return strokeColor;
    }

    /**
     * データソースURLを取得
     * 
     * @return URL文字列、アセットのみの場合はnull
     */
    @Nullable
    public String getDataSourceUrl() {
        return dataSourceUrl;
    }

    /**
     * キャッシュファイル名を取得
     * 
     * @return ファイル名
     */
    @NonNull
    public String getCacheFileName() {
        return cacheFileName;
    }

    /**
     * アセットファイルパスを取得
     * 
     * @return アセットファイルパス、存在しない場合はnull
     */
    @Nullable
    public String getAssetFilePath() {
        return assetFilePath;
    }

    /**
     * アセットからデータを読み込むかどうか
     * 
     * @return アセットからデータを読み込む場合はtrue
     */
    public boolean hasAssetData() {
        return assetFilePath != null && !assetFilePath.isEmpty();
    }

    /**
     * SharedPreferencesキーを取得
     * 
     * @return キー文字列（layer_visible_{id}形式）
     */
    @NonNull
    public String getVisibilityPrefKey() {
        return "layer_visible_" + id;
    }

    /**
     * IDからLayerTypeを取得
     * 
     * @param id レイヤーID
     * @return 対応するLayerType、見つからない場合はnull
     */
    public static LayerType fromId(@NonNull String id) {
        for (LayerType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}

