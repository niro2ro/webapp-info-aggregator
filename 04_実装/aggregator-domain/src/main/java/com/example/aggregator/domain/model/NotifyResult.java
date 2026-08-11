package com.example.aggregator.domain.model;

/** 記事×利用者の通知確定結果（article_notifications.result・smallint）。0:Delivered / 1:GaveUp。 */
public enum NotifyResult {
    DELIVERED(0), GAVE_UP(1);

    private final short code;
    NotifyResult(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static NotifyResult fromCode(short code) {
        for (NotifyResult v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の NotifyResult コード: " + code);
    }
}
