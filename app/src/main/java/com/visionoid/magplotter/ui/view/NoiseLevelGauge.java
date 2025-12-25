/**
 * NoiseLevelGauge.java
 * 
 * VISIONOID MAG PLOTTER - ノイズレベルゲージView
 * 
 * 概要:
 *   オーディオVUメーター風のセグメント式レベルゲージ。
 *   ノイズ値に応じてセグメントが点灯し、色が変化する。
 * 
 * 主な仕様:
 *   - 12セグメントのバー表示
 *   - 安全域: 緑、警告域: 黄、危険域: 赤
 *   - 軽量実装（メモリ効率重視）
 * 
 * 制限事項:
 *   - 閾値はsetThresholds()で設定が必要
 */
package com.visionoid.magplotter.ui.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * ノイズレベルゲージViewクラス
 * 
 * VUメーター風のセグメント式レベル表示を行う。
 */
public class NoiseLevelGauge extends View {

    /** セグメント数 */
    private static final int SEGMENT_COUNT = 12;
    
    /** セグメント間の隙間（dp） */
    private static final float SEGMENT_GAP_DP = 2f;
    
    /** セグメントの角丸半径（dp） */
    private static final float CORNER_RADIUS_DP = 2f;

    /** 現在のノイズ値 */
    private double currentNoiseValue = 0;
    
    /** アニメーション用の表示値 */
    private double displayValue = 0;
    
    /** 安全閾値（μT） */
    private double safeThreshold = 10.0;
    
    /** 危険閾値（μT） */
    private double dangerThreshold = 50.0;
    
    /** 最大表示値（μT） */
    private double maxValue = 100.0;

    /** セグメント描画用Paint */
    private Paint segmentPaint;
    
    /** セグメント背景用Paint */
    private Paint backgroundPaint;
    
    /** セグメントの矩形 */
    private RectF segmentRect;
    
    /** アニメーター */
    private ValueAnimator valueAnimator;

    /** 安全色（緑） */
    private static final int COLOR_SAFE = Color.parseColor("#00FF88");
    
    /** 警告色（黄） */
    private static final int COLOR_WARNING = Color.parseColor("#FFDD00");
    
    /** 危険色（赤） */
    private static final int COLOR_DANGER = Color.parseColor("#FF0055");
    
    /** 非アクティブ色 */
    private static final int COLOR_INACTIVE = Color.parseColor("#1A1A2E");

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     */
    public NoiseLevelGauge(Context context) {
        super(context);
        init();
    }

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     * @param attrs 属性セット
     */
    public NoiseLevelGauge(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * コンストラクタ
     * 
     * @param context コンテキスト
     * @param attrs 属性セット
     * @param defStyleAttr デフォルトスタイル属性
     */
    public NoiseLevelGauge(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /**
     * 初期化
     */
    private void init() {
        // セグメント描画用Paint
        segmentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        segmentPaint.setStyle(Paint.Style.FILL);
        
        // 背景用Paint
        backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        backgroundPaint.setStyle(Paint.Style.FILL);
        backgroundPaint.setColor(COLOR_INACTIVE);
        
        segmentRect = new RectF();
    }

    /**
     * 閾値を設定
     * 
     * @param safeThreshold 安全閾値（μT）
     * @param dangerThreshold 危険閾値（μT）
     */
    public void setThresholds(double safeThreshold, double dangerThreshold) {
        this.safeThreshold = safeThreshold;
        this.dangerThreshold = dangerThreshold;
        this.maxValue = dangerThreshold * 2; // 最大値は危険閾値の2倍
        invalidate();
    }

    /**
     * ノイズ値を設定（アニメーション付き）
     * 
     * @param noiseValue ノイズ値（μT）
     */
    public void setNoiseValue(double noiseValue) {
        this.currentNoiseValue = noiseValue;
        
        // 前の値から新しい値へアニメーション
        if (valueAnimator != null && valueAnimator.isRunning()) {
            valueAnimator.cancel();
        }
        
        valueAnimator = ValueAnimator.ofFloat((float) displayValue, (float) noiseValue);
        valueAnimator.setDuration(150); // 150ms
        valueAnimator.setInterpolator(new DecelerateInterpolator());
        valueAnimator.addUpdateListener(animation -> {
            displayValue = (float) animation.getAnimatedValue();
            invalidate();
        });
        valueAnimator.start();
    }
    
    /**
     * ノイズ値を即座に設定（アニメーションなし）
     * 
     * @param noiseValue ノイズ値（μT）
     */
    public void setNoiseValueImmediate(double noiseValue) {
        this.currentNoiseValue = noiseValue;
        this.displayValue = noiseValue;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        
        if (width == 0 || height == 0) return;

        float gap = dpToPx(SEGMENT_GAP_DP);
        float cornerRadius = dpToPx(CORNER_RADIUS_DP);
        float segmentWidth = (width - gap * (SEGMENT_COUNT - 1)) / SEGMENT_COUNT;
        
        // 点灯するセグメント数を計算
        double ratio = Math.min(displayValue / maxValue, 1.0);
        int activeSegments = (int) Math.ceil(ratio * SEGMENT_COUNT);

        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float left = i * (segmentWidth + gap);
            float top = 0;
            float right = left + segmentWidth;
            float bottom = height;
            
            segmentRect.set(left, top, right, bottom);
            
            boolean isActive = i < activeSegments;
            
            if (isActive) {
                // セグメントの色を決定
                int segmentColor = getSegmentColor(i);
                segmentPaint.setColor(segmentColor);
                canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, segmentPaint);
            } else {
                // 非アクティブセグメント
                canvas.drawRoundRect(segmentRect, cornerRadius, cornerRadius, backgroundPaint);
            }
        }
    }

    /**
     * セグメントインデックスに応じた色を取得
     * 
     * @param index セグメントインデックス（0〜SEGMENT_COUNT-1）
     * @return 色
     */
    private int getSegmentColor(int index) {
        // セグメント位置に対応する値を計算
        double segmentValue = (index + 1) * maxValue / SEGMENT_COUNT;
        
        if (segmentValue <= safeThreshold) {
            return COLOR_SAFE;
        } else if (segmentValue <= dangerThreshold) {
            // 警告域：緑から黄色へのグラデーション
            float ratio = (float) ((segmentValue - safeThreshold) / (dangerThreshold - safeThreshold));
            return blendColors(COLOR_SAFE, COLOR_WARNING, ratio);
        } else {
            // 危険域：黄色から赤へのグラデーション
            float ratio = (float) Math.min((segmentValue - dangerThreshold) / dangerThreshold, 1.0);
            return blendColors(COLOR_WARNING, COLOR_DANGER, ratio);
        }
    }

    /**
     * 2色をブレンド
     * 
     * @param color1 色1
     * @param color2 色2
     * @param ratio ブレンド比率（0.0〜1.0）
     * @return ブレンドされた色
     */
    private int blendColors(int color1, int color2, float ratio) {
        float inverseRatio = 1f - ratio;
        int r = (int) (Color.red(color1) * inverseRatio + Color.red(color2) * ratio);
        int g = (int) (Color.green(color1) * inverseRatio + Color.green(color2) * ratio);
        int b = (int) (Color.blue(color1) * inverseRatio + Color.blue(color2) * ratio);
        return Color.rgb(r, g, b);
    }

    /**
     * dpをpxに変換
     * 
     * @param dp dp値
     * @return px値
     */
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (valueAnimator != null) {
            valueAnimator.cancel();
        }
    }
}
