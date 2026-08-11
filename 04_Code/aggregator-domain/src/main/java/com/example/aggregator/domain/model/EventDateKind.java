package com.example.aggregator.domain.model;

/**
 * 発生日が「何の日か」の種別（1:発売日 / 2:開催日 / 3:放送日 / 4:受付開始 / 9:その他）。
 * 表示ラベルと「発売日順」ソートに使う（precision とは別軸）。
 */
public enum EventDateKind {
    RELEASE(1), EVENT(2), BROADCAST(3), ACCEPT_START(4), OTHER(9);

    private final short code;
    EventDateKind(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static EventDateKind fromCode(short code) {
        for (EventDateKind v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の EventDateKind コード: " + code);
    }

    /** 画面表示用の日本語ラベル（BD-SC-00-07）。 */
    public String label() {
        return switch (this) {
            case RELEASE -> "発売日";
            case EVENT -> "開催日";
            case BROADCAST -> "放送日";
            case ACCEPT_START -> "受付開始";
            case OTHER -> "";
        };
    }
}
