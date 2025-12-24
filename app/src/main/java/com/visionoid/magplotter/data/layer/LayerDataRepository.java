/**
 * LayerDataRepository.java
 * 
 * VISIONOID MAG PLOTTER - レイヤーデータリポジトリ
 * 
 * 概要:
 *   GeoJSONレイヤーデータのダウンロード、キャッシュ管理を行う。
 *   ハイブリッド方式：オンライン時はダウンロード、オフライン時はキャッシュを使用。
 * 
 * 主な仕様:
 *   - GeoJSONデータのダウンロード
 *   - ローカルキャッシュへの保存・読み込み
 *   - キャッシュ有効期限の管理（30日）
 *   - ネットワーク状態の確認
 * 
 * 制限事項:
 *   - バックグラウンドスレッドで実行が必要
 */
package com.visionoid.magplotter.data.layer;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.preference.PreferenceManager;

import com.visionoid.magplotter.ui.map.layer.LayerType;

import android.content.res.AssetManager;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * レイヤーデータリポジトリクラス
 * 
 * GeoJSONのダウンロードとキャッシュ管理を行う。
 */
public class LayerDataRepository {

    /** ログタグ */
    private static final String TAG = "LayerDataRepository";

    /** キャッシュ有効期限（ミリ秒）: 30日 */
    private static final long CACHE_EXPIRY_MS = 30L * 24 * 60 * 60 * 1000;

    /** SharedPreferencesキーのプレフィックス: キャッシュタイムスタンプ */
    private static final String PREF_CACHE_TIMESTAMP_PREFIX = "layer_cache_timestamp_";

    /** 接続タイムアウト（ミリ秒） */
    private static final int CONNECTION_TIMEOUT = 30000;

    /** 読み取りタイムアウト（ミリ秒） */
    private static final int READ_TIMEOUT = 60000;

    /** キャッシュディレクトリ名 */
    private static final String CACHE_DIR_NAME = "layer_cache";

    /** コンテキスト */
    private final Context context;

    /** SharedPreferences */
    private final SharedPreferences preferences;

    /** キャッシュディレクトリ */
    private final File cacheDir;

    /** バックグラウンド実行用ExecutorService */
    private final ExecutorService executor;

    /**
     * データ取得コールバックインターフェース
     */
    public interface DataCallback {
        /**
         * データ取得成功
         * 
         * @param geoJson GeoJSON文字列
         * @param fromCache キャッシュから取得したかどうか
         */
        void onSuccess(@NonNull String geoJson, boolean fromCache);

        /**
         * データ取得失敗
         * 
         * @param error エラーメッセージ
         */
        void onError(@NonNull String error);

        /**
         * 進捗更新
         * 
         * @param progress 進捗（0-100）
         */
        default void onProgress(int progress) {}
    }

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public LayerDataRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.preferences = PreferenceManager.getDefaultSharedPreferences(context);
        this.cacheDir = new File(context.getFilesDir(), CACHE_DIR_NAME);
        this.executor = Executors.newSingleThreadExecutor();

        // キャッシュディレクトリを作成
        if (!cacheDir.exists()) {
            boolean created = cacheDir.mkdirs();
            Log.d(TAG, "キャッシュディレクトリ作成: " + created);
        }
    }

    /**
     * レイヤーデータを取得（非同期）
     * 
     * オンライン時: ダウンロードしてキャッシュ更新
     * オフライン時: キャッシュから読み込み
     * 
     * @param layerType レイヤータイプ
     * @param forceRefresh 強制リフレッシュフラグ
     * @param callback コールバック
     */
    public void getLayerData(
            @NonNull LayerType layerType,
            boolean forceRefresh,
            @NonNull DataCallback callback
    ) {
        executor.execute(() -> {
            try {
                String geoJson = getLayerDataSync(layerType, forceRefresh, callback);
                if (geoJson != null) {
                    boolean fromCache = !forceRefresh && isCacheValid(layerType);
                    callback.onSuccess(geoJson, fromCache);
                } else {
                    // データ取得失敗
                    Log.e(TAG, "getLayerDataSync returned null for: " + layerType.getId());
                    callback.onError("データが見つかりません: " + layerType.getId());
                }
            } catch (Exception e) {
                Log.e(TAG, "データ取得エラー: " + layerType.getId(), e);
                callback.onError(e.getMessage() != null ? e.getMessage() : "Unknown error");
            }
        });
    }

    /**
     * レイヤーデータを同期的に取得
     * 
     * @param layerType レイヤータイプ
     * @param forceRefresh 強制リフレッシュフラグ
     * @param callback 進捗通知用コールバック（nullでも可）
     * @return GeoJSON文字列、失敗時はnull
     */
    @WorkerThread
    @Nullable
    public String getLayerDataSync(
            @NonNull LayerType layerType,
            boolean forceRefresh,
            @Nullable DataCallback callback
    ) {
        Log.d(TAG, "getLayerDataSync開始: " + layerType.getId() + ", forceRefresh=" + forceRefresh);
        
        // キャッシュが有効で強制リフレッシュでない場合はキャッシュを返す
        if (!forceRefresh && isCacheValid(layerType)) {
            String cached = readFromCache(layerType);
            if (cached != null) {
                Log.d(TAG, "キャッシュから読み込み成功: " + layerType.getId() + ", size=" + cached.length());
                return cached;
            }
        }

        // オンラインの場合はダウンロード（URLが設定されている場合のみ）
        String dataSourceUrl = layerType.getDataSourceUrl();
        if (dataSourceUrl != null && !dataSourceUrl.isEmpty()) {
            boolean networkAvailable = isNetworkAvailable();
            Log.d(TAG, "ネットワーク状態: " + networkAvailable);
            
            if (networkAvailable) {
                Log.d(TAG, "ダウンロード試行: " + dataSourceUrl);
                String downloaded = downloadLayerData(layerType, callback);
                if (downloaded != null) {
                    Log.d(TAG, "ダウンロード成功: " + layerType.getId() + ", size=" + downloaded.length());
                    saveToCache(layerType, downloaded);
                    return downloaded;
                }
                Log.d(TAG, "ダウンロード失敗: " + layerType.getId());
            }
        } else {
            Log.d(TAG, "オンラインダウンロードURLなし、アセットを使用: " + layerType.getId());
        }

        // オフラインまたはダウンロード失敗時はキャッシュを試行
        String cached = readFromCache(layerType);
        if (cached != null) {
            Log.d(TAG, "キャッシュ（期限切れ含む）を使用: " + layerType.getId());
            return cached;
        }

        // キャッシュもない場合はアセットから読み込み（フォールバック）
        Log.d(TAG, "アセットから読み込み試行: " + layerType.getId());
        String fromAssets = loadFromAssets(layerType);
        if (fromAssets != null) {
            Log.d(TAG, "アセットから読み込み成功: " + layerType.getId() + ", size=" + fromAssets.length());
            return fromAssets;
        }

        // 全て失敗
        Log.e(TAG, "全てのデータ取得方法が失敗: " + layerType.getId());
        return null;
    }

    /**
     * アセットからレイヤーデータを読み込み
     * 
     * @param layerType レイヤータイプ
     * @return GeoJSON文字列、存在しない場合はnull
     */
    @WorkerThread
    @Nullable
    private String loadFromAssets(@NonNull LayerType layerType) {
        // まず、LayerTypeに定義されたアセットパスを試す
        String assetPath = layerType.getAssetFilePath();
        if (assetPath != null) {
            String content = readAssetFile(assetPath);
            if (content != null) {
                return content;
            }
        }
        
        // フォールバック: 旧形式のサンプルファイル
        String fallbackPath = getFallbackAssetPath(layerType);
        if (fallbackPath != null) {
            return readAssetFile(fallbackPath);
        }
        
        return null;
    }

    /**
     * アセットファイルを読み込み
     * 
     * @param assetPath アセットファイルパス
     * @return ファイル内容、存在しない場合はnull
     */
    @WorkerThread
    @Nullable
    private String readAssetFile(@NonNull String assetPath) {
        try {
            AssetManager assetManager = context.getAssets();
            try (InputStream is = assetManager.open(assetPath);
                 BufferedReader reader = new BufferedReader(
                         new InputStreamReader(is, StandardCharsets.UTF_8), 65536)) {  // 64KBバッファ
                
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[65536];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                }
                
                Log.d(TAG, "アセットから読み込み完了: " + assetPath + ", size=" + sb.length());
                return sb.toString();
            }
        } catch (IOException e) {
            Log.w(TAG, "アセットファイルが見つかりません: " + assetPath);
            return null;
        }
    }

    /**
     * フォールバック用のアセットパスを取得
     * 
     * @param layerType レイヤータイプ
     * @return アセットファイルパス、対応するファイルがない場合はnull
     */
    @Nullable
    private String getFallbackAssetPath(@NonNull LayerType layerType) {
        switch (layerType) {
            case DID:
                return "sample_did.geojson";
            case AIRPORT_RESTRICTION:
                return "sample_airport.geojson";
            case NO_FLY_ZONE:
                return "sample_no_fly.geojson";
            default:
                return null;
        }
    }

    /**
     * レイヤーデータをダウンロード
     * 
     * @param layerType レイヤータイプ
     * @param callback 進捗通知用コールバック
     * @return GeoJSON文字列
     */
    @WorkerThread
    @Nullable
    private String downloadLayerData(@NonNull LayerType layerType, @Nullable DataCallback callback) {
        String urlString = layerType.getDataSourceUrl();
        
        // URLが設定されていない場合はnullを返す
        if (urlString == null || urlString.isEmpty()) {
            Log.d(TAG, "ダウンロードURLが設定されていません: " + layerType.getId());
            return null;
        }
        
        // URLがZIPファイルの場合は別処理
        if (urlString.toLowerCase().endsWith(".zip")) {
            return downloadAndExtractZip(urlString, layerType, callback);
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json, application/geo+json");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "ダウンロードエラー: HTTP " + responseCode + " - " + layerType.getId());
                return null;
            }

            int contentLength = connection.getContentLength();
            
            try (InputStream is = new BufferedInputStream(connection.getInputStream());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                
                StringBuilder sb = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                int totalRead = 0;

                while ((read = reader.read(buffer)) != -1) {
                    sb.append(buffer, 0, read);
                    totalRead += read;

                    if (callback != null && contentLength > 0) {
                        int progress = (int) ((totalRead * 100L) / contentLength);
                        callback.onProgress(progress);
                    }
                }

                Log.d(TAG, "ダウンロード完了: " + layerType.getId() + " (" + sb.length() + " bytes)");
                return sb.toString();
            }

        } catch (IOException e) {
            Log.e(TAG, "ダウンロード例外: " + layerType.getId(), e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * ZIPファイルをダウンロードしてGeoJSONを抽出
     * 
     * @param urlString ZIPファイルURL
     * @param layerType レイヤータイプ
     * @param callback 進捗通知用コールバック
     * @return GeoJSON文字列
     */
    @WorkerThread
    @Nullable
    private String downloadAndExtractZip(
            @NonNull String urlString,
            @NonNull LayerType layerType,
            @Nullable DataCallback callback
    ) {
        HttpURLConnection connection = null;
        File tempZipFile = null;

        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECTION_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "ZIPダウンロードエラー: HTTP " + responseCode);
                return null;
            }

            int contentLength = connection.getContentLength();
            
            // 一時ファイルにダウンロード
            tempZipFile = new File(cacheDir, "temp_" + layerType.getId() + ".zip");
            
            try (InputStream is = new BufferedInputStream(connection.getInputStream());
                 FileOutputStream fos = new FileOutputStream(tempZipFile)) {
                
                byte[] buffer = new byte[8192];
                int read;
                int totalRead = 0;

                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                    totalRead += read;

                    if (callback != null && contentLength > 0) {
                        int progress = (int) ((totalRead * 50L) / contentLength);  // 50%まで
                        callback.onProgress(progress);
                    }
                }
            }

            // ZIPを解凍してGeoJSONを抽出
            return extractGeoJsonFromZip(tempZipFile, callback);

        } catch (IOException e) {
            Log.e(TAG, "ZIPダウンロード例外", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempZipFile != null && tempZipFile.exists()) {
                boolean deleted = tempZipFile.delete();
                Log.d(TAG, "一時ファイル削除: " + deleted);
            }
        }
    }

    /**
     * ZIPファイルからGeoJSONを抽出
     * 
     * @param zipFile ZIPファイル
     * @param callback 進捗通知用コールバック
     * @return GeoJSON文字列
     */
    @Nullable
    private String extractGeoJsonFromZip(@NonNull File zipFile, @Nullable DataCallback callback) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().toLowerCase();
                
                // GeoJSONまたはJSONファイルを探す
                if (name.endsWith(".geojson") || name.endsWith(".json")) {
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(zis, StandardCharsets.UTF_8));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    
                    while ((line = reader.readLine()) != null) {
                        sb.append(line).append("\n");
                    }

                    if (callback != null) {
                        callback.onProgress(100);
                    }

                    Log.d(TAG, "ZIP抽出完了: " + entry.getName());
                    return sb.toString();
                }
                
                zis.closeEntry();
            }

            Log.w(TAG, "ZIPにGeoJSONファイルが見つかりません");
            return null;

        } catch (IOException e) {
            Log.e(TAG, "ZIP抽出例外", e);
            return null;
        }
    }

    /**
     * キャッシュからデータを読み込み
     * 
     * @param layerType レイヤータイプ
     * @return GeoJSON文字列、存在しない場合はnull
     */
    @WorkerThread
    @Nullable
    private String readFromCache(@NonNull LayerType layerType) {
        File cacheFile = getCacheFile(layerType);
        if (!cacheFile.exists()) {
            return null;
        }

        try (FileInputStream fis = new FileInputStream(cacheFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fis, StandardCharsets.UTF_8))) {
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            
            return sb.toString();

        } catch (IOException e) {
            Log.e(TAG, "キャッシュ読み込みエラー: " + layerType.getId(), e);
            return null;
        }
    }

    /**
     * キャッシュにデータを保存
     * 
     * @param layerType レイヤータイプ
     * @param geoJson GeoJSON文字列
     */
    @WorkerThread
    private void saveToCache(@NonNull LayerType layerType, @NonNull String geoJson) {
        File cacheFile = getCacheFile(layerType);

        try (FileOutputStream fos = new FileOutputStream(cacheFile)) {
            fos.write(geoJson.getBytes(StandardCharsets.UTF_8));
            
            // タイムスタンプを保存
            preferences.edit()
                    .putLong(getCacheTimestampKey(layerType), System.currentTimeMillis())
                    .apply();

            Log.d(TAG, "キャッシュ保存完了: " + layerType.getId());

        } catch (IOException e) {
            Log.e(TAG, "キャッシュ保存エラー: " + layerType.getId(), e);
        }
    }

    /**
     * キャッシュが有効かチェック
     * 
     * @param layerType レイヤータイプ
     * @return 有効ならtrue
     */
    public boolean isCacheValid(@NonNull LayerType layerType) {
        File cacheFile = getCacheFile(layerType);
        if (!cacheFile.exists()) {
            return false;
        }

        long timestamp = preferences.getLong(getCacheTimestampKey(layerType), 0);
        long age = System.currentTimeMillis() - timestamp;

        return age < CACHE_EXPIRY_MS;
    }

    /**
     * キャッシュが存在するかチェック
     * 
     * @param layerType レイヤータイプ
     * @return 存在すればtrue
     */
    public boolean hasCachedData(@NonNull LayerType layerType) {
        return getCacheFile(layerType).exists();
    }

    /**
     * キャッシュを削除
     * 
     * @param layerType レイヤータイプ
     */
    public void clearCache(@NonNull LayerType layerType) {
        File cacheFile = getCacheFile(layerType);
        if (cacheFile.exists()) {
            boolean deleted = cacheFile.delete();
            Log.d(TAG, "キャッシュ削除: " + layerType.getId() + " -> " + deleted);
        }

        preferences.edit()
                .remove(getCacheTimestampKey(layerType))
                .apply();
    }

    /**
     * 全キャッシュを削除
     */
    public void clearAllCache() {
        for (LayerType type : LayerType.values()) {
            clearCache(type);
        }
    }

    /**
     * キャッシュファイルを取得
     * 
     * @param layerType レイヤータイプ
     * @return キャッシュファイル
     */
    @NonNull
    private File getCacheFile(@NonNull LayerType layerType) {
        return new File(cacheDir, layerType.getCacheFileName());
    }

    /**
     * キャッシュタイムスタンプのSharedPreferencesキーを取得
     * 
     * @param layerType レイヤータイプ
     * @return キー文字列
     */
    @NonNull
    private String getCacheTimestampKey(@NonNull LayerType layerType) {
        return PREF_CACHE_TIMESTAMP_PREFIX + layerType.getId();
    }

    /**
     * ネットワークが利用可能かチェック
     * 
     * @return 利用可能ならtrue
     */
    public boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (cm == null) {
            return false;
        }

        NetworkCapabilities capabilities = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (capabilities == null) {
            return false;
        }

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    /**
     * キャッシュサイズを取得
     * 
     * @return キャッシュサイズ（バイト）
     */
    public long getCacheSize() {
        long size = 0;
        File[] files = cacheDir.listFiles();
        if (files != null) {
            for (File file : files) {
                size += file.length();
            }
        }
        return size;
    }

    /**
     * ExecutorServiceをシャットダウン
     */
    public void shutdown() {
        executor.shutdown();
    }
}

