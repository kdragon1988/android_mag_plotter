/**
 * DrawingMode.java
 * 
 * VISIONOID MAG PLOTTER - 作図モード列挙
 * 
 * 概要:
 *   地図上の作図モードを定義する列挙型。
 */
package com.visionoid.magplotter.ui.map.drawing;

/**
 * 作図モード列挙型
 */
public enum DrawingMode {
    /** 非作図モード（通常の地図操作） */
    NONE,
    
    /** 多角形描画モード */
    POLYGON,
    
    /** 線描画モード */
    POLYLINE,
    
    /** 円描画モード */
    CIRCLE,
    
    /** 編集モード（頂点ドラッグ） */
    EDIT
}

