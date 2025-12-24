/**
 * SplashActivity.java
 * 
 * VISIONOID MAG PLOTTER - スプラッシュ画面
 * 
 * 概要:
 *   アプリ起動時のスプラッシュ画面を表示するアクティビティ。
 *   スパイテック風のスキャンアニメーションを表示。
 * 
 * 主な仕様:
 *   - 起動アニメーション表示（約2.5秒）
 *   - システム初期化のシミュレーション
 *   - ミッション一覧画面への遷移
 * 
 * 制限事項:
 *   - 戻るボタンを無効化
 */
package com.visionoid.magplotter.ui.splash;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.visionoid.magplotter.R;
import com.visionoid.magplotter.ui.mission.MissionListActivity;

/**
 * スプラッシュ画面アクティビティ
 * 
 * アプリ起動時にスパイテック風のアニメーションを表示。
 */
public class SplashActivity extends AppCompatActivity {

    /** ハンドラ（UIスレッド） */
    private Handler handler;

    /** スプラッシュ表示時間（ミリ秒） */
    private static final long SPLASH_DURATION = 2500;

    // UI要素
    private TextView textAppName;
    private TextView textTagline;
    private TextView textStatus;
    private View scanLine;
    private ProgressBar progressBar;
    private View containerLogo;

    /** 初期化ステータスメッセージ配列 */
    private final String[] statusMessages = {
            "INITIALIZING SYSTEM...",
            "LOADING SENSORS...",
            "CALIBRATING...",
            "SYSTEM READY"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        handler = new Handler(Looper.getMainLooper());

        // UI要素を取得
        initializeViews();

        // アニメーション開始
        startAnimations();

        // 一定時間後にメイン画面へ遷移
        handler.postDelayed(this::navigateToMain, SPLASH_DURATION);
    }

    /**
     * UI要素を初期化
     */
    private void initializeViews() {
        textAppName = findViewById(R.id.text_app_name);
        textTagline = findViewById(R.id.text_tagline);
        textStatus = findViewById(R.id.text_status);
        scanLine = findViewById(R.id.scan_line);
        progressBar = findViewById(R.id.progress_bar);
        containerLogo = findViewById(R.id.container_logo);

        // 初期状態を設定
        textAppName.setAlpha(0f);
        textTagline.setAlpha(0f);
        textStatus.setAlpha(0f);
        if (containerLogo != null) {
            containerLogo.setScaleX(0.8f);
            containerLogo.setScaleY(0.8f);
        }
    }

    /**
     * アニメーションを開始
     */
    private void startAnimations() {
        // ロゴのスケールアニメーション
        if (containerLogo != null) {
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(containerLogo, "scaleX", 0.8f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(containerLogo, "scaleY", 0.8f, 1.0f);
            scaleX.setDuration(500);
            scaleY.setDuration(500);
            scaleX.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleY.setInterpolator(new AccelerateDecelerateInterpolator());
            scaleX.start();
            scaleY.start();
        }

        // アプリ名のフェードイン
        ObjectAnimator fadeInAppName = ObjectAnimator.ofFloat(textAppName, "alpha", 0f, 1f);
        fadeInAppName.setDuration(600);
        fadeInAppName.setStartDelay(200);
        fadeInAppName.start();

        // タグラインのフェードイン
        ObjectAnimator fadeInTagline = ObjectAnimator.ofFloat(textTagline, "alpha", 0f, 1f);
        fadeInTagline.setDuration(600);
        fadeInTagline.setStartDelay(500);
        fadeInTagline.start();

        // ステータステキストのフェードイン
        ObjectAnimator fadeInStatus = ObjectAnimator.ofFloat(textStatus, "alpha", 0f, 1f);
        fadeInStatus.setDuration(400);
        fadeInStatus.setStartDelay(800);
        fadeInStatus.start();

        // スキャンラインアニメーション
        startScanLineAnimation();

        // ステータスメッセージの更新
        startStatusUpdates();

        // プログレスバーアニメーション
        startProgressAnimation();
    }

    /**
     * スキャンラインアニメーションを開始
     */
    private void startScanLineAnimation() {
        if (scanLine == null) return;

        scanLine.post(() -> {
            View parent = (View) scanLine.getParent();
            if (parent == null) return;

            int parentHeight = parent.getHeight();
            
            ObjectAnimator scanAnimator = ObjectAnimator.ofFloat(
                    scanLine, "translationY", 0f, parentHeight
            );
            scanAnimator.setDuration(1500);
            scanAnimator.setRepeatCount(ValueAnimator.INFINITE);
            scanAnimator.setRepeatMode(ValueAnimator.RESTART);
            scanAnimator.setInterpolator(new LinearInterpolator());
            scanAnimator.start();
        });
    }

    /**
     * ステータスメッセージの更新を開始
     */
    private void startStatusUpdates() {
        int delay = 0;
        for (int i = 0; i < statusMessages.length; i++) {
            final int index = i;
            handler.postDelayed(() -> {
                if (textStatus != null) {
                    textStatus.setText(statusMessages[index]);
                    
                    // 最後のメッセージ時に色を変更
                    if (index == statusMessages.length - 1) {
                        textStatus.setTextColor(getColor(R.color.status_safe));
                    }
                }
            }, delay);
            delay += 600;
        }
    }

    /**
     * プログレスバーアニメーションを開始
     */
    private void startProgressAnimation() {
        if (progressBar == null) return;

        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnimator.setDuration(SPLASH_DURATION - 300);
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.start();
    }

    /**
     * メイン画面へ遷移
     */
    private void navigateToMain() {
        // フェードアウトアニメーション
        View rootView = findViewById(android.R.id.content);
        ObjectAnimator fadeOut = ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0f);
        fadeOut.setDuration(300);
        fadeOut.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Intent intent = new Intent(SplashActivity.this, MissionListActivity.class);
                startActivity(intent);
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                finish();
            }
        });
        fadeOut.start();
    }

    @Override
    public void onBackPressed() {
        // スプラッシュ画面では戻るボタンを無効化
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}

