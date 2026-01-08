/**
 * UbxMessage.java
 * 
 * VISIONOID MAG PLOTTER - UBXメッセージデータクラス
 * 
 * 概要:
 *   u-blox UBXプロトコルのメッセージを表すデータクラス。
 *   パース済みのUBXメッセージ情報を保持する。
 * 
 * UBXプロトコル構造:
 *   - Sync Char 1: 0xB5
 *   - Sync Char 2: 0x62
 *   - Class: 1 byte
 *   - ID: 1 byte
 *   - Length: 2 bytes (little-endian)
 *   - Payload: variable
 *   - Checksum: 2 bytes (CK_A, CK_B)
 */
package com.visionoid.magplotter.gps;

/**
 * UBXメッセージ
 */
public class UbxMessage {

    // === UBXメッセージクラス定義 ===
    
    /** NAV - Navigation Results */
    public static final int CLASS_NAV = 0x01;
    
    /** RXM - Receiver Manager Messages */
    public static final int CLASS_RXM = 0x02;
    
    /** INF - Information Messages */
    public static final int CLASS_INF = 0x04;
    
    /** ACK - Ack/Nak Messages */
    public static final int CLASS_ACK = 0x05;
    
    /** CFG - Configuration Input Messages */
    public static final int CLASS_CFG = 0x06;
    
    /** UPD - Firmware Update Messages */
    public static final int CLASS_UPD = 0x09;
    
    /** MON - Monitoring Messages */
    public static final int CLASS_MON = 0x0A;
    
    /** AID - AssistNow Aiding Messages */
    public static final int CLASS_AID = 0x0B;
    
    /** TIM - Timing Messages */
    public static final int CLASS_TIM = 0x0D;
    
    /** ESF - External Sensor Fusion Messages */
    public static final int CLASS_ESF = 0x10;
    
    /** MGA - Multiple GNSS Assistance Messages */
    public static final int CLASS_MGA = 0x13;
    
    /** LOG - Logging Messages */
    public static final int CLASS_LOG = 0x21;
    
    /** SEC - Security Feature Messages */
    public static final int CLASS_SEC = 0x27;
    
    /** HNR - High Rate Navigation Results */
    public static final int CLASS_HNR = 0x28;

    // === UBXメッセージID定義 ===
    
    // NAV Class
    /** NAV-PVT: Navigation Position Velocity Time Solution */
    public static final int ID_NAV_PVT = 0x07;
    
    /** NAV-ATT: Attitude Solution */
    public static final int ID_NAV_ATT = 0x05;
    
    /** NAV-STATUS: Receiver Navigation Status */
    public static final int ID_NAV_STATUS = 0x03;
    
    // ESF Class (External Sensor Fusion)
    /** ESF-RAW: Raw Sensor Measurements */
    public static final int ID_ESF_RAW = 0x03;
    
    /** ESF-MEAS: External Sensor Fusion Measurements */
    public static final int ID_ESF_MEAS = 0x02;
    
    /** ESF-STATUS: External Sensor Fusion Status */
    public static final int ID_ESF_STATUS = 0x10;
    
    // MON Class
    /** MON-HW: Hardware Status */
    public static final int ID_MON_HW = 0x09;
    
    // HNR Class (High Navigation Rate)
    /** HNR-ATT: Attitude Solution */
    public static final int ID_HNR_ATT = 0x01;
    
    /** HNR-INS: Vehicle Dynamics Information */
    public static final int ID_HNR_INS = 0x02;

    // === インスタンス変数 ===
    
    /** メッセージクラス */
    private final int messageClass;
    
    /** メッセージID */
    private final int messageId;
    
    /** ペイロード長 */
    private final int length;
    
    /** ペイロードデータ */
    private final byte[] payload;
    
    /** 受信タイムスタンプ */
    private final long timestamp;

    /**
     * コンストラクタ
     * 
     * @param messageClass メッセージクラス
     * @param messageId メッセージID
     * @param payload ペイロードデータ
     */
    public UbxMessage(int messageClass, int messageId, byte[] payload) {
        this.messageClass = messageClass;
        this.messageId = messageId;
        this.payload = payload != null ? payload : new byte[0];
        this.length = this.payload.length;
        this.timestamp = System.currentTimeMillis();
    }

    // === Getters ===

    /**
     * メッセージクラスを取得
     * @return メッセージクラス
     */
    public int getMessageClass() {
        return messageClass;
    }

    /**
     * メッセージIDを取得
     * @return メッセージID
     */
    public int getMessageId() {
        return messageId;
    }

    /**
     * ペイロード長を取得
     * @return ペイロード長
     */
    public int getLength() {
        return length;
    }

    /**
     * ペイロードを取得
     * @return ペイロードデータ
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * タイムスタンプを取得
     * @return 受信タイムスタンプ
     */
    public long getTimestamp() {
        return timestamp;
    }

    // === ペイロード読み取りユーティリティ ===

    /**
     * ペイロードから符号なし8ビット値を読み取り
     * @param offset オフセット
     * @return 値
     */
    public int getU1(int offset) {
        if (offset >= payload.length) return 0;
        return payload[offset] & 0xFF;
    }

    /**
     * ペイロードから符号付き8ビット値を読み取り
     * @param offset オフセット
     * @return 値
     */
    public int getI1(int offset) {
        if (offset >= payload.length) return 0;
        return payload[offset];
    }

    /**
     * ペイロードから符号なし16ビット値を読み取り（リトルエンディアン）
     * @param offset オフセット
     * @return 値
     */
    public int getU2(int offset) {
        if (offset + 1 >= payload.length) return 0;
        return (payload[offset] & 0xFF) | ((payload[offset + 1] & 0xFF) << 8);
    }

    /**
     * ペイロードから符号付き16ビット値を読み取り（リトルエンディアン）
     * @param offset オフセット
     * @return 値
     */
    public int getI2(int offset) {
        int value = getU2(offset);
        if (value > 32767) {
            value -= 65536;
        }
        return value;
    }

    /**
     * ペイロードから符号なし32ビット値を読み取り（リトルエンディアン）
     * @param offset オフセット
     * @return 値
     */
    public long getU4(int offset) {
        if (offset + 3 >= payload.length) return 0;
        return (payload[offset] & 0xFFL) |
               ((payload[offset + 1] & 0xFFL) << 8) |
               ((payload[offset + 2] & 0xFFL) << 16) |
               ((payload[offset + 3] & 0xFFL) << 24);
    }

    /**
     * ペイロードから符号付き32ビット値を読み取り（リトルエンディアン）
     * @param offset オフセット
     * @return 値
     */
    public int getI4(int offset) {
        if (offset + 3 >= payload.length) return 0;
        return (payload[offset] & 0xFF) |
               ((payload[offset + 1] & 0xFF) << 8) |
               ((payload[offset + 2] & 0xFF) << 16) |
               ((payload[offset + 3] & 0xFF) << 24);
    }

    /**
     * メッセージ種別を文字列で取得
     * @return メッセージ種別文字列
     */
    public String getMessageTypeName() {
        String className = getClassName(messageClass);
        String idName = getIdName(messageClass, messageId);
        return className + "-" + idName;
    }

    /**
     * クラス名を取得
     */
    private String getClassName(int cls) {
        switch (cls) {
            case CLASS_NAV: return "NAV";
            case CLASS_RXM: return "RXM";
            case CLASS_INF: return "INF";
            case CLASS_ACK: return "ACK";
            case CLASS_CFG: return "CFG";
            case CLASS_UPD: return "UPD";
            case CLASS_MON: return "MON";
            case CLASS_AID: return "AID";
            case CLASS_TIM: return "TIM";
            case CLASS_ESF: return "ESF";
            case CLASS_MGA: return "MGA";
            case CLASS_LOG: return "LOG";
            case CLASS_SEC: return "SEC";
            case CLASS_HNR: return "HNR";
            default: return String.format("0x%02X", cls);
        }
    }

    /**
     * メッセージID名を取得
     */
    private String getIdName(int cls, int id) {
        if (cls == CLASS_NAV) {
            switch (id) {
                case ID_NAV_PVT: return "PVT";
                case ID_NAV_ATT: return "ATT";
                case ID_NAV_STATUS: return "STATUS";
            }
        } else if (cls == CLASS_ESF) {
            switch (id) {
                case ID_ESF_RAW: return "RAW";
                case ID_ESF_MEAS: return "MEAS";
                case ID_ESF_STATUS: return "STATUS";
            }
        } else if (cls == CLASS_MON) {
            switch (id) {
                case ID_MON_HW: return "HW";
            }
        } else if (cls == CLASS_HNR) {
            switch (id) {
                case ID_HNR_ATT: return "ATT";
                case ID_HNR_INS: return "INS";
            }
        }
        return String.format("0x%02X", id);
    }

    @Override
    public String toString() {
        return String.format("UbxMessage{%s, len=%d}", getMessageTypeName(), length);
    }
}

