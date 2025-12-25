/**
 * AppDatabase.java
 * 
 * VISIONOID MAG PLOTTER - アプリケーションデータベース
 * 
 * 概要:
 *   Roomデータベースのメインクラス。
 *   シングルトンパターンでデータベースインスタンスを管理。
 * 
 * 主な仕様:
 *   - Mission と MeasurementPoint のエンティティを管理
 *   - スレッドセーフなシングルトン実装
 *   - マイグレーション対応
 * 
 * 制限事項:
 *   - アプリケーションコンテキストを使用してインスタンス化する必要がある
 */
package com.visionoid.magplotter.data.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.visionoid.magplotter.data.dao.DrawingShapeDao;
import com.visionoid.magplotter.data.dao.MeasurementPointDao;
import com.visionoid.magplotter.data.dao.MissionDao;
import com.visionoid.magplotter.data.model.DrawingShape;
import com.visionoid.magplotter.data.model.MeasurementPoint;
import com.visionoid.magplotter.data.model.Mission;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * アプリケーションデータベースクラス
 * 
 * Roomデータベースの抽象クラス。
 * シングルトンパターンで実装。
 */
@Database(
    entities = {
        Mission.class,
        MeasurementPoint.class,
        DrawingShape.class
    },
    version = 2,
    exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    /** データベース名 */
    private static final String DATABASE_NAME = "visionoid_mag_plotter.db";

    /** スレッドプール（バックグラウンド処理用） */
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /** シングルトンインスタンス */
    private static volatile AppDatabase instance;

    /**
     * MissionDaoを取得
     * @return MissionDaoインスタンス
     */
    public abstract MissionDao missionDao();

    /**
     * MeasurementPointDaoを取得
     * @return MeasurementPointDaoインスタンス
     */
    public abstract MeasurementPointDao measurementPointDao();

    /**
     * DrawingShapeDaoを取得
     * @return DrawingShapeDaoインスタンス
     */
    public abstract DrawingShapeDao drawingShapeDao();

    /**
     * データベースインスタンスを取得
     * 
     * @param context アプリケーションコンテキスト
     * @return AppDatabaseインスタンス
     */
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            DATABASE_NAME
                    )
                    .addCallback(roomDatabaseCallback)
                    .fallbackToDestructiveMigration()
                    .build();
                }
            }
        }
        return instance;
    }

    /**
     * データベースコールバック
     * 
     * データベース作成時の初期化処理を定義。
     */
    private static final RoomDatabase.Callback roomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);
            // データベース作成時の初期化処理（必要に応じて実装）
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            // データベースオープン時の処理（必要に応じて実装）
        }
    };

    /**
     * データベースをクリア
     * 
     * テスト用途。全データを削除してデータベースを再構築。
     */
    public void clearAllTables() {
        if (instance != null) {
            databaseWriteExecutor.execute(() -> {
                instance.clearAllTables();
            });
        }
    }
}

