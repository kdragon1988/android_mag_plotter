/**
 * MagPlotterApplication.java
 * 
 * VISIONOID MAG PLOTTER - アプリケーションクラス
 * 
 * 概要:
 *   アプリケーション全体のライフサイクルを管理するクラス。
 *   アプリ起動時の初期化処理を担当。
 * 
 * 主な仕様:
 *   - osmdroidの初期設定
 *   - データベースの初期化
 *   - 共有設定の初期化
 * 
 * 制限事項:
 *   - AndroidManifestで指定する必要がある
 */
package com.visionoid.magplotter;

import android.app.Application;
import android.content.Context;

import org.osmdroid.config.Configuration;

import java.io.File;

/**
 * アプリケーションクラス
 * 
 * アプリケーション全体の初期化を管理。
 */
public class MagPlotterApplication extends Application {

    /** アプリケーションインスタンス（シングルトン） */
    private static MagPlotterApplication instance;

    /**
     * アプリケーションインスタンスを取得
     * 
     * @return アプリケーションインスタンス
     */
    public static MagPlotterApplication getInstance() {
        return instance;
    }

    /**
     * アプリケーションコンテキストを取得
     * 
     * @return アプリケーションコンテキスト
     */
    public static Context getAppContext() {
        return instance.getApplicationContext();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        // osmdroid の初期設定
        initializeOsmdroid();
    }

    /**
     * osmdroid の初期化
     * 
     * 地図タイルのキャッシュディレクトリなどを設定。
     */
    private void initializeOsmdroid() {
        // osmdroid 設定を取得
        Configuration.getInstance().load(
            getApplicationContext(),
            getSharedPreferences("osmdroid", MODE_PRIVATE)
        );
        
        // User-Agent を設定（OSM のポリシー準拠）
        Configuration.getInstance().setUserAgentValue(
            getPackageName() + "/1.0.0"
        );
        
        // タイルキャッシュディレクトリを設定
        File osmdroidBasePath = new File(getCacheDir(), "osmdroid");
        Configuration.getInstance().setOsmdroidBasePath(osmdroidBasePath);
        
        File osmdroidTileCache = new File(osmdroidBasePath, "tiles");
        Configuration.getInstance().setOsmdroidTileCache(osmdroidTileCache);
        
        // オフラインモード時のタイル有効期限を設定（30日）
        Configuration.getInstance().setExpirationOverrideDuration(30L * 24 * 60 * 60 * 1000);
    }
}

