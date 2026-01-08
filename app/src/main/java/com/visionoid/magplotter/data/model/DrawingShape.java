/**
 * DrawingShape.java
 * 
 * VISIONOID MAG PLOTTER - 作図シェイプエンティティ
 * 
 * 概要:
 *   地図上に描画された図形（多角形、線、円）の情報を格納するデータクラス。
 *   Roomデータベースのエンティティとして使用される。
 * 
 * 主な仕様:
 *   - ミッションに紐付けて図形を保存
 *   - 多角形、線（ポリライン）、円の3種類をサポート
 *   - 座標はJSON形式で保存
 *   - 面積・周囲長・距離を自動計算して保存
 * 
 * 制限事項:
 *   - missionIdは必須（外部キー制約）
 *   - 座標データはJSON配列形式で保存
 */
package com.visionoid.magplotter.data.model;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 作図シェイプエンティティクラス
 * 
 * 地図上に描画された図形データを表現する。
 */
@Entity(
    tableName = "drawing_shapes",
    foreignKeys = @ForeignKey(
        entity = Mission.class,
        parentColumns = "id",
        childColumns = "mission_id",
        onDelete = ForeignKey.CASCADE
    ),
    indices = {@Index("mission_id")}
)
public class DrawingShape {

    // ==================== 図形タイプ定数 ====================
    
    /** 多角形（ポリゴン） */
    public static final String TYPE_POLYGON = "polygon";
    
    /** 線（ポリライン） */
    public static final String TYPE_POLYLINE = "polyline";
    
    /** 円（サークル） */
    public static final String TYPE_CIRCLE = "circle";

    // ==================== フィールド ====================

    /** シェイプID（自動生成） */
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    private long id;

    /** ミッションID（外部キー） */
    @ColumnInfo(name = "mission_id")
    private long missionId;

    /** 図形名（ユーザー入力） */
    @NonNull
    @ColumnInfo(name = "name")
    private String name;

    /** 図形タイプ（polygon, polyline, circle） */
    @NonNull
    @ColumnInfo(name = "shape_type")
    private String shapeType;

    /** 
     * 座標データ（JSON形式）
     * 
     * - polygon/polyline: [{"lat": 35.0, "lng": 139.0}, ...]
     * - circle: {"center": {"lat": 35.0, "lng": 139.0}, "radius": 100.0}
     */
    @NonNull
    @ColumnInfo(name = "coordinates_json")
    private String coordinatesJson;

    /** 塗りつぶし色（ARGB） */
    @ColumnInfo(name = "fill_color")
    private int fillColor;

    /** 線の色（ARGB） */
    @ColumnInfo(name = "stroke_color")
    private int strokeColor;

    /** 線の幅（px） */
    @ColumnInfo(name = "stroke_width", defaultValue = "3.0")
    private float strokeWidth;

    /** 面積（平方メートル、polygon/circleのみ） */
    @ColumnInfo(name = "area", defaultValue = "0.0")
    private double area;

    /** 周囲長（メートル、polygon/polyline/circleで使用） */
    @ColumnInfo(name = "perimeter", defaultValue = "0.0")
    private double perimeter;

    /** 作成日時（Unixタイムスタンプ） */
    @ColumnInfo(name = "created_at")
    private long createdAt;

    /** 更新日時（Unixタイムスタンプ） */
    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    /** 表示フラグ */
    @ColumnInfo(name = "is_visible", defaultValue = "1")
    private boolean isVisible;

    // ==================== コンストラクタ ====================

    /**
     * コンストラクタ
     * 
     * @param missionId ミッションID
     * @param name 図形名
     * @param shapeType 図形タイプ（TYPE_POLYGON, TYPE_POLYLINE, TYPE_CIRCLE）
     * @param coordinatesJson 座標データ（JSON形式）
     */
    public DrawingShape(long missionId, @NonNull String name, @NonNull String shapeType, @NonNull String coordinatesJson) {
        this.missionId = missionId;
        this.name = name;
        this.shapeType = shapeType;
        this.coordinatesJson = coordinatesJson;
        this.fillColor = 0x40FF5722;  // オレンジ（半透明）
        this.strokeColor = 0xFFFF5722; // オレンジ
        this.strokeWidth = 3.0f;
        this.area = 0.0;
        this.perimeter = 0.0;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = System.currentTimeMillis();
        this.isVisible = true;
    }

    // ==================== Getter/Setter ====================

    /**
     * シェイプIDを取得
     * @return シェイプID
     */
    public long getId() {
        return id;
    }

    /**
     * シェイプIDを設定
     * @param id シェイプID
     */
    public void setId(long id) {
        this.id = id;
    }

    /**
     * ミッションIDを取得
     * @return ミッションID
     */
    public long getMissionId() {
        return missionId;
    }

    /**
     * ミッションIDを設定
     * @param missionId ミッションID
     */
    public void setMissionId(long missionId) {
        this.missionId = missionId;
    }

    /**
     * 図形名を取得
     * @return 図形名
     */
    @NonNull
    public String getName() {
        return name;
    }

    /**
     * 図形名を設定
     * @param name 図形名
     */
    public void setName(@NonNull String name) {
        this.name = name;
    }

    /**
     * 図形タイプを取得
     * @return 図形タイプ
     */
    @NonNull
    public String getShapeType() {
        return shapeType;
    }

    /**
     * 図形タイプを設定
     * @param shapeType 図形タイプ
     */
    public void setShapeType(@NonNull String shapeType) {
        this.shapeType = shapeType;
    }

    /**
     * 座標データJSONを取得
     * @return 座標データJSON
     */
    @NonNull
    public String getCoordinatesJson() {
        return coordinatesJson;
    }

    /**
     * 座標データJSONを設定
     * @param coordinatesJson 座標データJSON
     */
    public void setCoordinatesJson(@NonNull String coordinatesJson) {
        this.coordinatesJson = coordinatesJson;
    }

    /**
     * 塗りつぶし色を取得
     * @return 塗りつぶし色（ARGB）
     */
    public int getFillColor() {
        return fillColor;
    }

    /**
     * 塗りつぶし色を設定
     * @param fillColor 塗りつぶし色（ARGB）
     */
    public void setFillColor(int fillColor) {
        this.fillColor = fillColor;
    }

    /**
     * 線の色を取得
     * @return 線の色（ARGB）
     */
    public int getStrokeColor() {
        return strokeColor;
    }

    /**
     * 線の色を設定
     * @param strokeColor 線の色（ARGB）
     */
    public void setStrokeColor(int strokeColor) {
        this.strokeColor = strokeColor;
    }

    /**
     * 線の幅を取得
     * @return 線の幅（px）
     */
    public float getStrokeWidth() {
        return strokeWidth;
    }

    /**
     * 線の幅を設定
     * @param strokeWidth 線の幅（px）
     */
    public void setStrokeWidth(float strokeWidth) {
        this.strokeWidth = strokeWidth;
    }

    /**
     * 面積を取得
     * @return 面積（平方メートル）
     */
    public double getArea() {
        return area;
    }

    /**
     * 面積を設定
     * @param area 面積（平方メートル）
     */
    public void setArea(double area) {
        this.area = area;
    }

    /**
     * 周囲長を取得
     * @return 周囲長（メートル）
     */
    public double getPerimeter() {
        return perimeter;
    }

    /**
     * 周囲長を設定
     * @param perimeter 周囲長（メートル）
     */
    public void setPerimeter(double perimeter) {
        this.perimeter = perimeter;
    }

    /**
     * 作成日時を取得
     * @return 作成日時（Unixタイムスタンプ）
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 作成日時を設定
     * @param createdAt 作成日時（Unixタイムスタンプ）
     */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 更新日時を取得
     * @return 更新日時（Unixタイムスタンプ）
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 更新日時を設定
     * @param updatedAt 更新日時（Unixタイムスタンプ）
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 表示フラグを取得
     * @return 表示フラグ
     */
    public boolean isVisible() {
        return isVisible;
    }

    /**
     * 表示フラグを設定
     * @param visible 表示フラグ
     */
    public void setVisible(boolean visible) {
        isVisible = visible;
    }

    /**
     * 更新日時を現在時刻に更新
     */
    public void updateTimestamp() {
        this.updatedAt = System.currentTimeMillis();
    }

    // ==================== ユーティリティ ====================

    /**
     * 多角形かどうかを判定
     * @return 多角形の場合true
     */
    public boolean isPolygon() {
        return TYPE_POLYGON.equals(shapeType);
    }

    /**
     * 線かどうかを判定
     * @return 線の場合true
     */
    public boolean isPolyline() {
        return TYPE_POLYLINE.equals(shapeType);
    }

    /**
     * 円かどうかを判定
     * @return 円の場合true
     */
    public boolean isCircle() {
        return TYPE_CIRCLE.equals(shapeType);
    }

    /**
     * 面積を表示用文字列に変換
     * @return 面積文字列（例: "123.4 m²" または "1.23 ha"）
     */
    public String getAreaDisplayString() {
        if (area < 10000) {
            return String.format("%.1f m²", area);
        } else {
            return String.format("%.2f ha", area / 10000);
        }
    }

    /**
     * 周囲長を表示用文字列に変換
     * @return 周囲長文字列（例: "123.4 m" または "1.23 km"）
     */
    public String getPerimeterDisplayString() {
        if (perimeter < 1000) {
            return String.format("%.1f m", perimeter);
        } else {
            return String.format("%.2f km", perimeter / 1000);
        }
    }
}


