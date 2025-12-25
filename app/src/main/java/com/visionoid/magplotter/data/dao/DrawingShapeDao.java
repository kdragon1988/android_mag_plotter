/**
 * DrawingShapeDao.java
 * 
 * VISIONOID MAG PLOTTER - 作図シェイプDAO
 * 
 * 概要:
 *   DrawingShapeエンティティに対するデータアクセスオブジェクト。
 *   Roomデータベースの各種クエリを定義。
 * 
 * 主な仕様:
 *   - CRUD操作のサポート
 *   - ミッションIDによるフィルタリング
 *   - LiveDataによるリアクティブ更新
 * 
 * 制限事項:
 *   - バックグラウンドスレッドでの実行が必要（LiveData以外）
 */
package com.visionoid.magplotter.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.visionoid.magplotter.data.model.DrawingShape;

import java.util.List;

/**
 * 作図シェイプDAOインターフェース
 */
@Dao
public interface DrawingShapeDao {

    // ==================== 挿入 ====================

    /**
     * シェイプを挿入
     * 
     * @param shape 挿入するシェイプ
     * @return 挿入されたシェイプのID
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(DrawingShape shape);

    /**
     * 複数のシェイプを挿入
     * 
     * @param shapes 挿入するシェイプリスト
     * @return 挿入されたシェイプのIDリスト
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    List<Long> insertAll(List<DrawingShape> shapes);

    // ==================== 更新 ====================

    /**
     * シェイプを更新
     * 
     * @param shape 更新するシェイプ
     */
    @Update
    void update(DrawingShape shape);

    /**
     * シェイプの表示状態を更新
     * 
     * @param id シェイプID
     * @param isVisible 表示状態
     */
    @Query("UPDATE drawing_shapes SET is_visible = :isVisible, updated_at = :updatedAt WHERE id = :id")
    void updateVisibility(long id, boolean isVisible, long updatedAt);

    /**
     * シェイプの座標を更新
     * 
     * @param id シェイプID
     * @param coordinatesJson 座標データJSON
     * @param area 面積
     * @param perimeter 周囲長
     * @param updatedAt 更新日時
     */
    @Query("UPDATE drawing_shapes SET coordinates_json = :coordinatesJson, area = :area, perimeter = :perimeter, updated_at = :updatedAt WHERE id = :id")
    void updateCoordinates(long id, String coordinatesJson, double area, double perimeter, long updatedAt);

    /**
     * シェイプ名を更新
     * 
     * @param id シェイプID
     * @param name 新しい名前
     * @param updatedAt 更新日時
     */
    @Query("UPDATE drawing_shapes SET name = :name, updated_at = :updatedAt WHERE id = :id")
    void updateName(long id, String name, long updatedAt);

    /**
     * シェイプの色を更新
     * 
     * @param id シェイプID
     * @param fillColor 塗りつぶし色
     * @param strokeColor 線の色
     * @param updatedAt 更新日時
     */
    @Query("UPDATE drawing_shapes SET fill_color = :fillColor, stroke_color = :strokeColor, updated_at = :updatedAt WHERE id = :id")
    void updateColors(long id, int fillColor, int strokeColor, long updatedAt);

    // ==================== 削除 ====================

    /**
     * シェイプを削除
     * 
     * @param shape 削除するシェイプ
     */
    @Delete
    void delete(DrawingShape shape);

    /**
     * IDでシェイプを削除
     * 
     * @param id 削除するシェイプのID
     */
    @Query("DELETE FROM drawing_shapes WHERE id = :id")
    void deleteById(long id);

    /**
     * ミッションの全シェイプを削除
     * 
     * @param missionId ミッションID
     */
    @Query("DELETE FROM drawing_shapes WHERE mission_id = :missionId")
    void deleteByMissionId(long missionId);

    // ==================== 取得 ====================

    /**
     * IDでシェイプを取得
     * 
     * @param id シェイプID
     * @return シェイプ
     */
    @Query("SELECT * FROM drawing_shapes WHERE id = :id")
    DrawingShape getById(long id);

    /**
     * IDでシェイプを取得（LiveData）
     * 
     * @param id シェイプID
     * @return シェイプのLiveData
     */
    @Query("SELECT * FROM drawing_shapes WHERE id = :id")
    LiveData<DrawingShape> getByIdLive(long id);

    /**
     * ミッションの全シェイプを取得（作成日時順）
     * 
     * @param missionId ミッションID
     * @return シェイプリストのLiveData
     */
    @Query("SELECT * FROM drawing_shapes WHERE mission_id = :missionId ORDER BY created_at ASC")
    LiveData<List<DrawingShape>> getByMissionIdLive(long missionId);

    /**
     * ミッションの全シェイプを取得（同期）
     * 
     * @param missionId ミッションID
     * @return シェイプリスト
     */
    @Query("SELECT * FROM drawing_shapes WHERE mission_id = :missionId ORDER BY created_at ASC")
    List<DrawingShape> getByMissionId(long missionId);

    /**
     * ミッションの表示中シェイプを取得
     * 
     * @param missionId ミッションID
     * @return 表示中シェイプリストのLiveData
     */
    @Query("SELECT * FROM drawing_shapes WHERE mission_id = :missionId AND is_visible = 1 ORDER BY created_at ASC")
    LiveData<List<DrawingShape>> getVisibleByMissionIdLive(long missionId);

    /**
     * ミッションのシェイプ数を取得
     * 
     * @param missionId ミッションID
     * @return シェイプ数
     */
    @Query("SELECT COUNT(*) FROM drawing_shapes WHERE mission_id = :missionId")
    int getCountByMissionId(long missionId);

    /**
     * タイプ別にシェイプを取得
     * 
     * @param missionId ミッションID
     * @param shapeType シェイプタイプ
     * @return シェイプリスト
     */
    @Query("SELECT * FROM drawing_shapes WHERE mission_id = :missionId AND shape_type = :shapeType ORDER BY created_at ASC")
    List<DrawingShape> getByMissionIdAndType(long missionId, String shapeType);
}

