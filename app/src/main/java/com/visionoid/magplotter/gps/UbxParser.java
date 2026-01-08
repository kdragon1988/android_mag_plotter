/**
 * UbxParser.java
 * 
 * VISIONOID MAG PLOTTER - UBXプロトコルパーサー
 * 
 * 概要:
 *   u-blox UBXバイナリプロトコルをパースするクラス。
 *   シリアルストリームからUBXメッセージを抽出し、解析する。
 * 
 * UBXプロトコル構造:
 *   | Sync1 | Sync2 | Class | ID | Length(2) | Payload | CK_A | CK_B |
 *   | 0xB5  | 0x62  |  1B   | 1B |    2B     |   var   |  1B  |  1B  |
 * 
 * 対応メッセージ:
 *   - ESF-RAW: 外部センサー生データ（磁気センサー含む）
 *   - ESF-MEAS: 外部センサー計測データ
 *   - NAV-PVT: 位置・速度・時刻
 */
package com.visionoid.magplotter.gps;

import android.util.Log;

/**
 * UBXプロトコルパーサー
 */
public class UbxParser {

    private static final String TAG = "UbxParser";

    /** UBX同期文字1 */
    private static final byte SYNC_CHAR_1 = (byte) 0xB5;
    
    /** UBX同期文字2 */
    private static final byte SYNC_CHAR_2 = 0x62;

    /** パース状態 */
    private enum ParseState {
        WAIT_SYNC1,
        WAIT_SYNC2,
        WAIT_CLASS,
        WAIT_ID,
        WAIT_LENGTH1,
        WAIT_LENGTH2,
        WAIT_PAYLOAD,
        WAIT_CK_A,
        WAIT_CK_B
    }

    /** 現在のパース状態 */
    private ParseState state = ParseState.WAIT_SYNC1;

    /** メッセージクラス */
    private int messageClass;
    
    /** メッセージID */
    private int messageId;
    
    /** ペイロード長 */
    private int payloadLength;
    
    /** ペイロード受信カウント */
    private int payloadCount;
    
    /** ペイロードバッファ */
    private byte[] payloadBuffer = new byte[1024];
    
    /** チェックサムA */
    private int ckA;
    
    /** チェックサムB */
    private int ckB;
    
    /** 計算チェックサムA */
    private int calcCkA;
    
    /** 計算チェックサムB */
    private int calcCkB;

    /** メッセージ受信リスナー */
    private OnUbxMessageListener messageListener;

    /** 磁気データリスナー */
    private OnMagneticDataListener magneticListener;

    /**
     * UBXメッセージ受信リスナー
     */
    public interface OnUbxMessageListener {
        /**
         * UBXメッセージを受信した時に呼ばれる
         * @param message パース済みメッセージ
         */
        void onUbxMessage(UbxMessage message);
    }

    /**
     * 磁気データリスナー
     */
    public interface OnMagneticDataListener {
        /**
         * 磁気センサーデータを受信した時に呼ばれる
         * @param magX X軸磁場（μT）
         * @param magY Y軸磁場（μT）
         * @param magZ Z軸磁場（μT）
         * @param totalField 総磁場強度（μT）
         */
        void onMagneticData(float magX, float magY, float magZ, float totalField);
    }

    /**
     * コンストラクタ
     */
    public UbxParser() {
        reset();
    }

    /**
     * メッセージリスナーを設定
     * @param listener リスナー
     */
    public void setOnUbxMessageListener(OnUbxMessageListener listener) {
        this.messageListener = listener;
    }

    /**
     * 磁気データリスナーを設定
     * @param listener リスナー
     */
    public void setOnMagneticDataListener(OnMagneticDataListener listener) {
        this.magneticListener = listener;
    }

    /**
     * バイトデータをパース
     * @param data バイトデータ
     */
    public void parse(byte[] data) {
        if (data == null) return;
        for (byte b : data) {
            parseByte(b);
        }
    }

    /**
     * 1バイトをパース
     * @param b バイト
     */
    private void parseByte(byte b) {
        int unsignedByte = b & 0xFF;

        switch (state) {
            case WAIT_SYNC1:
                if (b == SYNC_CHAR_1) {
                    state = ParseState.WAIT_SYNC2;
                }
                break;

            case WAIT_SYNC2:
                if (b == SYNC_CHAR_2) {
                    state = ParseState.WAIT_CLASS;
                    calcCkA = 0;
                    calcCkB = 0;
                } else {
                    state = ParseState.WAIT_SYNC1;
                }
                break;

            case WAIT_CLASS:
                messageClass = unsignedByte;
                updateChecksum(unsignedByte);
                state = ParseState.WAIT_ID;
                break;

            case WAIT_ID:
                messageId = unsignedByte;
                updateChecksum(unsignedByte);
                state = ParseState.WAIT_LENGTH1;
                break;

            case WAIT_LENGTH1:
                payloadLength = unsignedByte;
                updateChecksum(unsignedByte);
                state = ParseState.WAIT_LENGTH2;
                break;

            case WAIT_LENGTH2:
                payloadLength |= (unsignedByte << 8);
                updateChecksum(unsignedByte);
                payloadCount = 0;
                
                if (payloadLength > payloadBuffer.length) {
                    payloadBuffer = new byte[payloadLength];
                }
                
                if (payloadLength > 0) {
                    state = ParseState.WAIT_PAYLOAD;
                } else {
                    state = ParseState.WAIT_CK_A;
                }
                break;

            case WAIT_PAYLOAD:
                payloadBuffer[payloadCount++] = b;
                updateChecksum(unsignedByte);
                if (payloadCount >= payloadLength) {
                    state = ParseState.WAIT_CK_A;
                }
                break;

            case WAIT_CK_A:
                ckA = unsignedByte;
                state = ParseState.WAIT_CK_B;
                break;

            case WAIT_CK_B:
                ckB = unsignedByte;
                
                // チェックサム検証
                if (ckA == (calcCkA & 0xFF) && ckB == (calcCkB & 0xFF)) {
                    // 有効なメッセージ
                    byte[] payload = new byte[payloadLength];
                    System.arraycopy(payloadBuffer, 0, payload, 0, payloadLength);
                    UbxMessage message = new UbxMessage(messageClass, messageId, payload);
                    processMessage(message);
                } else {
                    Log.w(TAG, String.format("UBXチェックサムエラー: expected %02X%02X, got %02X%02X",
                            calcCkA & 0xFF, calcCkB & 0xFF, ckA, ckB));
                }
                
                state = ParseState.WAIT_SYNC1;
                break;
        }
    }

    /**
     * チェックサムを更新
     */
    private void updateChecksum(int b) {
        calcCkA = (calcCkA + b) & 0xFF;
        calcCkB = (calcCkB + calcCkA) & 0xFF;
    }

    /**
     * パース済みメッセージを処理
     */
    private void processMessage(UbxMessage message) {
        Log.d(TAG, "UBXメッセージ受信: " + message.getMessageTypeName() + ", len=" + message.getLength());

        // メッセージリスナーに通知
        if (messageListener != null) {
            messageListener.onUbxMessage(message);
        }

        // メッセージタイプに応じた処理
        if (message.getMessageClass() == UbxMessage.CLASS_ESF) {
            if (message.getMessageId() == UbxMessage.ID_ESF_RAW) {
                parseEsfRaw(message);
            } else if (message.getMessageId() == UbxMessage.ID_ESF_MEAS) {
                parseEsfMeas(message);
            }
        }
    }

    /**
     * ESF-RAWメッセージをパース（外部センサー生データ）
     * 
     * ペイロード構造:
     *   - reserved1[4]: 予約
     *   - data[N]: センサーデータ（各4バイト）
     *     - dataType (bits 0-7): センサータイプ
     *     - dataField (bits 8-31): データ値（24ビット符号付き）
     * 
     * センサータイプ:
     *   - 14: 磁力計X
     *   - 15: 磁力計Y
     *   - 16: 磁力計Z
     */
    private void parseEsfRaw(UbxMessage message) {
        if (message.getLength() < 8) return;

        float magX = 0, magY = 0, magZ = 0;
        boolean hasMagData = false;

        // データブロックをパース（オフセット4から開始）
        int offset = 4;
        while (offset + 4 <= message.getLength()) {
            long data = message.getU4(offset);
            int dataType = (int) (data & 0xFF);
            int dataValue = (int) ((data >> 8) & 0xFFFFFF);
            
            // 24ビット符号付きに変換
            if (dataValue > 0x7FFFFF) {
                dataValue -= 0x1000000;
            }

            // センサータイプに応じて処理
            switch (dataType) {
                case 14: // 磁力計X
                    magX = dataValue * 0.001f; // ミリガウス → μT変換（要調整）
                    hasMagData = true;
                    break;
                case 15: // 磁力計Y
                    magY = dataValue * 0.001f;
                    hasMagData = true;
                    break;
                case 16: // 磁力計Z
                    magZ = dataValue * 0.001f;
                    hasMagData = true;
                    break;
            }
            
            offset += 4;
        }

        if (hasMagData && magneticListener != null) {
            float totalField = (float) Math.sqrt(magX * magX + magY * magY + magZ * magZ);
            magneticListener.onMagneticData(magX, magY, magZ, totalField);
        }
    }

    /**
     * ESF-MEASメッセージをパース（外部センサー計測データ）
     * 
     * ペイロード構造:
     *   - timeTag[4]: タイムタグ
     *   - flags[2]: フラグ
     *   - id[2]: センサーID
     *   - data[N]: センサーデータ
     */
    private void parseEsfMeas(UbxMessage message) {
        if (message.getLength() < 8) return;

        long timeTag = message.getU4(0);
        int flags = message.getU2(4);
        int numMeas = (flags >> 11) & 0x1F;

        float magX = 0, magY = 0, magZ = 0;
        boolean hasMagData = false;

        // 各計測データをパース
        int offset = 8;
        for (int i = 0; i < numMeas && offset + 4 <= message.getLength(); i++) {
            long data = message.getU4(offset);
            int dataType = (int) ((data >> 24) & 0x3F);
            int dataValue = (int) (data & 0xFFFFFF);
            
            // 24ビット符号付きに変換
            if (dataValue > 0x7FFFFF) {
                dataValue -= 0x1000000;
            }

            // センサータイプに応じて処理
            switch (dataType) {
                case 14: // 磁力計X
                    magX = dataValue * 0.001f;
                    hasMagData = true;
                    break;
                case 15: // 磁力計Y
                    magY = dataValue * 0.001f;
                    hasMagData = true;
                    break;
                case 16: // 磁力計Z
                    magZ = dataValue * 0.001f;
                    hasMagData = true;
                    break;
            }
            
            offset += 4;
        }

        if (hasMagData && magneticListener != null) {
            float totalField = (float) Math.sqrt(magX * magX + magY * magY + magZ * magZ);
            magneticListener.onMagneticData(magX, magY, magZ, totalField);
        }
    }

    /**
     * UBXメッセージを生成
     * @param cls メッセージクラス
     * @param id メッセージID
     * @param payload ペイロード
     * @return UBXメッセージバイト配列
     */
    public static byte[] buildMessage(int cls, int id, byte[] payload) {
        int payloadLen = payload != null ? payload.length : 0;
        byte[] message = new byte[8 + payloadLen];
        
        message[0] = SYNC_CHAR_1;
        message[1] = SYNC_CHAR_2;
        message[2] = (byte) cls;
        message[3] = (byte) id;
        message[4] = (byte) (payloadLen & 0xFF);
        message[5] = (byte) ((payloadLen >> 8) & 0xFF);
        
        if (payload != null && payloadLen > 0) {
            System.arraycopy(payload, 0, message, 6, payloadLen);
        }
        
        // チェックサム計算
        int ckA = 0, ckB = 0;
        for (int i = 2; i < 6 + payloadLen; i++) {
            ckA = (ckA + (message[i] & 0xFF)) & 0xFF;
            ckB = (ckB + ckA) & 0xFF;
        }
        message[6 + payloadLen] = (byte) ckA;
        message[7 + payloadLen] = (byte) ckB;
        
        return message;
    }

    /**
     * ESF-MEASメッセージ有効化コマンドを生成
     * @return コマンドバイト配列
     */
    public static byte[] buildEnableEsfMeasCommand() {
        // UBX-CFG-MSG: ESF-MEAS (0x10, 0x02) を有効化
        byte[] payload = new byte[] {
            (byte) UbxMessage.CLASS_ESF,  // msgClass
            (byte) UbxMessage.ID_ESF_MEAS, // msgID
            0x01  // rate (1Hz)
        };
        return buildMessage(UbxMessage.CLASS_CFG, 0x01, payload);
    }

    /**
     * ESF-RAWメッセージ有効化コマンドを生成
     * @return コマンドバイト配列
     */
    public static byte[] buildEnableEsfRawCommand() {
        // UBX-CFG-MSG: ESF-RAW (0x10, 0x03) を有効化
        byte[] payload = new byte[] {
            (byte) UbxMessage.CLASS_ESF,  // msgClass
            (byte) UbxMessage.ID_ESF_RAW, // msgID
            0x01  // rate (1Hz)
        };
        return buildMessage(UbxMessage.CLASS_CFG, 0x01, payload);
    }

    /**
     * パーサーをリセット
     */
    public void reset() {
        state = ParseState.WAIT_SYNC1;
        messageClass = 0;
        messageId = 0;
        payloadLength = 0;
        payloadCount = 0;
        ckA = 0;
        ckB = 0;
        calcCkA = 0;
        calcCkB = 0;
    }
}

