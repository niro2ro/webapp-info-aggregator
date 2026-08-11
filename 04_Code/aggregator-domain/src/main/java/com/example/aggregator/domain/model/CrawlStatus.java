package com.example.aggregator.domain.model;

/** 収集ログのステータス（0:Success / 1:PartialError / 2:Failed）。 */
public enum CrawlStatus {
    SUCCESS(0), PARTIAL_ERROR(1), FAILED(2);

    private final short code;
    CrawlStatus(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static CrawlStatus fromCode(short code) {
        for (CrawlStatus v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の CrawlStatus コード: " + code);
    }
}
