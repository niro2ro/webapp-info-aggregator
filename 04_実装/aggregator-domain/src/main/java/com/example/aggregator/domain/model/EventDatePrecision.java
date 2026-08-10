package com.example.aggregator.domain.model;

/** 発生日の確からしさ（0:Exact / 1:Month / 2:Season / 3:Ongoing / 9:Unknown）。種別(EventDateKind)とは別軸。 */
public enum EventDatePrecision {
    EXACT(0), MONTH(1), SEASON(2), ONGOING(3), UNKNOWN(9);

    private final short code;
    EventDatePrecision(int code) { this.code = (short) code; }
    public short code() { return code; }
    public static EventDatePrecision fromCode(short code) {
        for (EventDatePrecision v : values()) if (v.code == code) return v;
        throw new IllegalArgumentException("未知の EventDatePrecision コード: " + code);
    }
}
